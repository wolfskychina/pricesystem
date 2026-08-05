package com.bank.trading.common.core.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TraceContext} 单元测试，验证 traceId 的生成、MDC 读写与清理逻辑。
 */
@DisplayName("TraceContext 工具类测试")
class TraceContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("generate 应返回 32 位无横线 UUID")
    void generate_shouldReturn32CharHexWithoutDashes() {
        String traceId = TraceContext.generate();

        assertNotNull(traceId);
        assertEquals(32, traceId.length(), "UUID 去横线后应为 32 位");
        assertFalse(traceId.contains("-"), "不应包含横线");
    }

    @Test
    @DisplayName("generate 每次生成不同值")
    void generate_shouldReturnUniqueValues() {
        String id1 = TraceContext.generate();
        String id2 = TraceContext.generate();

        assertNotEquals(id1, id2, "两次生成应不同");
    }

    @Test
    @DisplayName("setTraceId 传入有效值时应写入 MDC")
    void setTraceId_withValidValue_shouldPutToMdc() {
        TraceContext.setTraceId("test-trace-001");

        assertEquals("test-trace-001", TraceContext.getTraceId());
        assertEquals("test-trace-001", MDC.get(TraceContext.TRACE_ID_KEY));
    }

    @Test
    @DisplayName("setTraceId 传入 null 时应自动生成新 traceId")
    void setTraceId_withNull_shouldGenerateNewTraceId() {
        TraceContext.setTraceId(null);

        String traceId = TraceContext.getTraceId();
        assertNotNull(traceId, "null 入参应自动生成");
        assertEquals(32, traceId.length(), "生成的应为 32 位 UUID");
    }

    @Test
    @DisplayName("setTraceId 传入空白字符串时应自动生成新 traceId")
    void setTraceId_withBlankString_shouldGenerateNewTraceId() {
        TraceContext.setTraceId("   ");

        String traceId = TraceContext.getTraceId();
        assertNotNull(traceId, "空白入参应自动生成");
        assertEquals(32, traceId.length(), "生成的应为 32 位 UUID");
    }

    @Test
    @DisplayName("getTraceId 在未设置时应返回 null")
    void getTraceId_whenNotSet_shouldReturnNull() {
        assertNull(TraceContext.getTraceId());
    }

    @Test
    @DisplayName("clear 应移除 MDC 中的 traceId")
    void clear_shouldRemoveTraceIdFromMdc() {
        TraceContext.setTraceId("to-be-cleared");
        assertNotNull(TraceContext.getTraceId());

        TraceContext.clear();

        assertNull(TraceContext.getTraceId(), "clear 后应返回 null");
        assertNull(MDC.get(TraceContext.TRACE_ID_KEY), "MDC 中应已移除");
    }

    @Test
    @DisplayName("常量值应符合约定")
    void constants_shouldHaveExpectedValues() {
        assertEquals("traceId", TraceContext.TRACE_ID_KEY, "MDC 键名");
        assertEquals("X-Trace-Id", TraceContext.TRACE_ID_HEADER, "HTTP 头名");
        assertEquals("trace-id", TraceContext.KAFKA_TRACE_HEADER, "Kafka header 名");
    }
}
