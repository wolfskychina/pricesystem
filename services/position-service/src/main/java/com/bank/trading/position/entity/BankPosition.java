package com.bank.trading.position.entity;

import java.math.BigDecimal;

/**
 * 银行自身持仓实体，记录银行作为做市商在单个合约上的实时头寸汇总。
 * <p>
 * 银行自身头寸与客户成交同步产生——银行是每一笔客户交易的对手方，
 * 因此银行头寸 = −Σ(客户头寸)。与 {@link HedgePosition}（对冲成交回报驱动）
 * 不同，本实体由 {@code trade-event} 驱动更新，反映银行真实的瞬时头寸。
 * <p>
 * <b>方向语义</b>：
 * <ul>
 *   <li>{@code qty > 0}：银行多头（客户净卖出，银行净买入）</li>
 *   <li>{@code qty < 0}：银行空头（客户净买入，银行净卖出）</li>
 * </ul>
 * <p>
 * <b>与对冲持仓的关系</b>：{@code BankPosition} 是银行在客户侧的持仓镜像，
 * 实时、无延迟；{@code HedgePosition} 是银行在交易所的对冲持仓，有成交延迟。
 * 两者之差 = 未对冲敞口（已产生的银行头寸中尚未被对冲单覆盖的部分）。
 * <p>
 * <b>成本计算</b>：与客户持仓逻辑一致，加权平均成本，反向变化时计算已实现盈亏。
 */
public class BankPosition {

    /** 内部主键 ID */
    private Long id;
    /** 合约代码（唯一） */
    private String symbol;
    /** 银行净持仓（正=多头，负=空头） */
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