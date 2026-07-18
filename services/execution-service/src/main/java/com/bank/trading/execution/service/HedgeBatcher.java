package com.bank.trading.execution.service;

import com.bank.trading.common.core.enums.OrderSide;
import com.bank.trading.common.core.event.TradeEvent;
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
 * 对冲聚合器（Hedge Batcher）。
 * <p>
 * 将短时间内同合约、同方向的多笔客户成交聚合为一笔对冲单提交到交易所，
 * 以减少交易所订单数量、降低手续费与市场冲击成本。
 * <p>
 * <b>聚合粒度</b>：按 {@code symbol + side} 分组，同一组内的所有子项合并为一笔对冲单。
 * <p>
 * <b>双触发机制</b>：
 * <ul>
 *   <li><b>时间触发</b>：每 {@code batching-window-ms} 毫秒定时出桶（默认 1000ms，开发环境）</li>
 *   <li><b>数量触发</b>：单桶累计数量 ≥ {@code batching-size-threshold} 立即出桶（默认 50 手）</li>
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

    /** 聚合开关：true=开启聚合，false=每笔成交独立对冲（兼容旧行为） */
    @Value("${execution.batching-enabled:false}")
    private boolean batchingEnabled;

    /** 聚合时间窗口（毫秒），开发环境默认 1000ms */
    @Value("${execution.batching-window-ms:1000}")
    private long batchingWindowMs;

    /** 数量阈值（手），单桶累计数量超过此值立即出桶 */
    @Value("${execution.batching-size-threshold:50}")
    private BigDecimal sizeThreshold;

    /** 对冲比例 */
    @Value("${execution.hedge-ratio:1.0}")
    private BigDecimal hedgeRatio;

    /**
     * 内存聚合桶。
     * key = symbol + ":" + side（例如 "AU2406:BUY"）
     * value = 该桶内的待出桶子项列表
     */
    private final ConcurrentHashMap<String, List<HedgeBatchItem>> buckets = new ConcurrentHashMap<>();

    public HedgeBatcher(HedgeBatchItemMapper batchItemMapper, ExecutionService executionService) {
        this.batchItemMapper = batchItemMapper;
        this.executionService = executionService;
    }

    /**
     * 将一笔客户成交加入聚合桶。
     * <p>
     * 若聚合未开启，直接调用 {@link ExecutionService#onTradeEventImmediate} 单笔对冲。
     * 若已开启，入桶后检查数量阈值，达到则立即出桶。
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

        String hedgeSide = calculateHedgeSide(event.getSide());
        BigDecimal hedgeQty = event.getQty().multiply(hedgeRatio).setScale(4, RoundingMode.HALF_UP);

        // 构造子项并持久化（状态=PENDING）
        HedgeBatchItem item = new HedgeBatchItem();
        item.setOriginalTradeId(event.getTradeId());
        item.setCustomerId(event.getCustomerId());
        item.setSymbol(event.getSymbol());
        item.setSide(hedgeSide);
        item.setQty(hedgeQty);
        item.setStatus("PENDING");
        item.setFilledQty(BigDecimal.ZERO);
        item.setAvgPrice(BigDecimal.ZERO);
        long now = System.currentTimeMillis();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        batchItemMapper.insert(item);

        String bucketKey = buildBucketKey(event.getSymbol(), hedgeSide);
        buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(item);

        log.info("Trade enqueued to hedge bucket: tradeId={}, bucket={}, qty={}",
                event.getTradeId(), bucketKey, hedgeQty);

        // 检查数量阈值，达到则立即出桶
        checkSizeThresholdAndFlush(bucketKey);

        return true;
    }

    /**
     * 定时出桶：由 Spring Scheduler 按固定频率触发。
     * <p>
     * 遍历所有桶，非空则出桶并提交交易所。
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
     * 检查指定桶的数量是否达到阈值，达到则立即出桶。
     *
     * @param bucketKey 桶键（symbol:side）
     */
    private void checkSizeThresholdAndFlush(String bucketKey) {
        List<HedgeBatchItem> bucket = buckets.get(bucketKey);
        if (bucket == null || bucket.isEmpty()) {
            return;
        }

        BigDecimal totalQty = bucket.stream()
                .map(HedgeBatchItem::getQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalQty.compareTo(sizeThreshold) >= 0) {
            log.info("Bucket size threshold reached, flushing immediately: bucket={}, totalQty={}, threshold={}",
                    bucketKey, totalQty, sizeThreshold);
            flushBucket(bucketKey);
        }
    }

    /**
     * 出桶：将桶内所有子项合并为一笔对冲单提交交易所。
     *
     * @param bucketKey 桶键（symbol:side）
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
     * 计算对冲方向（与客户成交相反）。
     *
     * @param customerSide 客户成交方向
     * @return 对冲方向
     */
    private String calculateHedgeSide(String customerSide) {
        OrderSide side = OrderSide.of(customerSide);
        return side == OrderSide.BUY ? "SELL" : "BUY";
    }

    /**
     * 构建桶键（symbol:side）。
     *
     * @param symbol 合约
     * @param side   方向
     * @return 桶键
     */
    private String buildBucketKey(String symbol, String side) {
        return symbol + ":" + side;
    }

    /**
     * 获取指定桶当前的子项数量（用于测试）。
     *
     * @param symbol 合约
     * @param side   对冲方向
     * @return 桶内子项数量
     */
    public int getBucketSize(String symbol, String side) {
        List<HedgeBatchItem> bucket = buckets.get(buildBucketKey(symbol, side));
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
