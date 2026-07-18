package com.bank.trading.simexchange.config;

import com.bank.trading.simexchange.engine.MarketDataEngine;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 行情定时调度器。
 * <p>
 * 该组件是模拟交易所（sim-exchange）行情驱动的"心跳"所在，承担两类职责：
 * <ol>
 *   <li>在应用启动完成（{@link ApplicationReadyEvent}）后，使用配置文件中的合约列表与
 *       tick 间隔初始化 {@link MarketDataEngine}，为每个合约创建独立的 GBM 价格生成器
 *       并生成首笔行情快照。</li>
 *   <li>以固定延迟触发 {@link MarketDataEngine#tick()}，使每个合约的价格按几何布朗运动
 *       演化一步，并刷新盘口行情。该 tick 也是 WebSocket 行情广播的同步节拍。</li>
 * </ol>
 * <p>
 * 业务背景：模拟交易所没有真实撮合盘口，所有行情均由本调度器周期性"推动"产生，
 * 调度周期由配置项 {@code sim-exchange.interval-ms} 决定（默认 1000ms）。
 */
@Component
public class MarketDataScheduler {

    /** 行情引擎，负责维护各合约的最新行情、价格生成器与盘口快照 */
    private final MarketDataEngine marketDataEngine;
    /** 模拟交易所配置属性，包含 tick 间隔与合约清单 */
    private final SimExchangeProperties properties;

    /** 日志器 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketDataScheduler.class);

    /**
     * 构造函数，通过 Spring 依赖注入获取行情引擎与配置属性。
     *
     * @param marketDataEngine 行情引擎实例
     * @param properties       模拟交易所配置属性
     */
    public MarketDataScheduler(MarketDataEngine marketDataEngine, SimExchangeProperties properties) {
        this.marketDataEngine = marketDataEngine;
        this.properties = properties;
    }

    /**
     * 应用就绪事件监听器，在 Spring Boot 完全启动后执行一次性初始化。
     * <p>
     * 将配置中的 tick 间隔（毫秒）换算为秒，作为 GBM 模型中的时间步长 dt，
     * 传入行情引擎以完成各合约价格生成器的构建与首笔行情生成。
     * <p>
     * 选择 {@link ApplicationReadyEvent} 而非 {@code ApplicationStartedEvent} 是为了
     * 确保所有 Bean（包括 WebSocket Handler 等）都已就绪后再启动行情循环。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // 将毫秒级 tick 间隔换算为秒，作为 GBM 模型的单步时间长度
        double intervalSeconds = properties.getIntervalMs() / 1000.0;
        // 初始化行情引擎：注册合约、创建价格生成器、生成首笔行情快照
        marketDataEngine.init(properties.getSymbols(), intervalSeconds);
    }

    /**
     * 行情 tick 任务，按固定延迟周期性触发行情更新。
     * <p>
     * 调度策略使用 {@code fixedDelay}（自上次执行结束起计时）而非 {@code fixedRate}，
     * 这样即使某次 tick 处理耗时较长，也不会出现任务叠加执行的情况，保证行情时序稳定。
     * <p>
     * 异常被捕获并记录日志而不向上抛出，避免 Spring 调度器因异常而终止后续调度。
     */
    @Scheduled(fixedDelayString = "${sim-exchange.interval-ms:1000}")
    public void tick() {
        try {
            // 推动所有合约价格演化一步并刷新盘口行情
            marketDataEngine.tick();
        } catch (Exception e) {
            // 任何异常都不应中断后续 tick 调度，仅记录错误日志
            log.error("Market data tick error", e);
        }
    }
}
