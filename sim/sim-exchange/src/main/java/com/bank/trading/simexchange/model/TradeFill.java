package com.bank.trading.simexchange.model;

import java.math.BigDecimal;

public class TradeFill {

    private String tradeId;
    private String orderId;
    private String symbol;
    private String side;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    private Long tradeTime;

    public String getTradeId() {
        return tradeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getTradeTime() {
        return tradeTime;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setTradeTime(Long tradeTime) {
        this.tradeTime = tradeTime;
    }

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
    @Override
    public String toString() {
        return "TradeFill{tradeId='" + tradeId + "', orderId='" + orderId + "', symbol='" + symbol + "', side='" + side + "', qty=" + qty + ", price=" + price + ", amount=" + amount + ", tradeTime=" + tradeTime + "}";
    }

}
