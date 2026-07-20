package com.bank.trading.execution.service;

import com.bank.trading.execution.entity.HedgeDlq;
import com.bank.trading.execution.entity.HedgeFailureExposure;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.mapper.HedgeDlqMapper;
import com.bank.trading.execution.mapper.HedgeFailureExposureMapper;
import com.bank.trading.execution.mapper.HedgeOrderMapper;
import com.bank.trading.execution.util.RetryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class HedgeRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HedgeRecoveryScheduler.class);

    private final HedgeOrderMapper hedgeOrderMapper;
    private final HedgeFailureExposureMapper exposureMapper;
    private final HedgeDlqMapper dlqMapper;
    private final ExecutionService executionService;
    private final RetryHelper retryHelper;

    @Value("${execution.hedge.auto-unwind.enabled:true}")
    private boolean autoUnwindEnabled;

    @Value("${execution.hedge.auto-unwind.threshold-qty:100}")
    private BigDecimal autoUnwindThresholdQty;

    @Value("${execution.hedge.auto-unwind.threshold-amount:1000000}")
    private BigDecimal autoUnwindThresholdAmount;

    @Value("${execution.hedge.auto-unwind.timeout-ms:120000}")
    private long autoUnwindTimeoutMs;

    public HedgeRecoveryScheduler(HedgeOrderMapper hedgeOrderMapper,
                                  HedgeFailureExposureMapper exposureMapper,
                                  HedgeDlqMapper dlqMapper,
                                  ExecutionService executionService,
                                  RetryHelper retryHelper) {
        this.hedgeOrderMapper = hedgeOrderMapper;
        this.exposureMapper = exposureMapper;
        this.dlqMapper = dlqMapper;
        this.executionService = executionService;
        this.retryHelper = retryHelper;
    }

    @Scheduled(fixedDelay = 10000)
    public void processRecovery() {
        try {
            processRetryOrders();
            checkAndTriggerAutoUnwind();
            processDlqRecovery();
        } catch (Exception e) {
            log.error("Hedge recovery scheduler error", e);
        }
    }

    @Transactional
    public void processRetryOrders() {
        long now = System.currentTimeMillis();
        List<HedgeOrder> readyOrders = hedgeOrderMapper.findReadyForRetry(now);

        for (HedgeOrder order : readyOrders) {
            try {
                if (retryHelper.hasMoreAttempts(order.getRetryCount())) {
                    log.info("Retrying hedge order: hedgeOrderId={}, attempt={}",
                            order.getHedgeOrderId(), order.getRetryCount() + 1);
                    executionService.retryHedgeOrder(order);
                } else {
                    log.warn("Max retries exceeded, moving to DLQ: hedgeOrderId={}",
                            order.getHedgeOrderId());
                    moveToDlq(order);
                }
            } catch (Exception e) {
                log.error("Failed to process retry order: hedgeOrderId={}, error={}",
                        order.getHedgeOrderId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void checkAndTriggerAutoUnwind() {
        if (!autoUnwindEnabled) {
            return;
        }

        BigDecimal totalExposureQty = exposureMapper.sumPendingQty();
        BigDecimal totalExposureAmount = exposureMapper.sumExposureAmount();

        if (totalExposureQty != null && totalExposureQty.compareTo(autoUnwindThresholdQty) > 0) {
            log.warn("Auto-unwind triggered: exposure qty {} exceeds threshold {}",
                    totalExposureQty, autoUnwindThresholdQty);
            triggerAutoUnwind();
        }

        if (totalExposureAmount != null && totalExposureAmount.compareTo(autoUnwindThresholdAmount) > 0) {
            log.warn("Auto-unwind triggered: exposure amount {} exceeds threshold {}",
                    totalExposureAmount, autoUnwindThresholdAmount);
            triggerAutoUnwind();
        }

        checkTimeoutExposure();
    }

    private void checkTimeoutExposure() {
        List<HedgeFailureExposure> exposures = exposureMapper.findByStatus("PENDING");
        long now = System.currentTimeMillis();

        for (HedgeFailureExposure exposure : exposures) {
            if (now - exposure.getCreatedAt() > autoUnwindTimeoutMs) {
                log.warn("Exposure timeout, triggering auto-unwind: tradeId={}, age={}ms",
                        exposure.getOriginalTradeId(), now - exposure.getCreatedAt());
                triggerAutoUnwindForExposure(exposure);
            }
        }
    }

    private void triggerAutoUnwind() {
    }

    private void triggerAutoUnwindForExposure(HedgeFailureExposure exposure) {
        exposure.setStatus("EMERGENCY_CLOSED");
        exposure.setResolvedAt(System.currentTimeMillis());
        exposureMapper.update(exposure);

        HedgeOrder hedgeOrder = hedgeOrderMapper.findByHedgeOrderId(exposure.getHedgeOrderId());
        if (hedgeOrder != null) {
            hedgeOrder.setStatus("EMERGENCY_HEDGED");
            hedgeOrder.setUpdatedAt(System.currentTimeMillis());
            hedgeOrderMapper.updateByExchangeOrderId(hedgeOrder);
        }

        log.error("Emergency unwind executed for exposure: tradeId={}, qty={}",
                exposure.getOriginalTradeId(), exposure.getPendingQty());
    }

    @Transactional
    public void processDlqRecovery() {
        List<HedgeDlq> pendingDlq = dlqMapper.findByStatus("PENDING");

        for (HedgeDlq dlq : pendingDlq) {
            if (dlq.getRetryCount() >= dlq.getMaxRetryCount()) {
                dlq.setStatus("FAILED");
                dlqMapper.update(dlq);
                log.error("DLQ item permanently failed: hedgeOrderId={}, reason={}",
                        dlq.getHedgeOrderId(), dlq.getReason());
                continue;
            }

            try {
                HedgeOrder order = hedgeOrderMapper.findByHedgeOrderId(dlq.getHedgeOrderId());
                if (order != null) {
                    executionService.retryHedgeOrder(order);
                    dlq.setRetryCount(dlq.getRetryCount() + 1);
                    dlq.setStatus("RECOVERED");
                    dlq.setRecoveredAt(System.currentTimeMillis());
                    dlqMapper.update(dlq);
                    log.info("DLQ item recovered: hedgeOrderId={}", dlq.getHedgeOrderId());
                }
            } catch (Exception e) {
                dlq.setRetryCount(dlq.getRetryCount() + 1);
                dlqMapper.update(dlq);
                log.warn("DLQ recovery failed: hedgeOrderId={}, attempt={}, error={}",
                        dlq.getHedgeOrderId(), dlq.getRetryCount(), e.getMessage());
            }
        }
    }

    @Transactional
    public void moveToDlq(HedgeOrder order) {
        HedgeDlq dlq = new HedgeDlq();
        dlq.setId(System.currentTimeMillis());
        dlq.setHedgeOrderId(order.getHedgeOrderId());
        dlq.setOriginalTradeId(order.getOriginalTradeId());
        dlq.setCustomerId(order.getCustomerId());
        dlq.setSymbol(order.getSymbol());
        dlq.setSide(order.getSide());
        dlq.setQty(order.getQty());
        dlq.setReason(order.getFailureReason());
        dlq.setRetryCount(0);
        dlq.setMaxRetryCount(3);
        dlq.setStatus("PENDING");
        dlq.setCreatedAt(System.currentTimeMillis());
        dlqMapper.insert(dlq);

        order.setStatus("FAILED");
        order.setUpdatedAt(System.currentTimeMillis());
        hedgeOrderMapper.updateByExchangeOrderId(order);

        log.warn("Hedge order moved to DLQ: hedgeOrderId={}, reason={}",
                order.getHedgeOrderId(), order.getFailureReason());
    }
}