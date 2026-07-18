package com.bank.trading.simexchange.model;

import java.math.BigDecimal;

/**
 * 交易所订单模型。
 * <p>
 * 表示一笔提交到模拟交易所的订单，承载订单基本信息、状态、成交结果等字段。
 * 该模型由撮合引擎（{@link com.bank.trading.simexchange.engine.MatchingEngine}）创建
 * 与维护，并通过 REST 接口返回给调用方。字段含义与传统交易所订单字段保持一致。
 * <p>
 * 因系统移除 Lombok，本类手写了全部 getter/setter/equals/hashCode/toString。
 */
public class ExchangeOrder {

    /** 模拟交易所生成的订单唯一 ID（UUID 去横线） */
    private String orderId;
    /** 客户端自定义订单号，用于幂等去重与客户端对账 */
    private String clientOrderId;
    /** 合约代码，例如 EURUSD */
    private String symbol;
    /** 订单方向，取值见 OrderSide 枚举，如 BUY/SELL */
    private String side;
    /** 订单类型，取值见 OrderType 枚举，如 MARKET/LIMIT */
    private String type;
    /** 委托数量 */
    private BigDecimal qty;
    /** 委托价格；市价单可空，限价单必填 */
    private BigDecimal price;
    /** 已成交数量；订单创建时为 0，成交后累加 */
    private BigDecimal filledQty;
    /** 平均成交价；按成交金额/成交量加权计算 */
    private BigDecimal avgPrice;
    /** 订单状态码，取值见 OrderStatus 枚举，如 NEW/FILLED/REJECTED */
    private String status;
    /** 订单创建时间（毫秒时间戳） */
    private Long createdAt;
    /** 订单最近更新时间（毫秒时间戳），撮合或状态变更时刷新 */
    private Long updatedAt;

    /** 获取订单 ID */
    public String getOrderId() {
        return orderId;
    }

    /** 获取客户端订单号 */
    public String getClientOrderId() {
        return clientOrderId;
    }

    /** 获取合约代码 */
    public String getSymbol() {
        return symbol;
    }

    /** 获取订单方向 */
    public String getSide() {
        return side;
    }

    /** 获取订单类型 */
    public String getType() {
        return type;
    }

    /** 获取委托数量 */
    public BigDecimal getQty() {
        return qty;
    }

    /** 获取委托价格 */
    public BigDecimal getPrice() {
        return price;
    }

    /** 获取已成交数量 */
    public BigDecimal getFilledQty() {
        return filledQty;
    }

    /** 获取平均成交价 */
    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    /** 获取订单状态码 */
    public String getStatus() {
        return status;
    }

    /** 获取订单创建时间 */
    public Long getCreatedAt() {
        return createdAt;
    }

    /** 获取订单最近更新时间 */
    public Long getUpdatedAt() {
        return updatedAt;
    }

    /** 设置订单 ID */
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    /** 设置客户端订单号 */
    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    /** 设置合约代码 */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /** 设置订单方向 */
    public void setSide(String side) {
        this.side = side;
    }

    /** 设置订单类型 */
    public void setType(String type) {
        this.type = type;
    }

    /** 设置委托数量 */
    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    /** 设置委托价格 */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /** 设置已成交数量 */
    public void setFilledQty(BigDecimal filledQty) {
        this.filledQty = filledQty;
    }

    /** 设置平均成交价 */
    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    /** 设置订单状态码 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 设置订单创建时间 */
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    /** 设置订单最近更新时间 */
    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 相等性判断，逐字段比较；样板方法。
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeOrder that = (ExchangeOrder) o;
        if (orderId != null ? !orderId.equals(that.orderId) : that.orderId != null) return false;
        if (clientOrderId != null ? !clientOrderId.equals(that.clientOrderId) : that.clientOrderId != null) return false;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (side != null ? !side.equals(that.side) : that.side != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (qty != null ? !qty.equals(that.qty) : that.qty != null) return false;
        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        if (filledQty != null ? !filledQty.equals(that.filledQty) : that.filledQty != null) return false;
        if (avgPrice != null ? !avgPrice.equals(that.avgPrice) : that.avgPrice != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        if (updatedAt != null ? !updatedAt.equals(that.updatedAt) : that.updatedAt != null) return false;
        return true;
    }

    /**
     * 哈希码计算，与 equals 保持一致；样板方法。
     */
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (orderId != null ? orderId.hashCode() : 0);
        result = 31 * result + (clientOrderId != null ? clientOrderId.hashCode() : 0);
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (side != null ? side.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (qty != null ? qty.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (filledQty != null ? filledQty.hashCode() : 0);
        result = 31 * result + (avgPrice != null ? avgPrice.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }

    /** 字符串表示，便于日志输出与调试 */
    @Override
    public String toString() {
        return "ExchangeOrder{orderId='" + orderId + "', clientOrderId='" + clientOrderId + "', symbol='" + symbol + "', side='" + side + "', type='" + type + "', qty=" + qty + ", price=" + price + ", filledQty=" + filledQty + ", avgPrice=" + avgPrice + ", status='" + status + "', createdAt=" + createdAt + ", updatedAt=" + updatedAt + "}";
    }

}
