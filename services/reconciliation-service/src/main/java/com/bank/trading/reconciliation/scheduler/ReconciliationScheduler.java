package com.bank.trading.reconciliation.scheduler;

import com.bank.trading.reconciliation.dto.ReconciliationResult;
import com.bank.trading.reconciliation.service.ReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 对账定时任务。
 * <p>
 * 默认每 5 分钟执行一次。可通过 {@code reconciliation.cron} 配置覆盖。
 * 上一次对账结果缓存在内存中，供 REST 接口查询。
 */
@Slf4j
@Component
public class ReconciliationScheduler {

    private final ReconciliationService service;
    private final AtomicReference<ReconciliationResult> lastResult = new AtomicReference<>();

    public ReconciliationScheduler(ReconciliationService service) {
        this.service = service;
    }

    /**
     * 定时对账任务。默认 cron 表达式为 "0 每5分钟一次"，即每 5 分钟整点执行。
     */
    @Scheduled(cron = "${reconciliation.cron:0 */5 * * * *}")
    public void scheduledReconcile() {
        try {
            ReconciliationResult result = service.reconcile();
            lastResult.set(result);
            if (result.isConsistent()) {
                log.info("Scheduled reconciliation PASS: batchId={}", result.getBatchId());
            } else {
                log.warn("Scheduled reconciliation FAIL: batchId={}, discrepancies={}",
                        result.getBatchId(), result.getDiscrepancies().size());
            }
        } catch (Exception e) {
            log.error("Scheduled reconciliation error: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取最近一次对账结果。
     */
    public ReconciliationResult getLastResult() {
        return lastResult.get();
    }
}
