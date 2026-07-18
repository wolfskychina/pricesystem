package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class TradeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tradeId;
    private String orderId;
    private String customerId;
    private String symbol;
    private String side;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    private Long tradeTime;
}
