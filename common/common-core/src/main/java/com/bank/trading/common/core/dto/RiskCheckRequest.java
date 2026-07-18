package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class RiskCheckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String symbol;
    private String side;
    private String orderType;
    private BigDecimal qty;
    private BigDecimal price;
    private String clientOrderId;
    private String traceId;
}
