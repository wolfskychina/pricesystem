package com.bank.trading.common.core.idgen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 发号器 Spring 配置。
 * <p>
 * 自动装配 {@link IdGenerator} 为 Spring Bean，供各业务服务注入使用。
 * 配置项：
 * <ul>
 *   <li>{@code id-generator.datacenter-id}：数据中心 ID（0-31），按部署机房分配，默认 0</li>
 *   <li>{@code id-generator.worker-id}：机器/实例 ID（0-31），同一数据中心内不重复，默认 0</li>
 * </ul>
 * <p>
 * 多实例部署时，必须通过环境变量或启动参数为每个实例分配唯一的 workerId，
 * 否则不同实例生成的 ID 会冲突。
 */
@Slf4j
@Configuration
public class IdGeneratorConfig {

    @Value("${id-generator.datacenter-id:0}")
    private long datacenterId;

    @Value("${id-generator.worker-id:0}")
    private long workerId;

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator idGenerator() {
        log.info("Initializing IdGenerator: datacenterId={}, workerId={}", datacenterId, workerId);
        return new IdGenerator(datacenterId, workerId);
    }
}
