package com.bank.trading.execution.config;

import com.bank.trading.execution.util.RetryHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HedgeConfig {

    @Value("${execution.hedge.retry.max-attempts:6}")
    private int maxAttempts;

    @Value("${execution.hedge.retry.initial-interval-ms:1000}")
    private long initialIntervalMs;

    @Value("${execution.hedge.retry.max-interval-ms:32000}")
    private long maxIntervalMs;

    @Value("${execution.hedge.retry.multiplier:2.0}")
    private double multiplier;

    @Value("${execution.hedge.retry.jitter-ratio:0.3}")
    private double jitterRatio;

    @Bean
    public RetryHelper retryHelper() {
        return new RetryHelper(maxAttempts, initialIntervalMs, maxIntervalMs, multiplier, jitterRatio);
    }
}