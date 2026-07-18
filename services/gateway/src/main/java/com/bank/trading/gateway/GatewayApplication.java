package com.bank.trading.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动类。
 * <p>
 * 基于 Spring Cloud Gateway，提供统一入口、路由转发、CORS、日志等基础能力。
 * 路由规则在 application.yml 中声明式配置，通过服务发现（Eureka）按 lb://service-name
 * 解析下游实例。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
