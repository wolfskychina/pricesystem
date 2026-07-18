package com.bank.trading.oms.service;

import com.bank.trading.oms.client.PricingServiceClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PriceProviderAdapter implements PriceProvider {

    private final PricingServiceClient pricingServiceClient;

    public PriceProviderAdapter(PricingServiceClient pricingServiceClient) {
        this.pricingServiceClient = pricingServiceClient;
    }

    @Override
    public BigDecimal getExecutionPrice(String symbol, String side) {
        return pricingServiceClient.getExecutionPrice(symbol, side);
    }
}
