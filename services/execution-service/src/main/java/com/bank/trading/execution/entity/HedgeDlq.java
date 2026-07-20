package com.bank.trading.execution.entity;

import java.math.BigDecimal;

public class HedgeDlq {

    private Long id;
    private String hedgeOrderId;
    private String originalTradeId;
    private String customerId;
    private String symbol;
    private String side;
    private BigDecimal qty;
    private String reason;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String status;
    private Long createdAt;
    private Long recoveredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHedgeOrderId() { return hedgeOrderId; }
    public void setHedgeOrderId(String hedgeOrderId) { this.hedgeOrderId = hedgeOrderId; }
    public String getOriginalTradeId() { return originalTradeId; }
    public void setOriginalTradeId(String originalTradeId) { this.originalTradeId = originalTradeId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(Integer maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getRecoveredAt() { return recoveredAt; }
    public void setRecoveredAt(Long recoveredAt) { this.recoveredAt = recoveredAt; }
}