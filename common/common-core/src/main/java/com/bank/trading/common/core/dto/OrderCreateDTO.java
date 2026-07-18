package com.bank.trading.common.core.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private String clientOrderId;
    private String customerId;
    private String symbol;
    private String side;
    private String type;
    private BigDecimal qty;
    private BigDecimal price;
    private String traceId;

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderCreateDTO that = (OrderCreateDTO) o;
        if (clientOrderId != null ? !clientOrderId.equals(that.clientOrderId) : that.clientOrderId != null) return false;
        if (customerId != null ? !customerId.equals(that.customerId) : that.customerId != null) return false;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (side != null ? !side.equals(that.side) : that.side != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (qty != null ? !qty.equals(that.qty) : that.qty != null) return false;
        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        if (traceId != null ? !traceId.equals(that.traceId) : that.traceId != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (clientOrderId != null ? clientOrderId.hashCode() : 0);
        result = 31 * result + (customerId != null ? customerId.hashCode() : 0);
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (side != null ? side.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (qty != null ? qty.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (traceId != null ? traceId.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "OrderCreateDTO{clientOrderId='" + clientOrderId + "', customerId='" + customerId + "', symbol='" + symbol + "', side='" + side + "', type='" + type + "', qty=" + qty + ", price=" + price + ", traceId='" + traceId + "'}";
    }

}
