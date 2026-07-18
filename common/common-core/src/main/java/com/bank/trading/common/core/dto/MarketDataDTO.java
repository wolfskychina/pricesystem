package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class MarketDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String symbol;
    private java.math.BigDecimal bidPrice;
    private java.math.BigDecimal askPrice;
    private java.math.BigDecimal lastPrice;
    private java.math.BigDecimal bidQty;
    private java.math.BigDecimal askQty;
    private java.math.BigDecimal lastQty;
    private Long volume;
    private Long timestamp;
}
