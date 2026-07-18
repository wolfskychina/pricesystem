package com.bank.trading.oms.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bank.trading.common.core.dto.QuoteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Slf4j
@Component
public class PricingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${oms.pricing-service-url:http://pricing-service}")
    private String pricingServiceUrl;

    public PricingServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public QuoteDTO getQuote(String symbol) {
        try {
            String url = pricingServiceUrl + "/quotes/" + symbol;
            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JSONObject json = JSON.parseObject(response);
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    QuoteDTO quote = new QuoteDTO();
                    quote.setSymbol(data.getString("symbol"));
                    quote.setCustomerBidPrice(data.getBigDecimal("customerBidPrice"));
                    quote.setCustomerAskPrice(data.getBigDecimal("customerAskPrice"));
                    quote.setQuoteId(data.getLong("quoteId"));
                    quote.setValidUntil(data.getLong("validUntil"));
                    return quote;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to call pricing service for symbol {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public BigDecimal getExecutionPrice(String symbol, String side) {
        QuoteDTO quote = getQuote(symbol);
        if (quote == null) {
            return null;
        }
        if ("BUY".equalsIgnoreCase(side)) {
            return quote.getCustomerAskPrice();
        } else {
            return quote.getCustomerBidPrice();
        }
    }
}
