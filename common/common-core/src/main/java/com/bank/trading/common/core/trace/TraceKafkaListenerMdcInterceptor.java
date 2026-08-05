package com.bank.trading.common.core.trace;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Kafka 消费侧 MDC 注入拦截器（Spring Kafka RecordInterceptor）。
 * <p>
 * 在每条消息被 {@code @KafkaListener} 方法消费前后触发：
 * <ul>
 *   <li>消费前（{@link #intercept}）：从 {@value TraceContext#KAFKA_TRACE_HEADER} header 提取 traceId，写入 MDC</li>
 *   <li>消费后成功（{@link #success}）/ 失败（{@link #failure}）：清理 MDC，避免线程池复用串流</li>
 * </ul>
 * <p>
 * <b>为什么不用 {@code ConsumerInterceptor}</b>：{@code ConsumerInterceptor.onConsume} 在 poll 线程
 * 批量触发（每批消息只调用一次），无法为每条消息单独设 MDC。而 {@code @KafkaListener} 是单条消费模型，
 * {@code RecordInterceptor} 恰好匹配——每条记录消费前后各触发一次。
 *
 * @see TraceContext#setTraceId(String) 写入 MDC
 */
public class TraceKafkaListenerMdcInterceptor implements RecordInterceptor<String, String> {

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        // 从 Kafka header 提取 traceId，写入 MDC
        Header header = record.headers().lastHeader(TraceContext.KAFKA_TRACE_HEADER);
        if (header != null) {
            String traceId = new String(header.value(), StandardCharsets.UTF_8);
            TraceContext.setTraceId(traceId);
        } else {
            // 消息无 traceId（上游未注入），生成新的保证日志可追踪
            TraceContext.setTraceId(null);
        }
        return record;
    }

    @Override
    public void success(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        // 消费成功，清理 MDC
        TraceContext.clear();
    }

    @Override
    public void failure(ConsumerRecord<String, String> record, Exception exception, Consumer<String, String> consumer) {
        // 消费失败，同样清理 MDC，避免线程池复用串流
        TraceContext.clear();
    }
}
