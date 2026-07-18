package com.bank.trading.position.entity;

import java.math.BigDecimal;

/**
 * 客户持仓实体，记录单个客户在单个合约上的净头寸与成本。
 * <p>
 * 持仓以 (customerId, symbol) 唯一标识，由 {@code trade-event} 累加更新。
 * <p>
 * <b>方向语义</b>：
 * <ul>
 *   <li>{@code qty > 0}：多头（客户净买入，持有正头寸）</li>
 *   <li>{@code qty < 0}：空头（客户净卖出，持有负头寸）</li>
 *   <li>{@code qty = 0}：已平仓，仅保留 realizedPnl</li>
 * </ul>
 * <p>
 * <b>成本计算</b>：avg_cost 是加权平均成本。买入时按数量加权更新；
 * 卖出时按已有 avg_cost 结算 realized_pnl，avg_cost 不变（同方向加仓才更新成本）。
 * 反向开仓（如多头反手为空头）时，先平掉旧头寸再以新价格开仓。
 * <p>
 * <b>乐观锁</b>：version 字段用于并发更新冲突检测（同一客户同一合约的事件
 * 由 Kafka 分区保证顺序，version 主要作为防御性措施）。
 */
public class Position {

    /** 内部主键 ID */
    private Long id;
    /** 客户 ID */
    private String customerId;
    /** 合约代码 */
    private String symbol;
    /** 净持仓数量（正=多头，负=空头） */
    private BigDecimal qty;
    /** 加权平均成本 */
    private BigDecimal avgCost;
    /** 已实现盈亏（累计） */
    private BigDecimal realizedPnl;
    /** 乐观锁版本号 */
    private Integer version;
    /** 创建时间（毫秒时间戳） */
    private Long createdAt;
    /** 更新时间（毫秒时间戳） */
    private Long updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
