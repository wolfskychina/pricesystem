package com.bank.trading.reconciliation.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置。
 * <p>
 * 为 {@link com.bank.trading.reconciliation.client.DownstreamClient} 提供配置好的
 * RestTemplate 实例。对账场景下允许较长的读取超时（10s），因为下游聚合查询可能较慢；
 * 连接超时较短（2s），下游不可达时快速失败并被对账逻辑记为 data-missing。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
