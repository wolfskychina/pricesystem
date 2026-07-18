package com.bank.trading.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 对账服务启动类。
 * <p>
 * 定时（@Scheduled）从下游服务（position-service / execution-service / account-service）
 * 拉取快照数据，做跨服务一致性对账：
 * <ul>
 *   <li>敞口对账：客户总持仓 − 对冲持仓 = 净敞口（应接近 0，超过阈值则告警）</li>
 *   <li>额度对账：账户 used_credit 与持仓占用计算结果对比</li>
 * </ul>
 * 对账结果通过 REST 接口暴露 + 日志输出，并提供补偿建议。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class ReconciliationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationApplication.class, args);
    }
}
