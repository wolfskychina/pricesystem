package com.bank.trading.simexchange.engine;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.enums.OrderStatus;
import com.bank.trading.common.core.enums.OrderType;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.TradeFill;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 撮合引擎，模拟交易所的成交对手方。
 * <p>
 * 该引擎接收外部提交的订单，基于当前行情快照进行即时撮合，不维护订单簿挂单队列。
 * 撮合规则：
 * <ul>
 *   <li><b>市价单（MARKET）</b>：按当前盘口对侧价全量成交（买单价参考卖价 askPrice，
 *       卖单价参考买价 bidPrice）。如果对应合约无行情，则拒单。</li>
 *   <li><b>限价单（LIMIT）</b>：先校验限价是否触及对侧盘口价（买单限价 ≥ askPrice，
 *       卖单限价 ≤ bidPrice），触及则按对侧盘口价全量成交，否则拒单。</li>
 * </ul>
 * <p>
 * 业务背景：模拟交易所作为做市商系统的对手方，所有订单"即来即撮"，不存在挂单等待。
 * 这与真实连续竞价交易所不同，但满足策略回测与功能验证需求。
 * <p>
 * 状态：所有订单按订单 ID 维护在 {@link #orderMap} 中，成交流水追加到 {@link #tradeFills}，
 * 撮合后订单状态置为 FILLED（全部成交）或 REJECTED（拒单）。
 */
@Component
public class MatchingEngine {

    /** 行情引擎，撮合时获取最新盘口价格作为成交价依据 */
    private final MarketDataEngine marketDataEngine;

    /** 日志器 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MatchingEngine.class);

    /**
     * 构造函数，通过依赖注入获取行情引擎。
     *
     * @param marketDataEngine 行情引擎实例
     */
    public MatchingEngine(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    /** 订单 ID -> 订单对象的映射，用于按 ID 查询订单状态 */
    private final Map<String, ExchangeOrder> orderMap = new ConcurrentHashMap<>();
    /** 全部成交流水，按成交时间顺序追加；通过 getTradeFills() 返回快照副本 */
    private final List<TradeFill> tradeFills = new ArrayList<>();
    /** 成交 ID 自增序列，用于生成唯一成交号（"T" + 自增数） */
    private final AtomicLong tradeIdGenerator = new AtomicLong(0);

    /**
     * 提交订单并立即撮合。
     * <p>
     * 业务逻辑：
     * <ol>
     *   <li>生成全局唯一订单 ID（UUID 去横线）。</li>
     *   <li>填充订单字段：客户单号、合约、方向、类型、数量、价格，初始成交量为 0，
     *       状态为 NEW，并记录创建/更新时间戳。</li>
     *   <li>将订单放入 {@link #orderMap} 以便后续查询。</li>
     *   <li>调用 {@link #matchOrder(ExchangeOrder)} 立即撮合，撮合结果会更新订单状态
     *       与成交字段。</li>
     *   <li>返回包含撮合结果的订单对象。</li>
     * </ol>
     *
     * @param clientOrderId 客户端自定义订单号，用于幂等去重与对账
     * @param symbol        合约代码
     * @param side          订单方向（BUY/SELL）
     * @param type          订单类型（MARKET/LIMIT）
     * @param qty           委托数量
     * @param price         委托价格；市价单可空，限价单必填
     * @return 包含撮合结果的订单对象（已成交或已拒单）
     */
    public ExchangeOrder submitOrder(String clientOrderId, String symbol, String side,
                                     String type, BigDecimal qty, BigDecimal price) {
        ExchangeOrder order = new ExchangeOrder();
        // 生成全局唯一订单 ID，去掉横线使 ID 更紧凑
        order.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        order.setClientOrderId(clientOrderId);
        order.setSymbol(symbol);
        order.setSide(side);
        order.setType(type);
        order.setQty(qty);
        order.setPrice(price);
        // 初始成交量为 0
        order.setFilledQty(BigDecimal.ZERO);
        // 初始状态为 NEW
        order.setStatus(OrderStatus.NEW.getCode());
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(System.currentTimeMillis());

        // 入表以支持后续按 ID 查询
        orderMap.put(order.getOrderId(), order);

        // 立即撮合：模拟交易所为对手方，订单到达即撮合
        matchOrder(order);

        return order;
    }

    /**
     * 订单撮合核心逻辑。
     * <p>
     * 业务流程：
     * <ol>
     *   <li>从行情引擎获取该合约最新盘口；若不存在行情，订单置为 REJECTED 并返回。</li>
     *   <li>解析订单方向与类型枚举。</li>
     *   <li><b>市价单</b>：成交价取对侧盘口价（买单取 askPrice，卖单取 bidPrice）。</li>
     *   <li><b>限价单</b>：先用委托价校验是否触及对侧盘口价：
     *       <ul>
     *         <li>买单：委托价 ≥ askPrice 才能成交（买方愿意以卖方报价或更高价买入）</li>
     *         <li>卖单：委托价 ≤ bidPrice 才能成交（卖方愿意以买方报价或更低价卖出）</li>
     *       </ul>
     *       若未触及，订单置为 REJECTED 并返回；否则成交价同样取对侧盘口价
     *       （即按当前可成交的最优价格成交，而非按委托价成交，这是做市对手方的常见做法）。</li>
     *   <li>调用 {@link #fillOrder} 完成全量成交。</li>
     * </ol>
     *
     * @param order 待撮合订单
     */
    private void matchOrder(ExchangeOrder order) {
        String symbol = order.getSymbol();
        var marketData = marketDataEngine.getLatest(symbol);
        if (marketData == null) {
            // 合约无行情，无法撮合，直接拒单
            order.setStatus(OrderStatus.REJECTED.getCode());
            order.setUpdatedAt(System.currentTimeMillis());
            log.warn("Order rejected: no market data for symbol={}", symbol);
            return;
        }

        OrderSide side = OrderSide.of(order.getSide());
        OrderType type = OrderType.of(order.getType());

        BigDecimal execPrice;
        if (type == OrderType.MARKET) {
            // 市价单：买方吃卖盘（按 askPrice 成交），卖方吃买盘（按 bidPrice 成交）
            execPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
        } else {
            // 限价单：先以委托价参与价格校验
            execPrice = order.getPrice();
            // 对侧盘口价：买方看卖价，卖方看买价
            BigDecimal marketPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
            // 价格触及判定：
            //   买单：委托价 >= 对侧卖价 → 愿意以等于或高于卖方报价买入，可成交
            //   卖单：委托价 <= 对侧买价 → 愿意以等于或低于买方报价卖出，可成交
            boolean canMatch = side == OrderSide.BUY
                    ? execPrice.compareTo(marketPrice) >= 0
                    : execPrice.compareTo(marketPrice) <= 0;
            if (!canMatch) {
                // 限价未触及对侧盘口价，模拟交易所即时拒单（不挂单等待）
                order.setStatus(OrderStatus.REJECTED.getCode());
                order.setUpdatedAt(System.currentTimeMillis());
                log.warn("Order rejected: limit price cannot match market, orderId={}", order.getOrderId());
                return;
            }
            // 限价单成交价仍取对侧盘口价而非委托价：做市对手方按当前可成交最优价成交，
            // 委托价仅作为是否允许成交的价格门槛
            execPrice = side == OrderSide.BUY ? marketData.getAskPrice() : marketData.getBidPrice();
        }

        // 按确定的成交价全量成交（模拟交易所不部分成交）
        fillOrder(order, order.getQty(), execPrice);
    }

    /**
     * 执行成交：生成成交流水并更新订单状态。
     * <p>
     * 业务逻辑：
     * <ol>
     *   <li>计算成交金额 = 成交量 × 成交价。</li>
     *   <li>构造 {@link TradeFill} 流水，分配唯一成交 ID（"T" + 自增序号）。</li>
     *   <li>将流水追加到 {@link #tradeFills}。</li>
     *   <li>更新订单：累计成交量、平均成交价（= 成交金额 / 成交量，保留 8 位小数）、
     *       状态置为 FILLED、刷新更新时间。</li>
     * </ol>
     *
     * @param order     待成交订单
     * @param fillQty   本次成交量
     * @param fillPrice 本次成交价
     */
    private void fillOrder(ExchangeOrder order, BigDecimal fillQty, BigDecimal fillPrice) {
        // 成交金额 = 成交量 × 成交价
        BigDecimal amount = fillQty.multiply(fillPrice);

        TradeFill fill = new TradeFill();
        // 生成唯一成交 ID，前缀 T 便于识别为 TradeFill
        fill.setTradeId("T" + tradeIdGenerator.incrementAndGet());
        fill.setOrderId(order.getOrderId());
        fill.setSymbol(order.getSymbol());
        fill.setSide(order.getSide());
        fill.setQty(fillQty);
        fill.setPrice(fillPrice);
        fill.setAmount(amount);
        fill.setTradeTime(System.currentTimeMillis());

        tradeFills.add(fill);

        // 更新订单累计成交量
        order.setFilledQty(order.getFilledQty().add(fillQty));
        // 平均成交价 = 成交金额 / 成交量，保留 8 位小数，四舍五入
        // 这里使用成交金额 / 单次成交量计算均价；多次部分成交场景下也能反映加权均价
        order.setAvgPrice(amount.divide(fillQty, 8, java.math.RoundingMode.HALF_UP));
        // 状态置为 FILLED（全部成交）
        order.setStatus(OrderStatus.FILLED.getCode());
        order.setUpdatedAt(System.currentTimeMillis());

        log.info("Order filled: orderId={}, symbol={}, side={}, qty={}, price={}",
                order.getOrderId(), order.getSymbol(), order.getSide(), fillQty, fillPrice);
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
     * <p>
     * 返回新建 {@link ArrayList} 副本以避免外部修改影响内部状态。
     *
     * @return 成交流水列表副本
     */
    public List<TradeFill> getTradeFills() {
        return new ArrayList<>(tradeFills);
    }
}
