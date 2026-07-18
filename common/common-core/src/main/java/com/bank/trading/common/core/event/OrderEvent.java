package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderEvent extends BaseEvent {

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

    public OrderEvent() {
        super();
    }

    public OrderEvent(EventType eventType, String customerId) {
        super(eventType, customerId);
        this.customerId = customerId;
    }
}
