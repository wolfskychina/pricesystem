package com.bank.trading.execution.client;

import com.bank.trading.execution.dto.ExchangeOrderRequest;
import com.bank.trading.execution.dto.ExchangeOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExchangeSessionClient 单元测试。
 * <p>
 * 验证与模拟交易所的交互逻辑：JSON 解析、错误处理、回调注册。
 * 采用手写 Mock RestTemplate 子类，拦截 HTTP 调用并返回预设响应。
 */
class ExchangeSessionClientTest {

    private StubRestTemplate stubRestTemplate;
    private ExchangeSessionClient client;

    @BeforeEach
    void setUp() {
        stubRestTemplate = new StubRestTemplate();
        client = new ExchangeSessionClient(stubRestTemplate,
                "http://localhost:8081", "http://localhost:8086/execution/callback");
    }

    @Test
    @DisplayName("submitOrder 解析 Result 包装的订单响应")
    void submitOrder_parsesResultWrappedResponse() {
        stubRestTemplate.postResponse = "{\"code\":200,\"message\":\"success\",\"data\":"
                + "{\"orderId\":\"ORD-001\",\"symbol\":\"AU2406\",\"side\":\"BUY\",\"type\":\"MARKET\","
                + "\"qty\":10,\"status\":\"NEW\",\"createdAt\":1700000000000}}";

        ExchangeOrderRequest request = new ExchangeOrderRequest();
        request.setSymbol("AU2406");
        request.setSide("BUY");
        request.setType("MARKET");
        request.setQty(new BigDecimal("10"));

        ExchangeOrderResponse response = client.submitOrder(request);

        assertNotNull(response);
        assertEquals("ORD-001", response.getOrderId());
        assertEquals("AU2406", response.getSymbol());
        assertEquals("BUY", response.getSide());
        assertEquals("NEW", response.getStatus());
    }

    @Test
    @DisplayName("submitOrder 交易所返回业务错误时返回 null")
    void submitOrder_businessError_returnsNull() {
        stubRestTemplate.postResponse = "{\"code\":400,\"message\":\"invalid symbol\",\"data\":null}";

        ExchangeOrderRequest request = new ExchangeOrderRequest();
        request.setSymbol("INVALID");
        request.setSide("BUY");
        request.setType("MARKET");
        request.setQty(new BigDecimal("10"));

        ExchangeOrderResponse response = client.submitOrder(request);

        assertNull(response);
    }

    @Test
    @DisplayName("submitOrder 网络异常时抛出 ExchangeException")
    void submitOrder_networkError_throwsExchangeException() {
        stubRestTemplate.throwOnPost = true;

        ExchangeOrderRequest request = new ExchangeOrderRequest();
        request.setSymbol("AU2406");
        request.setSide("BUY");
        request.setType("MARKET");
        request.setQty(new BigDecimal("10"));

        assertThrows(ExchangeSessionClient.ExchangeException.class, () -> client.submitOrder(request));
    }

    @Test
    @DisplayName("queryOrder 解析订单查询响应")
    void queryOrder_parsesResponse() {
        stubRestTemplate.getResponse = "{\"code\":200,\"message\":\"success\",\"data\":"
                + "{\"orderId\":\"ORD-002\",\"symbol\":\"AU2406\",\"side\":\"SELL\",\"type\":\"MARKET\","
                + "\"qty\":5,\"filledQty\":5,\"avgPrice\":520.50,\"status\":\"FILLED\"}}";

        ExchangeOrderResponse response = client.queryOrder("ORD-002");

        assertNotNull(response);
        assertEquals("ORD-002", response.getOrderId());
        assertEquals("FILLED", response.getStatus());
        assertEquals(0, new BigDecimal("520.50").compareTo(response.getAvgPrice()));
    }

    @Test
    @DisplayName("queryOrder 订单不存在时返回 null")
    void queryOrder_notFound_returnsNull() {
        stubRestTemplate.getResponse = "{\"code\":404,\"message\":\"Order not found\",\"data\":null}";

        ExchangeOrderResponse response = client.queryOrder("NON-EXISTENT");

        assertNull(response);
    }

    @Test
    @DisplayName("registerCallback 不抛异常（best effort 语义）")
    void registerCallback_doesNotThrow() {
        assertDoesNotThrow(() -> client.registerCallback());
    }

    @Test
    @DisplayName("registerCallback 网络失败时仅记录日志不抛异常")
    void registerCallback_networkFailure_doesNotThrow() {
        stubRestTemplate.throwOnPost = true;
        assertDoesNotThrow(() -> client.registerCallback());
    }

    @Test
    @DisplayName("registerCallback 发送正确的回调地址")
    void registerCallback_sendsCorrectCallbackUrl() {
        client.registerCallback();

        assertNotNull(stubRestTemplate.lastPostBody);
        assertTrue(stubRestTemplate.lastPostBody.contains("http://localhost:8086/execution/callback"));
    }

    /**
     * 手写 Mock RestTemplate，拦截 HTTP 调用。
     * <p>
     * 覆盖 postForObject / getForObject / postForEntity 方法，
     * 返回预设的响应字符串或抛出异常。
     */
    static class StubRestTemplate extends RestTemplate {
        String postResponse;
        String getResponse;
        boolean throwOnPost = false;
        String lastPostBody;

        @Override
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
            if (throwOnPost) {
                throw new RuntimeException("simulated network error");
            }
            // 记录请求体
            if (request instanceof HttpEntity) {
                Object body = ((HttpEntity<?>) request).getBody();
                lastPostBody = body != null ? body.toString() : null;
            }
            if (postResponse == null) {
                return null;
            }
            return responseType.cast(postResponse);
        }

        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
            if (throwOnPost) {
                throw new RuntimeException("simulated network error");
            }
            if (request instanceof HttpEntity) {
                Object body = ((HttpEntity<?>) request).getBody();
                lastPostBody = body != null ? body.toString() : null;
            }
            T body = postResponse != null ? responseType.cast(postResponse) : null;
            return new ResponseEntity<>(body, org.springframework.http.HttpStatus.OK);
        }

        @Override
        public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) {
            if (getResponse == null) {
                return null;
            }
            return responseType.cast(getResponse);
        }
    }
}
