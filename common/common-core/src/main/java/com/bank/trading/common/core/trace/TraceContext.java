package com.bank.trading.common.core.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪上下文工具类，封装 traceId 的 MDC 操作。
 * <p>
 * 全系统统一通过本类读写 traceId，避免散落使用 MDC 导致 key 不一致。
 * <ul>
 *   <li>{@link #TRACE_ID_KEY}：MDC 中的键名，logback pattern 用 {@code %X{traceId}} 读取</li>
 *   <li>{@link #TRACE_ID_HEADER}：HTTP 请求头名，REST 调用透传使用</li>
 *   <li>{@link #KAFKA_TRACE_HEADER}：Kafka RecordHeader 名，消息透传使用</li>
 * </ul>
 *
 * @see TraceIdFilter HTTP 入站过滤器，调用 {@link #setTraceId} 写入 MDC
 * @see TraceKafkaProducerInterceptor Kafka 出站拦截器，调用 {@link #getTraceId} 注入 header
 */
public final class TraceContext {

    /** MDC 中 traceId 的键名，logback pattern 用 %X{traceId} 读取 */
    public static final String TRACE_ID_KEY = "traceId";

    /** HTTP 请求头名，REST 调用透传 traceId */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** Kafka RecordHeader 名，消息透传 traceId */
    public static final String KAFKA_TRACE_HEADER = "trace-id";

    private TraceContext() {
    }

    /**
     * 生成 32 位无横线 UUID 作为 traceId。
     *
     * @return traceId 字符串
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置 traceId 到 MDC。若传入为空则自动生成新 traceId。
     * <p>
     * 用于请求入口（Filter / Kafka Listener / 定时任务）初始化追踪上下文。
     *
     * @param traceId 待设置的 traceId，null/空白时自动生成
     */
    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, generate());
        } else {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 获取当前 MDC 中的 traceId。
     *
     * @return traceId，未设置时返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 清除 MDC 中的 traceId。
     * <p>
     * 必须在请求处理结束时（finally 块）调用，避免线程池复用导致 traceId 串流。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
