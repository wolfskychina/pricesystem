package com.bank.trading.simclient.service;

import com.bank.trading.common.core.dto.OrderCreateDTO;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.simclient.dto.BatchOrderRequest;
import com.bank.trading.simclient.dto.SimClientStats;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模拟客户端核心服务：构造订单 + 异步提交 + 统计。
 * <p>
 * 单例，stats 全局共享。批量提交通过固定线程池异步进行，避免阻塞 HTTP 调用线程。
 */
@Slf4j
@Service
public class SimClientService {

    private static final List<String> DEFAULT_CUSTOMERS =
            Arrays.asList("SIM-CUST-001", "SIM-CUST-002", "SIM-CUST-003", "SIM-CUST-004", "SIM-CUST-005");
    private static final List<String> DEFAULT_SYMBOLS =
            Arrays.asList("AU2406", "AG2406", "CU2406", "RB2406");

    private final RestTemplate restTemplate;
    private final SimClientStats stats = new SimClientStats();
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final Random random = new Random();
    private final AtomicLong clientOrderSeq = new AtomicLong(0);

    @Value("${sim-client.target-orders-url:http://localhost:8080/api/orders}")
    private String defaultTargetOrdersUrl;

    public SimClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 异步批量提交订单。
     * <p>
     * 立即返回 batchId，实际提交在后台进行，可通过 /sim/stats 查看进度。
     *
     * @param request 批量下单请求
     * @return 批次 ID
     */
    public String submitBatch(BatchOrderRequest request) {
        String batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8);
        String targetUrl = request.getTargetOrdersUrl() != null && !request.getTargetOrdersUrl().isBlank()
                ? request.getTargetOrdersUrl() : defaultTargetOrdersUrl;

        List<OrderCreateDTO> orders = "FIXED".equalsIgnoreCase(request.getMode())
                ? request.getOrders() : generateRandomOrders(request);

        stats.setRunning(true);
        stats.setBatchId(batchId);

        CompletableFuture.runAsync(() -> {
            try {
                for (OrderCreateDTO order : orders) {
                    submitOne(targetUrl, order);
                    if (request.getIntervalMs() > 0) {
                        Thread.sleep(request.getIntervalMs());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch {} interrupted", batchId);
            } finally {
                stats.setRunning(false);
                log.info("Batch {} finished: submitted={}, succeeded={}, failed={}",
                        batchId, stats.getSubmitted(), stats.getSucceeded(), stats.getFailed());
            }
        }, executor);

        log.info("Batch {} started: {} orders to {}", batchId, orders.size(), targetUrl);
        return batchId;
    }

    /** 同步提交单笔订单，主要用于测试。 */
    public Result<?> submitOne(OrderCreateDTO order) {
        return submitOne(defaultTargetOrdersUrl, order);
    }

    private Result<?> submitOne(String targetUrl, OrderCreateDTO order) {
        stats.getSubmitted().incrementAndGet();
        long start = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<OrderCreateDTO> entity = new HttpEntity<>(order, headers);
            String response = restTemplate.postForObject(targetUrl, entity, String.class);
            long elapsed = System.currentTimeMillis() - start;
            stats.getTotalLatencyMs().addAndGet(elapsed);

            if (response != null) {
                JSONObject json = JSON.parseObject(response);
                int code = json.getIntValue("code");
                if (code == 200) {
                    stats.getSucceeded().incrementAndGet();
                    return Result.success(json.get("data"));
                } else {
                    stats.getFailed().incrementAndGet();
                    return Result.fail(code, json.getString("message"));
                }
            }
            stats.getFailed().incrementAndGet();
            return Result.fail("empty response");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            stats.getTotalLatencyMs().addAndGet(elapsed);
            stats.getFailed().incrementAndGet();
            log.warn("Submit order failed: clientOrderId={}, error={}", order.getClientOrderId(), e.getMessage());
            return Result.fail("submit failed: " + e.getMessage());
        }
    }

    private List<OrderCreateDTO> generateRandomOrders(BatchOrderRequest req) {
        List<String> customers = req.getCustomerIds() == null || req.getCustomerIds().isEmpty()
                ? DEFAULT_CUSTOMERS : req.getCustomerIds();
        List<String> symbols = req.getSymbols() == null || req.getSymbols().isEmpty()
                ? DEFAULT_SYMBOLS : req.getSymbols();
        int count = Math.max(1, req.getCount());
        OrderCreateDTO[] arr = new OrderCreateDTO[count];
        for (int i = 0; i < count; i++) {
            OrderCreateDTO o = new OrderCreateDTO();
            o.setClientOrderId("SIM-CLK-" + clientOrderSeq.incrementAndGet());
            o.setCustomerId(customers.get(random.nextInt(customers.size())));
            o.setSymbol(symbols.get(random.nextInt(symbols.size())));
            o.setSide(random.nextBoolean() ? "BUY" : "SELL");
            o.setType("MARKET");
            int maxQty = Math.max(1, req.getMaxQty());
            o.setQty(BigDecimal.valueOf(1 + random.nextInt(maxQty)));
            arr[i] = o;
        }
        return Arrays.asList(arr);
    }

    public SimClientStats getStats() {
        return stats;
    }

    public void resetStats() {
        stats.reset();
    }

    /** 测试可见：让单元测试可注入默认 URL */
    public void setDefaultTargetOrdersUrl(String url) {
        this.defaultTargetOrdersUrl = url;
    }
}
