package com.bank.trading.common.persistence.outbox;

import com.bank.trading.common.core.event.BaseEvent;

public interface OutboxService {

    void saveEvent(String topic, BaseEvent event, int shardId);

    boolean isSent(String eventId);
}
