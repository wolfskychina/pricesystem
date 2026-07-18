package com.bank.trading.refdata.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合约主数据实体。
 * <p>
 * 该实体映射参考数据服务（Reference Data Service）中的 {@code contract} 表，是银行做市商
 * 交易系统中"合约"主数据的持久化模型。合约是交易的基础标的，订单、行情、报价、风控、清算
 * 等模块都基于合约主数据进行标的识别与参数匹配。
 * </p>
 * <p>
 * 设计说明：
 * <ul>
 *   <li>因系统统一移除了 Lombok 依赖，故手写 getter/setter/equals/hashCode/toString</li>
 *   <li>金额、乘数、价位等使用 {@link BigDecimal} 保证精度，避免浮点误差</li>
 *   <li>日期与时间使用 JSR-310 日期时间 API（LocalDate/LocalDateTime）</li>
 * </ul>
 * </p>
 * <p>
 * 表与字段映射（数据库列 -> 实体字段，遵循下划线转驼峰命名规则）：
 * <pre>
 * id            -> id
 * code          -> code
 * name          -> name
 * exchange      -> exchange
 * product       -> product
 * multiplier    -> multiplier
 * tick_size     -> tickSize
 * min_qty       -> minQty
 * listed_date   -> listedDate
 * expiry_date   -> expiryDate
 * status        -> status
 * created_at    -> createdAt
 * updated_at    -> updatedAt
 * </pre>
 * </p>
 */
public class Contract {

    /** 主键 ID，数据库自增；插入后由 MyBatis useGeneratedKeys 回填 */
    private Long id;

    /** 合约代码，业务唯一标识，例如 IF2406（沪深 300 股指期货 2024 年 6 月合约）、CU2406 等 */
    private String code;

    /** 合约名称，便于人工识别，例如"沪深 300 股指期货 2406 合约" */
    private String name;

    /** 交易所代码，例如 SHFE（上期所）、CFFEX（中金所）、INE（能源中心）等 */
    private String exchange;

    /** 标的产品代码/类别，例如 IF、CU、SC 等，便于按品种聚合统计 */
    private String product;

    /** 合约乘数，用于手数与金额的换算，例如股指期货通常为 200 元/点 */
    private BigDecimal multiplier;

    /** 最小变动价位，报价与撮合的最小价格步长，例如 0.2 元 */
    private BigDecimal tickSize;

    /** 最小下单数量（手数），低于该值的订单将被拒单 */
    private BigDecimal minQty;

    /** 上市日期，合约开始可交易的日期 */
    private LocalDate listedDate;

    /** 到期日，合约最后交易日及交割日；到期后状态通常被置为 EXPIRED */
    private LocalDate expiryDate;

    /** 合约状态：ACTIVE（活跃可交易）、EXPIRED（已到期）、DELISTED（已下市）等 */
    private String status;

    /** 创建时间，由 Service 层在新建时写入，后续不可变 */
    private LocalDateTime createdAt;

    /** 最后更新时间，由 Service 层在每次更新时刷新 */
    private LocalDateTime updatedAt;

    /** 获取主键 ID */
    public Long getId() {
        return id;
    }

    /** 获取合约代码 */
    public String getCode() {
        return code;
    }

    /** 获取合约名称 */
    public String getName() {
        return name;
    }

    /** 获取交易所代码 */
    public String getExchange() {
        return exchange;
    }

    /** 获取标的产品代码 */
    public String getProduct() {
        return product;
    }

    /** 获取合约乘数 */
    public BigDecimal getMultiplier() {
        return multiplier;
    }

    /** 获取最小变动价位 */
    public BigDecimal getTickSize() {
        return tickSize;
    }

    /** 获取最小下单数量 */
    public BigDecimal getMinQty() {
        return minQty;
    }

    /** 获取上市日期 */
    public LocalDate getListedDate() {
        return listedDate;
    }

    /** 获取到期日 */
    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    /** 获取合约状态 */
    public String getStatus() {
        return status;
    }

    /** 获取创建时间 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 获取最后更新时间 */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 设置主键 ID */
    public void setId(Long id) {
        this.id = id;
    }

    /** 设置合约代码 */
    public void setCode(String code) {
        this.code = code;
    }

    /** 设置合约名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** 设置交易所代码 */
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    /** 设置标的产品代码 */
    public void setProduct(String product) {
        this.product = product;
    }

    /** 设置合约乘数 */
    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    /** 设置最小变动价位 */
    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
    }

    /** 设置最小下单数量 */
    public void setMinQty(BigDecimal minQty) {
        this.minQty = minQty;
    }

    /** 设置上市日期 */
    public void setListedDate(LocalDate listedDate) {
        this.listedDate = listedDate;
    }

    /** 设置到期日 */
    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    /** 设置合约状态 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 设置创建时间 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 设置最后更新时间 */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 基于全部业务字段判定相等性。
     * <p>
     * 逐字段比较，null 与非 null 区分对待，确保两个实体所有字段一致才被视为相等。
     * </p>
     *
     * @param o 待比较对象
     * @return 若所有字段相等返回 true，否则返回 false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // 同一引用直接判定相等，提升性能
        if (o == null || getClass() != o.getClass()) return false; // 类型不符或为 null 直接判定不等
        Contract that = (Contract) o;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (code != null ? !code.equals(that.code) : that.code != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (exchange != null ? !exchange.equals(that.exchange) : that.exchange != null) return false;
        if (product != null ? !product.equals(that.product) : that.product != null) return false;
        if (multiplier != null ? !multiplier.equals(that.multiplier) : that.multiplier != null) return false;
        if (tickSize != null ? !tickSize.equals(that.tickSize) : that.tickSize != null) return false;
        if (minQty != null ? !minQty.equals(that.minQty) : that.minQty != null) return false;
        if (listedDate != null ? !listedDate.equals(that.listedDate) : that.listedDate != null) return false;
        if (expiryDate != null ? !expiryDate.equals(that.expiryDate) : that.expiryDate != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        if (updatedAt != null ? !updatedAt.equals(that.updatedAt) : that.updatedAt != null) return false;
        return true;
    }

    /**
     * 基于全部业务字段计算哈希值。
     * <p>
     * 采用经典的"result = 31 * result + 字段哈希"组合方式，初始值取 17（非零奇素数），
     * 以减少哈希冲突。需与 equals 保持一致：所有参与 equals 判定的字段都参与哈希计算。
     * </p>
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        int result = 17; // 初始值取非零奇素数，降低低位冲突概率
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (exchange != null ? exchange.hashCode() : 0);
        result = 31 * result + (product != null ? product.hashCode() : 0);
        result = 31 * result + (multiplier != null ? multiplier.hashCode() : 0);
        result = 31 * result + (tickSize != null ? tickSize.hashCode() : 0);
        result = 31 * result + (minQty != null ? minQty.hashCode() : 0);
        result = 31 * result + (listedDate != null ? listedDate.hashCode() : 0);
        result = 31 * result + (expiryDate != null ? expiryDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }

    /**
     * 返回合约实体的字符串表示，包含全部字段，便于日志排查与调试。
     */
    @Override
    public String toString() {
        return "Contract{id=" + id + ", code='" + code + "', name='" + name + "', exchange='" + exchange + "', product='" + product + "', multiplier=" + multiplier + ", tickSize=" + tickSize + ", minQty=" + minQty + ", listedDate=" + listedDate + ", expiryDate=" + expiryDate + ", status='" + status + "', createdAt=" + createdAt + ", updatedAt=" + updatedAt + "}";
    }

}
