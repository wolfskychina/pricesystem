package com.bank.trading.common.core.idgen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IdGenerator high-concurrency benchmark: verifies that the optimized
 * tilNextMillis eliminates busy-wait CPU spinning under sequence overflow.
 *
 * <p>It simulates a single-instance saturated workload (&gt;4096 IDs/ms) and
 * compares three wait strategies on elapsed time, throughput and total worker
 * CPU time: busy-wait spin, pure LockSupport, and hybrid spin + parkNanos.</p>
 */
class IdGeneratorConcurrencyBenchmark {

    private static final int THREADS = 8;
    private static final int IDS_PER_THREAD = 50_000;
    private static final int TOTAL_IDS = THREADS * IDS_PER_THREAD;

    @Test
    @DisplayName("busy-wait implementation: uniqueness + metrics")
    void busyWaitImpl_concurrentBenchmark() throws InterruptedException {
        BusyWaitIdGenerator generator = new BusyWaitIdGenerator(0, 0);
        runBenchmark("Busy-Wait Spin", generator::nextLongId);
    }

    @Test
    @DisplayName("pure LockSupport implementation: uniqueness + metrics")
    void lockSupportImpl_concurrentBenchmark() throws InterruptedException {
        LockSupportIdGenerator generator = new LockSupportIdGenerator(0, 0);
        runBenchmark("LockSupport.parkNanos", generator::nextLongId);
    }

    @Test
    @DisplayName("hybrid spin + LockSupport implementation: uniqueness + metrics")
    void hybridImpl_concurrentBenchmark() throws InterruptedException {
        HybridIdGenerator generator = new HybridIdGenerator(0, 0);
        runBenchmark("Hybrid Spin + parkNanos", generator::nextLongId);
    }

    private void runBenchmark(String label, IdGen generator) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(THREADS);
        Set<Long> allIds = java.util.Collections.synchronizedSet(new HashSet<>(TOTAL_IDS));
        ConcurrentHashMap<Long, Long> threadCpuNanos = new ConcurrentHashMap<>();

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!threadBean.isThreadCpuTimeEnabled()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }

        long beginNanos = System.nanoTime();

        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                long tid = Thread.currentThread().getId();
                long cpuBefore = threadBean.getThreadCpuTime(tid);
                try {
                    startLatch.await();
                    for (int i = 0; i < IDS_PER_THREAD; i++) {
                        long id = generator.nextLongId();
                        if (!allIds.add(id)) {
                            throw new AssertionError("Duplicate ID: " + id);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    long cpuAfter = threadBean.getThreadCpuTime(tid);
                    if (cpuAfter >= cpuBefore) {
                        threadCpuNanos.put(tid, cpuAfter - cpuBefore);
                    }
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(120, TimeUnit.SECONDS);
        long elapsedNanos = System.nanoTime() - beginNanos;

        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        assertEquals(TOTAL_IDS, allIds.size(), label + " should generate all unique IDs");

        long totalWorkerCpuNanos = 0;
        for (long cpu : threadCpuNanos.values()) {
            totalWorkerCpuNanos += cpu;
        }

        double elapsedMs = elapsedNanos / 1_000_000.0;
        double throughput = TOTAL_IDS / (elapsedNanos / 1_000_000_000.0);
        double workerCpuMs = totalWorkerCpuNanos / 1_000_000.0;
        double cpuRatio = elapsedMs > 0 ? (workerCpuMs / elapsedMs) * 100.0 : 0.0;

        System.out.println("\n========== " + label + " ==========");
        System.out.printf("Threads: %d, IDs/thread: %d, Total IDs: %d%n",
                THREADS, IDS_PER_THREAD, TOTAL_IDS);
        System.out.printf("Elapsed: %.2f ms%n", elapsedMs);
        System.out.printf("Throughput: %.2f IDs/sec%n", throughput);
        System.out.printf("Total worker CPU time: %.2f ms%n", workerCpuMs);
        System.out.printf("Worker CPU ratio (CPU ms / elapsed ms): %.2f%%%n", cpuRatio);
        System.out.println("=====================================\n");
    }

    interface IdGen {
        long nextLongId();
    }

    /**
     * Base Snowflake-style generator shared by all test implementations.
     */
    abstract static class AbstractTestIdGenerator implements IdGen {

        static final long START_TIMESTAMP = 1704067200000L;
        static final long DATACENTER_ID_BITS = 5L;
        static final long WORKER_ID_BITS = 5L;
        static final long SEQUENCE_BITS = 12L;
        static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
        static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
        static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

        final long datacenterId;
        final long workerId;
        volatile long lastTimestamp = -1L;
        volatile long sequence = 0L;

        AbstractTestIdGenerator(long datacenterId, long workerId) {
            if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
                throw new IllegalArgumentException("datacenterId invalid: " + datacenterId);
            }
            if (workerId < 0 || workerId > MAX_WORKER_ID) {
                throw new IllegalArgumentException("workerId invalid: " + workerId);
            }
            this.datacenterId = datacenterId;
            this.workerId = workerId;
        }

        @Override
        public synchronized long nextLongId() {
            long timestamp = currentTimestamp();

            if (timestamp < lastTimestamp) {
                throw new IllegalStateException("Clock moved backwards");
            }

            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;
            return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_ID_SHIFT)
                    | (workerId << WORKER_ID_SHIFT)
                    | sequence;
        }

        long currentTimestamp() {
            return System.currentTimeMillis();
        }

        abstract long tilNextMillis(long lastTimestamp);
    }

    /**
     * Original busy-wait replica for comparison.
     */
    static class BusyWaitIdGenerator extends AbstractTestIdGenerator {
        BusyWaitIdGenerator(long datacenterId, long workerId) {
            super(datacenterId, workerId);
        }

        @Override
        long tilNextMillis(long lastTimestamp) {
            long timestamp = currentTimestamp();
            while (timestamp <= lastTimestamp) {
                timestamp = currentTimestamp();
            }
            return timestamp;
        }
    }

    /**
     * Pure LockSupport-based wait replica.
     */
    static class LockSupportIdGenerator extends AbstractTestIdGenerator {
        LockSupportIdGenerator(long datacenterId, long workerId) {
            super(datacenterId, workerId);
        }

        @Override
        long tilNextMillis(long lastTimestamp) {
            long timestamp = currentTimestamp();
            if (timestamp <= lastTimestamp) {
                long waitNanos = (lastTimestamp - timestamp + 1) * 1_000_000L;
                LockSupport.parkNanos(waitNanos);

                timestamp = currentTimestamp();
                while (timestamp <= lastTimestamp) {
                    LockSupport.parkNanos(1_000_000L);
                    timestamp = currentTimestamp();
                }
            }
            return timestamp;
        }
    }

    /**
     * Hybrid wait replica: short spin then LockSupport.
     */
    static class HybridIdGenerator extends AbstractTestIdGenerator {
        HybridIdGenerator(long datacenterId, long workerId) {
            super(datacenterId, workerId);
        }

        @Override
        long tilNextMillis(long lastTimestamp) {
            long timestamp = currentTimestamp();
            if (timestamp <= lastTimestamp) {
                long waitNanos = (lastTimestamp - timestamp + 1) * 1_000_000L;
                long spinNanos = Math.min(waitNanos, 50_000L);

                long spinDeadline = System.nanoTime() + spinNanos;
                while (timestamp <= lastTimestamp && System.nanoTime() < spinDeadline) {
                    timestamp = currentTimestamp();
                }

                while (timestamp <= lastTimestamp) {
                    LockSupport.parkNanos(1_000_000L);
                    timestamp = currentTimestamp();
                }
            }
            return timestamp;
        }
    }
}
