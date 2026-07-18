package com.bank.trading.execution.entity;

import java.math.BigDecimal;

/**
 * 对冲成交流水实体，记录对冲单在交易所的成交结果。
 * <p>
 * 当 execution-service 收到 sim-exchange 的 Webhook 成交通知后，将通知内容
 * 持久化为本实体，作为对冲成交的审计凭证与下游事件发布的源头。
 * <p>
 * 一笔对冲订单可能产生多条成交流水（部分成交场景，本期模拟暂不涉及，
 * 但数据模型已预留支持）。
 */
public class HedgeTrade {

    /** 内部主键 ID */
    private Long id;
    /** 关联的对冲订单内部 ID（HedgeOrder.hedgeOrderId） */
    private String hedgeOrderId;
    /** 关联的交易所订单 ID（HedgeOrder.exchangeOrderId） */
    private String exchangeOrderId;
    /** 交易所成交 ID（来自 Webhook 通知） */
    private String exchangeTradeId;
    /** 原始客户成交 ID，用于对冲盈亏归因 */
    private String originalTradeId;
    /** 合约代码 */
    private String symbol;
    /** 成交方向 */
    private String side;
    /** 成交数量 */
    private BigDecimal qty;
    /** 成交价格 */
    private BigDecimal price;
    /** 成交金额 = qty × price */
    private BigDecimal amount;
    /** 成交时间（毫秒时间戳） */
    private Long tradeTime;
    /** 记录创建时间（毫秒时间戳） */
    private Long createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getHedgeOrderId() { return hedgeOrderId; }
    public void setHedgeOrderId(String hedgeOrderId) { this.hedgeOrderId = hedgeOrderId; }
    public String getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }
    public String getExchangeTradeId() { return exchangeTradeId; }
    public void setExchangeTradeId(String exchangeTradeId) { this.exchangeTradeId = exchangeTradeId; }
    public String getOriginalTradeId() { return originalTradeId; }
    public void setOriginalTradeId(String originalTradeId) { this.originalTradeId = originalTradeId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getTradeTime() { return tradeTime; }
    public void setTradeTime(Long tradeTime) { this.tradeTime = tradeTime; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
