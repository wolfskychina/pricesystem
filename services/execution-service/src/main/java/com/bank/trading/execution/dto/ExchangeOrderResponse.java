package com.bank.trading.execution.dto;

import java.math.BigDecimal;

/**
 * 模拟交易所返回的订单对象。
 * <p>
 * 对应 sim-exchange {@code POST /exchange/orders} 同步返回的订单（状态=NEW），
 * 以及 {@code GET /exchange/orders/{id}} 查询返回的订单（状态可能为 FILLED/REJECTED）。
 * <p>
 * 注意：由于 sim-exchange 返回的是 {@code Result<ExchangeOrder>} 包装结构，
 * 反序列化时需先解析外层 Result 的 data 字段。
 */
public class ExchangeOrderResponse {

    /** 模拟交易所生成的订单唯一 ID */
    private String orderId;
    /** 客户端自定义订单号 */
    private String clientOrderId;
    /** 合约代码 */
    private String symbol;
    /** 订单方向 */
    private String side;
    /** 订单类型 */
    private String type;
    /** 委托数量 */
    private BigDecimal qty;
    /** 委托价格 */
    private BigDecimal price;
    /** 已成交数量 */
    private BigDecimal filledQty;
    /** 平均成交价 */
    private BigDecimal avgPrice;
    /** 订单状态码（NEW/ACCEPTED/FILLED/REJECTED） */
    private String status;
    /** 创建时间（毫秒时间戳） */
    private Long createdAt;
    /** 更新时间（毫秒时间戳） */
    private Long updatedAt;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getClientOrderId() { return clientOrderId; }
    public void setClientOrderId(String clientOrderId) { this.clientOrderId = clientOrderId; }
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
