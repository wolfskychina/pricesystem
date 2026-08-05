package com.bank.trading.common.core.trace;

import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * RestTemplate 出站链路追踪定制器。
 * <p>
 * Spring Boot 会自动把所有 {@link RestTemplateCustomizer} Bean 应用到 {@code RestTemplateBuilder}
 * 构建的每个 RestTemplate 实例上。本类为 RestTemplate 添加拦截器，在出站请求时把当前 MDC 中的
 * traceId 注入 {@value TraceContext#TRACE_ID_HEADER} 请求头，实现 REST 调用透传。
 * <p>
 * <b>覆盖范围</b>：所有用 {@code RestTemplateBuilder} 构建的 RestTemplate（reconciliation/execution/
 * sim-client/sim-exchange 的 RestTemplateConfig）。对于用 {@code new RestTemplate()} 直接构造的
 * 实例（如 oms 的 OmsApplication），需改为 {@code RestTemplateBuilder} 注入方式才能生效。
 *
 * @see TraceContext#getTraceId() 从 MDC 读取当前 traceId
 */
public class TraceRestTemplateCustomizer implements RestTemplateCustomizer {

    @Override
    public void customize(RestTemplate restTemplate) {
        restTemplate.getInterceptors().add(new TraceRequestInterceptor());
    }

    /**
     * RestTemplate 出站拦截器：注入 X-Trace-Id 头。
     */
    private static class TraceRequestInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            String traceId = TraceContext.getTraceId();
            if (traceId != null && !traceId.isBlank()) {
                request.getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
            }
            return execution.execute(request, body);
        }
    }
}
