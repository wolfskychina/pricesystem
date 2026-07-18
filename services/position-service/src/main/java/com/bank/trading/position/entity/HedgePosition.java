package com.bank.trading.position.entity;

import java.math.BigDecimal;

/**
 * 对冲持仓实体，记录做市商在单个合约上的对冲头寸汇总。
 * <p>
 * 对冲头寸按合约维度汇总（不区分客户），由 {@code hedge-fill-event} 累加更新。
 * 做市商的对冲目标是让净敞口趋近于 0：客户买入时做市商建立空头，需对冲买入；
 * 客户卖出时做市商建立多头，需对冲卖出。
 * <p>
 * <b>方向语义</b>：
 * <ul>
 *   <li>{@code qty > 0}：对冲多头（净对冲买入）</li>
 *   <li>{@code qty < 0}：对冲空头（净对冲卖出）</li>
 * </ul>
 * <p>
 * <b>净敞口计算</b>：{@code netExposure = sum(customerPosition.qty) - hedgePosition.qty}
 * （客户总头寸 − 对冲头寸）。完全对冲时净敞口为 0。
 * <p>
 * 注意：客户头寸的"多空方向"是客户视角（BUY 增加多头），对冲头寸的方向是做市商
 * 对冲单的方向。由于对冲方向 = 客户方向（客户 BUY → 对冲 BUY 平掉做市商空头），
 * 两者同向累加，相减即为净敞口。
 */
public class HedgePosition {

    /** 内部主键 ID */
    private Long id;
    /** 合约代码（唯一） */
    private String symbol;
    /** 对冲净头寸（正=多头对冲，负=空头对冲） */
    private BigDecimal qty;
    /** 加权平均对冲成本 */
    private BigDecimal avgCost;
    /** 乐观锁版本号 */
    private Integer version;
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
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
