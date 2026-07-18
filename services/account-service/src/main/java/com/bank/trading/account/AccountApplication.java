package com.bank.trading.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 客户账户服务启动类。
 * <p>
 * account-service 是做市商交易系统的客户账户管理模块，核心职责：
 * <ol>
 *   <li>客户主数据 CRUD（创建、查询、修改、停用）</li>
 *   <li>信用额度管理（credit_limit / used_credit / available_credit）</li>
 *   <li>消费客户成交事件（trade-event），按成交金额扣减/释放可用额度</li>
 *   <li>对外提供客户与信用查询接口，供 risk-service 事前风控调用</li>
 * </ol>
 * <p>
 * <b>可用额度模型</b>：
 * <ul>
 *   <li>available_credit = credit_limit − used_credit</li>
 *   <li>客户 BUY 成交 → used_credit += amount（占用信用）</li>
 *   <li>客户 SELL 成交 → used_credit −= amount（释放信用）</li>
 * </ul>
 * <p>
 * <b>幂等消费</b>：通过 processed_events 表去重，保证 Kafka 至少一次投递语义下
 * 业务状态不被重复扣减。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
