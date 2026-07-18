package com.bank.trading.outbox.relay;

import com.bank.trading.common.persistence.outbox.OutboxMessage;
import com.bank.trading.common.persistence.outbox.OutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox Relay 轮询组件。
 * <p>
 * 核心流程（每次轮询执行）：
 * <ol>
 *   <li>从 outbox 表批量拉取 status=PENDING 的消息（按 id 升序，保证投递顺序）</li>
 *   <li>逐条发送到 Kafka（同步等待 ack，确保消息不丢）</li>
 *   <li>发送成功 → 标记 SENT（终态）</li>
 *   <li>发送失败 → 标记 FAILED 并递增 retry_count；超过 MAX_RETRY 则永久失败</li>
 * </ol>
 * <p>
 * <b>幂等保证</b>：即使 Relay 重复发送同一条消息，Kafka 端的消费者通过
 * processed_events 表去重，保证业务状态不被重复更新。
 * <p>
 * <b>多实例部署</b>：当前实现使用普通 SELECT，单实例运行即可。
 * 如需多实例并发中继，可将 findPending 升级为 {@code SELECT ... FOR UPDATE SKIP LOCKED}。
 */
@Slf4j
@Component
public class OutboxRelayRunner {

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    @Value("${outbox.relay.max-retry:10}")
    private int maxRetry;

    @Value("${outbox.relay.shard-id:0}")
    private int shardId;

    public OutboxRelayRunner(OutboxMapper outboxMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 定时轮询 outbox 表，将 PENDING 消息投递到 Kafka。
     * <p>
     * 默认 100ms 间隔（可通过 outbox.relay.delay 配置），每次拉取最多 batchSize 条。
     */
    @Scheduled(fixedDelayString = "${outbox.relay.delay:100}")
    public void relay() {
        try {
            List<OutboxMessage> messages = outboxMapper.findPending(shardId, batchSize);
            if (messages.isEmpty()) {
                return;
            }

            log.debug("Outbox relay: fetched {} pending messages (shard={})", messages.size(), shardId);

            for (OutboxMessage msg : messages) {
                processMessage(msg);
            }
        } catch (Exception e) {
            log.error("Outbox relay error", e);
        }
    }

    /**
     * 处理单条 outbox 消息：发送 Kafka + 更新状态。
     *
     * @param msg outbox 消息
     */
    private void processMessage(OutboxMessage msg) {
        // 超过重试上限，标记永久失败
        if (msg.getRetryCount() != null && msg.getRetryCount() >= maxRetry) {
            outboxMapper.markFailed(msg.getId());
            log.error("Outbox message permanently failed after {} retries: eventId={}, topic={}",
                    maxRetry, msg.getEventId(), msg.getTopic());
            return;
        }

        try {
            // 同步发送，等待 Kafka ack
            kafkaTemplate.send(msg.getTopic(), msg.getPartitionKey(), msg.getPayload()).get();
            outboxMapper.markSent(msg.getId());
            log.debug("Outbox message sent: id={}, eventId={}, topic={}",
                    msg.getId(), msg.getEventId(), msg.getTopic());
        } catch (Exception e) {
            outboxMapper.markFailed(msg.getId());
            log.warn("Outbox message send failed: id={}, eventId={}, topic={}, retryCount={}, error={}",
                    msg.getId(), msg.getEventId(), msg.getTopic(),
                    msg.getRetryCount() != null ? msg.getRetryCount() + 1 : 1, e.getMessage());
        }
    }
}
