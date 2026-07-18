package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerQuoteEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    private String symbol;
    private String customerId;
    private BigDecimal customerBidPrice;
    private BigDecimal customerAskPrice;
    private BigDecimal marketBidPrice;
    private BigDecimal marketAskPrice;
    private BigDecimal spread;
    private Long quoteId;
    private Long validUntil;

    public CustomerQuoteEvent() {
        super(EventType.CUSTOMER_QUOTE, null);
    }

    public CustomerQuoteEvent(String symbol, String customerId) {
        super(EventType.CUSTOMER_QUOTE, customerId);
        this.symbol = symbol;
        this.customerId = customerId;
    }
}
