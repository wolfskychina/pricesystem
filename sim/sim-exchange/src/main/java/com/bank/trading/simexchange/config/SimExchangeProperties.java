package com.bank.trading.simexchange.config;

import com.bank.trading.simexchange.model.SymbolConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 模拟交易所配置属性。
 * <p>
 * 通过 Spring Boot 的 {@link ConfigurationProperties} 机制，将 {@code application.yml} 中
 * 前缀为 {@code sim-exchange} 的配置项绑定到本类字段。配置示例：
 * <pre>
 * sim-exchange:
 *   interval-ms: 1000        # 行情 tick 间隔（毫秒）
 *   symbols:                 # 合约清单
 *     - code: EURUSD
 *       name: 欧元兑美元
 *       initial-price: 1.0850
 *       volatility: 0.05
 *       drift: 0.0
 *       tick-size: 0.0001
 *       multiplier: 1
 *       min-qty: 10000
 * </pre>
 * <p>
 * 该配置类在系统中是模拟交易所行为的"参数面板"，调整这里的值即可改变行情生成节奏
 * 与各合约的价格特性。
 */
@Component
@ConfigurationProperties(prefix = "sim-exchange")
public class SimExchangeProperties {

    /** 行情 tick 间隔，单位毫秒。决定行情刷新频率与 GBM 模型的单步时间长度 */
    private int intervalMs;
    /** 合约清单配置，每个元素描述一个可交易合约的价格模型参数与盘口参数 */
    private List<SymbolConfig> symbols;

    /** 获取行情 tick 间隔（毫秒） */
    public int getIntervalMs() {
        return intervalMs;
    }

    /** 获取合约清单配置 */
    public List<SymbolConfig> getSymbols() {
        return symbols;
    }

    /** 设置行情 tick 间隔（毫秒），由 Spring 配置绑定调用 */
    public void setIntervalMs(int intervalMs) {
        this.intervalMs = intervalMs;
    }

    /** 设置合约清单配置，由 Spring 配置绑定调用 */
    public void setSymbols(List<SymbolConfig> symbols) {
        this.symbols = symbols;
    }

    /**
     * 相等性判断，用于配置比对与测试断言。
     * <p>
     * 样板方法，逐字段比较 intervalMs 与 symbols。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimExchangeProperties that = (SimExchangeProperties) o;
        if (intervalMs != that.intervalMs) return false;
        if (symbols != null ? !symbols.equals(that.symbols) : that.symbols != null) return false;
        return true;
    }

    /**
     * 哈希码计算，与 equals 保持一致。
     * <p>
     * 采用 31 作为乘子的经典写法，分布性较好。
     */
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (int) intervalMs;
        result = 31 * result + (symbols != null ? symbols.hashCode() : 0);
        return result;
    }

    /** 字符串表示，便于日志输出与调试 */
    @Override
    public String toString() {
        return "SimExchangeProperties{intervalMs=" + intervalMs + ", symbols=" + symbols + "}";
    }

}
