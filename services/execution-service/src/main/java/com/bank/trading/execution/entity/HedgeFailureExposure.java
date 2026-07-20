package com.bank.trading.execution.entity;

import java.math.BigDecimal;

public class HedgeFailureExposure {

    private Long id;
    private String customerId;
    private String symbol;
    private String side;
    private BigDecimal pendingQty;
    private BigDecimal exposureAmount;
    private String status;
    private Integer retryCount;
    private Long lastRetryAt;
    private String originalTradeId;
    private String hedgeOrderId;
    private Long createdAt;
    private Long resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public BigDecimal getPendingQty() { return pendingQty; }
    public void setPendingQty(BigDecimal pendingQty) { this.pendingQty = pendingQty; }
    public BigDecimal getExposureAmount() { return exposureAmount; }
    public void setExposureAmount(BigDecimal exposureAmount) { this.exposureAmount = exposureAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Long getLastRetryAt() { return lastRetryAt; }
    public void setLastRetryAt(Long lastRetryAt) { this.lastRetryAt = lastRetryAt; }
    public String getOriginalTradeId() { return originalTradeId; }
    public void setOriginalTradeId(String originalTradeId) { this.originalTradeId = originalTradeId; }
    public String getHedgeOrderId() { return hedgeOrderId; }
    public void setHedgeOrderId(String hedgeOrderId) { this.hedgeOrderId = hedgeOrderId; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Long resolvedAt) { this.resolvedAt = resolvedAt; }
}