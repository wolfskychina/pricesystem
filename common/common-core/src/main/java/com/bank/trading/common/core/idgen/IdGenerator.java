package com.bank.trading.common.core.idgen;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式 ID 生成器（Snowflake 风格）。
 * <p>
 * ID 结构（64 位有符号 long）：
 * <pre>
 * ┌─ 符号位(1) ─┬── 时间戳(41) ──┬─ 数据中心(5) ─┬─ 机器(5) ─┬─ 序列号(12) ─┐
 * │      0      │ 毫秒时间戳偏移  │  datacenterId │  workerId │   sequence   │
 * └─────────────┴────────────────┴───────────────┴───────────┴──────────────┘
 * </pre>
 * <ul>
 *   <li><b>时间戳位</b>：41 bit，精确到毫秒，可用约 69 年（相对于基准时间）</li>
 *   <li><b>数据中心位</b>：5 bit，支持 0-31（按机房分配）</li>
 *   <li><b>机器位</b>：5 bit，支持 0-31（按实例分配）</li>
 *   <li><b>序列号位</b>：12 bit，支持 0-4095（同一毫秒内自增）</li>
 * </ul>
 * <p>
 * 特性：
 * <ul>
 *   <li>纯内存计算，无数据库依赖，高性能（单实例约 409.6 万/秒）</li>
 *   <li>ID 有序递增（按时间），利于数据库索引（B+Tree 顺序写入友好）</li>
 *   <li>天然支持分布式：datacenterId + workerId 保证跨实例唯一</li>
 *   <li>long 型可直接取模用于分片路由：{@code id % totalShards}</li>
 * </ul>
 * <p>
 * <b>时钟回拨处理</b>：检测到系统时钟回拨时抛出异常，由上层决定重试或降级策略。
 * 生产环境建议配合 NTP 同步监控，避免时钟回拨超过 5ms 的极端场景。
 */
@Slf4j
public class IdGenerator {

    /**
     * 基准时间戳：2024-01-01 00:00:00 UTC（毫秒）。
     * 41 bit 时间戳可覆盖到 2093 年。
     */
    private static final long START_TIMESTAMP = 1704067200000L;

    private static final long DATACENTER_ID_BITS = 5L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    /** 数据中心最大编号：2^5 - 1 = 31 */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    /** 机器最大编号：2^5 - 1 = 31 */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    /** 序列号最大值：2^12 - 1 = 4095 */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /** 机器 ID 左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 数据中心 ID 左移位数 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 时间戳左移位数 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private final long datacenterId;
    private final long workerId;

    /** 上一次生成 ID 的时间戳 */
    private volatile long lastTimestamp = -1L;
    /** 同一毫秒内的序列号 */
    private volatile long sequence = 0L;

    /**
     * 构造发号器。
     *
     * @param datacenterId 数据中心 ID（0-31），按部署机房分配
     * @param workerId     机器/实例 ID（0-31），同一数据中心内不重复
     */
    public IdGenerator(long datacenterId, long workerId) {
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException(
                    "datacenterId must be between 0 and " + MAX_DATACENTER_ID + ", got: " + datacenterId);
        }
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    "workerId must be between 0 and " + MAX_WORKER_ID + ", got: " + workerId);
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * 生成下一个 64 位 long 型 ID。
     * <p>
     * 线程安全（synchronized），并发场景下性能足够（QPS > 400 万/秒）。
     *
     * @return 全局唯一的有序 long 型 ID
     */
    public synchronized long nextLongId() {
        long timestamp = currentTimestamp();

        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            log.error("Clock moved backwards. Refusing to generate id for {} ms. "
                    + "datacenterId={}, workerId={}", offset, datacenterId, workerId);
            throw new IllegalStateException(
                    "Clock moved backwards by " + offset + " ms. Cannot generate id.");
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内，序列号自增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号溢出，等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置为 0
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成带前缀的字符串型业务 ID。
     * <p>
     * 例如：{@code nextId("ORD")} → "ORD-4611686018427387904"
     *
     * @param prefix 业务前缀（如 ORD / TRD / EVT）
     * @return 前缀 + "-" + long ID
     */
    public String nextId(String prefix) {
        return prefix + "-" + nextLongId();
    }

    /**
     * 获取当前时间戳（毫秒）。
     */
    private long currentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 阻塞等待直到下一毫秒。
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    /**
     * 解析 ID 的组成部分（用于调试/监控）。
     *
     * @param id 生成的 long 型 ID
     * @return ID 各段信息
     */
    public static IdComponents parse(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
        long dcId = (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
        long wkId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long seq = id & MAX_SEQUENCE;
        return new IdComponents(timestamp, dcId, wkId, seq);
    }

    /**
     * ID 组成部分（调试用）。
     */
    public record IdComponents(long timestamp, long datacenterId, long workerId, long sequence) {
        @Override
        public String toString() {
            return String.format("IdComponents{timestamp=%d, datacenterId=%d, workerId=%d, sequence=%d}",
                    timestamp, datacenterId, workerId, sequence);
        }
    }
}
