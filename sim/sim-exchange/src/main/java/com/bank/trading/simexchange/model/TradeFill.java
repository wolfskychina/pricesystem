package com.bank.trading.simexchange.model;

import java.math.BigDecimal;

/**
 * 成交流水模型。
 * <p>
 * 表示一笔订单成交产生的流水记录，记录成交 ID、关联订单、合约、方向、成交量、
 * 成交价、成交金额与成交时间。每次撮合成功后由撮合引擎（{@link com.bank.trading.simexchange.engine.MatchingEngine}）
 * 生成一条 {@code TradeFill}，追加到成交流水列表中，供查询接口与对账使用。
 * <p>
 * 因系统移除 Lombok，本类手写了全部 getter/setter/equals/hashCode/toString。
 */
public class TradeFill {

    /** 成交 ID，由撮合引擎生成（"T" + 自增序号），全局唯一 */
    private String tradeId;
    /** 关联的订单 ID，对应 {@link ExchangeOrder#getOrderId()} */
    private String orderId;
    /** 合约代码 */
    private String symbol;
    /** 成交方向，继承自关联订单（BUY/SELL） */
    private String side;
    /** 成交数量 */
    private BigDecimal qty;
    /** 成交价格 */
    private BigDecimal price;
    /** 成交金额 = 成交数量 × 成交价格 */
    private BigDecimal amount;
    /** 成交时间（毫秒时间戳） */
    private Long tradeTime;

    /** 获取成交 ID */
    public String getTradeId() {
        return tradeId;
    }

    /** 获取关联订单 ID */
    public String getOrderId() {
        return orderId;
    }

    /** 获取合约代码 */
    public String getSymbol() {
        return symbol;
    }

    /** 获取成交方向 */
    public String getSide() {
        return side;
    }

    /** 获取成交数量 */
    public BigDecimal getQty() {
        return qty;
    }

    /** 获取成交价格 */
    public BigDecimal getPrice() {
        return price;
    }

    /** 获取成交金额 */
    public BigDecimal getAmount() {
        return amount;
    }

    /** 获取成交时间 */
    public Long getTradeTime() {
        return tradeTime;
    }

    /** 设置成交 ID */
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    /** 设置关联订单 ID */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /** 设置合约代码 */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /** 设置成交方向 */
    public void setSide(String side) {
        this.side = side;
    }

    /** 设置成交数量 */
    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    /** 设置成交价格 */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /** 设置成交金额 */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /** 设置成交时间 */
    public void setTradeTime(Long tradeTime) {
        this.tradeTime = tradeTime;
    }

    /**
     * 相等性判断，逐字段比较；样板方法。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeFill that = (TradeFill) o;
        if (tradeId != null ? !tradeId.equals(that.tradeId) : that.tradeId != null) return false;
        if (orderId != null ? !orderId.equals(that.orderId) : that.orderId != null) return false;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (side != null ? !side.equals(that.side) : that.side != null) return false;
        if (qty != null ? !qty.equals(that.qty) : that.qty != null) return false;
        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (tradeTime != null ? !tradeTime.equals(that.tradeTime) : that.tradeTime != null) return false;
        return true;
    }

    /**
     * 哈希码计算，与 equals 保持一致；样板方法。
     */
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (tradeId != null ? tradeId.hashCode() : 0);
        result = 31 * result + (orderId != null ? orderId.hashCode() : 0);
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (side != null ? side.hashCode() : 0);
        result = 31 * result + (qty != null ? qty.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (tradeTime != null ? tradeTime.hashCode() : 0);
        return result;
    }

    /** 字符串表示，便于日志输出与调试 */
    @Override
    public String toString() {
        return "TradeFill{tradeId='" + tradeId + "', orderId='" + orderId + "', symbol='" + symbol + "', side='" + side + "', qty=" + qty + ", price=" + price + ", amount=" + amount + ", tradeTime=" + tradeTime + "}";
    }

}
