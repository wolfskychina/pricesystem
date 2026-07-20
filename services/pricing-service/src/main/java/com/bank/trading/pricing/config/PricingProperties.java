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

    /**
     * 报价节流窗口时间（毫秒）。
     * 同一合约在该时间窗口内的多次行情变更只触发一次报价重算，
     * 避免震荡行情时高频推送，保护客户端性能。
     * 默认值：500ms，对标国内期货市场做市商报价频率。
     */
    private int throttleWindowMs = 500;

    /**
     * 报价有效期（秒）。
     * 每条报价生成后在该时间内有效，超期报价不可成交。
     * 默认值：3秒，防止客户用过期报价在快速行情中成交造成滑点损失。
     */
    private int quoteTtlSeconds = 3;

    @Data
    public static class SpreadConfig {
        private String symbol;
        private int bidSpreadBps;
        private int askSpreadBps;
    }
}
