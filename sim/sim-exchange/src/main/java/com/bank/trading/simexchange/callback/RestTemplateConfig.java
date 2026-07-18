package com.bank.trading.simexchange.callback;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置。
 * <p>
 * 为 {@link CallbackRegistry} 提供一个配置好的 RestTemplate 实例，
 * 设置合理的连接与读取超时，避免回调推送阻塞撮合线程。
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建 RestTemplate Bean。
     * <p>
     * 连接超时 2 秒，读取超时 3 秒：回调推送为 best effort，
     * 不应因 EMS 不可达而长时间阻塞。
     *
     * @param builder RestTemplate 构造器
     * @return 配置好的 RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }
}
