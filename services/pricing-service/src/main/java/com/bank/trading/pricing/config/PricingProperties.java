package com.bank.trading.pricing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "pricing")
public class PricingProperties {

    private String marketDataTopic = "market-data";
    private String quoteTopic = "customer-quote";
    private int defaultBidSpreadBps = 5;
    private int defaultAskSpreadBps = 5;
    private List<SpreadConfig> spreadConfigs = new ArrayList<>();

    @Data
    public static class SpreadConfig {
        private String symbol;
        private int bidSpreadBps;
        private int askSpreadBps;
    }
}
