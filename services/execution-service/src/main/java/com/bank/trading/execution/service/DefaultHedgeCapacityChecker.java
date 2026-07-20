package com.bank.trading.execution.service;

import com.bank.trading.execution.exception.HedgeCapacityException;
import com.bank.trading.execution.mapper.HedgeFailureExposureMapper;
import com.bank.trading.execution.mapper.HedgeOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DefaultHedgeCapacityChecker implements HedgeCapacityChecker {

    private static final Logger log = LoggerFactory.getLogger(DefaultHedgeCapacityChecker.class);

    private final HedgeOrderMapper hedgeOrderMapper;
    private final HedgeFailureExposureMapper exposureMapper;
    private final RestTemplate restTemplate;

    @Value("${execution.exchange-base-url:http://localhost:8081}")
    private String exchangeBaseUrl;

    @Value("${execution.hedge.capacity-check.enabled:true}")
    private boolean capacityCheckEnabled;

    @Value("${execution.hedge.capacity-check.max-retry-queue-size:1000}")
    private int maxRetryQueueSize;

    @Value("${execution.hedge.capacity-check.max-allowed-exposure-qty:100}")
    private BigDecimal maxAllowedExposureQty;

    @Value("${execution.hedge.capacity-check.exchange-health-timeout-ms:5000}")
    private int exchangeHealthTimeoutMs;

    @Value("${execution.hedge.capacity-check.consecutive-fail-threshold:3}")
    private int consecutiveFailThreshold;

    private final AtomicBoolean exchangeReachable = new AtomicBoolean(true);
    private final AtomicInteger consecutiveFailCount = new AtomicInteger(0);
    private final AtomicLong lastHealthCheckTime = new AtomicLong(System.currentTimeMillis());
    private volatile BigDecimal openExposureQty = BigDecimal.ZERO;
    private volatile int retryQueueSize = 0;

    public DefaultHedgeCapacityChecker(HedgeOrderMapper hedgeOrderMapper,
                                       HedgeFailureExposureMapper exposureMapper,
                                       RestTemplate restTemplate) {
        this.hedgeOrderMapper = hedgeOrderMapper;
        this.exposureMapper = exposureMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public void checkCapacity(String symbol, BigDecimal qty, String side) {
        if (!capacityCheckEnabled) {
            return;
        }

        quickCheck(symbol);

        checkRetryQueue();
        checkOpenExposure(qty);
    }

    @Override
    public void quickCheck(String symbol) {
        if (!capacityCheckEnabled) {
            return;
        }

        checkExchangeHealth();
        checkContractStatus(symbol);
    }

    private void checkExchangeHealth() {
        if (!exchangeReachable.get()) {
            throw new HedgeCapacityException("Exchange is unreachable",
                    "EXCHANGE_UNREACHABLE", "ALL");
        }
    }

    private void checkContractStatus(String symbol) {
    }

    private void checkRetryQueue() {
        if (retryQueueSize > maxRetryQueueSize) {
            throw new HedgeCapacityException("Retry queue is full",
                    "RETRY_QUEUE_FULL", "ALL", 60000);
        }
    }

    private void checkOpenExposure(BigDecimal additionalQty) {
        BigDecimal totalExposure = openExposureQty.add(additionalQty);
        if (totalExposure.compareTo(maxAllowedExposureQty) > 0) {
            throw new HedgeCapacityException("Open exposure exceeds threshold",
                    "EXPOSURE_EXCEEDED", "ALL", 30000);
        }
    }

    @Override
    public CapacityStatus getCapacityStatus() {
        CapacityStatus status = new CapacityStatus();
        status.setHealthy(exchangeReachable.get() && retryQueueSize <= maxRetryQueueSize);
        status.setRetryQueueSize(retryQueueSize);
        status.setOpenExposureQty(openExposureQty);
        status.setExchangeReachable(exchangeReachable.get());
        status.setLastHealthCheckTime(lastHealthCheckTime.get());
        return status;
    }

    @Scheduled(fixedDelayString = "${execution.hedge.capacity-check.health-check-interval-ms:10000}")
    public void performHealthCheck() {
        try {
            String healthUrl = exchangeBaseUrl + "/exchange/health";
            restTemplate.getForObject(healthUrl, String.class);
            exchangeReachable.set(true);
            consecutiveFailCount.set(0);
            lastHealthCheckTime.set(System.currentTimeMillis());
            log.debug("Exchange health check passed");
        } catch (Exception e) {
            int failCount = consecutiveFailCount.incrementAndGet();
            log.warn("Exchange health check failed ({} consecutive): {}", failCount, e.getMessage());
            if (failCount >= consecutiveFailThreshold) {
                exchangeReachable.set(false);
                log.error("Exchange marked as unreachable after {} consecutive failures", consecutiveFailCount);
            }
        }

        refreshMetrics();
    }

    private void refreshMetrics() {
        try {
            retryQueueSize = hedgeOrderMapper.countByStatus("RETRYING");
            BigDecimal exposure = exposureMapper.sumPendingQty();
            openExposureQty = exposure != null ? exposure : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to refresh capacity metrics", e);
        }
    }

    public void refreshMetricsManual() {
        refreshMetrics();
    }
}