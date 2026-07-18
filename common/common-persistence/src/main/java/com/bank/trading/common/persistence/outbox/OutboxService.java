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

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY = 10;
    private final Map<String, Boolean> sentCache = new ConcurrentHashMap<>();

    @Transactional
    public void saveEvent(String topic, BaseEvent event, int shardId) {
        OutboxMessage message = new OutboxMessage();
        message.setEventId(event.getEventId());
        message.setTopic(topic);
        message.setPartitionKey(event.getPartitionKey());
        message.setPayload(JSON.toJSONString(event));
        message.setStatus(OutboxMessage.STATUS_PENDING);
        message.setRetryCount(0);
        message.setCreatedAt(LocalDateTime.now());
        message.setShardId(shardId);
        outboxMapper.insert(message);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay:100}")
    public void relay() {
        try {
            List<OutboxMessage> messages = outboxMapper.findPending(0, BATCH_SIZE);
            for (OutboxMessage msg : messages) {
                if (msg.getRetryCount() >= MAX_RETRY) {
                    outboxMapper.markFailed(msg.getId());
                    log.error("Outbox message failed after {} retries: eventId={}", MAX_RETRY, msg.getEventId());
                    continue;
                }
                try {
                    kafkaTemplate.send(msg.getTopic(), msg.getPartitionKey(), msg.getPayload()).get();
                    outboxMapper.markSent(msg.getId());
                    sentCache.put(msg.getEventId(), true);
                } catch (Exception e) {
                    log.warn("Failed to send outbox message: eventId={}, error={}", msg.getEventId(), e.getMessage());
                    outboxMapper.markFailed(msg.getId());
                }
            }
        } catch (Exception e) {
            log.error("Outbox relay error", e);
        }
    }

    public boolean isSent(String eventId) {
        return sentCache.containsKey(eventId);
    }
}
