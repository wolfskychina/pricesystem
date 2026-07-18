package com.bank.trading.oms.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Order {
    private Long id;
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
    private String traceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
