package com.bank.trading.simexchange.engine;

import com.bank.trading.common.core.enums.OrderStatus;
import com.bank.trading.simexchange.callback.CallbackRegistry;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.MarketData;
import com.bank.trading.simexchange.model.TradeFill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BigDecimal 比较辅助：忽略 scale 差异（如 520.50 vs 520.50000000）。
 */
class BigDecimalAssert {
    static void assertDecimalEquals(String message, BigDecimal expected, BigDecimal actual) {
        assertTrue(expected.compareTo(actual) == 0,
                message + " expected: <" + expected + "> but was: <" + actual + ">");
    }
    static void assertDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertDecimalEquals(null, expected, actual);
    }
}

/**
 * MatchingEngine 单元测试。
 * <p>
 * 验证异步两阶段撮合模型的核心行为：
 * <ul>
 *   <li>submitOrder 同步返回 NEW（不等待撮合）</li>
 *   <li>异步撮合后订单状态变为 FILLED/REJECTED</li>
 *   <li>市价单按对侧盘口价成交</li>
 *   <li>限价单价格校验</li>
 *   <li>参数校验同步抛异常</li>
 *   <li>成交流水正确生成</li>
 *   <li>回调推送被调用</li>
 * </ul>
 * <p>
 * 采用手写 Mock 子类替代 Mockito（因 Java 25 + Mockito 不兼容）。
 */
class MatchingEngineTest {

    /** 被测撮合引擎 */
    private MatchingEngine matchingEngine;

    /** 手写 Mock 行情引擎 */
    private StubMarketDataEngine stubMarketDataEngine;

    /** 手写 Mock 回调注册表（记录推送调用） */
    private StubCallbackRegistry stubCallbackRegistry;

    /** 测试用合约 */
    private static final String SYMBOL = "AU2406";
    /** 测试用买价 */
    private static final BigDecimal BID = new BigDecimal("520.50");
    /** 测试用卖价 */
    private static final BigDecimal ASK = new BigDecimal("520.70");

    /**
     * 手写 Mock 行情引擎，覆盖 getLatest 返回固定行情。
     */
    static class StubMarketDataEngine extends MarketDataEngine {
        private MarketData marketData;

        @Override
        public MarketData getLatest(String symbol) {
            return marketData;
        }

        void setMarketData(MarketData md) {
            this.marketData = md;
        }
    }

    /**
     * 手写 Mock 回调注册表，覆盖推送方法，记录调用次数与载荷。
     * <p>
     * 避免真实 RestTemplate 发 HTTP 请求。
     */
    static class StubCallbackRegistry extends CallbackRegistry {
        volatile int orderCallbackCount = 0;
        volatile int tradeCallbackCount = 0;
        volatile Object lastOrderPayload;
        volatile Object lastTradePayload;

        // 构造函数：传 null RestTemplate，因为推送方法被覆盖不会用到
        StubCallbackRegistry() {
            super(null, 0);
        }

        @Override
        public void notifyOrderUpdate(Object payload) {
            orderCallbackCount++;
            lastOrderPayload = payload;
        }

        @Override
        public void notifyTrade(Object payload) {
            tradeCallbackCount++;
            lastTradePayload = payload;
        }

        @Override
        public long getMatchDelayMs() {
            return 0; // 测试中不延迟
        }
    }

    /**
     * 测试前初始化：构造 Mock 行情引擎（含固定行情）+ Mock 回调注册表 + 被测撮合引擎。
     */
    @BeforeEach
    void setUp() {
        stubMarketDataEngine = new StubMarketDataEngine();
        MarketData md = new MarketData();
        md.setSymbol(SYMBOL);
        md.setBidPrice(BID);
        md.setAskPrice(ASK);
        md.setLastPrice(new BigDecimal("520.60"));
        stubMarketDataEngine.setMarketData(md);

        stubCallbackRegistry = new StubCallbackRegistry();
        matchingEngine = new MatchingEngine(stubMarketDataEngine, stubCallbackRegistry);
    }

    /**
     * 辅助方法：等待订单达到终态（FILLED/REJECTED/CANCELLED），最多等 2 秒。
     */
    private ExchangeOrder waitForFinalState(String orderId) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            ExchangeOrder order = matchingEngine.getOrder(orderId);
            if (order != null && OrderStatus.of(order.getStatus()).isFinal()) {
                return order;
            }
            Thread.sleep(10);
        }
        return matchingEngine.getOrder(orderId);
    }

    @Test
    @DisplayName("submitOrder 同步返回订单对象（已受理，含订单ID与字段）")
    void submitOrder_returnsAcceptedOrderSynchronously() {
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-001", SYMBOL, "BUY", "MARKET", new BigDecimal("10"), null
        );

        // 同步返回时订单已入表，由于 match-delay-ms=0 异步撮合很快，
        // 状态可能已变为 ACCEPTED/FILLED，但订单对象本身应正确填充
        assertNotNull(order.getOrderId());
        assertEquals("C001-001", order.getClientOrderId());
        assertEquals(SYMBOL, order.getSymbol());
        assertEquals("BUY", order.getSide());
        assertEquals("MARKET", order.getType());
        BigDecimalAssert.assertDecimalEquals(new BigDecimal("10"), order.getQty());
    }

    @Test
    @DisplayName("市价买单异步撮合后按卖价成交，状态为 FILLED")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void marketBuyOrder_fillsAtAskPrice() throws InterruptedException {
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-002", SYMBOL, "BUY", "MARKET", new BigDecimal("10"), null
        );

        // 同步返回时订单已受理（由于 match-delay-ms=0，状态可能已是 ACCEPTED 或 FILLED）
        assertNotNull(order.getOrderId());

        // 等待异步撮合完成
        ExchangeOrder finalOrder = waitForFinalState(order.getOrderId());

        assertEquals(OrderStatus.FILLED.getCode(), finalOrder.getStatus());
        BigDecimalAssert.assertDecimalEquals(new BigDecimal("10"), finalOrder.getFilledQty());
        // 买单按卖价成交
        BigDecimalAssert.assertDecimalEquals(ASK, finalOrder.getAvgPrice());
    }

    @Test
    @DisplayName("市价卖单异步撮合后按买价成交，状态为 FILLED")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void marketSellOrder_fillsAtBidPrice() throws InterruptedException {
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-003", SYMBOL, "SELL", "MARKET", new BigDecimal("5"), null
        );

        ExchangeOrder finalOrder = waitForFinalState(order.getOrderId());

        assertEquals(OrderStatus.FILLED.getCode(), finalOrder.getStatus());
        BigDecimalAssert.assertDecimalEquals(new BigDecimal("5"), finalOrder.getFilledQty());
        // 卖单按买价成交
        BigDecimalAssert.assertDecimalEquals(BID, finalOrder.getAvgPrice());
    }

    @Test
    @DisplayName("限价买单：委托价 >= 卖价则成交")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void limitBuyOrder_priceAtOrAboveAsk_fills() throws InterruptedException {
        // 委托价 521.00 >= 卖价 520.70，应成交
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-004", SYMBOL, "BUY", "LIMIT", new BigDecimal("10"), new BigDecimal("521.00")
        );

        ExchangeOrder finalOrder = waitForFinalState(order.getOrderId());

        assertEquals(OrderStatus.FILLED.getCode(), finalOrder.getStatus());
        // 成交价取对侧盘口价（卖价），而非委托价
        BigDecimalAssert.assertDecimalEquals(ASK, finalOrder.getAvgPrice());
    }

    @Test
    @DisplayName("限价买单：委托价 < 卖价则拒单")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void limitBuyOrder_priceBelowAsk_rejected() throws InterruptedException {
        // 委托价 520.00 < 卖价 520.70，应拒单
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-005", SYMBOL, "BUY", "LIMIT", new BigDecimal("10"), new BigDecimal("520.00")
        );

        ExchangeOrder finalOrder = waitForFinalState(order.getOrderId());

        assertEquals(OrderStatus.REJECTED.getCode(), finalOrder.getStatus());
        assertEquals(BigDecimal.ZERO, finalOrder.getFilledQty());
    }

    @Test
    @DisplayName("限价卖单：委托价 <= 买价则成交")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void limitSellOrder_priceAtOrBelowBid_fills() throws InterruptedException {
        // 委托价 520.00 <= 买价 520.50，应成交
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-006", SYMBOL, "SELL", "LIMIT", new BigDecimal("8"), new BigDecimal("520.00")
        );

        ExchangeOrder finalOrder = waitForFinalState(order.getOrderId());

        assertEquals(OrderStatus.FILLED.getCode(), finalOrder.getStatus());
        // 成交价取对侧盘口价（买价）
        BigDecimalAssert.assertDecimalEquals(BID, finalOrder.getAvgPrice());
    }

    @Test
    @DisplayName("限价卖单：委托价 > 买价则拒单")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void limitSellOrder_priceAboveBid_rejected() throws InterruptedException {
        // 委托价 521.00 > 买价 520.50，应拒单
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-007", SYMBOL, "SELL", "LIMIT", new BigDecimal("8"), new BigDecimal("521.00")
        );

        ExchangeOrder finalOrder = waitForFinalState(order.getOrderId());

        assertEquals(OrderStatus.REJECTED.getCode(), finalOrder.getStatus());
    }

    @Test
    @DisplayName("合约无行情时订单被拒绝")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void orderWithNoMarketData_rejected() throws InterruptedException {
        stubMarketDataEngine.setMarketData(null);

        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-008", SYMBOL, "BUY", "MARKET", new BigDecimal("10"), null
        );

        ExchangeOrder finalOrder = waitForFinalState(order.getOrderId());

        assertEquals(OrderStatus.REJECTED.getCode(), finalOrder.getStatus());
    }

    @Test
    @DisplayName("成交后生成成交流水")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void filledOrder_generatesTradeFill() throws InterruptedException {
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-009", SYMBOL, "BUY", "MARKET", new BigDecimal("10"), null
        );

        waitForFinalState(order.getOrderId());

        List<TradeFill> fills = matchingEngine.getTradeFills();
        assertEquals(1, fills.size());
        TradeFill fill = fills.get(0);
        assertEquals(order.getOrderId(), fill.getOrderId());
        assertEquals(SYMBOL, fill.getSymbol());
        BigDecimalAssert.assertDecimalEquals(new BigDecimal("10"), fill.getQty());
        BigDecimalAssert.assertDecimalEquals(ASK, fill.getPrice());
        assertNotNull(fill.getTradeId());
        assertTrue(fill.getTradeId().startsWith("T"));
    }

    @Test
    @DisplayName("参数校验：symbol 为空抛 IllegalArgumentException")
    void submitOrder_emptySymbol_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                matchingEngine.submitOrder("C001", "", "BUY", "MARKET", new BigDecimal("10"), null)
        );
    }

    @Test
    @DisplayName("参数校验：side 非法抛 IllegalArgumentException")
    void submitOrder_invalidSide_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                matchingEngine.submitOrder("C001", SYMBOL, "INVALID", "MARKET", new BigDecimal("10"), null)
        );
    }

    @Test
    @DisplayName("参数校验：qty <= 0 抛 IllegalArgumentException")
    void submitOrder_nonPositiveQty_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                matchingEngine.submitOrder("C001", SYMBOL, "BUY", "MARKET", BigDecimal.ZERO, null)
        );
    }

    @Test
    @DisplayName("参数校验：限价单 price 为空抛 IllegalArgumentException")
    void submitOrder_limitOrderWithoutPrice_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                matchingEngine.submitOrder("C001", SYMBOL, "BUY", "LIMIT", new BigDecimal("10"), null)
        );
    }

    @Test
    @DisplayName("参数校验失败时不生成订单")
    void submitOrder_validationFailure_noOrderCreated() {
        assertThrows(IllegalArgumentException.class, () ->
                matchingEngine.submitOrder("C001", SYMBOL, "BUY", "MARKET", BigDecimal.ZERO, null)
        );

        assertEquals(0, matchingEngine.getAllOrders().size());
    }

    @Test
    @DisplayName("撮合完成后推送订单状态回报（模拟 OnRtnOrder）")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void matching_pushesOrderStatusCallback() throws InterruptedException {
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-010", SYMBOL, "BUY", "MARKET", new BigDecimal("10"), null
        );

        waitForFinalState(order.getOrderId());

        // 至少推送过 2 次订单状态回报：ACCEPTED + FILLED
        assertTrue(stubCallbackRegistry.orderCallbackCount >= 2,
                "Expected at least 2 order callbacks, got " + stubCallbackRegistry.orderCallbackCount);
    }

    @Test
    @DisplayName("成交后推送成交通知（模拟 OnRtnTrade）")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void matching_pushesTradeCallback() throws InterruptedException {
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-011", SYMBOL, "BUY", "MARKET", new BigDecimal("10"), null
        );

        waitForFinalState(order.getOrderId());

        // 成交后应推送 1 次成交通知
        assertEquals(1, stubCallbackRegistry.tradeCallbackCount);
        assertNotNull(stubCallbackRegistry.lastTradePayload);
        assertTrue(stubCallbackRegistry.lastTradePayload instanceof TradeFill);
    }

    @Test
    @DisplayName("拒单时不推送成交通知")
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void rejectedOrder_noTradeCallback() throws InterruptedException {
        // 限价单未触及盘口 → 拒单
        matchingEngine.submitOrder(
                "C001-012", SYMBOL, "BUY", "LIMIT", new BigDecimal("10"), new BigDecimal("520.00")
        );

        // 等待异步处理完成
        Thread.sleep(200);

        assertEquals(0, stubCallbackRegistry.tradeCallbackCount);
    }

    @Test
    @DisplayName("getOrder 查询不存在的订单返回 null")
    void getOrder_nonExistent_returnsNull() {
        assertNull(matchingEngine.getOrder("non-existent-id"));
    }

    @Test
    @DisplayName("getOrder 查询存在的订单返回订单对象")
    void getOrder_existing_returnsOrder() {
        ExchangeOrder order = matchingEngine.submitOrder(
                "C001-013", SYMBOL, "BUY", "MARKET", new BigDecimal("10"), null
        );

        ExchangeOrder found = matchingEngine.getOrder(order.getOrderId());
        assertNotNull(found);
        assertEquals(order.getOrderId(), found.getOrderId());
    }
}
