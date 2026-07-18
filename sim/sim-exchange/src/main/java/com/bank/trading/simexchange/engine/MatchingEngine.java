package com.bank.trading.simexchange.engine;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.enums.OrderStatus;
import com.bank.trading.common.core.enums.OrderType;
import com.bank.trading.simexchange.callback.CallbackRegistry;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.TradeFill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合引擎，模拟交易所的成交对手方。
 * <p>
 * 采用<b>异步两阶段撮合模型</b>，对齐真实期货交易所（CTP / CME Globex FIX）的接口语义：
 * <ol>
 *   <li><b>同步受理阶段</b>：{@link #submitOrder} 接收订单后仅做参数校验，
 *       生成订单 ID、入表，状态置为 NEW，立即返回。此时<b>不撮合、不成交</b>。</li>
 *   <li><b>异步撮合阶段</b>：订单入表后提交到异步线程池，延迟 {@code match-delay-ms} 后撮合。
 *       撮合规则按订单类型决定成交价：
 *       <ul>
 *         <li>市价单（MARKET）：按对侧盘口价全量成交（买单取 askPrice，卖单取 bidPrice）</li>
 *         <li>限价单（LIMIT）：委托价触及对侧盘口价则按对侧盘口价成交，否则拒单</li>
 *       </ul>
 *       若合约无行情，订单置为 REJECTED。</li>
 *   <li><b>回调推送阶段</b>：撮合完成后通过 {@link CallbackRegistry} 推送两类 Webhook 回调：
 *       <ul>
 *         <li>订单状态回报（模拟 CTP {@code OnRtnOrder}）：状态变为 ACCEPTED / FILLED / REJECTED</li>
 *         <li>成交通知（模拟 CTP {@code OnRtnTrade}）：成交时推送 TradeFill</li>
 *       </ul>
 * </ol>
 * <p>
 * 与真实交易所的差异：真实交易所通过 TCP 长连接 + SDK 回调推送，本模拟通过 HTTP Webhook 推送，
 * 但<b>语义一致</b>——下单同步返回受理，成交异步推送。
 * <p>
 * 状态：所有订单按订单 ID 维护在 {@link #orderMap} 中，成交流水追加到 {@link #tradeFills}。
 */
@Component
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    /** 行情引擎，撮合时获取最新盘口价格作为成交价依据 */
    private final MarketDataEngine marketDataEngine;

    /** Webhook 回调注册表，撮合完成后推送订单回报与成交通知 */
    private final CallbackRegistry callbackRegistry;

    /** 订单 ID -> 订单对象的映射，用于按 ID 查询订单状态 */
    private final Map<String, ExchangeOrder> orderMap = new ConcurrentHashMap<>();

    /** 全部成交流水，按成交时间顺序追加；通过 getTradeFills() 返回快照副本 */
    private final List<TradeFill> tradeFills = new ArrayList<>();

    /** 成交 ID 自增序列，用于生成唯一成交号（"T" + 自增数） */
    private final AtomicLong tradeIdGenerator = new AtomicLong(0);

    /** 异步撮合线程池，单线程保证撮合顺序；真实交易所也是串行撮合 */
    private final ExecutorService matchingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "matching-engine");
        t.setDaemon(true);
        return t;
    });

    /**
     * 构造函数，通过依赖注入获取行情引擎与回调注册表。
     *
     * @param marketDataEngine 行情引擎实例
     * @param callbackRegistry Webhook 回调注册表
     */
    @Autowired
    public MatchingEngine(MarketDataEngine marketDataEngine, CallbackRegistry callbackRegistry) {
        this.marketDataEngine = marketDataEngine;
        this.callbackRegistry = callbackRegistry;
    }

    /**
     * 提交订单（同步受理阶段）。
     * <p>
     * 业务逻辑：
     * <ol>
     *   <li>参数校验：合约、方向、类型、数量必填；限价单价格必填；数量必须大于 0。
     *       校验失败直接抛出 IllegalArgumentException（同步拒绝，模拟 CTP 前置参数校验）。</li>
     *   <li>生成全局唯一订单 ID（UUID 去横线）。</li>
     *   <li>填充订单字段，初始成交量为 0，状态为 NEW，记录创建/更新时间戳。</li>
     *   <li>将订单放入 {@link #orderMap} 以便后续查询。</li>
     *   <li>提交异步撮合任务到 {@link #matchingExecutor}。</li>
     *   <li><b>同步返回状态为 NEW 的订单对象</b>，不等待撮合结果。</li>
     * </ol>
     *
     * @param clientOrderId 客户端自定义订单号，用于幂等去重与对账
     * @param symbol        合约代码
     * @param side          订单方向（BUY/SELL）
     * @param type          订单类型（MARKET/LIMIT）
     * @param qty           委托数量
     * @param price         委托价格；市价单可空，限价单必填
     * @return 状态为 NEW 的订单对象（尚未撮合）
     * @throws IllegalArgumentException 参数校验失败
     */
    public ExchangeOrder submitOrder(String clientOrderId, String symbol, String side,
                                     String type, BigDecimal qty, BigDecimal price) {
        // 参数校验（同步拒绝，模拟 CTP 前置校验）
        validateOrderParams(symbol, side, type, qty, price);

        ExchangeOrder order = new ExchangeOrder();
        order.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        order.setClientOrderId(clientOrderId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setType(type);
        order.setQty(qty);
        order.setPrice(price);
        order.setFilledQty(BigDecimal.ZERO);
        order.setStatus(OrderStatus.NEW.getCode());
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());

        orderMap.put(order.getOrderId(), order);

        // 提交异步撮合任务
        matchingExecutor.submit(() -> matchOrderAsync(order));

        log.info("Order accepted (NEW), pending async matching: orderId={}, symbol={}, side={}, qty={}",
                order.getOrderId(), symbol, side, qty);

        return order;
    }

    /**
     * 参数校验。校验失败抛出 IllegalArgumentException，模拟 CTP 前置机的同步参数校验。
     *
     * @param symbol 合约代码
     * @param side   方向
     * @param type   类型
     * @param qty    数量
     * @param price  价格（限价单必填）
     */
    private void validateOrderParams(String symbol, String side, String type,
                                     BigDecimal qty, BigDecimal price) {
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("symbol is required");
        }
        if (side == null || (!side.equals("BUY") && !side.equals("SELL"))) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        if (type == null || (!type.equals("MARKET") && !type.equals("LIMIT"))) {
            throw new IllegalArgumentException("type must be MARKET or LIMIT");
        }
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        if (type.equals("LIMIT") && (price == null || price.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("price is required and must be positive for LIMIT order");
        }
    }

    /**
     * 异步撮合（撮合阶段 + 回调推送阶段）。
     * <p>
     * 在异步线程中执行：延迟 → 撮合 → 推送回调。延迟模拟真实交易所从受理到撮合的网络与处理延迟。
     *
     * @param order 待撮合订单（状态为 NEW）
     */
    private void matchOrderAsync(ExchangeOrder order) {
        try {
            // 延迟撮合，模拟交易所处理延迟
            long delay = callbackRegistry.getMatchDelayMs();
            if (delay > 0) {
                Thread.sleep(delay);
            }

            // 状态先置为 ACCEPTED，表示订单已进入撮合队列（模拟 CTP OnRtnOrder ACCEPTED 回报）
            updateOrderStatus(order, OrderStatus.ACCEPTED);

            // 执行撮合
            matchOrder(order);

            // 推送成交通知（如有成交）
            // 在 matchOrder/fillOrder 中已经生成了 TradeFill，这里查询推送
            TradeFill latestFill = findLatestFillByOrderId(order.getOrderId());
            if (latestFill != null) {
                callbackRegistry.notifyTrade(latestFill);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Matching interrupted: orderId={}", order.getOrderId());
            updateOrderStatus(order, OrderStatus.REJECTED);
        } catch (Exception e) {
            log.error("Matching error: orderId={}", order.getOrderId(), e);
            updateOrderStatus(order, OrderStatus.REJECTED);
        }
    }

    /**
     * 更新订单状态并推送订单状态回报（模拟 CTP OnRtnOrder）。
     *
     * @param order  订单对象
     * @param status 新状态
     */
    private void updateOrderStatus(ExchangeOrder order, OrderStatus status) {
        order.setStatus(status.getCode());
        order.setUpdatedAt(System.currentTimeMillis());
        // 推送订单状态回报
        callbackRegistry.notifyOrderUpdate(order);
        log.info("Order status update: orderId={}, status={}", order.getOrderId(), status.getCode());
    }

    /**
     * 订单撮合核心逻辑。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>从行情引擎获取该合约最新盘口；若不存在行情，订单置为 REJECTED 并返回。</li>
     *   <li>解析订单方向与类型枚举。</li>
     *   <li>市价单：成交价取对侧盘口价（买单取 askPrice，卖单取 bidPrice）。</li>
     *   <li>限价单：先用委托价校验是否触及对侧盘口价；未触及则置为 REJECTED，否则按对侧盘口价成交。</li>
     *   <li>调用 {@link #fillOrder} 完成全量成交。</li>
     * </ol>
     *
     * @param order 待撮合订单
     */
    private void matchOrder(ExchangeOrder order) {
        String symbol = order.getSymbol();
        var marketData = marketDataEngine.getLatest(symbol);
        if (marketData == null) {
            updateOrderStatus(order, OrderStatus.REJECTED);
            log.warn("Order rejected: no market data for symbol={}", symbol);
            return;
        }

        OrderSide side = OrderSide.of(order.getSide());
        OrderType type = OrderType.of(order.getType());

        BigDecimal execPrice;
        if (type == OrderType.MARKET) {
            execPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
        } else {
            execPrice = order.getPrice();
            BigDecimal marketPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
            boolean canMatch = side == OrderSide.BUY
                    ? execPrice.compareTo(marketPrice) >= 0
                    : execPrice.compareTo(marketPrice) <= 0;
            if (!canMatch) {
                updateOrderStatus(order, OrderStatus.REJECTED);
                log.warn("Order rejected: limit price cannot match market, orderId={}", order.getOrderId());
                return;
            }
            execPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
        }

        fillOrder(order, order.getQty(), execPrice);
    }

    /**
     * 执行成交：生成成交流水并更新订单状态为 FILLED。
     * <p>
     * 业务逻辑：
     * <ol>
     *   <li>计算成交金额 = 成交量 × 成交价。</li>
     *   <li>构造 {@link TradeFill} 流水，分配唯一成交 ID（"T" + 自增序号）。</li>
     *   <li>将流水追加到 {@link #tradeFills}。</li>
     *   <li>更新订单：累计成交量、平均成交价、状态置为 FILLED、刷新更新时间。</li>
     * </ol>
     *
     * @param order     待成交订单
     * @param fillQty   本次成交量
     * @param fillPrice 本次成交价
     */
    private void fillOrder(ExchangeOrder order, BigDecimal fillQty, BigDecimal fillPrice) {
        BigDecimal amount = fillQty.multiply(fillPrice);

        TradeFill fill = new TradeFill();
        fill.setTradeId("T" + tradeIdGenerator.incrementAndGet());
        fill.setOrderId(order.getOrderId());
        fill.setSymbol(order.getSymbol());
        fill.setSide(order.getSide());
        fill.setQty(fillQty);
        fill.setPrice(fillPrice);
        fill.setAmount(amount);
        fill.setTradeTime(System.currentTimeMillis());

        tradeFills.add(fill);

        order.setFilledQty(order.getFilledQty().add(fillQty));
        order.setAvgPrice(amount.divide(fillQty, 8, RoundingMode.HALF_UP));
        updateOrderStatus(order, OrderStatus.FILLED);

        log.info("Order filled: orderId={}, symbol={}, side={}, qty={}, price={}",
                order.getOrderId(), order.getSymbol(), order.getSide(), fillQty, fillPrice);
    }

    /**
     * 查找指定订单的最新成交流水（用于回调推送）。
     *
     * @param orderId 订单 ID
     * @return 最新成交流水；无成交返回 null
     */
    private TradeFill findLatestFillByOrderId(String orderId) {
        TradeFill latest = null;
        for (TradeFill fill : tradeFills) {
            if (orderId.equals(fill.getOrderId())) {
                latest = fill;
            }
        }
        return latest;
    }

    /**
     * 根据订单 ID 查询订单。
     *
     * @param orderId 订单 ID
     * @return 订单对象；不存在时返回 null
     */
    public ExchangeOrder getOrder(String orderId) {
        return orderMap.get(orderId);
    }

    /**
     * 获取全部成交流水的副本。
     *
     * @return 成交流水列表副本
     */
    public List<TradeFill> getTradeFills() {
        return new ArrayList<>(tradeFills);
    }

    /**
     * 获取全部订单的副本（用于测试与监控）。
     *
     * @return 订单列表副本
     */
    public List<ExchangeOrder> getAllOrders() {
        return new ArrayList<>(orderMap.values());
    }

    /**
     * 仅供测试使用：清空所有订单与成交流水。
     * <p>
     * 生产环境不应调用此方法。
     */
    public void clearForTest() {
        orderMap.clear();
        tradeFills.clear();
        tradeIdGenerator.set(0);
    }
}
