package com.bank.trading.execution.dto;

import java.math.BigDecimal;

/**
 * 模拟交易所通过 Webhook 推送的成交通知载荷。
 * <p>
 * 对应 sim-exchange {@code POST /execution/callback/trade} 的请求体，
 * 模拟真实期货交易所 CTP 的 {@code OnRtnTrade} 回调。
 * <p>
 * 当对冲订单在交易所撮合成交后，sim-exchange 会主动 POST 该对象到
 * execution-service 的回调接口。
 */
public class ExchangeTradeNotification {

    /** 交易所成交 ID（如 "T1"） */
    private String tradeId;
    /** 关联的交易所订单 ID */
    private String orderId;
    /** 合约代码 */
    private String symbol;
    /** 成交方向（BUY/SELL） */
    private String side;
    /** 成交数量 */
    private BigDecimal qty;
    /** 成交价格 */
    private BigDecimal price;
    /** 成交金额 = qty × price */
    private BigDecimal amount;
    /** 成交时间（毫秒时间戳） */
    private Long tradeTime;

    public String getTradeId() { return tradeId; }
    public void setTradeId(String tradeId) { this.tradeId = tradeId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public BigDecimal getQty() { return qty; }
    public void setQty(BigDecimal qty) { this.qty = qty; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getTradeTime() { return tradeTime; }
    public void setTradeTime(Long tradeTime) { this.tradeTime = tradeTime; }
}
