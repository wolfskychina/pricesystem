package com.bank.trading.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 全局日志与链路追踪过滤器。
 * <p>
 * 为每个进入网关的请求生成 traceId（若上游未携带则生成新的），写入 MDC 与请求头，
 * 便于在 ELK / 日志中按 traceId 串联整条调用链。
 * <p>
 * 过滤器顺序：{@link Ordered#HIGHEST_PRECEDENCE}，确保后续过滤器/下游服务能拿到 traceId。
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    /** 链路追踪 ID 的请求头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        final String finalTraceId = traceId;
        // 将 traceId 注入下游请求头
        ServerHttpRequest mutated = request.mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();
        final ServerWebExchange finalExchange = exchange.mutate().request(mutated).build();

        final String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod().name();
        final String path = request.getPath().value();
        final long start = System.currentTimeMillis();
        log.info("[{}] {} {} -> routing", finalTraceId, method, path);

        return chain.filter(finalExchange).doFinally(signalType -> {
            long elapsed = System.currentTimeMillis() - start;
            int status = finalExchange.getResponse().getStatusCode() == null
                    ? -1 : finalExchange.getResponse().getStatusCode().value();
            log.info("[{}] {} {} <- {} ({}ms)", finalTraceId, method, path, status, elapsed);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
