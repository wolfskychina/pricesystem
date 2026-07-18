package com.bank.trading.common.persistence.outbox;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbox 中继服务，负责将 outbox 表中的待发送消息投递到 Kafka。
 *
 * <p>本服务实现了 Transactional Outbox 模式的"中继"环节，是保证分布式最终一致性的关键组件。
 * 业务事件在本地事务中写入 outbox 表后，由本服务的定时任务异步拉取并投递到 Kafka，
 * 实现"至少一次"（at-least-once）投递语义。</p>
 *
 * <p><b>中继工作流程：</b>
 * <ol>
 *   <li>定时任务（默认每 100ms）拉取 PENDING 状态的消息（批量，上限 100 条）；</li>
 *   <li>逐条投递到 Kafka（同步等待 ack，key=partitionKey 保证同分区有序）；</li>
 *   <li>投递成功 → 标记 SENT；投递失败 → 标记 FAILED 并递增 retry_count；</li>
 *   <li>retry_count 达到 MAX_RETRY（10）则永久标记 FAILED，记录 ERROR 日志，需人工介入。</li>
 * </ol>
 *
 * <p><b>幂等性保障：</b>由于采用"至少一次"投递，消费者可能收到重复消息。
 * 消费者需通过 {@link com.bank.trading.common.persistence.idempotent.IdempotentConsumer}
 * 基于 eventId 去重，保证业务幂等。</p>
 *
 * <p><b>已知限制：</b>
 * <ul>
 *   <li>当前实现仅中继 0 号分片（{@code findPending(0, BATCH_SIZE)}），
 *       多分片场景需扩展为遍历所有分片；</li>
 *   <li>FAILED 状态消息不会自动重回 PENDING 队列，需补偿任务处理；</li>
 *   <li>{@link #sentCache} 内存缓存仅用于单实例内的快速判重，重启后失效，
 *       持久化判重依赖 outbox 表的 SENT 状态。</li>
 * </ul></p>
 *
 * @see OutboxMapper
 * @see OutboxMessage
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMapper outboxMapper;
    /** Kafka 发送模板 */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 单次拉取消息批量大小，控制内存占用与投递延迟 */
    private static final int BATCH_SIZE = 100;
    /** 最大重试次数，超过则永久失败 */
    private static final int MAX_RETRY = 10;
    /** 已发送 eventId 内存缓存，用于快速判重（单实例、重启失效） */
    private final Map<String, Boolean> sentCache = new ConcurrentHashMap<>();

    /**
     * 将业务事件写入 outbox 表（在业务本地事务内调用）。
     *
     * <p>本方法与业务状态变更、事件存储在同一本地事务中执行，保证三者原子性：
     * 要么全部成功（业务成功 + 事件持久化 + 消息待发送），要么全部回滚。
     * 消息初始状态为 PENDING，由 {@link #relay()} 中继任务异步投递。</p>
     *
     * @param topic   Kafka 主题名（通常等于事件类型）
     * @param event   业务事件对象
     * @param shardId 分片编号
     */
    @Transactional
    public void saveEvent(String topic, BaseEvent event, int shardId) {
        OutboxMessage message = new OutboxMessage();
        message.setEventId(event.getEventId());
        message.setTopic(topic);
        message.setPartitionKey(event.getPartitionKey());
        // 事件对象序列化为 JSON 作为消息载荷
        message.setPayload(JSON.toJSONString(event));
        message.setStatus(OutboxMessage.STATUS_PENDING);
        message.setRetryCount(0);
        message.setCreatedAt(LocalDateTime.now());
        message.setShardId(shardId);
        outboxMapper.insert(message);
    }

    /**
     * Outbox 中继定时任务，拉取待发送消息并投递到 Kafka。
     *
     * <p>默认每 100ms 执行一次（可通过 {@code outbox.relay.delay} 配置）。
     * 每次拉取最多 {@link #BATCH_SIZE} 条 PENDING 消息，逐条同步投递到 Kafka。</p>
     *
     * <p><b>投递逻辑：</b>
     * <ul>
     *   <li>retry_count 达到 MAX_RETRY → 直接标记 FAILED 并记录 ERROR，不再重试；</li>
     *   <li>Kafka 发送成功（同步等待 ack）→ 标记 SENT 并加入内存缓存；</li>
     *   <li>Kafka 发送失败 → 标记 FAILED 并递增 retry_count，下次任务会重新拉取重试
     *       （注：当前实现 markFailed 后状态为 FAILED 不会自动回到 PENDING，
     *       需补偿任务处理；此处为已知限制）。</li>
     * </ul>
     * 异常被 catch 避免任务中断，保证中继持续运行。</p>
     */
    @Scheduled(fixedDelayString = "${outbox.relay.delay:100}")
    public void relay() {
        try {
            // 拉取 0 号分片的待发送消息（多分片场景需扩展遍历）
            List<OutboxMessage> messages = outboxMapper.findPending(0, BATCH_SIZE);
            for (OutboxMessage msg : messages) {
                // 重试次数超限：永久失败，记录错误日志，需人工介入
                if (msg.getRetryCount() >= MAX_RETRY) {
                    outboxMapper.markFailed(msg.getId());
                    log.error("Outbox message failed after {} retries: eventId={}", MAX_RETRY, msg.getEventId());
                    continue;
                }
                try {
                    // 同步发送到 Kafka：key=partitionKey 保证同一聚合根消息落到同一分区有序
                    // .get() 阻塞等待 Kafka ack，确保投递成功后再标记 SENT
                    kafkaTemplate.send(msg.getTopic(), msg.getPartitionKey(), msg.getPayload()).get();
                    outboxMapper.markSent(msg.getId());
                    // 加入内存缓存，便于快速判重（重启失效，持久化判重依赖 outbox 表状态）
                    sentCache.put(msg.getEventId(), true);
                } catch (Exception e) {
                    // 投递失败：标记 FAILED 并递增重试计数，下次任务重新拉取
                    log.warn("Failed to send outbox message: eventId={}, error={}", msg.getEventId(), e.getMessage());
                    outboxMapper.markFailed(msg.getId());
                }
            }
        } catch (Exception e) {
            // 兜底捕获异常，避免定时任务线程中断导致中继停止
            log.error("Outbox relay error", e);
        }
    }

    /**
     * 判断某事件是否已成功投递（基于内存缓存，重启失效）。
     *
     * <p>用于业务层快速判断事件是否已发送。注意：本方法仅检查内存缓存，
     * 服务重启后缓存丢失。持久化判重应查询 outbox 表的 SENT 状态。</p>
     *
     * @param eventId 事件 ID
     * @return 已发送返回 true；未发送或重启后缓存丢失返回 false
     */
    public boolean isSent(String eventId) {
        return sentCache.containsKey(eventId);
    }
}
