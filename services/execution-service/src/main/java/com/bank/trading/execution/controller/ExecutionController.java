package com.bank.trading.execution.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.execution.entity.HedgeFailureExposure;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.entity.HedgeTrade;
import com.bank.trading.execution.exception.HedgeCapacityException;
import com.bank.trading.execution.mapper.HedgeDlqMapper;
import com.bank.trading.execution.mapper.HedgeFailureExposureMapper;
import com.bank.trading.execution.mapper.HedgeOrderMapper;
import com.bank.trading.execution.service.DefaultHedgeCapacityChecker;
import com.bank.trading.execution.service.ExecutionService;
import com.bank.trading.execution.service.HedgeCapacityChecker;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/execution")
public class ExecutionController {

    private final ExecutionService executionService;
    private final HedgeOrderMapper hedgeOrderMapper;
    private final HedgeFailureExposureMapper exposureMapper;
    private final HedgeDlqMapper dlqMapper;
    private final HedgeCapacityChecker capacityChecker;

    public ExecutionController(ExecutionService executionService,
                              HedgeOrderMapper hedgeOrderMapper,
                              HedgeFailureExposureMapper exposureMapper,
                              HedgeDlqMapper dlqMapper,
                              HedgeCapacityChecker capacityChecker) {
        this.executionService = executionService;
        this.hedgeOrderMapper = hedgeOrderMapper;
        this.exposureMapper = exposureMapper;
        this.dlqMapper = dlqMapper;
        this.capacityChecker = capacityChecker;
    }

    @GetMapping("/orders")
    public Result<List<HedgeOrder>> listHedgeOrders(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(executionService.findRecentHedgeOrders(limit));
    }

    @GetMapping("/orders/{hedgeOrderId}/trades")
    public Result<List<HedgeTrade>> listHedgeTrades(@PathVariable String hedgeOrderId) {
        return Result.success(executionService.findHedgeTrades(hedgeOrderId));
    }

    @PostMapping("/hedge-capacity/check")
    public Result<Void> checkHedgeCapacity(@RequestParam String symbol,
                                           @RequestParam BigDecimal qty,
                                           @RequestParam String side) {
        try {
            capacityChecker.checkCapacity(symbol, qty, side);
            return Result.success();
        } catch (HedgeCapacityException e) {
            Map<String, Object> data = new HashMap<>();
            data.put("reason", e.getReason());
            data.put("symbol", e.getSymbol());
            data.put("suggestedRetryAfter", e.getSuggestedRetryAfter());
            return Result.fail(409, "HEDGE_CAPACITY_INSUFFICIENT", data);
        }
    }

    @GetMapping("/hedge-capacity/status")
    public Result<HedgeCapacityChecker.CapacityStatus> getCapacityStatus() {
        return Result.success(capacityChecker.getCapacityStatus());
    }

    @GetMapping("/hedge-failures")
    public Result<List<HedgeFailureExposure>> listHedgeFailures() {
        return Result.success(exposureMapper.findByStatus("PENDING"));
    }

    @GetMapping("/hedge-failures/summary")
    public Result<List<HedgeFailureExposureMapper.HedgeFailureExposureSummary>> getFailureSummary() {
        return Result.success(exposureMapper.getSummaryBySymbol());
    }

    @PostMapping("/orders/{hedgeOrderId}/retry")
    public Result<Void> retryHedgeOrder(@PathVariable String hedgeOrderId) {
        HedgeOrder order = hedgeOrderMapper.findByHedgeOrderId(hedgeOrderId);
        if (order == null) {
            return Result.fail(404, "Hedge order not found");
        }
        executionService.retryHedgeOrder(order);
        return Result.success();
    }

    @GetMapping("/hedge-metrics")
    public Result<Map<String, Object>> getHedgeMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("retryingCount", hedgeOrderMapper.countByStatus("RETRYING"));
        metrics.put("pendingExposureQty", exposureMapper.sumPendingQty());
        metrics.put("pendingExposureAmount", exposureMapper.sumExposureAmount());
        metrics.put("dlqSize", dlqMapper.countPending());
        return Result.success(metrics);
    }
}