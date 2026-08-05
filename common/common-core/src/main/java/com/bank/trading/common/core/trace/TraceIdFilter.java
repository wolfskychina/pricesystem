package com.bank.trading.common.core.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP 入站链路追踪过滤器（Servlet 版）。
 * <p>
 * 每个进入 Servlet 服务的 HTTP 请求，从 {@value TraceContext#TRACE_ID_HEADER} 头提取 traceId
 * （上游未携带则自动生成），写入 MDC 供日志打印；请求结束清理 MDC，避免线程池复用串流。
 * <p>
 * 同时把 traceId 回写到响应头，便于客户端排查问题。
 * <p>
 * <b>装配范围</b>：仅 Servlet 服务（oms/risk/execution/account/position/pricing/notify/refdata/
 * reconciliation）。gateway 是响应式（WebFlux），由其自带 RequestLogFilter 处理，本类不适用。
 *
 * @see TraceContext MDC 操作工具类
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 从请求头提取 traceId，缺失则生成
        String traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        TraceContext.setTraceId(traceId);

        // 2. 回写响应头，便于客户端关联日志
        response.setHeader(TraceContext.TRACE_ID_HEADER, TraceContext.getTraceId());

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 3. 必须清理，防止线程池复用导致 traceId 串流
            TraceContext.clear();
        }
    }
}
