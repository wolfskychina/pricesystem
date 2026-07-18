package com.bank.trading.account.dto;

import java.math.BigDecimal;

/**
 * 客户信用额度信息视图，作为 {@code GET /accounts/{customerId}/credit} 接口的返回值。
 * <p>
 * 包含总授信、已用额度、可用额度三项，便于风控服务（risk-service）事前风控判断。
 * <p>
 * 本类是只读视图，不持久化，由 AccountService 实时计算 availableCredit 得出。
 */
public class CreditInfo {

    /** 客户 ID */
    private String customerId;
    /** 总信用额度 */
    private BigDecimal creditLimit;
    /** 已用额度 */
    private BigDecimal usedCredit;
    /** 可用额度 = creditLimit - usedCredit */
    private BigDecimal availableCredit;

    public CreditInfo() {
    }

    public CreditInfo(String customerId, BigDecimal creditLimit,
                      BigDecimal usedCredit, BigDecimal availableCredit) {
        this.customerId = customerId;
        this.creditLimit = creditLimit;
        this.usedCredit = usedCredit;
        this.availableCredit = availableCredit;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public BigDecimal getUsedCredit() { return usedCredit; }
    public void setUsedCredit(BigDecimal usedCredit) { this.usedCredit = usedCredit; }
    public BigDecimal getAvailableCredit() { return availableCredit; }
    public void setAvailableCredit(BigDecimal availableCredit) { this.availableCredit = availableCredit; }
}
