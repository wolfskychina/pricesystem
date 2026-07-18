package com.bank.trading.simexchange.model;

import java.math.BigDecimal;

/**
 * 合约配置模型。
 * <p>
 * 描述一个可交易合约的全部参数，包括身份信息（代码、名称）、价格模型参数（初始价、
 * 漂移率、波动率）、盘口参数（tickSize、minQty）等。该模型从配置文件
 * （{@code application.yml} 中 {@code sim-exchange.symbols} 节点）加载，由行情引擎
 * 用于初始化每个合约的 GBM 价格生成器与盘口合成规则。
 * <p>
 * 因系统移除 Lombok，本类手写了全部 getter/setter/equals/hashCode/toString。
 */
public class SymbolConfig {

    /** 合约代码，全局唯一标识，例如 EURUSD */
    private String code;
    /** 合约名称（人类可读），例如"欧元兑美元" */
    private String name;
    /** 初始价格，作为 GBM 模型的起点价 */
    private BigDecimal initialPrice;
    /** 年化波动率 sigma，控制价格波动的剧烈程度 */
    private double volatility;
    /** 年化漂移率 mu，控制价格的平均变化趋势；0 表示无趋势 */
    private double drift;
    /** 最小变动价位（tick size），所有报价必须是其整数倍 */
    private BigDecimal tickSize;
    /** 合约乘数，用于将价格换算为合约面值；当前行情引擎未直接使用，预留字段 */
    private int multiplier;
    /** 最小下单量，行情合成时用于派生成交量与挂单量 */
    private BigDecimal minQty;

    /** 获取合约代码 */
    public String getCode() {
        return code;
    }

    /** 获取合约名称 */
    public String getName() {
        return name;
    }

    /** 获取初始价格 */
    public BigDecimal getInitialPrice() {
        return initialPrice;
    }

    /** 获取年化波动率 */
    public double getVolatility() {
        return volatility;
    }

    /** 获取年化漂移率 */
    public double getDrift() {
        return drift;
    }

    /** 获取最小变动价位 */
    public BigDecimal getTickSize() {
        return tickSize;
    }

    /** 获取合约乘数 */
    public int getMultiplier() {
        return multiplier;
    }

    /** 获取最小下单量 */
    public BigDecimal getMinQty() {
        return minQty;
    }

    /** 设置合约代码 */
    public void setCode(String code) {
        this.code = code;
    }

    /** 设置合约名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** 设置初始价格 */
    public void setInitialPrice(BigDecimal initialPrice) {
        this.initialPrice = initialPrice;
    }

    /** 设置年化波动率 */
    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    /** 设置年化漂移率 */
    public void setDrift(double drift) {
        this.drift = drift;
    }

    /** 设置最小变动价位 */
    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
    }

    /** 设置合约乘数 */
    public void setMultiplier(int multiplier) {
        this.multiplier = multiplier;
    }

    /** 设置最小下单量 */
    public void setMinQty(BigDecimal minQty) {
        this.minQty = minQty;
    }

    /**
     * 相等性判断，逐字段比较；double 字段使用 Double.compare 避免精度问题；样板方法。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SymbolConfig that = (SymbolConfig) o;
        if (code != null ? !code.equals(that.code) : that.code != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (initialPrice != null ? !initialPrice.equals(that.initialPrice) : that.initialPrice != null) return false;
        if (Double.compare(that.volatility, volatility) != 0) return false;
        if (Double.compare(that.drift, drift) != 0) return false;
        if (tickSize != null ? !tickSize.equals(that.tickSize) : that.tickSize != null) return false;
        if (multiplier != that.multiplier) return false;
        if (minQty != null ? !minQty.equals(that.minQty) : that.minQty != null) return false;
        return true;
    }

    /**
     * 哈希码计算，与 equals 保持一致；double 字段使用 Double.doubleToLongBits 转换；样板方法。
     */
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (initialPrice != null ? initialPrice.hashCode() : 0);
        long temp = Double.doubleToLongBits(volatility);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(drift);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (tickSize != null ? tickSize.hashCode() : 0);
        result = 31 * result + (int) multiplier;
        result = 31 * result + (minQty != null ? minQty.hashCode() : 0);
        return result;
    }

    /** 字符串表示，便于日志输出与调试 */
    @Override
    public String toString() {
        return "SymbolConfig{code='" + code + "', name='" + name + "', initialPrice=" + initialPrice + ", volatility=" + volatility + ", drift=" + drift + ", tickSize=" + tickSize + ", multiplier=" + multiplier + ", minQty=" + minQty + "}";
    }

}
