package com.bank.trading.position.entity;

import java.math.BigDecimal;

/**
 * 净敞口读模型，用于敞口监控接口的返回值。
 * <p>
 * 净敞口 = 客户总头寸 − 对冲头寸。理论上完全对冲时为 0，非零值表示存在未对冲的敞口，
 * 需关注（可能是对冲延迟、对冲比例不足或对冲失败导致）。
 * <p>
 * 本类是只读的聚合视图，不持久化，由 PositionService 实时计算得出。
 */
public class NetExposure {

    /** 合约代码 */
    private String symbol;
    /** 客户总头寸（所有客户在该合约上的持仓之和；正=多头，负=空头） */
    private BigDecimal customerPosition;
    /** 对冲头寸（做市商对冲头寸；正=多头对冲，负=空头对冲） */
    private BigDecimal hedgePosition;
    /** 净敞口 = customerPosition - hedgePosition */
    private BigDecimal netExposure;

    public NetExposure() {
    }

    public NetExposure(String symbol, BigDecimal customerPosition,
                       BigDecimal hedgePosition, BigDecimal netExposure) {
        this.symbol = symbol;
        this.customerPosition = customerPosition;
        this.hedgePosition = hedgePosition;
        this.netExposure = netExposure;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getCustomerPosition() { return customerPosition; }
    public void setCustomerPosition(BigDecimal customerPosition) { this.customerPosition = customerPosition; }
    public BigDecimal getHedgePosition() { return hedgePosition; }
    public void setHedgePosition(BigDecimal hedgePosition) { this.hedgePosition = hedgePosition; }
    public BigDecimal getNetExposure() { return netExposure; }
    public void setNetExposure(BigDecimal netExposure) { this.netExposure = netExposure; }
}
