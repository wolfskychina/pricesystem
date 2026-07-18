package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class QuoteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String symbol;
    private BigDecimal marketBidPrice;
    private BigDecimal marketAskPrice;
    private BigDecimal customerBidPrice;
    private BigDecimal customerAskPrice;
    private BigDecimal spread;
    private Long quoteId;
    private Long validUntil;
    private Long timestamp;
}
