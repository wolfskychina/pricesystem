package com.bank.trading.common.core.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public class OrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private String orderId;
    private String clientOrderId;
    private String customerId;
    private String symbol;
    private String side;
    private String type;
    private BigDecimal qty;
    private BigDecimal filledQty;
    private BigDecimal price;
    private BigDecimal avgPrice;
    private String status;
    private String rejectReason;
    private Long createdAt;
    private Long updatedAt;

    public String getOrderId() {
        return orderId;
    }

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

    public BigDecimal getFilledQty() {
        return filledQty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public String getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public void setFilledQty(BigDecimal filledQty) {
        this.filledQty = filledQty;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderDTO that = (OrderDTO) o;
        if (orderId != null ? !orderId.equals(that.orderId) : that.orderId != null) return false;
        if (clientOrderId != null ? !clientOrderId.equals(that.clientOrderId) : that.clientOrderId != null) return false;
        if (customerId != null ? !customerId.equals(that.customerId) : that.customerId != null) return false;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (side != null ? !side.equals(that.side) : that.side != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (qty != null ? !qty.equals(that.qty) : that.qty != null) return false;
        if (filledQty != null ? !filledQty.equals(that.filledQty) : that.filledQty != null) return false;
        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        if (avgPrice != null ? !avgPrice.equals(that.avgPrice) : that.avgPrice != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (rejectReason != null ? !rejectReason.equals(that.rejectReason) : that.rejectReason != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        if (updatedAt != null ? !updatedAt.equals(that.updatedAt) : that.updatedAt != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (orderId != null ? orderId.hashCode() : 0);
        result = 31 * result + (clientOrderId != null ? clientOrderId.hashCode() : 0);
        result = 31 * result + (customerId != null ? customerId.hashCode() : 0);
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (side != null ? side.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (qty != null ? qty.hashCode() : 0);
        result = 31 * result + (filledQty != null ? filledQty.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (avgPrice != null ? avgPrice.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (rejectReason != null ? rejectReason.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "OrderDTO{orderId='" + orderId + "', clientOrderId='" + clientOrderId + "', customerId='" + customerId + "', symbol='" + symbol + "', side='" + side + "', type='" + type + "', qty=" + qty + ", filledQty=" + filledQty + ", price=" + price + ", avgPrice=" + avgPrice + ", status='" + status + "', rejectReason='" + rejectReason + "', createdAt=" + createdAt + ", updatedAt=" + updatedAt + "}";
    }

}
