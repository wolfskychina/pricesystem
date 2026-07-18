package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class MarketDataEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    private String symbol;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private BigDecimal lastPrice;
    private BigDecimal bidQty;
    private BigDecimal askQty;
    private BigDecimal lastQty;
    private Long volume;
    private Long timestamp;

    public MarketDataEvent() {
        super(EventType.MARKET_DATA, null);
    }

    public MarketDataEvent(String symbol) {
        super(EventType.MARKET_DATA, symbol);
    }
}
