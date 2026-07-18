package com.bank.trading.simexchange.engine;

import java.util.Random;

/**
 * 基于几何布朗运动（Geometric Brownian Motion, GBM）的价格生成器。
 * <p>
 * 该类是模拟交易所行情生成的"数学心脏"，每个合约对应一个独立的 {@code GbmPriceGenerator}
 * 实例。每调用一次 {@link #nextPrice(double)}，就以上一时刻价格为起点，按 GBM 模型
 * 演化出下一时刻价格。
 *
 * <h2>数学原理</h2>
 * GBM 是 Black-Scholes 期权定价模型中描述标的资产价格运动的经典随机过程，其随机微分
 * 方程形式为：
 * <pre>
 *   dS(t) = mu * S(t) dt + sigma * S(t) dW(t)
 * </pre>
 * 其中：
 * <ul>
 *   <li>S(t)：t 时刻资产价格</li>
 *   <li>mu：漂移率（drift），表示价格的平均变化趋势，年化</li>
 *   <li>sigma：波动率（volatility），表示价格波动的剧烈程度，年化</li>
 *   <li>W(t)：标准维纳过程（布朗运动），dW(t) ~ N(0, dt)</li>
 * </ul>
 * <p>
 * 对上述 SDE 用 Ito 引理求解，可得价格在时间步长 dt 后的解析解：
 * <pre>
 *   S(t+dt) = S(t) * exp( (mu - 0.5 * sigma^2) * dt + sigma * sqrt(dt) * z )
 * </pre>
 * 其中 z ~ N(0, 1) 是标准正态分布随机变量。本类 {@link #nextPrice(double)} 即实现该公式。
 *
 * <h2>关键点说明</h2>
 * <ul>
 *   <li>公式中的 {@code -0.5 * sigma^2 * dt} 项是 Ito 校正项，源于随机微积分中
 *       二阶项不可忽略；若省略该项会导致长期模拟出现正向偏差（价格系统性偏高）。</li>
 *   <li>使用 {@code exp(...)} 而非线性形式，保证价格始终为正，符合资产价格非负的
 *       经济学约束。</li>
 *   <li>{@code sqrt(dt) * z} 体现了布朗运动的方差与时间成正比的特性：
 *       Var(dW) = dt，故标准差为 sqrt(dt)。</li>
 *   <li>dt 以"年"为单位，所以构造函数中将秒数除以 86400（一年的秒数）做归一化，
 *       使得 drift 与 volatility 可以按年化参数给定，与金融惯例一致。</li>
 * </ul>
 */
public class GbmPriceGenerator {

    /** 漂移率 mu（年化），表示价格的平均变化趋势；0 表示无趋势的纯波动 */
    private final double drift;
    /** 波动率 sigma（年化），表示价格波动的剧烈程度；越大价格越剧烈震荡 */
    private final double volatility;
    /**
     * 单步时间长度 dt，单位：年。
     * 由构造函数将秒数除以 86400（一年秒数）得到，使得 drift/volatility 可按年化口径配置。
     */
    private final double dt;
    /** 随机数源，用于产生标准正态分布随机变量 z；可注入便于测试确定性 */
    private final Random random;

    /**
     * 全参数构造函数，允许注入随机数源（便于单元测试中固定随机序列以做断言）。
     *
     * @param drift      漂移率 mu（年化）
     * @param volatility 波动率 sigma（年化）
     * @param dt         单步时间长度（年）
     * @param random     随机数源
     */
    public GbmPriceGenerator(double drift, double volatility, double dt, Random random) {
        this.drift = drift;
        this.volatility = volatility;
        this.dt = dt;
        this.random = random;
    }

    /**
     * 便捷构造函数，使用默认随机数源，并将秒数时间步长归一化为"年"单位。
     * <p>
     * 业务场景：行情引擎按 tick 间隔（如 1 秒）调用一次，需将秒数换算为年化时间步长
     * 以与 drift/volatility 的年化口径对齐。
     *
     * @param drift       漂移率 mu（年化）
     * @param volatility  波动率 sigma（年化）
     * @param dtSeconds   单步时间长度（秒），由调度器 tick 间隔换算而来
     */
    public GbmPriceGenerator(double drift, double volatility, double dtSeconds) {
        this.drift = drift;
        this.volatility = volatility;
        // 将秒换算为年：86400 = 60*60*24，即一天的秒数（此处按 365 天近似一年）
        this.dt = dtSeconds / 86400.0;
        this.random = new Random();
    }

    /**
     * 给定当前价格，按 GBM 模型生成下一时刻价格。
     * <p>
     * 实现公式：
     * <pre>
     *   S(t+dt) = S(t) * exp( (mu - 0.5 * sigma^2) * dt + sigma * sqrt(dt) * z )
     * </pre>
     *
     * @param currentPrice 当前价格 S(t)，必须为正数
     * @return 下一时刻价格 S(t+dt)，恒为正数
     */
    public double nextPrice(double currentPrice) {
        // 标准正态分布随机变量 z ~ N(0, 1)
        double z = random.nextGaussian();
        // Ito 校正后的漂移项：(mu - 0.5 * sigma^2) * dt
        // 其中 -0.5*sigma^2 是 Ito 项，避免长期模拟出现正向偏差
        double mu = drift - 0.5 * volatility * volatility;
        // 指数部分：漂移项 + 随机扰动项 sigma*sqrt(dt)*z
        // sqrt(dt) 体现布朗运动方差与时间成正比的特性
        double exponent = mu * dt + volatility * Math.sqrt(dt) * z;
        // 用指数函数保证价格为正，并实现乘性演化
        return currentPrice * Math.exp(exponent);
    }
}
