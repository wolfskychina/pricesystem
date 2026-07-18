package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class OrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;
    private String clientOrderId;
    private String customerId;
    private String symbol;
    private String side;
    private String type;
    private BigDecimal qty;
    private BigDecimal filledQty;
    private BigDecimal price;
    private BigDecimal avgPrice;
    private String status;
    private String rejectReason;
    private Long createdAt;
    private Long updatedAt;
}
