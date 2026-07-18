package com.bank.trading.execution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 对冲执行服务启动类。
 * <p>
 * execution-service 是做市商的对冲执行模块，核心职责：
 * <ol>
 *   <li>消费客户成交事件（trade-event）</li>
 *   <li>计算对冲方向与数量（反向、按比例）</li>
 *   <li>调用 sim-exchange 下单（异步两阶段：同步受理 + Webhook 回调成交）</li>
 *   <li>接收 sim-exchange Webhook 回调，记录对冲成交</li>
 *   <li>发布对冲成交事件（hedge-fill-event）到 Kafka</li>
 * </ol>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class ExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionApplication.class, args);
    }
}
