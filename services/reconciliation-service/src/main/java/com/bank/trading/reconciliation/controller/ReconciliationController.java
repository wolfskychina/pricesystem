package com.bank.trading.reconciliation.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.reconciliation.dto.ReconciliationResult;
import com.bank.trading.reconciliation.scheduler.ReconciliationScheduler;
import com.bank.trading.reconciliation.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对账 REST 接口。
 * <p>
 * 暴露对账结果查询与手动触发能力：
 * <ul>
 *   <li>{@code GET  /reconciliation/last}    —— 查询最近一次定时对账结果（可能为 null）</li>
 *   <li>{@code POST /reconciliation/trigger} —— 手动触发一次对账并返回结果</li>
 *   <li>{@code GET  /reconciliation/health}  —— 服务存活探针</li>
 * </ul>
 */
@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationScheduler scheduler;
    private final ReconciliationService service;

    public ReconciliationController(ReconciliationScheduler scheduler, ReconciliationService service) {
        this.scheduler = scheduler;
        this.service = service;
    }

    /**
     * 查询最近一次定时对账结果。
     * <p>
     * 服务启动后尚未触发定时任务时返回 data=null。
     */
    @GetMapping("/last")
    public Result<ReconciliationResult> last() {
        ReconciliationResult result = scheduler.getLastResult();
        return Result.success(result);
    }

    /**
     * 手动触发一次对账。
     * <p>
     * 同步执行并返回结果，便于运维主动验证系统一致性。
     * 注意：不会覆盖定时任务缓存（避免手动触发干扰告警判断）。
     */
    @PostMapping("/trigger")
    public Result<ReconciliationResult> trigger() {
        log.info("Manual reconciliation triggered");
        ReconciliationResult result = service.reconcile();
        return Result.success(result);
    }

    /**
     * 服务存活探针。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("component", "reconciliation-service");
        ReconciliationResult last = scheduler.getLastResult();
        if (last != null) {
            data.put("lastBatchId", last.getBatchId());
            data.put("lastConsistent", last.isConsistent());
            data.put("lastFinishedAt", last.getFinishedAt());
        } else {
            data.put("lastBatchId", null);
            data.put("remark", "尚未执行过对账");
        }
        return Result.success(data);
    }
}
