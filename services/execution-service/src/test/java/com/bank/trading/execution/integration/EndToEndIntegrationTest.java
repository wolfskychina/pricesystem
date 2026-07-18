package com.bank.trading.execution.integration;

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
import com.bank.trading.execution.service.ExecutionService;
import com.bank.trading.execution.service.HedgeBatcher;
import com.bank.trading.simexchange.callback.CallbackRegistry;
import com.bank.trading.simexchange.engine.MarketDataEngine;
import com.bank.trading.simexchange.engine.MatchingEngine;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.SymbolConfig;
import com.bank.trading.simexchange.model.TradeFill;
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
 * 端到端集成测试：验证 sim-exchange + execution-service 完整链路。
 * <p>
 * 在单 JVM 内组装真实组件，验证异步两阶段撮合 + 对冲聚合的完整业务流程：
 * <ol>
 *   <li>TradeEvent → HedgeBatcher.enqueue（入桶）</li>
 *   <li>flushBucket → ExecutionService.submitBatchedOrder（聚合提交）</li>
 *   <li>桥接 ExchangeSessionClient → MatchingEngine.submitOrder（真实撮合引擎受理）</li>
 *   <li>MatchingEngine 异步撮合 → 桥接 CallbackRegistry（模拟 Webhook 回调）</li>
 *   <li>ExecutionService.onOrderNotification/onTradeNotification（真实业务处理）</li>
 *   <li>聚合订单分摊 → 逐笔发布 hedge-fill-event</li>
 * </ol>
 * <p>
 * <b>桥接设计</b>：
 * <ul>
 *   <li>{@link BridgeExchangeSessionClient}：绕过 HTTP，直接调用 MatchingEngine.submitOrder，
 *       但保留对象模型转换（ExchangeOrderRequest → MatchingEngine 参数 → ExchangeOrderResponse）</li>
 *   <li>{@link BridgeCallbackRegistry}：绕过 HTTP Webhook，直接调用 ExecutionService 的回调方法，
 *       但保留对象模型转换（ExchangeOrder → ExchangeOrderResponse，TradeFill → ExchangeTradeNotification）</li>
 * </ul>
 * 这种设计既验证了真实撮合逻辑与业务处理逻辑，又验证了两个模块的对象模型兼容性
 * （字段名一致，JSON 序列化在真实 HTTP 场景下也能正常工作）。
 */
class EndToEndIntegrationTest {

    private MarketDataEngine marketDataEngine;
    private BridgeCallbackRegistry callbackRegistry;
    private MatchingEngine matchingEngine;
    private InMemoryHedgeOrderMapper hedgeOrderMapper;
    private InMemoryHedgeTradeMapper hedgeTradeMapper;
    private InMemoryBatchItemMapper batchItemMapper;
    private CapturingKafkaTemplate kafkaTemplate;
    private BridgeExchangeSessionClient exchangeSessionClient;
    private ExecutionService executionService;
    private HedgeBatcher hedgeBatcher;

    @BeforeEach
    void setUp() {
        // 1. 初始化行情引擎，注册一个合约
        marketDataEngine = new MarketDataEngine();
        SymbolConfig config = new SymbolConfig();
        config.setCode("AU2406");
        config.setName("黄金期货");
        config.setInitialPrice(new BigDecimal("520.00"));
        config.setTickSize(new BigDecimal("0.01"));
        config.setMinQty(new BigDecimal("1"));
        config.setVolatility(0.1);
        config.setDrift(0.0);
        config.setMultiplier(100);
        marketDataEngine.init(List.of(config), 1.0);

        // 2. 初始化内存 Mapper 和 Kafka
        hedgeOrderMapper = new InMemoryHedgeOrderMapper();
        hedgeTradeMapper = new InMemoryHedgeTradeMapper();
        batchItemMapper = new InMemoryBatchItemMapper();
        kafkaTemplate = new CapturingKafkaTemplate();

        // 3. 打破循环依赖（ExecutionService → ExchangeSessionClient → MatchingEngine
        //    → CallbackRegistry → ExecutionService）：
        //    用 holder 模式，CallbackRegistry 在回调时从 holder 获取 ExecutionService。
        ExecutionServiceHolder holder = new ExecutionServiceHolder();

        // 4. 创建桥接 CallbackRegistry（依赖 holder，回调时从 holder 获取）
        callbackRegistry = new BridgeCallbackRegistry(holder, 50L);

        // 5. 创建真实 MatchingEngine（依赖行情引擎和桥接回调）
        matchingEngine = new MatchingEngine(marketDataEngine, callbackRegistry);
        matchingEngine.clearForTest();

        // 6. 创建桥接 ExchangeSessionClient（依赖 MatchingEngine）
        exchangeSessionClient = new BridgeExchangeSessionClient(matchingEngine);

        // 7. 创建 ExecutionService（通过构造函数注入 exchangeSessionClient，避免反射设置 final 字段）
        executionService = new ExecutionService(hedgeOrderMapper, hedgeTradeMapper,
                batchItemMapper, exchangeSessionClient, kafkaTemplate);
        setField(executionService, "tradeTopic", "trade-event");
        setField(executionService, "hedgeFillTopic", "hedge-fill-event");
        setField(executionService, "hedgeOrderType", "MARKET");
        setField(executionService, "hedgeRatio", new BigDecimal("1.0"));

        // 8. 注入到 holder，打破循环
        holder.service = executionService;

        // 9. 创建 HedgeBatcher（依赖 executionService）
        hedgeBatcher = new HedgeBatcher(batchItemMapper, executionService);
        hedgeBatcher.setBatchingEnabled(true);
        hedgeBatcher.setBatchingWindowMs(999_999);    // 大窗口，避免定时触发
        hedgeBatcher.setSizeThreshold(new BigDecimal("999"));  // 大阈值，避免数量触发
        hedgeBatcher.setHedgeRatio(new BigDecimal("1.0"));
    }

    // ==================== 端到端测试场景 ====================

    @Test
    @DisplayName("E2E-1: 聚合对冲完整链路 - 3笔客户买入 → 1笔聚合对冲卖出 → 异步成交 → 3条hedge-fill-event")
    void endToEnd_batchedHedgeFlow_threeCustomerBuys() throws Exception {
        // 1. 模拟 3 笔客户买入成交，入桶
        hedgeBatcher.enqueue(buildTradeEvent("T-E2E-1", "AU2406", "BUY", new BigDecimal("5")));
        hedgeBatcher.enqueue(buildTradeEvent("T-E2E-2", "AU2406", "BUY", new BigDecimal("3")));
        hedgeBatcher.enqueue(buildTradeEvent("T-E2E-3", "AU2406", "BUY", new BigDecimal("2")));

        // 验证入桶成功
        assertEquals(3, hedgeBatcher.getBucketSize("AU2406", "BUY"), "3笔应入同一桶（对冲BUY）");
        assertEquals(0, hedgeOrderMapper.orders.size(), "入桶阶段不应创建对冲订单");

        // 2. 手动触发出桶，提交聚合对冲单
        hedgeBatcher.flushBucket("AU2406:BUY");

        // 3. 验证聚合对冲订单已提交到交易所（同步受理阶段）
        assertEquals(1, hedgeOrderMapper.orders.size(), "出桶应创建1笔聚合对冲订单");
        HedgeOrder batchedOrder = hedgeOrderMapper.orders.get(0);
        assertEquals(1, batchedOrder.getIsBatched(), "应为聚合订单");
        assertEquals(3, batchedOrder.getBatchItemCount(), "应包含3个子项");
        assertDecimalEquals(new BigDecimal("10.0000"), batchedOrder.getQty(), "总量=5+3+2=10");
        assertEquals("BUY", batchedOrder.getSide(), "客户BUY→对冲BUY");
        assertEquals("NEW", batchedOrder.getStatus(), "同步受理后状态为NEW");
        assertNotNull(batchedOrder.getExchangeOrderId(), "应有交易所订单ID");

        // 验证 sim-exchange 也受理了订单
        assertEquals(1, matchingEngine.getAllOrders().size(),
                "sim-exchange 应有1笔订单");
        ExchangeOrder simOrder = matchingEngine.getAllOrders().get(0);
        assertEquals(batchedOrder.getExchangeOrderId(), simOrder.getOrderId(),
                "execution-service 与 sim-exchange 的订单ID应一致");

        // 4. 等待异步撮合完成（撮合延迟 50ms + 回调处理）
        awaitOrderFilledAndEvents(batchedOrder.getHedgeOrderId(), 3, 2000);

        // 5. 验证最终状态
        HedgeOrder filledOrder = hedgeOrderMapper.findByHedgeOrderId(batchedOrder.getHedgeOrderId());
        assertEquals("FILLED", filledOrder.getStatus(), "撮合完成后对冲订单状态应为FILLED");
        assertDecimalEquals(new BigDecimal("10.0000"), filledOrder.getFilledQty(),
                "全量成交，成交量=10");
        assertTrue(filledOrder.getAvgPrice().compareTo(BigDecimal.ZERO) > 0,
                "应有成交均价: " + filledOrder.getAvgPrice());

        // 6. 验证 sim-exchange 的成交流水
        List<TradeFill> simFills = matchingEngine.getTradeFills();
        assertEquals(1, simFills.size(), "sim-exchange 应有1条成交流水");
        assertEquals("BUY", simFills.get(0).getSide(), "对冲单方向为BUY");

        // 7. 验证 execution-service 的对冲成交流水
        assertEquals(1, hedgeTradeMapper.trades.size(), "应有1条对冲成交流水");

        // 8. 验证聚合子项状态与分摊
        List<HedgeBatchItem> items = batchItemMapper.findByHedgeOrderId(batchedOrder.getHedgeOrderId());
        assertEquals(3, items.size(), "应有3条聚合子项");
        for (HedgeBatchItem item : items) {
            assertEquals("FILLED", item.getStatus(), "所有子项状态应为FILLED");
            assertTrue(item.getFilledQty().compareTo(BigDecimal.ZERO) > 0,
                    "子项成交量应大于0: " + item.getFilledQty());
            assertTrue(item.getAvgPrice().compareTo(BigDecimal.ZERO) > 0,
                    "子项成交价应大于0: " + item.getAvgPrice());
        }

        // 验证分摊数量之和 = 总成交数量
        BigDecimal totalAllocated = items.stream()
                .map(HedgeBatchItem::getFilledQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertDecimalEquals(new BigDecimal("10.0000"), totalAllocated,
                "子项分摊数量之和必须等于总成交数量");

        // 9. 验证 hedge-fill-event 发布（每子项1条）
        assertEquals(3, kafkaTemplate.sentMessages.size(), "应发布3条hedge-fill-event");
        for (CapturingKafkaTemplate.SentMessage msg : kafkaTemplate.sentMessages) {
            assertEquals("hedge-fill-event", msg.topic, "topic应为hedge-fill-event");
            assertEquals("AU2406", msg.key, "分区键应为合约代码");
            assertTrue(msg.value.contains("originalTradeId"), "事件应包含originalTradeId");
            assertTrue(msg.value.contains("T-E2E-"), "事件应关联原始成交ID");
        }
    }

    @Test
    @DisplayName("E2E-2: 单笔立即对冲链路 - batching关闭时直通对冲")
    void endToEnd_singleImmediateHedge() throws Exception {
        hedgeBatcher.setBatchingEnabled(false);

        TradeEvent event = buildTradeEvent("T-E2E-SINGLE", "AU2406", "SELL", new BigDecimal("8"));

        hedgeBatcher.enqueue(event);

        // 验证：单笔直接对冲，不经过聚合桶
        assertEquals(1, hedgeOrderMapper.orders.size(), "应创建1笔对冲订单");
        HedgeOrder order = hedgeOrderMapper.orders.get(0);
        assertEquals(0, order.getIsBatched(), "非聚合订单");
        assertEquals("SELL", order.getSide(), "客户SELL→对冲SELL");

        // 等待异步撮合
        awaitOrderFilledAndEvents(order.getHedgeOrderId(), 1, 2000);

        HedgeOrder filled = hedgeOrderMapper.findByHedgeOrderId(order.getHedgeOrderId());
        assertEquals("FILLED", filled.getStatus());
        assertDecimalEquals(new BigDecimal("8.0000"), filled.getFilledQty());

        // 单笔对冲发1条事件
        assertEquals(1, kafkaTemplate.sentMessages.size());
    }

    @Test
    @DisplayName("E2E-3: 数量阈值触发 - 累计达阈值立即出桶")
    void endToEnd_sizeThresholdFlush() throws Exception {
        hedgeBatcher.setSizeThreshold(new BigDecimal("20"));

        // 第一笔 15 手，未达阈值
        hedgeBatcher.enqueue(buildTradeEvent("T-THR-1", "AU2406", "BUY", new BigDecimal("15")));
        assertEquals(0, hedgeOrderMapper.orders.size(), "未达阈值不应出桶");

        // 第二笔 10 手，累计 25 手，达到 20 手阈值
        hedgeBatcher.enqueue(buildTradeEvent("T-THR-2", "AU2406", "BUY", new BigDecimal("10")));

        assertEquals(1, hedgeOrderMapper.orders.size(), "达阈值应立即出桶");
        HedgeOrder order = hedgeOrderMapper.orders.get(0);
        assertEquals(2, order.getBatchItemCount());
        assertDecimalEquals(new BigDecimal("25.0000"), order.getQty());

        awaitOrderFilledAndEvents(order.getHedgeOrderId(), 2, 2000);

        assertEquals(2, kafkaTemplate.sentMessages.size(), "2子项应发2条事件");
    }

    @Test
    @DisplayName("E2E-4: 不同方向分桶 - 客户买卖同时成交，分别对冲")
    void endToEnd_oppositeDirections_separateBuckets() throws Exception {
        hedgeBatcher.enqueue(buildTradeEvent("T-BUY", "AU2406", "BUY", new BigDecimal("5")));
        hedgeBatcher.enqueue(buildTradeEvent("T-SELL", "AU2406", "SELL", new BigDecimal("3")));

        // 验证分桶
        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "BUY"), "BUY客户→BUY对冲桶");
        assertEquals(1, hedgeBatcher.getBucketSize("AU2406", "SELL"), "SELL客户→SELL对冲桶");

        // 分别出桶
        hedgeBatcher.flushBucket("AU2406:BUY");
        hedgeBatcher.flushBucket("AU2406:SELL");

        assertEquals(2, hedgeOrderMapper.orders.size(), "应创建2笔对冲订单（不同方向）");
        long sellCount = hedgeOrderMapper.orders.stream()
                .filter(o -> "SELL".equals(o.getSide())).count();
        long buyCount = hedgeOrderMapper.orders.stream()
                .filter(o -> "BUY".equals(o.getSide())).count();
        assertEquals(1, sellCount, "1笔对冲SELL");
        assertEquals(1, buyCount, "1笔对冲BUY");

        awaitOrderFilledAndEvents(hedgeOrderMapper.orders.get(0).getHedgeOrderId(), 1, 2000);
        awaitOrderFilledAndEvents(hedgeOrderMapper.orders.get(1).getHedgeOrderId(), 2, 2000);

        assertEquals(2, kafkaTemplate.sentMessages.size(), "应发2条事件");
    }

    // ==================== 辅助方法 ====================

    private TradeEvent buildTradeEvent(String tradeId, String symbol, String side, BigDecimal qty) {
        TradeEvent event = new TradeEvent("CUST-E2E");
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

    /**
     * 等待指定对冲订单状态变为 FILLED，并额外等待 onTradeNotification 完成。
     * <p>
     * MatchingEngine 的异步撮合流程是：先 notifyOrderUpdate(FILLED)（更新订单状态），
     * 再 notifyTrade（触发成交处理与事件发布）。awaitOrderFilled 检测到 FILLED 时，
     * notifyTrade 可能尚未执行。因此检测到 FILLED 后需等待事件到达。
     *
     * @param hedgeOrderId     对冲订单 ID
     * @param expectedEvents   预期的事件数量
     * @param timeoutMs        超时时间（毫秒）
     */
    private void awaitOrderFilledAndEvents(String hedgeOrderId, int expectedEvents, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        // 阶段1：等待订单状态变为 FILLED
        while (System.currentTimeMillis() < deadline) {
            HedgeOrder order = hedgeOrderMapper.findByHedgeOrderId(hedgeOrderId);
            if (order != null && "FILLED".equals(order.getStatus())) {
                break;
            }
            Thread.sleep(20);
        }
        HedgeOrder order = hedgeOrderMapper.findByHedgeOrderId(hedgeOrderId);
        if (order == null || !"FILLED".equals(order.getStatus())) {
            fail("Order not FILLED within timeout: hedgeOrderId=" + hedgeOrderId
                    + ", currentStatus=" + (order != null ? order.getStatus() : "null"));
        }
        // 阶段2：等待 onTradeNotification 完成（事件发布）
        while (System.currentTimeMillis() < deadline) {
            if (kafkaTemplate.sentMessages.size() >= expectedEvents) {
                return;
            }
            Thread.sleep(20);
        }
        fail("Expected " + expectedEvents + " events, but got " + kafkaTemplate.sentMessages.size()
                + " within timeout for hedgeOrderId=" + hedgeOrderId);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName + " on " + target.getClass().getSimpleName(), e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                "expected: <" + expected + "> but was: <" + actual + ">");
    }

    private void assertDecimalEquals(BigDecimal expected, BigDecimal actual, String msg) {
        assertTrue(expected.compareTo(actual) == 0, msg
                + " expected: <" + expected + "> but was: <" + actual + ">");
    }

    // ==================== 桥接组件 ====================

    /**
     * ExecutionService 的可变持有者，用于打破循环依赖。
     * <p>
     * CallbackRegistry 在回调时从 holder 获取 ExecutionService 实例，
     * 允许 ExecutionService 在 CallbackRegistry 之后创建。
     */
    static class ExecutionServiceHolder {
        volatile ExecutionService service;
    }

    /**
     * 桥接版 ExchangeSessionClient。
     * <p>
     * 绕过 HTTP，直接调用 MatchingEngine.submitOrder，但保留对象模型转换。
     * 验证 execution-service 的请求模型与 sim-exchange 的受理逻辑兼容。
     */
    static class BridgeExchangeSessionClient extends ExchangeSessionClient {
        private final MatchingEngine matchingEngine;

        BridgeExchangeSessionClient(MatchingEngine matchingEngine) {
            super(null, "http://bridge-exchange", "http://bridge-callback");
            this.matchingEngine = matchingEngine;
        }

        @Override
        public void registerCallback() {
            // 桥接模式不需要注册回调
        }

        @Override
        public ExchangeOrderResponse submitOrder(ExchangeOrderRequest request) {
            // 调用真实 MatchingEngine.submitOrder
            ExchangeOrder order = matchingEngine.submitOrder(
                    request.getClientOrderId(),
                    request.getSymbol(),
                    request.getSide(),
                    request.getType(),
                    request.getQty(),
                    request.getPrice());
            // 转换为 ExchangeOrderResponse（字段一一对应）
            return convertToResponse(order);
        }

        @Override
        public ExchangeOrderResponse queryOrder(String exchangeOrderId) {
            ExchangeOrder order = matchingEngine.getOrder(exchangeOrderId);
            return order != null ? convertToResponse(order) : null;
        }

        private ExchangeOrderResponse convertToResponse(ExchangeOrder order) {
            ExchangeOrderResponse response = new ExchangeOrderResponse();
            response.setOrderId(order.getOrderId());
            response.setClientOrderId(order.getClientOrderId());
            response.setSymbol(order.getSymbol());
            response.setSide(order.getSide());
            response.setType(order.getType());
            response.setQty(order.getQty());
            response.setPrice(order.getPrice());
            response.setFilledQty(order.getFilledQty());
            response.setAvgPrice(order.getAvgPrice());
            response.setStatus(order.getStatus());
            response.setCreatedAt(order.getCreatedAt());
            response.setUpdatedAt(order.getUpdatedAt());
            return response;
        }
    }

    /**
     * 桥接版 CallbackRegistry。
     * <p>
     * 绕过 HTTP Webhook，直接调用 ExecutionService 的回调方法，但保留对象模型转换。
     * 验证 sim-exchange 的回调模型与 execution-service 的接收逻辑兼容。
     * <p>
     * 通过 ExecutionServiceHolder 获取 ExecutionService，打破循环依赖。
     */
    static class BridgeCallbackRegistry extends CallbackRegistry {
        private final ExecutionServiceHolder holder;
        private final long matchDelayMs;

        BridgeCallbackRegistry(ExecutionServiceHolder holder, long matchDelayMs) {
            super(null, matchDelayMs);
            this.holder = holder;
            this.matchDelayMs = matchDelayMs;
        }

        @Override
        public long getMatchDelayMs() {
            return matchDelayMs;
        }

        @Override
        public void notifyOrderUpdate(Object payload) {
            if (payload instanceof ExchangeOrder) {
                ExchangeOrderResponse response = convertToResponse((ExchangeOrder) payload);
                holder.service.onOrderNotification(response);
            }
        }

        @Override
        public void notifyTrade(Object payload) {
            if (payload instanceof TradeFill) {
                ExchangeTradeNotification notification = convertToNotification((TradeFill) payload);
                holder.service.onTradeNotification(notification);
            }
        }

        private ExchangeOrderResponse convertToResponse(ExchangeOrder order) {
            ExchangeOrderResponse response = new ExchangeOrderResponse();
            response.setOrderId(order.getOrderId());
            response.setClientOrderId(order.getClientOrderId());
            response.setSymbol(order.getSymbol());
            response.setSide(order.getSide());
            response.setType(order.getType());
            response.setQty(order.getQty());
            response.setPrice(order.getPrice());
            response.setFilledQty(order.getFilledQty());
            response.setAvgPrice(order.getAvgPrice());
            response.setStatus(order.getStatus());
            response.setCreatedAt(order.getCreatedAt());
            response.setUpdatedAt(order.getUpdatedAt());
            return response;
        }

        private ExchangeTradeNotification convertToNotification(TradeFill fill) {
            ExchangeTradeNotification notification = new ExchangeTradeNotification();
            notification.setTradeId(fill.getTradeId());
            notification.setOrderId(fill.getOrderId());
            notification.setSymbol(fill.getSymbol());
            notification.setSide(fill.getSide());
            notification.setQty(fill.getQty());
            notification.setPrice(fill.getPrice());
            notification.setAmount(fill.getAmount());
            notification.setTradeTime(fill.getTradeTime());
            return notification;
        }
    }

    // ==================== 内存 Mock 实现 ====================

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
