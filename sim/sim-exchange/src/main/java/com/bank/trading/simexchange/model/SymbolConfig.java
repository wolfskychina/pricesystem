package com.bank.trading.simexchange.model;

import java.math.BigDecimal;

public class SymbolConfig {

    private String code;
    private String name;
    private BigDecimal initialPrice;
    private double volatility;
    private double drift;
    private BigDecimal tickSize;
    private int multiplier;
    private BigDecimal minQty;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getInitialPrice() {
        return initialPrice;
    }

    public double getVolatility() {
        return volatility;
    }

    public double getDrift() {
        return drift;
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public BigDecimal getMinQty() {
        return minQty;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInitialPrice(BigDecimal initialPrice) {
        this.initialPrice = initialPrice;
    }

    public void setVolatility(double volatility) {
        this.volatility = volatility;
    }

    public void setDrift(double drift) {
        this.drift = drift;
    }

    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
    }

    public void setMultiplier(int multiplier) {
        this.multiplier = multiplier;
    }

    public void setMinQty(BigDecimal minQty) {
        this.minQty = minQty;
    }

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

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (initialPrice != null ? initialPrice.hashCode() : 0);
        long temp = Double.doubleToLongBits(volatility);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        long temp = Double.doubleToLongBits(drift);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (tickSize != null ? tickSize.hashCode() : 0);
        result = 31 * result + (int) multiplier;
        result = 31 * result + (minQty != null ? minQty.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "SymbolConfig{code='" + code + "', name='" + name + "', initialPrice=" + initialPrice + ", volatility=" + volatility + ", drift=" + drift + ", tickSize=" + tickSize + ", multiplier=" + multiplier + ", minQty=" + minQty + "}";
    }

}
