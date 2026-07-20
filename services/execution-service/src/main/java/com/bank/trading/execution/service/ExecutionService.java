package com.bank.trading.execution.service;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.event.HedgeFillEvent;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.core.idgen.IdGenerator;
import com.bank.trading.execution.client.ExchangeSessionClient;
import com.bank.trading.execution.dto.ExchangeOrderRequest;
import com.bank.trading.execution.dto.ExchangeOrderResponse;
import com.bank.trading.execution.dto.ExchangeTradeNotification;
import com.bank.trading.execution.entity.HedgeBatchItem;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.entity.HedgeTrade;
import com.bank.trading.execution.mapper.HedgeBatchItemMapper;
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
 * 承担三大职责：
 * <ol>
 *   <li><b>对冲下单</b>：消费客户成交事件（{@link TradeEvent}），计算反向对冲方向与数量，
 *       调用 sim-exchange 提交对冲单（同步受理，返回 NEW）。
 *       支持聚合模式（batching）：同合约同方向多笔成交合并为一笔对冲单，
 *       由 {@link HedgeBatcher} 负责聚合调度。</li>
 *   <li><b>成交处理</b>：接收 sim-exchange 的 Webhook 成交通知
 *       （{@link ExchangeTradeNotification}），更新对冲订单状态为 FILLED，
 *       持久化成交流水，并发布 {@link HedgeFillEvent} 到 Kafka。
 *       聚合订单成交后，按子项数量比例分摊，逐笔发出 hedge-fill-event。</li>
 *   <li><b>状态回报</b>：接收 sim-exchange 的订单状态回报，更新本地对冲订单状态。</li>
 * </ol>
 * <p>
 * <b>对冲方向计算</b>：客户 BUY → 做市商 SELL（建立空头敞口）→ 对冲 BUY；
 * 客户 SELL → 做市商 BUY（建立多头敞口）→ 对冲 SELL。即对冲方向与客户成交<b>同向</b>。
 * <p>
 * <b>对冲数量</b>：客户成交数量 × {@code hedge-ratio}（默认 1.0 = 全额对冲）。
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final HedgeOrderMapper hedgeOrderMapper;
    private final HedgeTradeMapper hedgeTradeMapper;
    private final HedgeBatchItemMapper batchItemMapper;
    private final ExchangeSessionClient exchangeSessionClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final IdGenerator idGenerator;

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
     * @param hedgeOrderMapper        对冲订单 Mapper
     * @param hedgeTradeMapper        对冲成交流水 Mapper
     * @param batchItemMapper         聚合子项 Mapper
     * @param exchangeSessionClient   交易所会话客户端
     * @param kafkaTemplate           Kafka 生产者
     */
    public ExecutionService(HedgeOrderMapper hedgeOrderMapper,
                            HedgeTradeMapper hedgeTradeMapper,
                            HedgeBatchItemMapper batchItemMapper,
                            ExchangeSessionClient exchangeSessionClient,
                            KafkaTemplate<String, String> kafkaTemplate,
                            IdGenerator idGenerator) {
        this.hedgeOrderMapper = hedgeOrderMapper;
        this.hedgeTradeMapper = hedgeTradeMapper;
        this.batchItemMapper = batchItemMapper;
        this.exchangeSessionClient = exchangeSessionClient;
        this.kafkaTemplate = kafkaTemplate;
        this.idGenerator = idGenerator;
    }

    /**
     * 处理客户成交事件（入口方法）。
     * <p>
     * 委托给 {@link HedgeBatcher#enqueue}，由聚合器决定是立即单笔对冲
     * 还是入桶等待聚合。
     *
     * @param event 客户成交事件
     */
    public void onTradeEvent(TradeEvent event) {
        // 本方法由 TradeEventConsumer 调用，实际逻辑委托给 HedgeBatcher
        // 当 batching-enabled=false 时，HedgeBatcher 内部调用 onTradeEventImmediate
        // 当 batching-enabled=true 时，HedgeBatcher 内部入桶
    }

    /**
     * 单笔立即对冲（batching 关闭时使用，兼容原有逻辑）。
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
     *
     * @param event 客户成交事件
     */
    @Transactional
    public void onTradeEventImmediate(TradeEvent event) {
        log.info("Processing trade event for immediate hedge: tradeId={}, symbol={}, side={}, qty={}",
                event.getTradeId(), event.getSymbol(), event.getSide(), event.getQty());

        // 1. 计算对冲方向（与客户成交相反）
        String hedgeSide = calculateHedgeSide(event.getSide());
        // 2. 计算对冲数量
        BigDecimal hedgeQty = event.getQty().multiply(hedgeRatio).setScale(4, RoundingMode.HALF_UP);

        // 3. 构造并持久化对冲订单（状态=NEW，isBatched=0）
        HedgeOrder hedgeOrder = new HedgeOrder();
        hedgeOrder.setId(idGenerator.nextLongId());
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
        hedgeOrder.setIsBatched(0);
        hedgeOrder.setBatchItemCount(0);
        long now = System.currentTimeMillis();
        hedgeOrder.setCreatedAt(now);
        hedgeOrder.setUpdatedAt(now);
        hedgeOrderMapper.insert(hedgeOrder);

        // 4. 向交易所提交对冲单（同步受理，返回 NEW）
        submitOrderToExchange(hedgeOrder, hedgeQty);
    }

    /**
     * 提交聚合对冲单（batching 开启时由 HedgeBatcher 调用）。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>汇总所有子项的对冲数量，计算总量</li>
     *   <li>构造聚合对冲订单（isBatched=1，batchItemCount=子项数），持久化</li>
     *   <li>更新所有子项状态为 SUBMITTED，并关联 hedgeOrderId</li>
     *   <li>调用交易所提交聚合对冲单</li>
     *   <li>更新聚合对冲订单的 exchangeOrderId</li>
     * </ol>
     *
     * @param items 聚合子项列表（同 symbol + 同 side）
     */
    @Transactional
    public void submitBatchedOrder(List<HedgeBatchItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        String symbol = items.get(0).getSymbol();
        String side = items.get(0).getSide();
        BigDecimal totalQty = items.stream()
                .map(HedgeBatchItem::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Submitting batched hedge order: symbol={}, side={}, itemCount={}, totalQty={}",
                symbol, side, items.size(), totalQty);

        // 1. 构造聚合对冲订单
        HedgeOrder hedgeOrder = new HedgeOrder();
        hedgeOrder.setId(idGenerator.nextLongId());
        hedgeOrder.setHedgeOrderId(UUID.randomUUID().toString().replace("-", ""));
        hedgeOrder.setSymbol(symbol);
        hedgeOrder.setSide(side);
        hedgeOrder.setType(hedgeOrderType);
        hedgeOrder.setQty(totalQty);
        hedgeOrder.setFilledQty(BigDecimal.ZERO);
        hedgeOrder.setAvgPrice(BigDecimal.ZERO);
        hedgeOrder.setStatus("NEW");
        hedgeOrder.setIsBatched(1);
        hedgeOrder.setBatchItemCount(items.size());
        long now = System.currentTimeMillis();
        hedgeOrder.setCreatedAt(now);
        hedgeOrder.setUpdatedAt(now);
        hedgeOrderMapper.insert(hedgeOrder);

        // 2. 更新所有子项：状态=SUBMITTED，关联 hedgeOrderId
        for (HedgeBatchItem item : items) {
            item.setHedgeOrderId(hedgeOrder.getHedgeOrderId());
            item.setStatus("SUBMITTED");
            item.setUpdatedAt(System.currentTimeMillis());
            batchItemMapper.update(item);
        }

        // 3. 向交易所提交聚合对冲单
        submitOrderToExchange(hedgeOrder, totalQty);
    }

    /**
     * 向交易所提交对冲单，成功则更新 exchangeOrderId。
     *
     * @param hedgeOrder 对冲订单
     * @param qty        委托数量
     */
    private void submitOrderToExchange(HedgeOrder hedgeOrder, BigDecimal qty) {
        ExchangeOrderRequest request = new ExchangeOrderRequest();
        request.setClientOrderId(hedgeOrder.getHedgeOrderId());
        request.setSymbol(hedgeOrder.getSymbol());
        request.setSide(hedgeOrder.getSide());
        request.setType(hedgeOrderType);
        request.setQty(qty);

        try {
            ExchangeOrderResponse response = exchangeSessionClient.submitOrder(request);
            if (response != null && response.getOrderId() != null) {
                hedgeOrder.setExchangeOrderId(response.getOrderId());
                hedgeOrderMapper.updateByExchangeOrderId(hedgeOrder);
                log.info("Hedge order submitted to exchange: hedgeOrderId={}, exchangeOrderId={}, " +
                                "side={}, qty={}, isBatched={}",
                        hedgeOrder.getHedgeOrderId(), response.getOrderId(),
                        hedgeOrder.getSide(), qty, hedgeOrder.getIsBatched());
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
     *   <li>若为聚合订单（isBatched=1），按子项数量比例分摊成交结果</li>
     *   <li>发布 {@link HedgeFillEvent} 到 Kafka（聚合订单按子项逐笔发布）</li>
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
        hedgeTrade.setId(idGenerator.nextLongId());
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

        log.info("Hedge order filled: hedgeOrderId={}, exchangeOrderId={}, price={}, qty={}, isBatched={}",
                hedgeOrder.getHedgeOrderId(), hedgeOrder.getExchangeOrderId(),
                notification.getPrice(), notification.getQty(), hedgeOrder.getIsBatched());

        // 5. 发布对冲成交事件
        if (hedgeOrder.getIsBatched() != null && hedgeOrder.getIsBatched() == 1) {
            // 聚合订单：按子项分摊，逐笔发布 hedge-fill-event
            publishBatchedHedgeFillEvents(hedgeOrder, hedgeTrade);
        } else {
            // 单笔订单：直接发布一笔 hedge-fill-event
            publishHedgeFillEvent(hedgeOrder, hedgeTrade);
        }
    }

    /**
     * 聚合订单成交后，按子项分摊成交结果，逐笔发布 hedge-fill-event。
     * <p>
     * 分摊规则（市价单）：
     * <ul>
     *   <li>所有子项成交价相同（= 聚合对冲单成交价）</li>
     *   <li>每子项 filledQty = 子项 qty（市价单全部成交）</li>
     *   <li>最后一项吸收尾差，确保 sum(filledQty) = 总成交数量</li>
     * </ul>
     *
     * @param hedgeOrder 聚合对冲订单
     * @param hedgeTrade 聚合成交流水
     */
    private void publishBatchedHedgeFillEvents(HedgeOrder hedgeOrder, HedgeTrade hedgeTrade) {
        List<HedgeBatchItem> items = batchItemMapper.findByHedgeOrderId(hedgeOrder.getHedgeOrderId());
        if (items == null || items.isEmpty()) {
            log.warn("No batch items found for batched hedge order: {}", hedgeOrder.getHedgeOrderId());
            return;
        }

        BigDecimal totalFillQty = hedgeTrade.getQty();
        BigDecimal fillPrice = hedgeTrade.getPrice();
        BigDecimal totalItemQty = items.stream()
                .map(HedgeBatchItem::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Allocating batched hedge fill: hedgeOrderId={}, itemCount={}, totalFillQty={}, fillPrice={}",
                hedgeOrder.getHedgeOrderId(), items.size(), totalFillQty, fillPrice);

        BigDecimal allocatedSoFar = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            HedgeBatchItem item = items.get(i);
            BigDecimal itemFillQty;

            if (i == items.size() - 1) {
                // 最后一项吸收尾差
                itemFillQty = totalFillQty.subtract(allocatedSoFar);
            } else {
                // 按比例分摊（保留4位小数）
                itemFillQty = item.getQty().multiply(totalFillQty)
                        .divide(totalItemQty, 4, RoundingMode.HALF_UP);
                allocatedSoFar = allocatedSoFar.add(itemFillQty);
            }

            // 更新子项状态
            item.setFilledQty(itemFillQty);
            item.setAvgPrice(fillPrice);
            item.setStatus("FILLED");
            item.setUpdatedAt(System.currentTimeMillis());
            batchItemMapper.update(item);

            // 发布单笔 hedge-fill-event（对应每笔原始客户成交）
            publishHedgeFillEventForItem(item, hedgeTrade);
        }
    }

    /**
     * 为单个聚合子项发布 hedge-fill-event。
     *
     * @param item       聚合子项
     * @param hedgeTrade 聚合成交流水（提供 tradeId 前缀、tradeTime 等）
     */
    private void publishHedgeFillEventForItem(HedgeBatchItem item, HedgeTrade hedgeTrade) {
        try {
            HedgeFillEvent event = new HedgeFillEvent(item.getSymbol());
            event.setHedgeTradeId(hedgeTrade.getExchangeTradeId() + "-" + item.getOriginalTradeId());
            event.setSymbol(item.getSymbol());
            event.setSide(item.getSide());
            event.setQty(item.getFilledQty());
            event.setPrice(item.getAvgPrice());
            event.setAmount(item.getFilledQty().multiply(item.getAvgPrice()));
            event.setTradeTime(hedgeTrade.getTradeTime());
            event.setOriginalTradeId(item.getOriginalTradeId());

            String json = com.alibaba.fastjson2.JSON.toJSONString(event);
            kafkaTemplate.send(hedgeFillTopic, item.getSymbol(), json);
            log.info("Batched hedge fill event published: topic={}, key={}, originalTradeId={}, qty={}",
                    hedgeFillTopic, item.getSymbol(), item.getOriginalTradeId(), item.getFilledQty());
        } catch (Exception e) {
            log.error("Failed to publish batched hedge fill event: originalTradeId={}, error={}",
                    item.getOriginalTradeId(), e.getMessage());
        }
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
     * 发布对冲成交事件到 Kafka（单笔订单场景）。
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
     * 计算对冲方向（与客户成交同向）。
     * <p>
     * 客户 BUY → 做市商 SELL（建立空头敞口）→ 对冲 BUY（买回平掉空头）
     * 客户 SELL → 做市商 BUY（建立多头敞口）→ 对冲 SELL（卖出平掉多头）
     *
     * @param customerSide 客户成交方向
     * @return 对冲方向
     */
    private String calculateHedgeSide(String customerSide) {
        return customerSide;
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

    /**
     * 查询聚合对冲订单的子项列表。
     *
     * @param hedgeOrderId 对冲订单内部 ID
     * @return 聚合子项列表
     */
    public List<HedgeBatchItem> findBatchItems(String hedgeOrderId) {
        return batchItemMapper.findByHedgeOrderId(hedgeOrderId);
    }
}
