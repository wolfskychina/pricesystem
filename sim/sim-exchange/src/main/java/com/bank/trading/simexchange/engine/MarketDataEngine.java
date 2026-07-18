package com.bank.trading.simexchange.engine;

import com.bank.trading.simexchange.model.MarketData;
import com.bank.trading.simexchange.model.SymbolConfig;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 行情引擎，模拟交易所行情生成的核心组件。
 * <p>
 * 该引擎为每个注册合约维护独立的状态（配置、GBM 生成器、当前价、累计成交量、最新行情快照），
 * 在每次 {@link #tick()} 调用中按 GBM 模型演化各合约价格，并基于最新价合成盘口行情
 * （买价、卖价、买量、卖量、最新成交价、最新成交量、累计成交量）。
 * <p>
 * 业务背景：真实交易所的盘口由真实订单簿驱动，而模拟交易所没有订单簿，盘口通过规则化
 * 方式从最新价派生（详见 {@link #generateMarketData(String)} 注释），既保证行情逼真，
 * 又避免维护复杂撮合簿的开销。
 * <p>
 * 线程安全：使用 {@link ConcurrentHashMap} 与 {@link CopyOnWriteArrayList} 以支持
 * 行情调度线程（写）与 REST/WebSocket 读取线程（读）之间的安全并发访问。
 */
@Component
public class MarketDataEngine {


    /** 日志器 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketDataEngine.class);

    /** 合约代码 -> 合约配置，提供 tickSize、minQty 等盘口合成所需参数 */
    private final Map<String, SymbolConfig> symbolConfigs = new ConcurrentHashMap<>();
    /** 合约代码 -> GBM 价格生成器，每个合约独立演化价格 */
    private final Map<String, GbmPriceGenerator> generators = new ConcurrentHashMap<>();
    /** 合约代码 -> 当前价格（double），作为下次 GBM 演化的起点 */
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();
    /** 合约代码 -> 最新行情快照，供 REST/WebSocket 读取 */
    private final Map<String, MarketData> latestMarketData = new ConcurrentHashMap<>();
    /** 合约代码 -> 累计成交量，每次最新成交数量累加进去 */
    private final Map<String, Long> volumes = new ConcurrentHashMap<>();

    /** 全部合约代码列表，使用 CopyOnWriteArrayList 保证遍历时的线程安全 */
    private final List<String> symbols = new CopyOnWriteArrayList<>();

    /**
     * 初始化行情引擎。
     * <p>
     * 业务逻辑：遍历配置的合约清单，为每个合约注册配置、创建对应的 GBM 价格生成器
     * （以合约自身的 drift/volatility 与全局 tick 间隔为参数），将当前价置为初始价，
     * 累计成交量清零，并立即生成首笔行情快照，使交易所启动后即可对外提供行情。
     *
     * @param configs        合约配置清单
     * @param intervalSeconds tick 间隔（秒），作为 GBM 单步时间长度
     */
    public void init(List<SymbolConfig> configs, double intervalSeconds) {
        for (SymbolConfig config : configs) {
            symbolConfigs.put(config.getCode(), config);
            // 为该合约创建独立的 GBM 生成器，使用其自身的 drift/volatility
            generators.put(config.getCode(),
                    new GbmPriceGenerator(config.getDrift(), config.getVolatility(), intervalSeconds));
            // 当前价初始化为配置中的初始价，作为首次 GBM 演化的起点
            currentPrices.put(config.getCode(), config.getInitialPrice().doubleValue());
            // 累计成交量从 0 开始
            volumes.put(config.getCode(), 0L);
            symbols.add(config.getCode());
            // 立即生成首笔行情，使交易所启动后即可对外提供行情快照
            generateMarketData(config.getCode());
        }
        log.info("MarketDataEngine initialized with {} symbols: {}", symbols.size(), symbols);
    }

    /**
     * 推动一次行情 tick。
     * <p>
     * 业务逻辑：对所有注册合约逐一调用 {@link #generateMarketData(String)}，使每个合约
     * 价格按 GBM 演化一步并刷新盘口。该方法由调度器周期性触发，频率由配置项
     * {@code sim-exchange.interval-ms} 决定。
     */
    public void tick() {
        for (String symbol : symbols) {
            generateMarketData(symbol);
        }
    }

    /**
     * 为指定合约生成一笔新的行情快照。
     * <p>
     * 业务逻辑要点：
     * <ol>
     *   <li>使用 GBM 生成器基于当前价演化得到新价，并更新当前价缓存。</li>
     *   <li>将新价按 tickSize 取整，得到合规的最小报价单位整数倍的"最新成交价"。</li>
     *   <li>以最新成交价为中心，按 1~3 倍 tickSize 随机生成买卖价差（spread），
     *       买价 = 最新价 - spread，卖价 = 最新价 + spread；买价下限不低于一个 tickSize，
     *       避免出现非正价格。</li>
     *   <li>最新成交量按 minQty 的 1~10 倍随机生成，模拟一笔成交；
     *       买卖盘挂量按 minQty 的 5~24 倍随机生成，模拟做市挂单深度。</li>
     *   <li>累计成交量累加本次最新成交量。</li>
     *   <li>组装 {@link MarketData} 并存入 latestMarketData，供 REST/WebSocket 读取。</li>
     * </ol>
     * <p>
     * 设计说明：盘口并非来自真实订单簿，而是从最新价派生，这是模拟交易所与真实交易所
     * 的关键差异。这种规则化合成既保证了行情的逼真度（有合理价差与挂单深度），
     * 又避免了维护复杂撮合簿的开销，适合做策略验证与功能测试。
     *
     * @param symbol 合约代码
     */
    private void generateMarketData(String symbol) {
        SymbolConfig config = symbolConfigs.get(symbol);
        GbmPriceGenerator gen = generators.get(symbol);
        double currentPrice = currentPrices.get(symbol);

        // 按 GBM 演化得到新价（保留 double 精度，下面再按 tickSize 取整）
        double newPrice = gen.nextPrice(currentPrice);
        currentPrices.put(symbol, newPrice);

        BigDecimal tickSize = config.getTickSize();
        // 将新价按 tickSize 四舍五入取整，保证报价是 tickSize 的整数倍（合规报价）
        BigDecimal lastPrice = roundToTick(newPrice, tickSize);

        // 买卖价差 spread = tickSize * (1 + 0..2)，随机取 1、2 或 3 倍 tickSize
        // 模拟真实行情中做市商的报价价差，价差大小反映市场流动性
        BigDecimal spread = tickSize.multiply(BigDecimal.valueOf(1 + (int)(Math.random() * 3)));
        // 买价 = 最新价 - 价差；max(tickSize) 防止价格被减到非正数
        BigDecimal bidPrice = lastPrice.subtract(spread).max(tickSize);
        // 卖价 = 最新价 + 价差
        BigDecimal askPrice = lastPrice.add(spread);

        // 最新成交量为 minQty 的 1~10 倍随机，模拟单笔成交规模
        BigDecimal lastQty = BigDecimal.valueOf(config.getMinQty().intValue() * (1 + (int)(Math.random() * 10)));
        // 买盘挂量为 minQty 的 5~24 倍随机，模拟买方挂单深度
        BigDecimal bidQty = BigDecimal.valueOf(config.getMinQty().intValue() * (5 + (int)(Math.random() * 20)));
        // 卖盘挂量为 minQty 的 5~24 倍随机，模拟卖方挂单深度
        BigDecimal askQty = BigDecimal.valueOf(config.getMinQty().intValue() * (5 + (int)(Math.random() * 20)));

        // 累计成交量 = 历史累计 + 本次最新成交量
        long volume = volumes.get(symbol) + lastQty.longValue();
        volumes.put(symbol, volume);

        MarketData md = new MarketData();
        md.setSymbol(symbol);
        md.setBidPrice(bidPrice);
        md.setAskPrice(askPrice);
        md.setLastPrice(lastPrice);
        md.setBidQty(bidQty);
        md.setAskQty(askQty);
        md.setLastQty(lastQty);
        md.setVolume(volume);
        md.setTimestamp(System.currentTimeMillis());

        // 更新最新行情快照，供 REST 与 WebSocket 读取
        latestMarketData.put(symbol, md);
    }

    /**
     * 将价格按 tickSize 取整为合规报价。
     * <p>
     * 业务逻辑：先用价格除以 tickSize 得到"包含几个 tick"，四舍五入到整数个 tick，
     * 再乘回 tickSize 得到合规报价，并按 tickSize 的小数位数对齐精度。
     * <p>
     * 举例：tickSize=0.0001，价格 1.085056 → ticks=10851（四舍五入） → 报价 1.0851。
     * 这一步是模拟交易所确保报价符合合约最小变动价位约束的关键。
     *
     * @param price    原始价格（double）
     * @param tickSize 最小变动价位
     * @return 取整后的合规报价
     */
    private BigDecimal roundToTick(double price, BigDecimal tickSize) {
        BigDecimal bd = BigDecimal.valueOf(price);
        // 价格 / tickSize，四舍五入到 0 位小数，得到整数个 tick
        BigDecimal ticks = bd.divide(tickSize, 0, RoundingMode.HALF_UP);
        // 乘回 tickSize 还原为价格，并按 tickSize 的小数位数对齐精度
        return ticks.multiply(tickSize).setScale(tickSize.scale(), RoundingMode.HALF_UP);
    }

    /**
     * 获取全部合约代码列表。
     *
     * @return 合约代码字符串列表
     */
    public List<String> getSymbols() {
        return symbols;
    }

    /**
     * 获取指定合约的最新行情快照。
     *
     * @param symbol 合约代码
     * @return 最新行情；合约不存在或尚未生成行情时返回 null
     */
    public MarketData getLatest(String symbol) {
        return latestMarketData.get(symbol);
    }

    /**
     * 获取指定合约的最新成交价。
     * <p>
     * 业务背景：撮合引擎在处理市价单/限价单时需要参考最新价或盘口价，本方法提供
     * 便捷访问。
     *
     * @param symbol 合约代码
     * @return 最新成交价；合约不存在时返回 null
     */
    public BigDecimal getLastPrice(String symbol) {
        MarketData md = latestMarketData.get(symbol);
        return md != null ? md.getLastPrice() : null;
    }
}
