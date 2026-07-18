package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import com.bank.trading.common.core.enums.OrderSide;
import java.math.BigDecimal;

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

    public String getHedgeTradeId() {
        return hedgeTradeId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getTradeTime() {
        return tradeTime;
    }

    public String getOriginalTradeId() {
        return originalTradeId;
    }

    public void setHedgeTradeId(String hedgeTradeId) {
        this.hedgeTradeId = hedgeTradeId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setTradeTime(Long tradeTime) {
        this.tradeTime = tradeTime;
    }

    public void setOriginalTradeId(String originalTradeId) {
        this.originalTradeId = originalTradeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        HedgeFillEvent that = (HedgeFillEvent) o;
        if (hedgeTradeId != null ? !hedgeTradeId.equals(that.hedgeTradeId) : that.hedgeTradeId != null) return false;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (side != null ? !side.equals(that.side) : that.side != null) return false;
        if (qty != null ? !qty.equals(that.qty) : that.qty != null) return false;
        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (tradeTime != null ? !tradeTime.equals(that.tradeTime) : that.tradeTime != null) return false;
        if (originalTradeId != null ? !originalTradeId.equals(that.originalTradeId) : that.originalTradeId != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (hedgeTradeId != null ? hedgeTradeId.hashCode() : 0);
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (side != null ? side.hashCode() : 0);
        result = 31 * result + (qty != null ? qty.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (tradeTime != null ? tradeTime.hashCode() : 0);
        result = 31 * result + (originalTradeId != null ? originalTradeId.hashCode() : 0);
        return result;
    }

    @Override

    @Override

    @Override

    @Override

    @Override

    @Override

    public HedgeFillEvent() {
        super(EventType.HEDGE_FILLED, null);
    }

    public HedgeFillEvent(String symbol) {
        super(EventType.HEDGE_FILLED, symbol);
    }
    @Override
    public String toString() {
        return "HedgeFillEvent{hedgeTradeId='" + hedgeTradeId + "', symbol='" + symbol + "', side='" + side + "', qty=" + qty + ", price=" + price + ", amount=" + amount + ", tradeTime=" + tradeTime + ", originalTradeId='" + originalTradeId + "'}";
    }

}
