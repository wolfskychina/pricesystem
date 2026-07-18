package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class TradeEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    private String tradeId;
    private String orderId;
    private String clientOrderId;
    private String customerId;
    private String symbol;
    private String side;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    private Long tradeTime;

    public TradeEvent() {
        super(EventType.TRADE_FILLED, null);
    }

    public TradeEvent(String customerId) {
        super(EventType.TRADE_FILLED, customerId);
        this.customerId = customerId;
    }
}
