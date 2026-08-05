package com.bank.trading.common.core.trace;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka 出站链路追踪拦截器。
 * <p>
 * 发送 Kafka 消息时，自动把当前 MDC 中的 traceId 注入 {@value TraceContext#KAFKA_TRACE_HEADER}
 * RecordHeader，实现跨服务的 Kafka 消息透传。
 * <p>
 * <b>注册方式</b>：在各服务 application.yml 配置
 * <pre>
 * spring.kafka.producer.properties.interceptor.classes: com.bank.trading.common.core.trace.TraceKafkaProducerInterceptor
 * </pre>
 * 由 Kafka 客户端在创建 Producer 时反射实例化，<b>非</b> Spring Bean，无法用 @Autowired 注入。
 * <p>
 * <b>注意</b>：OutboxRelay 是定时任务，MDC 中的 traceId 是定时任务新建的，无法关联原始请求。
 * 因此 OutboxRelay 需显式从 OutboxMessage.traceId 构造带 header 的 ProducerRecord，
 * 本拦截器对已带 header 的消息不重复添加。
 *
 * @see TraceContext#getTraceId() 从 MDC 读取当前 traceId
 */
public class TraceKafkaProducerInterceptor implements ProducerInterceptor<String, String> {

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        String traceId = TraceContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            return record;
        }
        Headers headers = record.headers();
        // 避免重复添加（OutboxRelay 可能已显式注入）
        if (headers.lastHeader(TraceContext.KAFKA_TRACE_HEADER) != null) {
            return record;
        }
        headers.add(TraceContext.KAFKA_TRACE_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // 无需处理
    }

    @Override
    public void close() {
        // 无资源需释放
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // 无需配置
    }
}
