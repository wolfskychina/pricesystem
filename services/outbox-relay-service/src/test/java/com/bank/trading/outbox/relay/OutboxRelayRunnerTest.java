package com.bank.trading.outbox.relay;

import com.bank.trading.common.persistence.outbox.OutboxMessage;
import com.bank.trading.common.persistence.outbox.OutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OutboxRelayRunner 单元测试。
 * <p>
 * 使用内存 Mock Mapper 和可控行为的 KafkaTemplate 验证轮询逻辑。
 */
class OutboxRelayRunnerTest {

    private InMemoryOutboxMapper outboxMapper;
    private ControllableKafkaTemplate kafkaTemplate;
    private OutboxRelayRunner runner;

    @BeforeEach
    void setUp() {
        outboxMapper = new InMemoryOutboxMapper();
        kafkaTemplate = new ControllableKafkaTemplate();
        runner = new OutboxRelayRunner(outboxMapper, kafkaTemplate);
        ReflectionTestUtils.setField(runner, "batchSize", 100);
        ReflectionTestUtils.setField(runner, "maxRetry", 3);
        ReflectionTestUtils.setField(runner, "shardId", 0);
    }

    @Test
    void relay_emptyPending_doesNothing() {
        runner.relay();

        assertTrue(outboxMapper.sentIds.isEmpty());
        assertTrue(outboxMapper.failedIds.isEmpty());
        assertTrue(kafkaTemplate.sentMessages.isEmpty());
    }

    @Test
    void relay_success_marksSent() {
        OutboxMessage msg = createMessage(1L, "evt-001", "trade-event", 0);
        outboxMapper.pendingMessages.add(msg);
        kafkaTemplate.shouldFail = false;

        runner.relay();

        assertTrue(outboxMapper.sentIds.contains(1L));
        assertTrue(outboxMapper.failedIds.isEmpty());
        assertEquals(1, kafkaTemplate.sentMessages.size());
        assertEquals("trade-event", kafkaTemplate.sentMessages.get(0).topic());
    }

    @Test
    void relay_sendFailure_marksFailed() {
        OutboxMessage msg = createMessage(2L, "evt-002", "trade-event", 0);
        outboxMapper.pendingMessages.add(msg);
        kafkaTemplate.shouldFail = true;

        runner.relay();

        assertTrue(outboxMapper.failedIds.contains(2L));
        assertTrue(outboxMapper.sentIds.isEmpty());
    }

    @Test
    void relay_exceedsMaxRetry_marksFailedWithoutSending() {
        OutboxMessage msg = createMessage(3L, "evt-003", "trade-event", 3); // retryCount == maxRetry
        outboxMapper.pendingMessages.add(msg);

        runner.relay();

        assertTrue(outboxMapper.failedIds.contains(3L));
        assertTrue(kafkaTemplate.sentMessages.isEmpty(), "不应发送超过重试上限的消息");
    }

    @Test
    void relay_mixedBatch_processesEachIndependently() {
        OutboxMessage okMsg = createMessage(1L, "evt-ok", "trade-event", 0);
        outboxMapper.pendingMessages.add(okMsg);
        kafkaTemplate.failTopics.add("hedge-fill-event");

        OutboxMessage failMsg = createMessage(2L, "evt-fail", "hedge-fill-event", 0);
        outboxMapper.pendingMessages.add(failMsg);

        runner.relay();

        assertTrue(outboxMapper.sentIds.contains(1L));
        assertTrue(outboxMapper.failedIds.contains(2L));
    }

    // ==================== 辅助方法与 Mock 类 ====================

    private OutboxMessage createMessage(Long id, String eventId, String topic, int retryCount) {
        OutboxMessage msg = new OutboxMessage();
        msg.setId(id);
        msg.setEventId(eventId);
        msg.setTopic(topic);
        msg.setPartitionKey(topic.equals("trade-event") ? "CUST-001" : "AU2406");
        msg.setPayload("{\"eventId\":\"" + eventId + "\"}");
        msg.setStatus(OutboxMessage.STATUS_PENDING);
        msg.setRetryCount(retryCount);
        msg.setCreatedAt(LocalDateTime.now());
        msg.setShardId(0);
        return msg;
    }

    /** 内存 OutboxMapper，记录 markSent / markFailed 调用 */
    static class InMemoryOutboxMapper implements OutboxMapper {
        final List<OutboxMessage> pendingMessages = new CopyOnWriteArrayList<>();
        final List<Long> sentIds = new CopyOnWriteArrayList<>();
        final List<Long> failedIds = new CopyOnWriteArrayList<>();

        @Override
        public int insert(OutboxMessage message) { pendingMessages.add(message); return 1; }

        @Override
        public List<OutboxMessage> findPending(int shardId, int limit) {
            return pendingMessages.stream()
                    .filter(m -> "PENDING".equals(m.getStatus()) && m.getShardId() == shardId)
                    .limit(limit)
                    .toList();
        }

        @Override
        public int markSent(Long id) {
            sentIds.add(id);
            pendingMessages.removeIf(m -> m.getId().equals(id));
            return 1;
        }

        @Override
        public int markFailed(Long id) {
            failedIds.add(id);
            pendingMessages.removeIf(m -> m.getId().equals(id));
            return 1;
        }
    }

    /** 可控行为的 KafkaTemplate，支持全局失败和按 topic 失败 */
    static class ControllableKafkaTemplate extends KafkaTemplate<String, String> {
        final List<SentRecord> sentMessages = new CopyOnWriteArrayList<>();
        volatile boolean shouldFail = false;
        final List<String> failTopics = new CopyOnWriteArrayList<>();

        ControllableKafkaTemplate() {
            super(new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(
                    Map.of("bootstrap.servers", "localhost:9999")));
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
            sentMessages.add(new SentRecord(topic, key, data));
            if (shouldFail || failTopics.contains(topic)) {
                return CompletableFuture.failedFuture(new RuntimeException("mock failure"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    record SentRecord(String topic, String key, String data) {}
}
