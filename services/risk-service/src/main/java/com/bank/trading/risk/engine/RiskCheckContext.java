package com.bank.trading.risk.engine;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RiskCheckContext {
    private String customerId;
    private String symbol;
    private String side;
    private String orderType;
    private BigDecimal qty;
    private BigDecimal orderPrice;
    private BigDecimal marketMidPrice;
    private BigDecimal usedCredit;
    private BigDecimal dailyUsedAmount;
    private BigDecimal currentPosition;
    private String traceId;
}
