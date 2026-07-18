package com.bank.trading.common.persistence.eventstore;

import com.bank.trading.common.core.event.BaseEvent;

import java.util.List;

public interface EventStoreService {

    void appendEvent(BaseEvent event, String aggregateType, String aggregateId, int shardId);

    List<EventStoreRecord> findByCustomer(String customerId, int shardId);

    List<EventStoreRecord> findByAggregate(String aggregateType, String aggregateId, int shardId);
}
