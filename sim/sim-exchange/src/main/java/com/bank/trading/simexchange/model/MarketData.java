package com.bank.trading.simexchange.model;

import java.math.BigDecimal;

/**
 * 行情快照模型。
 * <p>
 * 表示某合约在某时刻的盘口行情快照，包含买价、卖价、最新成交价、对应数量、
 * 累计成交量与时间戳等字段。该模型由行情引擎（{@link com.bank.trading.simexchange.engine.MarketDataEngine}）
 * 在每个 tick 中生成，并通过 REST 接口返回或通过 WebSocket 广播给客户端。
 * <p>
 * 因系统移除 Lombok，本类手写了全部 getter/setter/equals/hashCode/toString。
 */
public class MarketData {

    /** 合约代码，例如 EURUSD */
    private String symbol;
    /** 买一价（bidPrice），即买方愿意支付的最高价 */
    private BigDecimal bidPrice;
    /** 卖一价（askPrice），即卖方愿意接受的最低价；askPrice > bidPrice */
    private BigDecimal askPrice;
    /** 最新成交价，按 tickSize 取整后的合规报价 */
    private BigDecimal lastPrice;
    /** 买一挂单量，反映买方挂单深度 */
    private BigDecimal bidQty;
    /** 卖一挂单量，反映卖方挂单深度 */
    private BigDecimal askQty;
    /** 最新成交量，即本 tick 模拟产生的一笔成交的数量 */
    private BigDecimal lastQty;
    /** 累计成交量，自启动以来所有 lastQty 的累加值 */
    private Long volume;
    /** 行情生成时间（毫秒时间戳） */
    private Long timestamp;

    /** 获取合约代码 */
    public String getSymbol() {
        return symbol;
    }

    /** 获取买一价 */
    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    /** 获取卖一价 */
    public BigDecimal getAskPrice() {
        return askPrice;
    }

    /** 获取最新成交价 */
    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    /** 获取买一挂单量 */
    public BigDecimal getBidQty() {
        return bidQty;
    }

    /** 获取卖一挂单量 */
    public BigDecimal getAskQty() {
        return askQty;
    }

    /** 获取最新成交量 */
    public BigDecimal getLastQty() {
        return lastQty;
    }

    /** 获取累计成交量 */
    public Long getVolume() {
        return volume;
    }

    /** 获取行情生成时间 */
    public Long getTimestamp() {
        return timestamp;
    }

    /** 设置合约代码 */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /** 设置买一价 */
    public void setBidPrice(BigDecimal bidPrice) {
        this.bidPrice = bidPrice;
    }

    /** 设置卖一价 */
    public void setAskPrice(BigDecimal askPrice) {
        this.askPrice = askPrice;
    }

    /** 设置最新成交价 */
    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    /** 设置买一挂单量 */
    public void setBidQty(BigDecimal bidQty) {
        this.bidQty = bidQty;
    }

    /** 设置卖一挂单量 */
    public void setAskQty(BigDecimal askQty) {
        this.askQty = askQty;
    }

    /** 设置最新成交量 */
    public void setLastQty(BigDecimal lastQty) {
        this.lastQty = lastQty;
    }

    /** 设置累计成交量 */
    public void setVolume(Long volume) {
        this.volume = volume;
    }

    /** 设置行情生成时间 */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 相等性判断，逐字段比较；样板方法。
     */
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

    /**
     * 哈希码计算，与 equals 保持一致；样板方法。
     */
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

    /** 字符串表示，便于日志输出与调试 */
    @Override
    public String toString() {
        return "MarketData{symbol='" + symbol + "', bidPrice=" + bidPrice + ", askPrice=" + askPrice + ", lastPrice=" + lastPrice + ", bidQty=" + bidQty + ", askQty=" + askQty + ", lastQty=" + lastQty + ", volume=" + volume + ", timestamp=" + timestamp + "}";
    }

}
