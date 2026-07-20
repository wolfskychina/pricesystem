package com.bank.trading.execution.service;

import com.bank.trading.execution.exception.HedgeCapacityException;

import java.math.BigDecimal;

public interface HedgeCapacityChecker {

    void checkCapacity(String symbol, BigDecimal qty, String side);

    void quickCheck(String symbol);

    CapacityStatus getCapacityStatus();

    class CapacityStatus {
        private boolean healthy;
        private int retryQueueSize;
        private BigDecimal openExposureQty;
        private boolean exchangeReachable;
        private long lastHealthCheckTime;

        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public int getRetryQueueSize() { return retryQueueSize; }
        public void setRetryQueueSize(int retryQueueSize) { this.retryQueueSize = retryQueueSize; }
        public BigDecimal getOpenExposureQty() { return openExposureQty; }
        public void setOpenExposureQty(BigDecimal openExposureQty) { this.openExposureQty = openExposureQty; }
        public boolean isExchangeReachable() { return exchangeReachable; }
        public void setExchangeReachable(boolean exchangeReachable) { this.exchangeReachable = exchangeReachable; }
        public long getLastHealthCheckTime() { return lastHealthCheckTime; }
        public void setLastHealthCheckTime(long lastHealthCheckTime) { this.lastHealthCheckTime = lastHealthCheckTime; }
    }
}