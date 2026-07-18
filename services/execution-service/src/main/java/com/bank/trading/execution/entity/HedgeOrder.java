package com.bank.trading.execution.entity;

import java.math.BigDecimal;

/**
 * 对冲订单实体，记录做市商向交易所提交的对冲单。
 * <p>
 * 当客户与做市商成交后，做市商产生库存敞口。为平掉敞口，做市商向交易所发送
 * 反向对冲单。本实体记录对冲单从提交到成交的全生命周期。
 * <p>
 * 状态流转：
 * <pre>
 *   NEW（已提交交易所，等待受理）
 *     → ACCEPTED（交易所已受理）
 *     → FILLED（交易所撮合成交）
 *     → REJECTED（交易所拒绝，如限价未触及盘口）
 * </pre>
 * <p>
 * 与 sim-exchange 的订单通过 {@link #exchangeOrderId} 关联；
 * 与原始客户成交通过 {@link #originalTradeId} 关联，用于对冲盈亏归因。
 */
public class HedgeOrder {

    /** 内部主键 ID */
    private Long id;
    /** 对冲单内部唯一 ID（UUID），同时作为提交交易所时的 clientOrderId */
    private String hedgeOrderId;
    /** 关联的交易所订单 ID（sim-exchange 返回的 orderId） */
    private String exchangeOrderId;
    /** 原始客户成交 ID，用于对冲盈亏归因（关联 TradeEvent.tradeId） */
    private String originalTradeId;
    /** 客户 ID（来自原始成交，用于审计追溯） */
    private String customerId;
    /** 合约代码 */
    private String symbol;
    /** 对冲方向（与客户成交相反：客户 BUY → 对冲 SELL，客户 SELL → 对冲 BUY） */
    private String side;
    /** 订单类型（MARKET/LIMIT） */
    private String type;
    /** 委托数量 */
    private BigDecimal qty;
    /** 委托价格；市价单为 null */
    private BigDecimal price;
    /** 已成交数量 */
    private BigDecimal filledQty;
    /** 平均成交价 */
    private BigDecimal avgPrice;
    /** 订单状态（NEW/ACCEPTED/FILLED/REJECTED） */
    private String status;
    /** 创建时间（毫秒时间戳） */
    private Long createdAt;
    /** 更新时间（毫秒时间戳） */
    private Long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHedgeOrderId() { return hedgeOrderId; }
    public void setHedgeOrderId(String hedgeOrderId) { this.hedgeOrderId = hedgeOrderId; }
    public String getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }
    public String getOriginalTradeId() { return originalTradeId; }
    public void setOriginalTradeId(String originalTradeId) { this.originalTradeId = originalTradeId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getFilledQty() { return filledQty; }
    public void setFilledQty(BigDecimal filledQty) { this.filledQty = filledQty; }
    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
