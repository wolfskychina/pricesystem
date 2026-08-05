package com.bank.trading.common.core.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * 全链路追踪自动装配类。
 * <p>
 * 集中注册追踪相关组件，通过 Spring Boot 自动装配机制（{@code AutoConfiguration.imports}）
 * 应用到所有依赖 common-core 的服务，无需各服务显式 @ComponentScan 或 @Import。
 * <p>
 * <b>装配的组件</b>：
 * <ul>
 *   <li>{@link TraceIdFilter}：HTTP 入站过滤器（仅 Servlet 服务）</li>
 *   <li>{@link TraceRestTemplateCustomizer}：HTTP 出站拦截器（仅含 RestTemplate 的服务）</li>
 *   <li>{@link TraceKafkaContainerCustomizer}：Kafka 消费侧 MDC 注入（仅含 Kafka Listener 的服务）</li>
 *   <li>{@link MdcTaskDecorator}：线程池/定时任务 MDC 传播</li>
 * </ul>
 * <p>
 * <b>开关</b>：{@code common.trace.enabled=false} 可一键禁用所有追踪组件（默认开启）。
 * <p>
 * <b>说明</b>：Kafka 生产者拦截器 {@link TraceKafkaProducerInterceptor} 由 Kafka 客户端反射实例化，
 * 非 Spring Bean，需在各服务 application.yml 配置 interceptor.classes 注册。
 *
 * @see TraceContext 追踪上下文工具类
 */
@AutoConfiguration
@ConditionalOnProperty(name = "common.trace.enabled", havingValue = "true", matchIfMissing = true)
public class TraceAutoConfiguration {

    /**
     * HTTP 入站过滤器，从请求头提取/生成 traceId 写入 MDC。
     * <p>
     * 仅当 classpath 含 Servlet API 时装配（排除响应式 gateway）。
     */
    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnMissingBean(TraceIdFilter.class)
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /**
     * RestTemplate 出站定制器，自动注入 X-Trace-Id 头到所有出站 REST 请求。
     * <p>
     * 仅当 classpath 含 RestTemplate 时装配。Spring Boot 会自动应用 Customizer 到所有
     * RestTemplateBuilder 构建的实例。
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.web.client.RestTemplate")
    @ConditionalOnMissingBean(TraceRestTemplateCustomizer.class)
    public RestTemplateCustomizer traceRestTemplateCustomizer() {
        return new TraceRestTemplateCustomizer();
    }

    /**
     * Kafka 监听容器工厂后置处理器，注册 RecordInterceptor 实现消费侧 traceId 注入 MDC。
     * <p>
     * 仅当 classpath 含 Spring Kafka 时装配。
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.kafka.config.AbstractKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(TraceKafkaContainerCustomizer.class)
    public TraceKafkaContainerCustomizer traceKafkaContainerCustomizer() {
        return new TraceKafkaContainerCustomizer();
    }

    /**
     * MDC 跨线程传播装饰器，应用于 @Async / @Scheduled / 线程池。
     * <p>
     * Spring Boot 会自动把 TaskDecorator Bean 应用到 TaskExecutor。
     */
    @Bean
    @ConditionalOnMissingBean(MdcTaskDecorator.class)
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
