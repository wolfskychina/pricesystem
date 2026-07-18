package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class HedgeFillEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    private String hedgeTradeId;
    private String symbol;
    private String side;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    private Long tradeTime;
    private String originalTradeId;

    public HedgeFillEvent() {
        super(EventType.HEDGE_FILLED, null);
    }

    public HedgeFillEvent(String symbol) {
        super(EventType.HEDGE_FILLED, symbol);
    }
}
