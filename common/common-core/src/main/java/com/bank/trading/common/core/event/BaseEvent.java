package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

@Data
public abstract class BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;
    private String partitionKey;
    private Long eventSeq;
    private Long occurredAt;
    private String producedBy;
    private String traceId;
    private Integer shardId;

    protected BaseEvent() {
        this.eventId = UUID.randomUUID().toString().replace("-", "");
        this.occurredAt = System.currentTimeMillis();
    }

    protected BaseEvent(EventType eventType, String partitionKey) {
        this();
        this.eventType = eventType.getCode();
        this.partitionKey = partitionKey;
    }
}
