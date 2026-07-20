package com.bank.trading.execution.entity;

import java.math.BigDecimal;

/**
 * 对冲聚合子项实体，记录聚合对冲订单与每笔原始客户成交的对应关系。
 * <p>
 * 当开启对冲聚合（batching）时，同一合约、同一方向的多笔客户成交会被
 * 合并为一笔对冲单提交到交易所。本实体记录每笔原始成交在聚合桶中的
 * 份额，用于成交后按比例分摊成交结果，并按原始成交笔数发出
 * {@code hedge-fill-event}。
 * <p>
 * 状态流转：
 * <pre>
 *   PENDING（入桶，等待聚合出桶下单）
 *     → SUBMITTED（聚合对冲单已提交交易所）
 *     → FILLED（聚合对冲单已成交，本项已分摊）
 * </pre>
 */
public class HedgeBatchItem {

    /** 内部主键 ID */
    private Long id;
    /** 聚合对冲订单 ID（关联 hedge_orders.hedge_order_id） */
    private String hedgeOrderId;
    /** 原始客户成交 ID（关联 TradeEvent.tradeId） */
    private String originalTradeId;
    /** 客户 ID */
    private String customerId;
    /** 合约代码 */
    private String symbol;
    /** 对冲方向（BUY/SELL） */
    private String side;
    /** 该子项对应的对冲数量（= 原始成交数量 × hedge-ratio） */
    private BigDecimal qty;
    /** 子项状态（PENDING/SUBMITTED/FILLED） */
    private String status;
    /** 分摊后的成交数量 */
    private BigDecimal filledQty;
    /** 分摊后的成交均价 */
    private BigDecimal avgPrice;
    /** 重试次数 */
    private Integer retryCount;
    /** 失败原因 */
    private String failureReason;
    /** 创建时间（毫秒时间戳） */
    private Long createdAt;
    /** 更新时间（毫秒时间戳） */
    private Long updatedAt;

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getFilledQty() { return filledQty; }
    public void setFilledQty(BigDecimal filledQty) { this.filledQty = filledQty; }
    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
