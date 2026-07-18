package com.bank.trading.common.persistence.outbox;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OutboxMessage {

    private Long id;
    private String eventId;
    private String topic;
    private String partitionKey;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private Integer shardId;

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
}
