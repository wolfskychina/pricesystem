package com.bank.trading.account.entity;

import java.math.BigDecimal;

/**
 * 客户账户实体，记录客户主数据与信用额度状态。
 * <p>
 * 一个客户对应一条记录，由 customer_id 唯一标识。信用额度模型：
 * <ul>
 *   <li>{@code creditLimit}：客户总信用额度（由运营设置）</li>
 *   <li>{@code usedCredit}：已用额度（持仓占用 + 挂单占用）</li>
 *   <li>{@code availableCredit} = creditLimit − usedCredit（计算字段，不持久化）</li>
 * </ul>
 * <p>
 * 当客户成交（trade-event）时：
 * <ul>
 *   <li>BUY → usedCredit += amount（占用信用）</li>
 *   <li>SELL → usedCredit −= amount（释放信用）</li>
 * </ul>
 * <p>
 * <b>乐观锁</b>：version 字段用于并发更新冲突检测（同一客户的事件由 Kafka
 * 分区保证顺序，version 主要作为防御性措施）。
 */
public class Customer {

    /** 内部主键 ID */
    private Long id;
    /** 客户唯一标识（业务主键） */
    private String customerId;
    /** 客户名称 */
    private String name;
    /** 客户等级（VIP/NORMAL） */
    private String level;
    /** 客户状态（ACTIVE/FROZEN/CLOSED） */
    private String status;
    /** 信用额度（总授信） */
    private BigDecimal creditLimit;
    /** 已用额度 */
    private BigDecimal usedCredit;
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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public BigDecimal getUsedCredit() { return usedCredit; }
    public void setUsedCredit(BigDecimal usedCredit) { this.usedCredit = usedCredit; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
