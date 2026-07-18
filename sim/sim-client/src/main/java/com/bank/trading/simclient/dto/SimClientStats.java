package com.bank.trading.simclient.dto;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 模拟客户端运行时统计。
 * <p>
 * 记录提交订单数、成功数、失败数、平均延迟等指标，供 REST 接口查询。
 */
@Data
public class SimClientStats {

    /** 已提交订单总数 */
    private AtomicLong submitted = new AtomicLong(0);
    /** 成功订单数（OMS 返回 code=200） */
    private AtomicLong succeeded = new AtomicLong(0);
    /** 失败订单数（HTTP 异常或 code != 200） */
    private AtomicLong failed = new AtomicLong(0);
    /** 累计响应延迟（毫秒），用于计算平均延迟 */
    private AtomicLong totalLatencyMs = new AtomicLong(0);
    /** 当前是否运行中 */
    private volatile boolean running = false;
    /** 当前批次 ID */
    private volatile String batchId;

    /** 平均延迟（毫秒），未提交时为 0 */
    public long getAvgLatencyMs() {
        long count = succeeded.get() + failed.get();
        return count == 0 ? 0 : totalLatencyMs.get() / count;
    }

    /** 重置所有计数器 */
    public void reset() {
        submitted.set(0);
        succeeded.set(0);
        failed.set(0);
        totalLatencyMs.set(0);
        running = false;
        batchId = null;
    }
}
