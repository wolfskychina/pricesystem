package com.bank.trading.execution.service;

import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.execution.entity.HedgeBatchItem;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.entity.HedgeTrade;
import com.bank.trading.execution.mapper.HedgeBatchItemMapper;
import com.bank.trading.execution.mapper.HedgeOrderMapper;
import com.bank.trading.execution.mapper.HedgeTradeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HedgeBatcher 单元测试。
 * <p>
 * 覆盖：入桶幂等、时间窗口出桶、数量阈值出桶、不同合约/方向分桶、
 * batching 关闭时直通单笔对冲。
 */
class HedgeBatcherTest {

    private HedgeBatcher hedgeBatcher;
    private InMemoryBatchItemMapper batchItemMapper;
    private ExecutionService executionService;
    private InMemoryHedgeOrderMapper hedgeOrderMapper;
    private InMemoryHedgeTradeMapper hedgeTradeMapper;
    private StubExchangeSessionClient exchangeClient;
    private CapturingKafkaTemplate kafkaTemplate;

    @BeforeEach
    void setUp() {
        batchItemMapper = new InMemoryBatchItemMapper();
        hedgeOrderMapper = new InMemoryHedgeOrderMapper();
        hedgeTradeMapper = new InMemoryHedgeTradeMapper();
        exchangeClient = new StubExchangeSessionClient();
        kafkaTemplate = new CapturingKafkaTemplate();

        executionService = new ExecutionService(
                hedgeOrderMapper, hedgeTradeMapper, batchItemMapper, exchangeClient, kafkaTemplate);
        setField(executionService, "tradeTopic", "trade-event");
        setField(executionService, "hedgeFillTopic", "hedge-fill-event");
        setField(executionService, "hedgeOrderType", "MARKET");
        setField(executionService, "hedgeRatio", new BigDecimal("1.0"));

        hedgeBatcher = new HedgeBatcher(batchItemMapper, executionService);
        hedgeBatcher.setBatchingEnabled(true);
        hedgeBatcher.setBatchingWindowMs(1000);
        hedgeBatcher.setSizeThreshold(new BigDecimal("50"));
        hedgeBatcher.setHedgeRatio(new BigDecimal("1.0"));
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

    // ==================== 入桶与分桶测试 ====================

    @Test
    @DisplayName("batching 关闭时直通单笔对冲")
    void enqueue_batchingDisabled_passesThroughToImmediate() {
        hedgeBatcher.setBatchingEnabled(false);
        exchangeClient.responseOrderId = "EXCH-IMM-001";
        TradeEvent event = buildTradeEvent("T001", "AU2406", "BUY", new BigDecimal("10"));

        hedgeBatcher.enqueue(event);

        assertEquals(1, hedgeOrderMapper.orders.size(), "应创建1笔对冲订单");
        assertEquals(0, batchItemMapper.items.size(), "batching关闭时不应创建聚合子项");
        HedgeOrder order = hedgeOrderMapper.orders.get(0);
        assertEquals("SELL", order.getSide(), "客户BUY→对冲SELL");
        assertEquals(0, order.getIsBatched(), "isBatched应为0（单笔）");
    }

    @Test
    @DisplayName("batching 开启时入桶，不立即下单")
    void enqueue_batchingEnabled_addsToBucketNoOrder() {
        TradeEvent event = buildTradeEvent("T002", "AU2406", "BUY", new BigDecimal("10"));

        hedgeBatcher.enqueue(event);

        assertEquals(1, batchItemMapper.items.size(), "应创建1条聚合子项");
        assertEquals(0, hedgeOrderMapper.orders.size(), "入桶时不应创建对冲订单");
        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "SELL"),
                "桶内应有1项（客户BUY→对冲SELL）");
    }

    @Test
    @DisplayName("同合约同方向合并到同一桶")
    void enqueue_sameSymbolSameSide_sameBucket() {
        hedgeBatcher.enqueue(buildTradeEvent("T1", "AU2406", "BUY", new BigDecimal("5")));
        hedgeBatcher.enqueue(buildTradeEvent("T2", "AU2406", "BUY", new BigDecimal("3")));

        assertEquals(2, hedgeBatcher.getBucketSize("AU2406", "SELL"),
                "同方向应合并到同一桶");
        assertEquals(2, batchItemMapper.items.size());
    }

    @Test
    @DisplayName("同合约不同方向分到不同桶")
    void enqueue_sameSymbolDifferentSide_differentBuckets() {
        hedgeBatcher.enqueue(buildTradeEvent("T1", "AU2406", "BUY", new BigDecimal("5")));
        hedgeBatcher.enqueue(buildTradeEvent("T2", "AU2406", "SELL", new BigDecimal("3")));

        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "SELL"), "BUY客户→SELL对冲桶");
        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "BUY"), "SELL客户→BUY对冲桶");
        assertEquals(2, hedgeBatcher.getActiveBucketCount(), "应有2个活跃桶");
    }

    @Test
    @DisplayName("不同合约分到不同桶")
    void enqueue_differentSymbol_differentBuckets() {
        hedgeBatcher.enqueue(buildTradeEvent("T1", "AU2406", "BUY", new BigDecimal("5")));
        hedgeBatcher.enqueue(buildTradeEvent("T2", "AG2406", "BUY", new BigDecimal("3")));

        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "SELL"));
        assertEquals(1, hedgeBatcher.getBucketSize("AG2406", "SELL"));
        assertEquals(2, hedgeBatcher.getActiveBucketCount());
    }

    // ==================== 幂等测试 ====================

    @Test
    @DisplayName("重复入桶被幂等跳过")
    void enqueue_duplicateTradeId_skipped() {
        TradeEvent event = buildTradeEvent("T-DUP", "AU2406", "BUY", new BigDecimal("10"));

        boolean first = hedgeBatcher.enqueue(event);
        boolean second = hedgeBatcher.enqueue(event);

        assertTrue(first, "第一次应成功入桶");
        assertFalse(second, "第二次应因幂等跳过");
        assertEquals(1, batchItemMapper.items.size(), "只应创建1条子项");
        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "SELL"), "桶内只有1项");
    }

    // ==================== 数量阈值出桶测试 ====================

    @Test
    @DisplayName("数量达到阈值立即出桶")
    void enqueue_sizeThresholdReached_flushesImmediately() {
        exchangeClient.responseOrderId = "EXCH-BATCH-001";
        hedgeBatcher.setSizeThreshold(new BigDecimal("20"));

        // 第一笔 15 手，未达阈值
        hedgeBatcher.enqueue(buildTradeEvent("T1", "AU2406", "BUY", new BigDecimal("15")));
        assertEquals(0, hedgeOrderMapper.orders.size(), "未达阈值不应出桶");

        // 第二笔 10 手，累计 25 手，达到 20 手阈值
        hedgeBatcher.enqueue(buildTradeEvent("T2", "AU2406", "BUY", new BigDecimal("10")));

        assertEquals(1, hedgeOrderMapper.orders.size(), "达到阈值应出桶创建1笔对冲订单");
        HedgeOrder order = hedgeOrderMapper.orders.get(0);
        assertEquals(1, order.getIsBatched(), "应为聚合订单");
        assertEquals(2, order.getBatchItemCount(), "应包含2个子项");
        assertDecimalEquals(new BigDecimal("25.0000"), order.getQty(), "总量应为25手");
        assertEquals("EXCH-BATCH-001", order.getExchangeOrderId());
    }

    @Test
    @DisplayName("单笔超大单直接达阈值立即出桶")
    void enqueue_singleLargeOrder_flushesImmediately() {
        exchangeClient.responseOrderId = "EXCH-LARGE-001";
        hedgeBatcher.setSizeThreshold(new BigDecimal("50"));

        hedgeBatcher.enqueue(buildTradeEvent("T-BIG", "AU2406", "BUY", new BigDecimal("100")));

        assertEquals(1, hedgeOrderMapper.orders.size(), "超大单应立即出桶");
        HedgeOrder order = hedgeOrderMapper.orders.get(0);
        assertDecimalEquals(new BigDecimal("100.0000"), order.getQty());
    }

    // ==================== 手动出桶测试 ====================

    @Test
    @DisplayName("手动 flushBucket 出桶提交聚合订单")
    void flushBucket_createsBatchedOrder() {
        exchangeClient.responseOrderId = "EXCH-FLUSH-001";
        hedgeBatcher.enqueue(buildTradeEvent("T1", "AU2406", "BUY", new BigDecimal("5")));
        hedgeBatcher.enqueue(buildTradeEvent("T2", "AU2406", "BUY", new BigDecimal("3")));
        hedgeBatcher.enqueue(buildTradeEvent("T3", "AU2406", "SELL", new BigDecimal("4")));

        hedgeBatcher.flushBucket("AU2406:SELL");

        assertEquals(1, hedgeOrderMapper.orders.size(), "出桶BUY方向应创建1笔订单");
        HedgeOrder order = hedgeOrderMapper.orders.get(0);
        assertEquals("SELL", order.getSide());
        assertEquals(2, order.getBatchItemCount());
        assertDecimalEquals(new BigDecimal("8.0000"), order.getQty());
        assertEquals(0, hedgeBatcher.getBucketSize("AU2406", "SELL"),
                "出桶后桶应清空");
        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "BUY"),
                "另一方向桶不受影响");
    }

    @Test
    @DisplayName("空桶 flush 不创建订单")
    void flushBucket_emptyBucket_noOrder() {
        hedgeBatcher.flushBucket("NONEXISTENT:BUY");
        assertEquals(0, hedgeOrderMapper.orders.size());
    }

    @Test
    @DisplayName("聚合子项状态从 PENDING 变 SUBMITTED")
    void flushBucket_itemsStatusUpdatedToSubmitted() {
        exchangeClient.responseOrderId = "EXCH-STATUS-001";
        hedgeBatcher.enqueue(buildTradeEvent("T1", "AG2406", "SELL", new BigDecimal("5")));

        HedgeBatchItem before = batchItemMapper.items.get(0);
        assertEquals("PENDING", before.getStatus(), "入桶后状态应为PENDING");
        assertNull(before.getHedgeOrderId(), "PENDING时无hedgeOrderId");

        hedgeBatcher.flushBucket("AG2406:BUY");

        HedgeBatchItem after = batchItemMapper.items.get(0);
        assertEquals("SUBMITTED", after.getStatus(), "出桶后状态应为SUBMITTED");
        assertNotNull(after.getHedgeOrderId(), "出桶后应关联hedgeOrderId");
    }

    // ==================== 辅助方法 ====================

    private TradeEvent buildTradeEvent(String tradeId, String symbol, String side, BigDecimal qty) {
        TradeEvent event = new TradeEvent("CUST001");
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

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                "expected: <" + expected + "> but was: <" + actual + ">");
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String msg) {
        assertTrue(expected.compareTo(actual) == 0, msg
                + " expected: <" + expected + "> but was: <" + actual + ">");
    }

    // ==================== Mock 实现类 ====================

    static class InMemoryBatchItemMapper implements HedgeBatchItemMapper {
        final List<HedgeBatchItem> items = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(HedgeBatchItem item) {
            item.setId(++idSeq);
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
        final List<com.bank.trading.execution.entity.HedgeTrade> trades = new ArrayList<>();
        long idSeq = 0;

        @Override public int insert(com.bank.trading.execution.entity.HedgeTrade trade) {
            trade.setId(++idSeq);
            trades.add(trade);
            return 1;
        }
        @Override public List<com.bank.trading.execution.entity.HedgeTrade> findByHedgeOrderId(String hedgeOrderId) {
            return trades.stream().filter(t -> hedgeOrderId.equals(t.getHedgeOrderId())).toList();
        }
        @Override public com.bank.trading.execution.entity.HedgeTrade findByExchangeTradeId(String exchangeTradeId) {
            return trades.stream().filter(t -> exchangeTradeId.equals(t.getExchangeTradeId())).findFirst().orElse(null);
        }
        @Override public List<com.bank.trading.execution.entity.HedgeTrade> findRecent(int limit) {
            return trades.stream().limit(limit).toList();
        }
    }

    static class StubExchangeSessionClient extends com.bank.trading.execution.client.ExchangeSessionClient {
        com.bank.trading.execution.dto.ExchangeOrderRequest lastSubmittedRequest;
        String responseOrderId = "EXCH-RESPONSE-001";
        boolean throwException = false;
        boolean returnNull = false;

        StubExchangeSessionClient() {
            super(null, "http://localhost:8081", "http://localhost:8086/execution/callback");
        }

        @Override public void registerCallback() { }

        @Override
        public com.bank.trading.execution.dto.ExchangeOrderResponse submitOrder(
                com.bank.trading.execution.dto.ExchangeOrderRequest request) {
            lastSubmittedRequest = request;
            if (throwException) {
                throw new RuntimeException("simulated exchange error");
            }
            if (returnNull) return null;
            com.bank.trading.execution.dto.ExchangeOrderResponse resp =
                    new com.bank.trading.execution.dto.ExchangeOrderResponse();
            resp.setOrderId(responseOrderId);
            resp.setSymbol(request.getSymbol());
            resp.setSide(request.getSide());
            resp.setType(request.getType());
            resp.setQty(request.getQty());
            resp.setStatus("NEW");
            resp.setCreatedAt(System.currentTimeMillis());
            resp.setUpdatedAt(System.currentTimeMillis());
            return resp;
        }

        @Override
        public com.bank.trading.execution.dto.ExchangeOrderResponse queryOrder(String exchangeOrderId) {
            return null;
        }
    }

    static class CapturingKafkaTemplate extends org.springframework.kafka.core.KafkaTemplate<String, String> {
        final List<SentMessage> sentMessages = new ArrayList<>();

        CapturingKafkaTemplate() {
            super(new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(
                    java.util.Collections.emptyMap()));
        }

        @Override
        public java.util.concurrent.CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> send(
                String topic, String key, String value) {
            sentMessages.add(new SentMessage(topic, key, value));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        static class SentMessage {
            final String topic, key, value;
            SentMessage(String topic, String key, String value) {
                this.topic = topic; this.key = key; this.value = value;
            }
        }
    }
}
