package com.bank.trading.simclient.service;

import com.bank.trading.common.core.dto.OrderCreateDTO;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.simclient.dto.BatchOrderRequest;
import com.bank.trading.simclient.dto.SimClientStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimClientService 单元测试。
 * <p>
 * 使用手写 StubRestTemplate（与 execution-service 测试同款风格），
 * 不依赖 Mockito（Java 25 兼容性问题）。
 */
class SimClientServiceTest {

    private StubRestTemplate stub;
    private SimClientService service;

    @BeforeEach
    void setUp() {
        stub = new StubRestTemplate();
        service = new SimClientService(stub);
        service.setDefaultTargetOrdersUrl("http://stub/orders");
        service.resetStats();
    }

    @Test
    @DisplayName("submitOne 解析 OMS 成功响应，stats.succeeded +1")
    void submitOne_success_incrementsSucceeded() {
        stub.postResponse = "{\"code\":200,\"message\":\"success\",\"data\":{\"orderId\":\"ORD-1\"}}";

        OrderCreateDTO order = newOrder();
        Result<?> result = service.submitOne(order);

        assertTrue(result.isSuccess());
        assertEquals(1, service.getStats().getSubmitted().get());
        assertEquals(1, service.getStats().getSucceeded().get());
        assertEquals(0, service.getStats().getFailed().get());
    }

    @Test
    @DisplayName("submitOne OMS 返回业务错误（code != 200），stats.failed +1")
    void submitOne_businessError_incrementsFailed() {
        stub.postResponse = "{\"code\":400,\"message\":\"risk rejected\",\"data\":null}";

        Result<?> result = service.submitOne(newOrder());

        assertFalse(result.isSuccess());
        assertEquals(400, result.getCode());
        assertEquals(1, service.getStats().getFailed().get());
    }

    @Test
    @DisplayName("submitOne 网络异常，stats.failed +1，不抛出")
    void submitOne_networkError_incrementsFailedNoThrow() {
        stub.throwOnPost = true;

        assertDoesNotThrow(() -> service.submitOne(newOrder()));
        assertEquals(1, service.getStats().getFailed().get());
    }

    @Test
    @DisplayName("submitBatch RANDOM 模式：立即返回 batchId，stats.running=true")
    void submitBatch_random_startsAsync() throws Exception {
        stub.postResponse = "{\"code\":200,\"message\":\"success\",\"data\":{\"orderId\":\"ORD-1\"}}";

        BatchOrderRequest req = new BatchOrderRequest();
        req.setMode("RANDOM");
        req.setCount(5);
        req.setIntervalMs(0);

        String batchId = service.submitBatch(req);
        assertNotNull(batchId);

        // 等待异步完成（最多 2 秒）
        for (int i = 0; i < 200 && service.getStats().isRunning(); i++) {
            Thread.sleep(10);
        }

        SimClientStats s = service.getStats();
        assertEquals(5, s.getSubmitted().get());
        assertEquals(5, s.getSucceeded().get());
        assertEquals(batchId, s.getBatchId());
        assertFalse(s.isRunning(), "异步批次完成后应 running=false");
    }

    @Test
    @DisplayName("reset 清零所有计数器")
    void reset_clearsAllCounters() {
        stub.postResponse = "{\"code\":200,\"message\":\"success\",\"data\":null}";
        service.submitOne(newOrder());
        assertEquals(1, service.getStats().getSubmitted().get());

        service.resetStats();

        assertEquals(0, service.getStats().getSubmitted().get());
        assertNull(service.getStats().getBatchId());
    }

    private OrderCreateDTO newOrder() {
        OrderCreateDTO o = new OrderCreateDTO();
        o.setClientOrderId("CLK-TEST-" + System.nanoTime());
        o.setCustomerId("SIM-CUST-001");
        o.setSymbol("AU2406");
        o.setSide("BUY");
        o.setType("MARKET");
        o.setQty(BigDecimal.TEN);
        return o;
    }

    static class StubRestTemplate extends RestTemplate {
        String postResponse;
        boolean throwOnPost = false;

        @Override
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
            if (throwOnPost) {
                throw new RuntimeException("simulated network error");
            }
            return postResponse == null ? null : responseType.cast(postResponse);
        }

        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
            if (throwOnPost) {
                throw new RuntimeException("simulated network error");
            }
            T body = postResponse == null ? null : responseType.cast(postResponse);
            return new ResponseEntity<>(body, org.springframework.http.HttpStatus.OK);
        }
    }
}
