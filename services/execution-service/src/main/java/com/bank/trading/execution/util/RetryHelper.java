package com.bank.trading.execution.util;

import java.util.Random;

public class RetryHelper {

    private static final Random RANDOM = new Random();

    private final int maxAttempts;
    private final long initialIntervalMs;
    private final long maxIntervalMs;
    private final double multiplier;
    private final double jitterRatio;

    public RetryHelper(int maxAttempts, long initialIntervalMs, long maxIntervalMs,
                       double multiplier, double jitterRatio) {
        this.maxAttempts = maxAttempts;
        this.initialIntervalMs = initialIntervalMs;
        this.maxIntervalMs = maxIntervalMs;
        this.multiplier = multiplier;
        this.jitterRatio = jitterRatio;
    }

    public long calculateNextRetryDelay(int currentAttempt) {
        if (currentAttempt >= maxAttempts) {
            return -1;
        }

        long delay = (long) (initialIntervalMs * Math.pow(multiplier, currentAttempt));
        delay = Math.min(delay, maxIntervalMs);

        if (jitterRatio > 0) {
            double jitter = delay * jitterRatio;
            delay = (long) (delay - jitter + RANDOM.nextDouble() * 2 * jitter);
        }

        return delay;
    }

    public long calculateNextRetryTime(int currentAttempt) {
        long delay = calculateNextRetryDelay(currentAttempt);
        if (delay < 0) {
            return -1;
        }
        return System.currentTimeMillis() + delay;
    }

    public boolean hasMoreAttempts(int currentAttempt) {
        return currentAttempt < maxAttempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}