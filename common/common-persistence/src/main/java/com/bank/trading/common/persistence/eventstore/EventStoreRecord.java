package com.bank.trading.common.persistence.eventstore;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventStoreRecord {

    private String eventId;
    private String topic;
    private String partitionKey;
    private Long eventSeq;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private LocalDateTime occurredAt;
    private String producedBy;
    private String traceId;
    private Integer shardId;
}
