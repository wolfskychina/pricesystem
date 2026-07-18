package com.bank.trading.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link RequestLogFilter} 的基本契约。
 * <p>
 * 不启动完整 Spring 上下文，仅测试过滤器常量与 order。
 */
class RequestLogFilterTest {

    @Test
    void shouldRunAtHighestPrecedence() {
        RequestLogFilter filter = new RequestLogFilter();
        assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder(),
                "RequestLogFilter 应该以最高优先级运行，确保下游能拿到 traceId");
    }

    @Test
    void traceIdHeaderConstantShouldBeStable() {
        // 保证下游服务（OMS 等）能按此常量名读取 traceId
        assertEquals("X-Trace-Id", RequestLogFilter.TRACE_ID_HEADER);
    }
}
