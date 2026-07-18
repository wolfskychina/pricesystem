package com.bank.trading.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 通知服务启动类。
 * <p>
 * 订阅 Kafka 多主题（trade-event / hedge-fill-event / customer-quote / market-data），
 * 通过 WebSocket 实时推送给前端监控面板，支持按客户订阅主题过滤。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyApplication.class, args);
    }
}
