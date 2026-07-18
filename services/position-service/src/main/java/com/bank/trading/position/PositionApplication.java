package com.bank.trading.position;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 持仓管理服务启动类。
 * <p>
 * position-service 是做市商交易系统的持仓账本模块，核心职责：
 * <ol>
 *   <li>消费客户成交事件（{@code trade-event}），按客户 + 合约维度累加持仓</li>
 *   <li>消费对冲成交事件（{@code hedge-fill-event}），按合约维度累加做市商对冲头寸</li>
 *   <li>计算净敞口 = 客户头寸 − 对冲头寸（敞口应趋近于 0，表示完全对冲）</li>
 *   <li>对外提供持仓查询与敞口监控 REST 接口</li>
 * </ol>
 * <p>
 * <b>幂等消费</b>：所有事件消费通过 {@code processed_events} 表去重，保证 Kafka 至少一次
 * 投递语义下业务状态不被重复累加。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class PositionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionApplication.class, args);
    }
}
