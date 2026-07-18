package com.bank.trading.simexchange.model;

import java.math.BigDecimal;

public class MarketData {

    private String symbol;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private BigDecimal lastPrice;
    private BigDecimal bidQty;
    private BigDecimal askQty;
    private BigDecimal lastQty;
    private Long volume;
    private Long timestamp;

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    public BigDecimal getAskPrice() {
        return askPrice;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public BigDecimal getBidQty() {
        return bidQty;
    }

    public BigDecimal getAskQty() {
        return askQty;
    }

    public BigDecimal getLastQty() {
        return lastQty;
    }

    public Long getVolume() {
        return volume;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setBidPrice(BigDecimal bidPrice) {
        this.bidPrice = bidPrice;
    }

    public void setAskPrice(BigDecimal askPrice) {
        this.askPrice = askPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public void setBidQty(BigDecimal bidQty) {
        this.bidQty = bidQty;
    }

    public void setAskQty(BigDecimal askQty) {
        this.askQty = askQty;
    }

    public void setLastQty(BigDecimal lastQty) {
        this.lastQty = lastQty;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketData that = (MarketData) o;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (bidPrice != null ? !bidPrice.equals(that.bidPrice) : that.bidPrice != null) return false;
        if (askPrice != null ? !askPrice.equals(that.askPrice) : that.askPrice != null) return false;
        if (lastPrice != null ? !lastPrice.equals(that.lastPrice) : that.lastPrice != null) return false;
        if (bidQty != null ? !bidQty.equals(that.bidQty) : that.bidQty != null) return false;
        if (askQty != null ? !askQty.equals(that.askQty) : that.askQty != null) return false;
        if (lastQty != null ? !lastQty.equals(that.lastQty) : that.lastQty != null) return false;
        if (volume != null ? !volume.equals(that.volume) : that.volume != null) return false;
        if (timestamp != null ? !timestamp.equals(that.timestamp) : that.timestamp != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (bidPrice != null ? bidPrice.hashCode() : 0);
        result = 31 * result + (askPrice != null ? askPrice.hashCode() : 0);
        result = 31 * result + (lastPrice != null ? lastPrice.hashCode() : 0);
        result = 31 * result + (bidQty != null ? bidQty.hashCode() : 0);
        result = 31 * result + (askQty != null ? askQty.hashCode() : 0);
        result = 31 * result + (lastQty != null ? lastQty.hashCode() : 0);
        result = 31 * result + (volume != null ? volume.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "MarketData{symbol='" + symbol + "', bidPrice=" + bidPrice + ", askPrice=" + askPrice + ", lastPrice=" + lastPrice + ", bidQty=" + bidQty + ", askQty=" + askQty + ", lastQty=" + lastQty + ", volume=" + volume + ", timestamp=" + timestamp + "}";
    }

}
