package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import java.math.BigDecimal;

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

    public String getSymbol() {
        return symbol;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getCustomerBidPrice() {
        return customerBidPrice;
    }

    public BigDecimal getCustomerAskPrice() {
        return customerAskPrice;
    }

    public BigDecimal getMarketBidPrice() {
        return marketBidPrice;
    }

    public BigDecimal getMarketAskPrice() {
        return marketAskPrice;
    }

    public BigDecimal getSpread() {
        return spread;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public Long getValidUntil() {
        return validUntil;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public void setCustomerBidPrice(BigDecimal customerBidPrice) {
        this.customerBidPrice = customerBidPrice;
    }

    public void setCustomerAskPrice(BigDecimal customerAskPrice) {
        this.customerAskPrice = customerAskPrice;
    }

    public void setMarketBidPrice(BigDecimal marketBidPrice) {
        this.marketBidPrice = marketBidPrice;
    }

    public void setMarketAskPrice(BigDecimal marketAskPrice) {
        this.marketAskPrice = marketAskPrice;
    }

    public void setSpread(BigDecimal spread) {
        this.spread = spread;
    }

    public void setQuoteId(Long quoteId) {
        this.quoteId = quoteId;
    }

    public void setValidUntil(Long validUntil) {
        this.validUntil = validUntil;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CustomerQuoteEvent that = (CustomerQuoteEvent) o;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (customerId != null ? !customerId.equals(that.customerId) : that.customerId != null) return false;
        if (customerBidPrice != null ? !customerBidPrice.equals(that.customerBidPrice) : that.customerBidPrice != null) return false;
        if (customerAskPrice != null ? !customerAskPrice.equals(that.customerAskPrice) : that.customerAskPrice != null) return false;
        if (marketBidPrice != null ? !marketBidPrice.equals(that.marketBidPrice) : that.marketBidPrice != null) return false;
        if (marketAskPrice != null ? !marketAskPrice.equals(that.marketAskPrice) : that.marketAskPrice != null) return false;
        if (spread != null ? !spread.equals(that.spread) : that.spread != null) return false;
        if (quoteId != null ? !quoteId.equals(that.quoteId) : that.quoteId != null) return false;
        if (validUntil != null ? !validUntil.equals(that.validUntil) : that.validUntil != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (customerId != null ? customerId.hashCode() : 0);
        result = 31 * result + (customerBidPrice != null ? customerBidPrice.hashCode() : 0);
        result = 31 * result + (customerAskPrice != null ? customerAskPrice.hashCode() : 0);
        result = 31 * result + (marketBidPrice != null ? marketBidPrice.hashCode() : 0);
        result = 31 * result + (marketAskPrice != null ? marketAskPrice.hashCode() : 0);
        result = 31 * result + (spread != null ? spread.hashCode() : 0);
        result = 31 * result + (quoteId != null ? quoteId.hashCode() : 0);
        result = 31 * result + (validUntil != null ? validUntil.hashCode() : 0);
        return result;
    }

    @Override

    @Override

    @Override

    @Override

    @Override

    @Override

    public CustomerQuoteEvent() {
        super(EventType.CUSTOMER_QUOTE, null);
    }

    public CustomerQuoteEvent(String symbol, String customerId) {
        super(EventType.CUSTOMER_QUOTE, customerId);
        this.symbol = symbol;
        this.customerId = customerId;
    }
    @Override
    public String toString() {
        return "CustomerQuoteEvent{symbol='" + symbol + "', customerId='" + customerId + "', customerBidPrice=" + customerBidPrice + ", customerAskPrice=" + customerAskPrice + ", marketBidPrice=" + marketBidPrice + ", marketAskPrice=" + marketAskPrice + ", spread=" + spread + ", quoteId=" + quoteId + ", validUntil=" + validUntil + "}";
    }

}
