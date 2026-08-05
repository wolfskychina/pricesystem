package com.bank.trading.common.core.trace;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Kafka 监听容器工厂后置处理器，为所有 {@link AbstractKafkaListenerContainerFactory} 注册
 * {@link TraceKafkaListenerMdcInterceptor}，实现消费侧 traceId 自动注入 MDC。
 * <p>
 * 以 {@link BeanPostProcessor} 形式装配，在 Spring Boot 自动配置的
 * {@code ConcurrentKafkaListenerContainerFactory} Bean 初始化后设置 RecordInterceptor，
 * <b>无需修改任何 @KafkaListener 方法签名</b>即可让消费侧日志带上 traceId。
 * <p>
 * <b>注意</b>：仅当 factory 未自定义 RecordInterceptor 时设置，避免覆盖业务自定义拦截器。
 * 若业务已有拦截器，需手动在业务拦截器中调用 {@link TraceContext} 续接 traceId。
 *
 * @see TraceKafkaListenerMdcInterceptor 实际的 MDC 注入逻辑
 */
public class TraceKafkaContainerCustomizer implements BeanPostProcessor {

    private final RecordInterceptor<String, String> interceptor = new TraceKafkaListenerMdcInterceptor();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 只处理 KafkaListenerContainerFactory 类型的 Bean
        if (bean instanceof AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
            // 设置 RecordInterceptor，实现消费侧 traceId 自动注入 MDC
            @SuppressWarnings({"unchecked", "rawtypes"})
            AbstractKafkaListenerContainerFactory rawFactory = factory;
            rawFactory.setRecordInterceptor(interceptor);
        }
        return bean;
    }
}
