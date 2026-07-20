package com.bank.trading.common.core.idgen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdGenerator 单元测试。
 */
class IdGeneratorTest {

    @Test
    @DisplayName("生成10000个ID，验证全局唯一性")
    void uniqueness_largeBatch() {
        IdGenerator generator = new IdGenerator(0, 0);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            long id = generator.nextLongId();
            assertTrue(ids.add(id), "ID 应全局唯一，发现重复: " + id);
        }
        assertEquals(10000, ids.size());
    }

    @Test
    @DisplayName("并发生成ID，验证唯一性（4线程 × 2500个）")
    void uniqueness_concurrent() throws InterruptedException {
        IdGenerator generator = new IdGenerator(0, 0);
        int threads = 4;
        int countPerThread = 2500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        Set<Long> allIds = java.util.Collections.synchronizedSet(new HashSet<>());

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < countPerThread; i++) {
                        long id = generator.nextLongId();
                        assertTrue(allIds.add(id), "并发 ID 应全局唯一");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(threads * countPerThread, allIds.size());
    }

    @Test
    @DisplayName("不同 workerId 生成 ID，验证不冲突")
    void differentWorkerIds_noConflict() {
        IdGenerator g1 = new IdGenerator(0, 1);
        IdGenerator g2 = new IdGenerator(0, 2);

        Set<Long> ids1 = new HashSet<>();
        Set<Long> ids2 = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids1.add(g1.nextLongId());
            ids2.add(g2.nextLongId());
        }

        ids1.retainAll(ids2);
        assertTrue(ids1.isEmpty(), "不同 workerId 生成的 ID 不应冲突");
    }

    @Test
    @DisplayName("ID 按时间有序递增")
    void ids_areMonotonicallyIncreasing() {
        IdGenerator generator = new IdGenerator(0, 0);
        long prev = generator.nextLongId();
        for (int i = 0; i < 100; i++) {
            long curr = generator.nextLongId();
            assertTrue(curr > prev, "ID 应单调递增: prev=" + prev + ", curr=" + curr);
            prev = curr;
        }
    }

    @Test
    @DisplayName("带前缀字符串 ID 格式正确")
    void nextId_withPrefix_formatCorrect() {
        IdGenerator generator = new IdGenerator(0, 0);
        String id = generator.nextId("ORD");
        assertTrue(id.startsWith("ORD-"), "前缀应为 ORD-: " + id);
        String numberPart = id.substring(4);
        assertDoesNotThrow(() -> Long.parseLong(numberPart), "后半部分应为数字: " + id);
    }

    @Test
    @DisplayName("非法 datacenterId 抛出异常")
    void invalidDatacenterId_throws() {
        assertThrows(IllegalArgumentException.class, () -> new IdGenerator(32, 0));
        assertThrows(IllegalArgumentException.class, () -> new IdGenerator(-1, 0));
    }

    @Test
    @DisplayName("非法 workerId 抛出异常")
    void invalidWorkerId_throws() {
        assertThrows(IllegalArgumentException.class, () -> new IdGenerator(0, 32));
        assertThrows(IllegalArgumentException.class, () -> new IdGenerator(0, -1));
    }

    @Test
    @DisplayName("解析 ID 各组成部分")
    void parseId_componentsCorrect() {
        IdGenerator generator = new IdGenerator(1, 2);
        long id = generator.nextLongId();
        IdGenerator.IdComponents comp = IdGenerator.parse(id);

        assertEquals(1, comp.datacenterId());
        assertEquals(2, comp.workerId());
        assertTrue(comp.timestamp() > 0);
        assertTrue(comp.sequence() >= 0);
    }
}
