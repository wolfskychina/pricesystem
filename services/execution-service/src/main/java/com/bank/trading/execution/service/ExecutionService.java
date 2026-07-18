package com.bank.trading.execution.service;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.event.HedgeFillEvent;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.execution.client.ExchangeSessionClient;
import com.bank.trading.execution.dto.ExchangeOrderRequest;
import com.bank.trading.execution.dto.ExchangeOrderResponse;
import com.bank.trading.execution.dto.ExchangeTradeNotification;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.entity.HedgeTrade;
import com.bank.trading.execution.mapper.HedgeOrderMapper;
import com.bank.trading.execution.mapper.HedgeTradeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * 对冲执行服务，是 execution-service 的核心业务类。
 * <p>
 * 承担两大职责：
 * <ol>
 *   <li><b>对冲下单</b>：消费客户成交事件（{@link TradeEvent}），计算反向对冲方向与数量，
 *       调用 sim-exchange 提交对冲单（同步受理，返回 NEW）。</li>
 *   <li><b>成交处理</b>：接收 sim-exchange 的 Webhook 成交通知
 *       （{@link ExchangeTradeNotification}），更新对冲订单状态为 FILLED，
 *       持久化成交流水，并发布 {@link HedgeFillEvent} 到 Kafka。</li>
 * </ol>
 * <p>
 * <b>对冲方向计算</b>：客户 BUY → 做市商 SELL（建立空头敞口）→ 对冲 BUY；
 * 客户 SELL → 做市商 BUY（建立多头敞口）→ 对冲 SELL。即对冲方向与客户成交<b>相反</b>。
 * <p>
 * <b>对冲数量</b>：客户成交数量 × {@code hedge-ratio}（默认 1.0 = 全额对冲）。
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final HedgeOrderMapper hedgeOrderMapper;
    private final HedgeTradeMapper hedgeTradeMapper;
    private final ExchangeSessionClient exchangeSessionClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 客户成交事件 topic */
    @Value("${execution.trade-topic:trade-event}")
    private String tradeTopic;

    /** 对冲成交事件 topic */
    @Value("${execution.hedge-fill-topic:hedge-fill-event}")
    private String hedgeFillTopic;

    /** 对冲下单类型（MARKET/LIMIT） */
    @Value("${execution.hedge-order-type:MARKET}")
    private String hedgeOrderType;

    /** 对冲比例（1.0 = 全额对冲） */
    @Value("${execution.hedge-ratio:1.0}")
    private BigDecimal hedgeRatio;

    /**
     * 构造函数，通过依赖注入获取各组件。
     *
     * @param hedgeOrderMapper      对冲订单 Mapper
     * @param hedgeTradeMapper      对冲成交流水 Mapper
     * @param exchangeSessionClient 交易所会话客户端
     * @param kafkaTemplate         Kafka 生产者
     */
    public ExecutionService(HedgeOrderMapper hedgeOrderMapper,
                            HedgeTradeMapper hedgeTradeMapper,
                            ExchangeSessionClient exchangeSessionClient,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.hedgeOrderMapper = hedgeOrderMapper;
        this.hedgeTradeMapper = hedgeTradeMapper;
        this.exchangeSessionClient = exchangeSessionClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 处理客户成交事件，发起对冲下单。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>根据客户成交方向计算对冲方向（反向）</li>
     *   <li>根据 hedge-ratio 计算对冲数量</li>
     *   <li>构造对冲订单实体，状态=NEW，持久化到 hedge_orders 表</li>
     *   <li>调用交易所 {@link ExchangeSessionClient#submitOrder} 提交对冲单（同步受理）</li>
     *   <li>更新对冲订单的 exchangeOrderId（交易所返回的订单 ID）</li>
     *   <li>等待异步 Webhook 回调推送成交结果</li>
     * </ol>
     * <p>
     * 错误处理：交易所下单失败时，对冲订单状态保持 NEW 不变，后续可通过对账任务重试。
     *
     * @param event 客户成交事件
     */
    @Transactional
    public void onTradeEvent(TradeEvent event) {
        log.info("Processing trade event for hedge: tradeId={}, symbol={}, side={}, qty={}",
                event.getTradeId(), event.getSymbol(), event.getSide(), event.getQty());

        // 1. 计算对冲方向（与客户成交相反）
        String hedgeSide = calculateHedgeSide(event.getSide());
        // 2. 计算对冲数量
        BigDecimal hedgeQty = event.getQty().multiply(hedgeRatio).setScale(4, RoundingMode.HALF_UP);

        // 3. 构造并持久化对冲订单（状态=NEW）
        HedgeOrder hedgeOrder = new HedgeOrder();
        hedgeOrder.setHedgeOrderId(UUID.randomUUID().toString().replace("-", ""));
        hedgeOrder.setOriginalTradeId(event.getTradeId());
        hedgeOrder.setCustomerId(event.getCustomerId());
        hedgeOrder.setSymbol(event.getSymbol());
        hedgeOrder.setSide(hedgeSide);
        hedgeOrder.setType(hedgeOrderType);
        hedgeOrder.setQty(hedgeQty);
        hedgeOrder.setFilledQty(BigDecimal.ZERO);
        hedgeOrder.setAvgPrice(BigDecimal.ZERO);
        hedgeOrder.setStatus("NEW");
        long now = System.currentTimeMillis();
        hedgeOrder.setCreatedAt(now);
        hedgeOrder.setUpdatedAt(now);
        hedgeOrderMapper.insert(hedgeOrder);

        // 4. 向交易所提交对冲单（同步受理，返回 NEW）
        ExchangeOrderRequest request = new ExchangeOrderRequest();
        request.setClientOrderId(hedgeOrder.getHedgeOrderId());
        request.setSymbol(event.getSymbol());
        request.setSide(hedgeSide);
        request.setType(hedgeOrderType);
        request.setQty(hedgeQty);

        try {
            ExchangeOrderResponse response = exchangeSessionClient.submitOrder(request);
            if (response != null && response.getOrderId() != null) {
                // 5. 更新对冲订单的交易所订单 ID
                hedgeOrder.setExchangeOrderId(response.getOrderId());
                hedgeOrderMapper.updateByExchangeOrderId(hedgeOrder);
                log.info("Hedge order submitted to exchange: hedgeOrderId={}, exchangeOrderId={}, side={}, qty={}",
                        hedgeOrder.getHedgeOrderId(), response.getOrderId(), hedgeSide, hedgeQty);
            } else {
                log.warn("Exchange returned null order, hedge order stays NEW: hedgeOrderId={}",
                        hedgeOrder.getHedgeOrderId());
            }
        } catch (Exception e) {
            log.error("Failed to submit hedge order to exchange: hedgeOrderId={}, error={}",
                    hedgeOrder.getHedgeOrderId(), e.getMessage());
            // 下单失败，订单保持 NEW 状态，等待后续重试或对账
        }
    }

    /**
     * 处理交易所成交通知（Webhook 回调）。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>幂等校验：检查 exchange_trade_id 是否已处理过（避免重复消费回调）</li>
     *   <li>根据 exchange_order_id 查找关联的对冲订单</li>
     *   <li>持久化成交流水到 hedge_trades 表</li>
     *   <li>更新对冲订单状态为 FILLED，填充成交量与成交价</li>
     *   <li>发布 {@link HedgeFillEvent} 到 Kafka，供 position-service 消费</li>
     * </ol>
     *
     * @param notification 交易所成交通知
     */
    @Transactional
    public void onTradeNotification(ExchangeTradeNotification notification) {
        log.info("Processing trade notification: exchangeTradeId={}, orderId={}, symbol={}, qty={}",
                notification.getTradeId(), notification.getOrderId(),
                notification.getSymbol(), notification.getQty());

        // 1. 幂等校验：同一交易所成交 ID 只处理一次
        HedgeTrade existing = hedgeTradeMapper.findByExchangeTradeId(notification.getTradeId());
        if (existing != null) {
            log.info("Trade notification already processed, skip: exchangeTradeId={}",
                    notification.getTradeId());
            return;
        }

        // 2. 查找关联的对冲订单
        HedgeOrder hedgeOrder = hedgeOrderMapper.findByExchangeOrderId(notification.getOrderId());
        if (hedgeOrder == null) {
            log.warn("Hedge order not found for exchangeOrderId: {}, skip notification",
                    notification.getOrderId());
            return;
        }

        // 3. 持久化成交流水
        HedgeTrade hedgeTrade = new HedgeTrade();
        hedgeTrade.setHedgeOrderId(hedgeOrder.getHedgeOrderId());
        hedgeTrade.setExchangeOrderId(notification.getOrderId());
        hedgeTrade.setExchangeTradeId(notification.getTradeId());
        hedgeTrade.setOriginalTradeId(hedgeOrder.getOriginalTradeId());
        hedgeTrade.setSymbol(notification.getSymbol());
        hedgeTrade.setSide(notification.getSide());
        hedgeTrade.setQty(notification.getQty());
        hedgeTrade.setPrice(notification.getPrice());
        hedgeTrade.setAmount(notification.getAmount());
        hedgeTrade.setTradeTime(notification.getTradeTime());
        hedgeTrade.setCreatedAt(System.currentTimeMillis());
        hedgeTradeMapper.insert(hedgeTrade);

        // 4. 更新对冲订单状态为 FILLED
        hedgeOrder.setFilledQty(notification.getQty());
        hedgeOrder.setAvgPrice(notification.getPrice());
        hedgeOrder.setStatus("FILLED");
        hedgeOrder.setUpdatedAt(System.currentTimeMillis());
        hedgeOrderMapper.updateByExchangeOrderId(hedgeOrder);

        log.info("Hedge order filled: hedgeOrderId={}, exchangeOrderId={}, price={}, qty={}",
                hedgeOrder.getHedgeOrderId(), hedgeOrder.getExchangeOrderId(),
                notification.getPrice(), notification.getQty());

        // 5. 发布对冲成交事件到 Kafka
        publishHedgeFillEvent(hedgeOrder, hedgeTrade);
    }

    /**
     * 处理交易所订单状态回报（Webhook 回调）。
     * <p>
     * 当订单状态变为 ACCEPTED/REJECTED 时，sim-exchange 会推送订单状态回报。
     * 本方法更新本地对冲订单状态，使运维可查询订单进展。
     *
     * @param order 交易所推送的订单状态
     */
    @Transactional
    public void onOrderNotification(ExchangeOrderResponse order) {
        log.info("Processing order notification: exchangeOrderId={}, status={}",
                order.getOrderId(), order.getStatus());

        HedgeOrder hedgeOrder = hedgeOrderMapper.findByExchangeOrderId(order.getOrderId());
        if (hedgeOrder == null) {
            log.warn("Hedge order not found for exchangeOrderId: {}, skip",
                    order.getOrderId());
            return;
        }

        // 仅更新非终态字段，避免覆盖成交回调的结果
        hedgeOrder.setStatus(order.getStatus());
        if (order.getFilledQty() != null) {
            hedgeOrder.setFilledQty(order.getFilledQty());
        }
        if (order.getAvgPrice() != null) {
            hedgeOrder.setAvgPrice(order.getAvgPrice());
        }
        hedgeOrder.setUpdatedAt(System.currentTimeMillis());
        hedgeOrderMapper.updateByExchangeOrderId(hedgeOrder);
    }

    /**
     * 发布对冲成交事件到 Kafka。
     * <p>
     * 以 symbol 作为分区键，保证同一合约的对冲事件有序消费，
     * 便于 position-service 正确累加对冲头寸。
     *
     * @param hedgeOrder 对冲订单
     * @param hedgeTrade 对冲成交流水
     */
    private void publishHedgeFillEvent(HedgeOrder hedgeOrder, HedgeTrade hedgeTrade) {
        try {
            HedgeFillEvent event = new HedgeFillEvent(hedgeOrder.getSymbol());
            event.setHedgeTradeId(hedgeTrade.getExchangeTradeId());
            event.setSymbol(hedgeOrder.getSymbol());
            event.setSide(hedgeOrder.getSide());
            event.setQty(hedgeTrade.getQty());
            event.setPrice(hedgeTrade.getPrice());
            event.setAmount(hedgeTrade.getAmount());
            event.setTradeTime(hedgeTrade.getTradeTime());
            event.setOriginalTradeId(hedgeOrder.getOriginalTradeId());

            String json = com.alibaba.fastjson2.JSON.toJSONString(event);
            kafkaTemplate.send(hedgeFillTopic, hedgeOrder.getSymbol(), json);
            log.info("Hedge fill event published: topic={}, key={}, hedgeTradeId={}",
                    hedgeFillTopic, hedgeOrder.getSymbol(), hedgeTrade.getExchangeTradeId());
        } catch (Exception e) {
            log.error("Failed to publish hedge fill event: hedgeOrderId={}, error={}",
                    hedgeOrder.getHedgeOrderId(), e.getMessage());
        }
    }

    /**
     * 计算对冲方向（与客户成交相反）。
     * <p>
     * 客户 BUY → 做市商 SELL → 对冲 BUY（买回平掉空头）
     * 客户 SELL → 做市商 BUY → 对冲 SELL（卖出平掉多头）
     *
     * @param customerSide 客户成交方向
     * @return 对冲方向
     */
    private String calculateHedgeSide(String customerSide) {
        OrderSide side = OrderSide.of(customerSide);
        return side == OrderSide.BUY ? "SELL" : "BUY";
    }

    /**
     * 查询最近的对冲订单（用于监控与展示）。
     *
     * @param limit 返回条数
     * @return 对冲订单列表
     */
    public List<HedgeOrder> findRecentHedgeOrders(int limit) {
        return hedgeOrderMapper.findRecent(limit);
    }

    /**
     * 查询指定对冲订单的成交流水。
     *
     * @param hedgeOrderId 对冲订单内部 ID
     * @return 成交流水列表
     */
    public List<HedgeTrade> findHedgeTrades(String hedgeOrderId) {
        return hedgeTradeMapper.findByHedgeOrderId(hedgeOrderId);
    }
}
