package com.bank.trading.execution.service;

import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.common.core.idgen.IdGenerator;
import com.bank.trading.common.core.trace.TraceContext;
import com.bank.trading.execution.entity.HedgeBatchItem;
import com.bank.trading.execution.mapper.HedgeBatchItemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对冲聚合器（Hedge Batcher）— 支持净额对冲（Net Exposure Netting）。
 * <p>
 * 将短时间内同合约的多笔客户成交聚合到同一桶中，出桶时对 BUY/SELL 反向成交进行
 * 净额相消，仅对净敞口提交一笔对冲单到交易所，最大限度减少订单数量、手续费与市场冲击。
 * <p>
 * <b>聚合粒度</b>：按 {@code symbol} 分组（同一合约的 BUY 和 SELL 对冲方向合并到同一桶）。
 * <p>
 * <b>净额计算</b>：出桶时计算 netQty = sum(BUY qty) - sum(SELL qty)
 * <ul>
 *   <li>netQty > 0：提交一笔 BUY 对冲单，数量 = netQty</li>
 *   <li>netQty < 0：提交一笔 SELL 对冲单，数量 = |netQty|</li>
 *   <li>netQty = 0：完全内部相消，不提交交易所订单，子项标记为 INTERNALLY_NETTED</li>
 * </ul>
 * <p>
 * <b>双触发机制</b>：
 * <ul>
 *   <li><b>时间触发</b>：每 {@code batching-window-ms} 毫秒定时出桶（默认 1000ms，开发环境）</li>
 *   <li><b>净敞口阈值触发</b>：桶内 |净敞口| ≥ {@code batching-size-threshold} 立即出桶（默认 50 手）</li>
 * </ul>
 * 任一条件满足即触发出桶，交由 {@link ExecutionService#submitBatchedOrder} 提交交易所。
 * <p>
 * <b>幂等保障</b>：同一 {@code originalTradeId} 只入桶一次（通过
 * {@link HedgeBatchItemMapper#findByOriginalTradeId} 做幂等校验）。
 */
@Component
public class HedgeBatcher {

    private static final Logger log = LoggerFactory.getLogger(HedgeBatcher.class);

    private final HedgeBatchItemMapper batchItemMapper;
    private final ExecutionService executionService;
    private final IdGenerator idGenerator;

    /** 聚合开关：true=开启聚合，false=每笔成交独立对冲（兼容旧行为） */
    @Value("${execution.batching-enabled:false}")
    private boolean batchingEnabled;

    /** 聚合时间窗口（毫秒），开发环境默认 1000ms */
    @Value("${execution.batching-window-ms:1000}")
    private long batchingWindowMs;

    /** 净敞口阈值（手），桶内 |净敞口| 超此值立即出桶 */
    @Value("${execution.batching-size-threshold:50}")
    private BigDecimal sizeThreshold;

    /** 对冲比例 */
    @Value("${execution.hedge-ratio:1.0}")
    private BigDecimal hedgeRatio;

    /**
     * 内存聚合桶。
     * key = symbol（合约代码，BUY 和 SELL 方向合并到同一桶）
     * value = 该桶内的待出桶子项列表（包含 BUY 和 SELL 两个方向）
     */
    private final ConcurrentHashMap<String, List<HedgeBatchItem>> buckets = new ConcurrentHashMap<>();

    public HedgeBatcher(HedgeBatchItemMapper batchItemMapper, ExecutionService executionService,
                        IdGenerator idGenerator) {
        this.batchItemMapper = batchItemMapper;
        this.executionService = executionService;
        this.idGenerator = idGenerator;
    }

    /**
     * 将一笔客户成交加入聚合桶。
     * <p>
     * 若聚合未开启，直接调用 {@link ExecutionService#onTradeEventImmediate} 单笔对冲。
     * 若已开启，入桶后检查净敞口阈值，达到则立即出桶。
     *
     * @param event 客户成交事件
     * @return true=入桶成功（或单笔对冲成功），false=已存在（幂等跳过）
     */
    public boolean enqueue(TradeEvent event) {
        if (!batchingEnabled) {
            executionService.onTradeEventImmediate(event);
            return true;
        }

        // 幂等校验：同一 originalTradeId 只入桶一次
        HedgeBatchItem existing = batchItemMapper.findByOriginalTradeId(event.getTradeId());
        if (existing != null) {
            log.debug("Trade already in batcher, skip: tradeId={}", event.getTradeId());
            return false;
        }

        // 对冲方向 = 客户成交方向（客户 BUY → 做市商空头 → 对冲 BUY 平掉空头）
        String hedgeSide = calculateHedgeSide(event.getSide());
        BigDecimal hedgeQty = event.getQty().multiply(hedgeRatio).setScale(4, RoundingMode.HALF_UP);

        // 构造子项并持久化（状态=PENDING，netted=0）
        HedgeBatchItem item = new HedgeBatchItem();
        item.setId(idGenerator.nextLongId());
        item.setOriginalTradeId(event.getTradeId());
        item.setCustomerId(event.getCustomerId());
        item.setSymbol(event.getSymbol());
        item.setSide(hedgeSide);
        item.setQty(hedgeQty);
        item.setStatus("PENDING");
        item.setFilledQty(BigDecimal.ZERO);
        item.setAvgPrice(BigDecimal.ZERO);
        item.setNetted(0);
        // 入桶时从 MDC 写入 traceId（关联原始客户请求）。
        // 出桶发 hedge-fill-event 时取值赋给 event，保证聚合模式下每条事件仍能关联回
        // 原始客户请求的 traceId（定时任务的 MDC 是新建的，无法续接）。
        item.setTraceId(TraceContext.getTraceId());
        long now = System.currentTimeMillis();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        batchItemMapper.insert(item);

        // 桶键 = symbol（BUY 和 SELL 合并到同一桶）
        String bucketKey = event.getSymbol();
        buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(item);

        log.info("Trade enqueued to hedge bucket: tradeId={}, bucket={}, side={}, qty={}",
                event.getTradeId(), bucketKey, hedgeSide, hedgeQty);

        // 检查净敞口阈值，达到则立即出桶
        checkNetExposureThresholdAndFlush(bucketKey);

        return true;
    }

    /**
     * 定时出桶：由 Spring Scheduler 按固定频率触发。
     * <p>
     * 遍历所有桶，非空则出桶并提交交易所（含净额计算）。
     */
    @Scheduled(fixedDelayString = "${execution.batching-window-ms:1000}")
    public void flushAllBuckets() {
        if (!batchingEnabled) {
            return;
        }

        for (String bucketKey : buckets.keySet()) {
            flushBucket(bucketKey);
        }
    }

    /**
     * 检查指定桶的净敞口是否达到阈值，达到则立即出桶。
     * <p>
     * 净敞口 = sum(BUY qty) - sum(SELL qty)，阈值判断基于 |净敞口|。
     *
     * @param bucketKey 桶键（symbol）
     */
    private void checkNetExposureThresholdAndFlush(String bucketKey) {
        List<HedgeBatchItem> bucket = buckets.get(bucketKey);
        if (bucket == null || bucket.isEmpty()) {
            return;
        }

        BigDecimal netQty = computeNetExposure(bucket);
        BigDecimal absNet = netQty.abs();

        if (absNet.compareTo(sizeThreshold) >= 0) {
            log.info("Net exposure threshold reached, flushing immediately: bucket={}, netQty={}, threshold={}",
                    bucketKey, netQty, sizeThreshold);
            flushBucket(bucketKey);
        }
    }

    /**
     * 计算桶内净敞口 = sum(BUY qty) - sum(SELL qty)。
     *
     * @param bucket 桶内子项列表
     * @return 净敞口（正=净多头需BUY对冲，负=净空头需SELL对冲）
     */
    private BigDecimal computeNetExposure(List<HedgeBatchItem> bucket) {
        BigDecimal buyTotal = BigDecimal.ZERO;
        BigDecimal sellTotal = BigDecimal.ZERO;
        for (HedgeBatchItem item : bucket) {
            if ("BUY".equals(item.getSide())) {
                buyTotal = buyTotal.add(item.getQty());
            } else {
                sellTotal = sellTotal.add(item.getQty());
            }
        }
        return buyTotal.subtract(sellTotal);
    }

    /**
     * 出桶：将桶内所有子项（含 BUY 和 SELL）合并，计算净敞口后提交对冲单。
     * <p>
     * 调用 {@link ExecutionService#submitBatchedOrder} 处理净额计算和交易所提交。
     *
     * @param bucketKey 桶键（symbol）
     */
    public synchronized void flushBucket(String bucketKey) {
        List<HedgeBatchItem> bucket = buckets.get(bucketKey);
        if (bucket == null || bucket.isEmpty()) {
            return;
        }

        List<HedgeBatchItem> items = new ArrayList<>(bucket);
        bucket.clear();

        try {
            executionService.submitBatchedOrder(items);
            log.info("Bucket flushed: bucketKey={}, itemCount={}", bucketKey, items.size());
        } catch (Exception e) {
            log.error("Failed to flush bucket: bucketKey={}, error={}", bucketKey, e.getMessage());
            // 出桶失败：将子项重新放回桶（下次重试），并更新状态为 PENDING
            bucket.addAll(items);
        }
    }

    /**
     * 计算对冲方向（与客户成交同向）。
     * <p>
     * 客户 BUY → 做市商 SELL（建立空头敞口）→ 对冲 BUY（买回平掉空头）
     * 客户 SELL → 做市商 BUY（建立多头敞口）→ 对冲 SELL（卖出平掉多头）
     *
     * @param customerSide 客户成交方向
     * @return 对冲方向
     */
    private String calculateHedgeSide(String customerSide) {
        return customerSide;
    }

    /**
     * 获取指定桶当前的子项数量（用于测试）。
     *
     * @param symbol 合约代码
     * @return 桶内子项数量
     */
    public int getBucketSize(String symbol) {
        List<HedgeBatchItem> bucket = buckets.get(symbol);
        return bucket == null ? 0 : bucket.size();
    }

    /**
     * 获取当前所有桶的数量（用于测试和监控）。
     *
     * @return 当前活跃桶的数量
     */
    public int getActiveBucketCount() {
        return (int) buckets.values().stream().filter(b -> !b.isEmpty()).count();
    }

    public boolean isBatchingEnabled() {
        return batchingEnabled;
    }

    public void setBatchingEnabled(boolean batchingEnabled) {
        this.batchingEnabled = batchingEnabled;
    }

    public void setBatchingWindowMs(long batchingWindowMs) {
        this.batchingWindowMs = batchingWindowMs;
    }

    public void setSizeThreshold(BigDecimal sizeThreshold) {
        this.sizeThreshold = sizeThreshold;
    }

    public void setHedgeRatio(BigDecimal hedgeRatio) {
        this.hedgeRatio = hedgeRatio;
    }
}
