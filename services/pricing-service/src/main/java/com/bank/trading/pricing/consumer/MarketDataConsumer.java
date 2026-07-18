package com.bank.trading.pricing.consumer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bank.trading.pricing.service.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class MarketDataConsumer {

    private final PricingService pricingService;

    public MarketDataConsumer(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @KafkaListener(topics = "${pricing.market-data-topic:market-data}",
            groupId = "${spring.kafka.consumer.group-id:pricing-service}")
    public void onMessage(String message) {
        try {
            JSONObject obj = JSON.parseObject(message);
            String symbol = obj.getString("symbol");
            BigDecimal bidPrice = obj.getBigDecimal("bidPrice");
            BigDecimal askPrice = obj.getBigDecimal("askPrice");
            if (symbol != null && bidPrice != null && askPrice != null) {
                pricingService.onMarketData(symbol, bidPrice, askPrice);
            }
        } catch (Exception e) {
            log.warn("Failed to parse market data message: {}", e.getMessage());
        }
    }
}
