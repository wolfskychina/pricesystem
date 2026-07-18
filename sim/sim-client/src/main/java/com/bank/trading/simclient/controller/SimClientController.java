package com.bank.trading.simclient.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.simclient.dto.BatchOrderRequest;
import com.bank.trading.simclient.dto.SimClientStats;
import com.bank.trading.simclient.service.SimClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟客户端控制接口。
 * <p>
 * 调用方通过 HTTP 触发批量下单、查询统计、重置计数器。
 * <ul>
 *   <li>POST /sim/submit-batch —— 异步批量下单，立即返回 batchId</li>
 *   <li>POST /sim/submit-sync  —— 同步提交单笔订单（仅测试用）</li>
 *   <li>GET  /sim/stats        —— 查询统计</li>
 *   <li>POST /sim/reset        —— 重置统计</li>
 * </ul>
 */
@RestController
@RequestMapping("/sim")
public class SimClientController {

    private final SimClientService simClientService;

    public SimClientController(SimClientService simClientService) {
        this.simClientService = simClientService;
    }

    @PostMapping("/submit-batch")
    public Result<Map<String, Object>> submitBatch(@RequestBody BatchOrderRequest request) {
        String batchId = simClientService.submitBatch(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchId", batchId);
        data.put("async", true);
        return Result.success(data);
    }

    @PostMapping("/submit-sync")
    public Result<?> submitSync(@RequestBody com.bank.trading.common.core.dto.OrderCreateDTO order) {
        return simClientService.submitOne(order);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        SimClientStats s = simClientService.getStats();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("submitted", s.getSubmitted().get());
        data.put("succeeded", s.getSucceeded().get());
        data.put("failed", s.getFailed().get());
        data.put("avgLatencyMs", s.getAvgLatencyMs());
        data.put("running", s.isRunning());
        data.put("batchId", s.getBatchId());
        return Result.success(data);
    }

    @PostMapping("/reset")
    public Result<Map<String, Object>> reset() {
        simClientService.resetStats();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reset", true);
        return Result.success(data);
    }
}
