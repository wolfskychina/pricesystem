package com.bank.trading.common.persistence.eventstore;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.BaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EventStoreServiceImpl implements EventStoreService {

    private static final Logger log = LoggerFactory.getLogger(EventStoreServiceImpl.class);

    private final Map<String, AtomicLong> seqCounters = new ConcurrentHashMap<>();
    private final EventStoreMapper eventStoreMapper;
    private String hostname;

    public EventStoreServiceImpl(EventStoreMapper eventStoreMapper) {
        this.eventStoreMapper = eventStoreMapper;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }
    }

    @Override
    @Transactional
    public void appendEvent(BaseEvent event, String aggregateType, String aggregateId, int shardId) {
        String key = event.getPartitionKey() + "_" + shardId;
        AtomicLong counter = seqCounters.computeIfAbsent(key, k -> {
            Long max = eventStoreMapper.getMaxSeq(event.getPartitionKey(), shardId);
            return new AtomicLong(max != null ? max : 0);
        });
        long seq = counter.incrementAndGet();
        event.setEventSeq(seq);

        EventStoreRecord record = new EventStoreRecord();
        record.setEventId(event.getEventId());
        record.setTopic(event.getEventType());
        record.setPartitionKey(event.getPartitionKey());
        record.setEventSeq(seq);
        record.setAggregateType(aggregateType);
        record.setAggregateId(aggregateId);
        record.setEventType(event.getEventType());
        record.setPayload(JSON.toJSONString(event));
        record.setOccurredAt(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(event.getOccurredAt()),
                ZoneId.systemDefault()));
        record.setProducedBy(hostname);
        record.setTraceId(event.getTraceId());
        record.setShardId(shardId);

        eventStoreMapper.insert(record);
    }

    @Override
    public List<EventStoreRecord> findByCustomer(String customerId, int shardId) {
        return eventStoreMapper.findByPartitionKey(customerId, shardId);
    }

    @Override
    public List<EventStoreRecord> findByAggregate(String aggregateType, String aggregateId, int shardId) {
        return eventStoreMapper.findByAggregate(aggregateType, aggregateId, shardId);
    }
}
