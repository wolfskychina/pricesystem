package com.bank.trading.execution.service;

import com.bank.trading.common.core.event.TradeEvent;
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
 * 采用手写 Mock 实现类（Java 25 + Mockito 不兼容）。
 * 覆盖：单笔立即对冲、聚合对冲提交、聚合成交分摊、订单状态回报。
 */
class ExecutionServiceTest {

    private ExecutionService executionService;
    private InMemoryHedgeOrderMapper hedgeOrderMapper;
    private InMemoryHedgeTradeMapper hedgeTradeMapper;
    private InMemoryBatchItemMapper batchItemMapper;
    private StubExchangeSessionClient exchangeSessionClient;
    private CapturingKafkaTemplate kafkaTemplate;

    @BeforeEach
    void setUp() {
        hedgeOrderMapper = new InMemoryHedgeOrderMapper();
        hedgeTradeMapper = new InMemoryHedgeTradeMapper();
        batchItemMapper = new InMemoryBatchItemMapper();
        exchangeSessionClient = new StubExchangeSessionClient();
        kafkaTemplate = new CapturingKafkaTemplate();
        executionService = new ExecutionService(hedgeOrderMapper, hedgeTradeMapper,
                batchItemMapper, exchangeSessionClient, kafkaTemplate);

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

    // ==================== 单笔立即对冲测试 ====================

    @Test
    @DisplayName("客户买入成交 → 对冲卖出方向")
    void onTradeEventImmediate_customerBuy_hedgeSell() {
        TradeEvent event = buildTradeEvent("T001", "AU2406", "BUY", new BigDecimal("10"));

        executionService.onTradeEventImmediate(event);

        assertEquals(1, hedgeOrderMapper.orders.size());
        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("SELL", hedgeOrder.getSide(), "客户 BUY 应触发对冲 SELL");
        assertEquals("AU2406", hedgeOrder.getSymbol());
        assertDecimalEquals(new BigDecimal("10.0000"), hedgeOrder.getQty());
        assertEquals("NEW", hedgeOrder.getStatus());
        assertEquals("T001", hedgeOrder.getOriginalTradeId());
        assertEquals(0, hedgeOrder.getIsBatched());
    }

    @Test
    @DisplayName("客户卖出成交 → 对冲买入方向")
    void onTradeEventImmediate_customerSell_hedgeBuy() {
        TradeEvent event = buildTradeEvent("T002", "AU2406", "SELL", new BigDecimal("5"));

        executionService.onTradeEventImmediate(event);

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("BUY", hedgeOrder.getSide(), "客户 SELL 应触发对冲 BUY");
    }

    @Test
    @DisplayName("对冲下单成功后，对冲订单关联交易所订单 ID")
    void onTradeEventImmediate_submitSuccess_storesExchangeOrderId() {
        exchangeSessionClient.responseOrderId = "EXCH-ORDER-001";
        TradeEvent event = buildTradeEvent("T003", "AU2406", "BUY", new BigDecimal("10"));

        executionService.onTradeEventImmediate(event);

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("EXCH-ORDER-001", hedgeOrder.getExchangeOrderId());
    }

    @Test
    @DisplayName("对冲数量按 hedge-ratio 计算")
    void onTradeEventImmediate_halfHedgeRatio_halfQty() {
        setField(executionService, "hedgeRatio", new BigDecimal("0.5"));
        TradeEvent event = buildTradeEvent("T004", "AU2406", "BUY", new BigDecimal("10"));

        executionService.onTradeEventImmediate(event);

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertDecimalEquals(new BigDecimal("5.0000"), hedgeOrder.getQty());
    }

    @Test
    @DisplayName("交易所下单失败时，对冲订单保持 NEW 状态不抛异常")
    void onTradeEventImmediate_exchangeFailure_orderStaysNew() {
        exchangeSessionClient.throwException = true;
        TradeEvent event = buildTradeEvent("T005", "AU2406", "BUY", new BigDecimal("10"));

        assertDoesNotThrow(() -> executionService.onTradeEventImmediate(event));

        HedgeOrder hedgeOrder = hedgeOrderMapper.orders.get(0);
        assertEquals("NEW", hedgeOrder.getStatus());
        assertNull(hedgeOrder.getExchangeOrderId());
    }

    @Test
    @DisplayName("向交易所提交的对冲请求字段正确")
    void onTradeEventImmediate_submittedRequestFieldsCorrect() {
        TradeEvent event = buildTradeEvent("T007", "AG2406", "SELL", new BigDecimal("20"));

        executionService.onTradeEventImmediate(event);

        ExchangeOrderRequest submitted = exchangeSessionClient.lastSubmittedRequest;
        assertNotNull(submitted);
        assertEquals("AG2406", submitted.getSymbol());
        assertEquals("BUY", submitted.getSide(), "客户 SELL → 对冲 BUY");
        assertEquals("MARKET", submitted.getType());
        assertDecimalEquals(new BigDecimal("20.0000"), submitted.getQty());
    }

    // ==================== 聚合对冲提交测试 ====================

    @Test
    @DisplayName("提交聚合对冲单 → 创建聚合订单 + 更新子项状态")
    void submitBatchedOrder_createsBatchedOrderAndUpdatesItems() {
        exchangeSessionClient.responseOrderId = "EXCH-BATCH-001";
        List<HedgeBatchItem> items = buildBatchItems(3, "AU2406", "SELL",
                new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("2"));

        executionService.submitBatchedOrder(items);

        assertEquals(1, hedgeOrderMapper.orders.size());
        HedgeOrder order = hedgeOrderMapper.orders.get(0);
        assertEquals(1, order.getIsBatched());
        assertEquals(3, order.getBatchItemCount());
        assertDecimalEquals(new BigDecimal("10.0000"), order.getQty());
        assertEquals("SELL", order.getSide());
        assertEquals("EXCH-BATCH-001", order.getExchangeOrderId());

        for (HedgeBatchItem item : batchItemMapper.items) {
            assertEquals("SUBMITTED", item.getStatus(), "子项状态应为 SUBMITTED");
            assertNotNull(item.getHedgeOrderId(), "子项应关联 hedgeOrderId");
            assertEquals(order.getHedgeOrderId(), item.getHedgeOrderId());
        }
    }

    @Test
    @DisplayName("空列表不创建订单")
    void submitBatchedOrder_emptyList_noOrder() {
        executionService.submitBatchedOrder(new ArrayList<>());
        assertEquals(0, hedgeOrderMapper.orders.size());
    }

    // ==================== 单笔成交通知测试 ====================

    @Test
    @DisplayName("单笔成交通知 → 对冲订单 FILLED + 发1条事件")
    void onTradeNotification_singleOrder_fillsAndPublishes() {
        exchangeSessionClient.responseOrderId = "EXCH-001";
        executionService.onTradeEventImmediate(buildTradeEvent("T010", "AU2406", "BUY", new BigDecimal("10")));

        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-TRADE-1", "EXCH-001", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));
        executionService.onTradeNotification(notification);

        HedgeOrder updated = hedgeOrderMapper.findByExchangeOrderId("EXCH-001");
        assertEquals("FILLED", updated.getStatus());
        assertDecimalEquals(new BigDecimal("10"), updated.getFilledQty());
        assertDecimalEquals(new BigDecimal("520.50"), updated.getAvgPrice());

        assertEquals(1, hedgeTradeMapper.trades.size());
        assertEquals(1, kafkaTemplate.sentMessages.size());
        assertEquals("AU2406", kafkaTemplate.sentMessages.get(0).key);
    }

    @Test
    @DisplayName("重复成交通知被幂等跳过")
    void onTradeNotification_duplicateTradeId_skipped() {
        exchangeSessionClient.responseOrderId = "EXCH-004";
        executionService.onTradeEventImmediate(buildTradeEvent("T013", "AU2406", "BUY", new BigDecimal("10")));

        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-TRADE-4", "EXCH-004", "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));

        executionService.onTradeNotification(notification);
        executionService.onTradeNotification(notification);

        assertEquals(1, hedgeTradeMapper.trades.size());
        assertEquals(1, kafkaTemplate.sentMessages.size());
    }

    // ==================== 聚合成交通知分摊测试 ====================

    @Test
    @DisplayName("聚合订单成交 → 按子项分摊 + 逐笔发事件")
    void onTradeNotification_batchedOrder_allocatesAndPublishesPerItem() {
        // 1. 提交聚合订单（3个子项：5+3+2=10手）
        exchangeSessionClient.responseOrderId = "EXCH-BATCH-FILL-001";
        List<HedgeBatchItem> items = buildBatchItems(3, "AU2406", "SELL",
                new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("2"));
        executionService.submitBatchedOrder(items);

        HedgeOrder batchedOrder = hedgeOrderMapper.orders.get(0);

        // 2. 收到成交通知
        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-BATCH-TRADE-1", "EXCH-BATCH-FILL-001",
                "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.50"));
        executionService.onTradeNotification(notification);

        // 3. 聚合订单状态更新
        HedgeOrder updated = hedgeOrderMapper.findByExchangeOrderId("EXCH-BATCH-FILL-001");
        assertEquals("FILLED", updated.getStatus());
        assertDecimalEquals(new BigDecimal("10"), updated.getFilledQty());

        // 4. 子项状态与数量
        List<HedgeBatchItem> filledItems = batchItemMapper.findByHedgeOrderId(batchedOrder.getHedgeOrderId());
        assertEquals(3, filledItems.size());
        for (HedgeBatchItem item : filledItems) {
            assertEquals("FILLED", item.getStatus());
            assertDecimalEquals(new BigDecimal("520.50"), item.getAvgPrice(),
                    "所有子项成交价应相同（市价单）");
        }

        // 5. 子项分摊数量之和 = 总成交数量
        BigDecimal totalFilled = filledItems.stream()
                .map(HedgeBatchItem::getFilledQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertDecimalEquals(new BigDecimal("10"), totalFilled);

        // 6. 发出3条 hedge-fill-event（每子项1条）
        assertEquals(3, kafkaTemplate.sentMessages.size(), "聚合订单应发3条事件");
        for (CapturingKafkaTemplate.SentMessage msg : kafkaTemplate.sentMessages) {
            assertEquals("hedge-fill-event", msg.topic);
            assertEquals("AU2406", msg.key);
            assertTrue(msg.value.contains("\"originalTradeId\""));
        }
    }

    @Test
    @DisplayName("聚合订单分摊尾差由最后一项吸收")
    void onTradeNotification_batchedOrder_lastItemAbsorbsRounding() {
        // 3个子项 1+1+1=3手，按比例分摊4位小数，验证尾差
        exchangeSessionClient.responseOrderId = "EXCH-BATCH-ROUND-001";
        List<HedgeBatchItem> items = buildBatchItems(3, "AG2406", "BUY",
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"));
        executionService.submitBatchedOrder(items);

        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-BATCH-TRADE-R", "EXCH-BATCH-ROUND-001",
                "AG2406", "BUY",
                new BigDecimal("3.0000"), new BigDecimal("100.1234"));
        executionService.onTradeNotification(notification);

        List<HedgeBatchItem> filled = batchItemMapper.items;
        BigDecimal total = filled.stream()
                .map(HedgeBatchItem::getFilledQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertDecimalEquals(new BigDecimal("3.0000"), total,
                "子项分摊数量之和必须严格等于总成交数量");
    }

    @Test
    @DisplayName("聚合成交通知也做幂等校验")
    void onTradeNotification_batchedDuplicate_skipped() {
        exchangeSessionClient.responseOrderId = "EXCH-BATCH-DUP-001";
        List<HedgeBatchItem> items = buildBatchItems(2, "AU2406", "SELL",
                new BigDecimal("5"), new BigDecimal("5"));
        executionService.submitBatchedOrder(items);

        ExchangeTradeNotification notification = buildTradeNotification(
                "EXCH-BATCH-TRADE-DUP", "EXCH-BATCH-DUP-001",
                "AU2406", "SELL",
                new BigDecimal("10"), new BigDecimal("520.00"));

        executionService.onTradeNotification(notification);
        executionService.onTradeNotification(notification);

        assertEquals(1, hedgeTradeMapper.trades.size(), "只应1条对冲流水");
        assertEquals(2, kafkaTemplate.sentMessages.size(), "只应发2条事件（第一次）");
    }

    // ==================== 订单状态回报测试 ====================

    @Test
    @DisplayName("收到订单状态回报 → 更新对冲订单状态")
    void onOrderNotification_updatesStatus() {
        exchangeSessionClient.responseOrderId = "EXCH-005";
        executionService.onTradeEventImmediate(buildTradeEvent("T020", "AU2406", "BUY", new BigDecimal("10")));

        ExchangeOrderResponse orderCallback = new ExchangeOrderResponse();
        orderCallback.setOrderId("EXCH-005");
        orderCallback.setStatus("ACCEPTED");
        executionService.onOrderNotification(orderCallback);

        HedgeOrder updated = hedgeOrderMapper.findByExchangeOrderId("EXCH-005");
        assertEquals("ACCEPTED", updated.getStatus());
    }

    @Test
    @DisplayName("订单状态回报但订单不存在时跳过")
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

    private List<HedgeBatchItem> buildBatchItems(int count, String symbol, String side,
            BigDecimal... quantities) {
        List<HedgeBatchItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HedgeBatchItem item = new HedgeBatchItem();
            item.setId((long) (i + 1));
            item.setOriginalTradeId("T-BATCH-" + (i + 1));
            item.setCustomerId("CUST001");
            item.setSymbol(symbol);
            item.setSide(side);
            item.setQty(quantities[i]);
            item.setStatus("PENDING");
            item.setFilledQty(BigDecimal.ZERO);
            item.setAvgPrice(BigDecimal.ZERO);
            item.setCreatedAt(System.currentTimeMillis());
            item.setUpdatedAt(System.currentTimeMillis());
            batchItemMapper.insert(item);
            items.add(item);
        }
        return items;
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                "expected: <" + expected + "> but was: <" + actual + ">");
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String msg) {
        assertTrue(expected.compareTo(actual) == 0, msg
                + " expected: <" + expected + "> but was: <" + actual + ">");
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

    static class InMemoryBatchItemMapper implements HedgeBatchItemMapper {
        final List<HedgeBatchItem> items = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgeBatchItem item) {
            if (item.getId() == null) {
                item.setId(++idSeq);
            } else {
                idSeq = Math.max(idSeq, item.getId());
            }
            items.add(item);
            return 1;
        }
        @Override public List<HedgeBatchItem> findByHedgeOrderId(String hedgeOrderId) {
            return items.stream().filter(i -> hedgeOrderId.equals(i.getHedgeOrderId())).toList();
        }
        @Override public HedgeBatchItem findByOriginalTradeId(String originalTradeId) {
            return items.stream().filter(i -> originalTradeId.equals(i.getOriginalTradeId()))
                    .findFirst().orElse(null);
        }
        @Override public int update(HedgeBatchItem item) {
            for (int i = 0; i < items.size(); i++) {
                if (item.getId() != null && item.getId().equals(items.get(i).getId())) {
                    items.set(i, item);
                    return 1;
                }
            }
            return 0;
        }
        @Override public int countByStatus(String status) {
            return (int) items.stream().filter(i -> status.equals(i.getStatus())).count();
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

        @Override public void registerCallback() { }

        @Override
        public ExchangeOrderResponse submitOrder(ExchangeOrderRequest request) {
            lastSubmittedRequest = request;
            if (throwException) {
                throw new RuntimeException("simulated exchange error");
            }
            if (returnNull) return null;
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
            final String topic, key, value;
            SentMessage(String topic, String key, String value) {
                this.topic = topic; this.key = key; this.value = value;
            }
        }
    }
}
