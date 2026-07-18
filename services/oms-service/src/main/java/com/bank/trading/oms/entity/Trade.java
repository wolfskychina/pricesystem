package com.bank.trading.oms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Trade {
    private Long id;
    private String tradeId;
    private String orderId;
    private String clientOrderId;
    private String customerId;
    private String symbol;
    private String side;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    private String tradeType;
    private LocalDateTime tradeTime;
}
