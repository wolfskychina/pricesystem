package com.bank.trading.execution.service;

import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.execution.client.ExchangeSessionClient;
import com.bank.trading.execution.dto.ExchangeOrderRequest;
import com.bank.trading.execution.dto.ExchangeOrderResponse;
import com.bank.trading.execution.dto.ExchangeTradeNotification;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.entity.HedgeTrade;
import com.bank.trading.execution.mapper.HedgeOrderMapper;
import com.bank.trading.execution.mapper.HedgeTradeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionService 单元测试。
 * <p>
 * 采用手写 Mock 实现类（Java 25 + Mockito 不兼容）：
 * <ul>
 *   <li>{@link InMemoryHedgeOrderMapper}：内存版对冲订单 Mapper</li>
 *   <li>{@link InMemoryHedgeTradeMapper}：内存版对冲成交 Mapper</li>
 *   <li>{@link StubExchangeSessionClient}：固定返回的交易所客户端</li>
 *   <li>{@link CapturingKafkaTemplate}：记录发送消息的 KafkaTemplate</li>
 * </ul>
 */
class ExecutionServiceTest {

    private ExecutionService executionService;
    private InMemoryHedgeOrderMapper hedgeOrderMapper;
    private InMemoryHedgeTradeMapper hedgeTradeMapper;
    private StubExchangeSessionClient exchangeSessionClient;
    private CapturingKafkaTemplate kafkaTemplate;

    @BeforeEach
    void setUp() {
        hedgeOrderMapper = new InMemoryHedgeOrderMapper();
        hedgeTradeMapper = new InMemoryHedgeTradeMapper();
        exchangeSessionClient = new StubExchangeSessionClient();
        kafkaTemplate = new CapturingKafkaTemplate();
        executionService = new ExecutionService(hedgeOrderMapper, hedgeTradeMapper,
                exchangeSessionClient, kafkaTemplate);

        // 通过反射设置 @Value 字段
        setField(executionService, "tradeTopic", "trade-event");
        setField(executionService, "hedgeFillTopic", "hedge-fill-event");
        setField(executionService, "hedgeOrderType", "MARKET");
        setField(executionService, "hedgeRatio", new BigDecimal("1.0"));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 对冲下单测试 ====================

    @Test
    @DisplayName("客户买入成交 → 对冲卖出方向")
    void onTradeEvent_customerBuy_hedgeSell() {
        TradeEvent event = buildTradeEvent("T001", "AU2406", "BUY", new BigDecimal("10"));

        executionService.onTradeEvent(event);

        assertEquals(1, hedgeOrderMapper.orders.size());
        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("SELL", hedgeOrder.getSide(), "客户 BUY 应触发对冲 SELL");
        assertEquals("AU2406", hedgeOrder.getSymbol());
        assertDecimalEquals(new BigDecimal("10.0000"), hedgeOrder.getQty());
        assertEquals("NEW", hedgeOrder.getStatus());
        assertEquals("T001", hedgeOrder.getOriginalTradeId());
    }

    @Test
    @DisplayName("客户卖出成交 → 对冲买入方向")
    void onTradeEvent_customerSell_hedgeBuy() {
        TradeEvent event = buildTradeEvent("T002", "AU2406", "SELL", new BigDecimal("5"));

        executionService.onTradeEvent(event);

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("BUY", hedgeOrder.getSide(), "客户 SELL 应触发对冲 BUY");
    }

    @Test
    @DisplayName("对冲下单成功后，对冲订单关联交易所订单 ID")
    void onTradeEvent_submitSuccess_storesExchangeOrderId() {
        exchangeSessionClient.responseOrderId = "EXCH-ORDER-001";
        TradeEvent event = buildTradeEvent("T003", "AU2406", "BUY", new BigDecimal("10"));

        executionService.onTradeEvent(event);

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("EXCH-ORDER-001", hedgeOrder.getExchangeOrderId());
    }

    @Test
    @DisplayName("对冲数量按 hedge-ratio 计算（0.5 = 半额对冲）")
    void onTradeEvent_halfHedgeRatio_halfQty() {
        setField(executionService, "hedgeRatio", new BigDecimal("0.5"));
        TradeEvent event = buildTradeEvent("T004", "AU2406", "BUY", new BigDecimal("10"));

        executionService.onTradeEvent(event);

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertDecimalEquals(new BigDecimal("5.0000"), hedgeOrder.getQty());
    }

    @Test
    @DisplayName("交易所下单失败时，对冲订单保持 NEW 状态不抛异常")
    void onTradeEvent_exchangeFailure_orderStaysNew() {
        exchangeSessionClient.throwException = true;
        TradeEvent event = buildTradeEvent("T005", "AU2406", "BUY", new BigDecimal("10"));

        // 不应抛异常
        assertDoesNotThrow(() -> executionService.onTradeEvent(event));

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("NEW", hedgeOrder.getStatus());
        assertNull(hedgeOrder.getExchangeOrderId());
    }

    @Test
    @DisplayName("交易所返回 null 时，对冲订单保持 NEW 状态")
    void onTradeEvent_exchangeReturnsNull_orderStaysNew() {
        exchangeSessionClient.returnNull = true;
        TradeEvent event = buildTradeEvent("T006", "AU2406", "BUY", new BigDecimal("10"));

        executionService.onTradeEvent(event);

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("NEW", hedgeOrder.getStatus());
        assertNull(hedgeOrder.getExchangeOrderId());
    }

    @Test
    @DisplayName("向交易所提交的对冲请求字段正确")
    void onTradeEvent_submittedRequestFieldsCorrect() {
        TradeEvent event = buildTradeEvent("T007", "AG2406", "SELL", new BigDecimal("20"));

        executionService.onTradeEvent(event);

        ExchangeOrderRequest submitted = exchangeSessionClient.lastSubmittedRequest;
        assertNotNull(submitted);
        assertEquals("AG2406", submitted.getSymbol());
        assertEquals("BUY", submitted.getSide(), "客户 SELL → 对冲 BUY");
        assertEquals("MARKET", submitted.getType());
        assertDecimalEquals(new BigDecimal("20.0000"), submitted.getQty());
    }

    // ==================== 成交通知回调测试 ====================

    @Test
    @DisplayName("收到成交通知 → 对冲订单状态更新为 FILLED")
    void onTradeNotification_orderBecomesFilled() {
        // 先下单
        exchangeSessionClient.responseOrderId = "EXCH-001";
        executionService.onTradeEvent(buildTradeEvent("T010", "AU2406", "BUY", new BigDecimal("10")));
        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);

        // 收到成交通知
        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-TRADE-1", "EXCH-001", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));
        executionService.onTradeNotification(notification);

        HedgeOrder updated = hedgeOrderMapper.findByExchangeOrderId("EXCH-001");
        assertEquals("FILLED", updated.getStatus());
        assertDecimalEquals(new BigDecimal("10"), updated.getFilledQty());
        assertDecimalEquals(new BigDecimal("520.50"), updated.getAvgPrice());
    }

    @Test
    @DisplayName("收到成交通知 → 持久化成交流水")
    void onTradeNotification_persistsHedgeTrade() {
        exchangeSessionClient.responseOrderId = "EXCH-002";
        executionService.onTradeEvent(buildTradeEvent("T011", "AU2406", "BUY", new BigDecimal("10")));

        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-TRADE-2", "EXCH-002", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));
        executionService.onTradeNotification(notification);

        assertEquals(1, hedgeTradeMapper.trades.size());
        HedgeTrade trade = hedgeTradeMapper.trades.get(0);
        assertEquals("EXCH-TRADE-2", trade.getExchangeTradeId());
        assertEquals("EXCH-002", trade.getExchangeOrderId());
        assertEquals("T011", trade.getOriginalTradeId());
        assertDecimalEquals(new BigDecimal("10"), trade.getQty());
        assertDecimalEquals(new BigDecimal("520.50"), trade.getPrice());
    }

    @Test
    @DisplayName("收到成交通知 → 发布 hedge-fill-event 到 Kafka")
    void onTradeNotification_publishesHedgeFillEvent() {
        exchangeSessionClient.responseOrderId = "EXCH-003";
        executionService.onTradeEvent(buildTradeEvent("T012", "AU2406", "BUY", new BigDecimal("10")));

        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-TRADE-3", "EXCH-003", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));
        executionService.onTradeNotification(notification);

        assertEquals(1, kafkaTemplate.sentMessages.size());
        CapturingKafkaTemplate.SentMessage msg = kafkaTemplate.sentMessages.get(0);
        assertEquals("hedge-fill-event", msg.topic);
        assertEquals("AU2406", msg.key, "分区键应为 symbol");
        assertTrue(msg.value.contains("AU2406"));
        assertTrue(msg.value.contains("SELL"));
        assertTrue(msg.value.contains("EXCH-TRADE-3"));
    }

    @Test
    @DisplayName("重复的成交通知被幂等跳过")
    void onTradeNotification_duplicateTradeId_skipped() {
        exchangeSessionClient.responseOrderId = "EXCH-004";
        executionService.onTradeEvent(buildTradeEvent("T013", "AU2406", "BUY", new BigDecimal("10")));

        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-TRADE-4", "EXCH-004", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));

        // 第一次处理
        executionService.onTradeNotification(notification);
        // 第二次重复处理
        executionService.onTradeNotification(notification);

        assertEquals(1, hedgeTradeMapper.trades.size(), "重复通知不应产生第二条流水");
        assertEquals(1, kafkaTemplate.sentMessages.size(), "重复通知不应发布第二条事件");
    }

    @Test
    @DisplayName("成交通知对应的对冲订单不存在时跳过")
    void onTradeNotification_hedgeOrderNotFound_skipped() {
        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-TRADE-5", "NON-EXISTENT", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));

        executionService.onTradeNotification(notification);

        assertEquals(0, hedgeTradeMapper.trades.size());
        assertEquals(0, kafkaTemplate.sentMessages.size());
    }

    // ==================== 订单状态回报测试 ====================

    @Test
    @DisplayName("收到订单状态回报 → 更新对冲订单状态")
    void onOrderNotification_updatesStatus() {
        exchangeSessionClient.responseOrderId = "EXCH-005";
        executionService.onTradeEvent(buildTradeEvent("T020", "AU2406", "BUY", new BigDecimal("10")));

        ExchangeOrderResponse orderCallback = new ExchangeOrderResponse();
        orderCallback.setOrderId("EXCH-005");
        orderCallback.setStatus("ACCEPTED");
        executionService.onOrderNotification(orderCallback);

        HedgeOrder updated = hedgeOrderMapper.findByExchangeOrderId("EXCH-005");
        assertEquals("ACCEPTED", updated.getStatus());
    }

    @Test
    @DisplayName("收到订单状态回报但订单不存在时跳过")
    void onOrderNotification_orderNotFound_skipped() {
        ExchangeOrderResponse orderCallback = new ExchangeOrderResponse();
        orderCallback.setOrderId("NON-EXISTENT");
        orderCallback.setStatus("ACCEPTED");

        assertDoesNotThrow(() -> executionService.onOrderNotification(orderCallback));
    }

    // ==================== 辅助方法 ====================

    private TradeEvent buildTradeEvent(String tradeId, String symbol, String side, BigDecimal qty) {
        TradeEvent event = new TradeEvent("C001");
        event.setTradeId(tradeId);
        event.setOrderId("ORD-" + tradeId);
        event.setSymbol(symbol);
        event.setSide(side);
        event.setQty(qty);
        event.setPrice(new BigDecimal("520.50"));
        event.setAmount(qty.multiply(new BigDecimal("520.50")));
        event.setTradeTime(System.currentTimeMillis());
        return event;
    }

    private ExchangeTradeNotification buildTradeNotification(String tradeId, String orderId,
            String symbol, String side, BigDecimal qty, BigDecimal price) {
        ExchangeTradeNotification n = new ExchangeTradeNotification();
        n.setTradeId(tradeId);
        n.setOrderId(orderId);
        n.setSymbol(symbol);
        n.setSide(side);
        n.setQty(qty);
        n.setPrice(price);
        n.setAmount(qty.multiply(price));
        n.setTradeTime(System.currentTimeMillis());
        return n;
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                "expected: <" + expected + "> but was: <" + actual + ">");
    }

    // ==================== Mock 实现类 ====================

    static class InMemoryHedgeOrderMapper implements HedgeOrderMapper {
        final List<HedgeOrder> orders = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgeOrder order) {
            order.setId(++idSeq);
            orders.add(order);
            return 1;
        }
        @Override public HedgeOrder findByHedgeOrderId(String hedgeOrderId) {
            return orders.stream().filter(o -> hedgeOrderId.equals(o.getHedgeOrderId())).findFirst().orElse(null);
        }
        @Override public HedgeOrder findByExchangeOrderId(String exchangeOrderId) {
            return orders.stream().filter(o -> exchangeOrderId.equals(o.getExchangeOrderId())).findFirst().orElse(null);
        }
        @Override public int updateByExchangeOrderId(HedgeOrder order) {
            for (int i = 0; i < orders.size(); i++) {
                HedgeOrder existing = orders.get(i);
                if (order.getExchangeOrderId() != null && order.getExchangeOrderId().equals(existing.getExchangeOrderId())) {
                    existing.setStatus(order.getStatus());
                    existing.setFilledQty(order.getFilledQty());
                    existing.setAvgPrice(order.getAvgPrice());
                    existing.setUpdatedAt(order.getUpdatedAt());
                    return 1;
                }
            }
            return 0;
        }
        @Override public List<HedgeOrder> findRecent(int limit) {
            return orders.stream().limit(limit).toList();
        }
        @Override public int countByStatus(String status) {
            return (int) orders.stream().filter(o -> status.equals(o.getStatus())).count();
        }
    }

    static class InMemoryHedgeTradeMapper implements HedgeTradeMapper {
        final List<HedgeTrade> trades = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgeTrade trade) {
            trade.setId(++idSeq);
            trades.add(trade);
            return 1;
        }
        @Override public List<HedgeTrade> findByHedgeOrderId(String hedgeOrderId) {
            return trades.stream().filter(t -> hedgeOrderId.equals(t.getHedgeOrderId())).toList();
        }
        @Override public HedgeTrade findByExchangeTradeId(String exchangeTradeId) {
            return trades.stream().filter(t -> exchangeTradeId.equals(t.getExchangeTradeId())).findFirst().orElse(null);
        }
        @Override public List<HedgeTrade> findRecent(int limit) {
            return trades.stream().limit(limit).toList();
        }
    }

    static class StubExchangeSessionClient extends ExchangeSessionClient {
        ExchangeOrderRequest lastSubmittedRequest;
        String responseOrderId = "EXCH-RESPONSE-001";
        boolean throwException = false;
        boolean returnNull = false;

        StubExchangeSessionClient() {
            super(null, "http://localhost:8081", "http://localhost:8086/execution/callback");
        }

        @Override
        public void registerCallback() {
            // 测试中不做真实注册
        }

        @Override
        public ExchangeOrderResponse submitOrder(ExchangeOrderRequest request) {
            lastSubmittedRequest = request;
            if (throwException) {
                throw new ExchangeException("simulated exchange error", new RuntimeException());
            }
            if (returnNull) {
                return null;
            }
            ExchangeOrderResponse response = new ExchangeOrderResponse();
            response.setOrderId(responseOrderId);
            response.setSymbol(request.getSymbol());
            response.setSide(request.getSide());
            response.setType(request.getType());
            response.setQty(request.getQty());
            response.setStatus("NEW");
            response.setCreatedAt(System.currentTimeMillis());
            response.setUpdatedAt(System.currentTimeMillis());
            return response;
        }

        @Override
        public ExchangeOrderResponse queryOrder(String exchangeOrderId) {
            return null;
        }
    }

    static class CapturingKafkaTemplate extends KafkaTemplate<String, String> {
        final List<SentMessage> sentMessages = new ArrayList<>();

        CapturingKafkaTemplate() {
            super(new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(
                    java.util.Collections.emptyMap()));
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String value) {
            sentMessages.add(new SentMessage(topic, key, value));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String value) {
            sentMessages.add(new SentMessage(topic, null, value));
            return CompletableFuture.completedFuture(null);
        }

        static class SentMessage {
            final String topic;
            final String key;
            final String value;
            SentMessage(String topic, String key, String value) {
                this.topic = topic;
                this.key = key;
                this.value = value;
            }
        }
    }
}
