package com.bank.trading.execution.dto;

import java.math.BigDecimal;

/**
 * 向模拟交易所提交对冲订单的请求体。
 * <p>
 * 对应 sim-exchange {@code POST /exchange/orders} 的请求载荷。
 * 字段命名与 sim-exchange 的 OrderRequest 保持一致，确保 JSON 反序列化正确。
 */
public class ExchangeOrderRequest {

    /** 客户端自定义订单号，用于幂等去重与对账；这里使用对冲单内部 ID */
    private String clientOrderId;
    /** 合约代码 */
    private String symbol;
    /** 订单方向（BUY/SELL），对冲方向与客户成交同向 */
    private String side;
    /** 订单类型（MARKET/LIMIT） */
    private String type;
    /** 委托数量 */
    private BigDecimal qty;
    /** 委托价格；市价单可空 */
    private BigDecimal price;

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
}
