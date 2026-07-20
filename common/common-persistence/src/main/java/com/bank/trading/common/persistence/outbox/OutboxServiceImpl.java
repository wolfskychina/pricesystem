package com.bank.trading.common.persistence.outbox;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.BaseEvent;
import com.bank.trading.common.core.idgen.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbox 服务实现：负责在业务事务内将事件写入 outbox 表。
 * <p>
 * 消息投递（轮询 outbox 表 → 发送 Kafka）由独立的 outbox-relay-service 进程负责，
 * 本类不再包含 relay 逻辑，保持职责单一。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxMapper outboxMapper;
    private final IdGenerator idGenerator;

    private final Map<String, Boolean> sentCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void saveEvent(String topic, BaseEvent event, int shardId) {
        OutboxMessage message = new OutboxMessage();
        message.setId(idGenerator.nextLongId());
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

    @Override
    public boolean isSent(String eventId) {
        return sentCache.containsKey(eventId);
    }
}
