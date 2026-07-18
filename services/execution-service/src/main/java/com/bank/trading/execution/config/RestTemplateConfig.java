package com.bank.trading.execution.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置。
 * <p>
 * 为 {@link com.bank.trading.execution.client.ExchangeSessionClient} 提供配置好的
 * RestTemplate 实例。连接与读取超时设置较短，避免交易所不可达时阻塞消费线程。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
}
