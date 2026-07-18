package com.bank.trading.common.persistence.outbox;

import java.time.LocalDateTime;

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
    private static final String STATUS_PENDING;
    private static final String STATUS_SENT;
    private static final String STATUS_FAILED;

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public Integer getShardId() {
        return shardId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public void setShardId(Integer shardId) {
        this.shardId = shardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutboxMessage that = (OutboxMessage) o;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) return false;
        if (topic != null ? !topic.equals(that.topic) : that.topic != null) return false;
        if (partitionKey != null ? !partitionKey.equals(that.partitionKey) : that.partitionKey != null) return false;
        if (payload != null ? !payload.equals(that.payload) : that.payload != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (retryCount != null ? !retryCount.equals(that.retryCount) : that.retryCount != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        if (sentAt != null ? !sentAt.equals(that.sentAt) : that.sentAt != null) return false;
        if (shardId != null ? !shardId.equals(that.shardId) : that.shardId != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (eventId != null ? eventId.hashCode() : 0);
        result = 31 * result + (topic != null ? topic.hashCode() : 0);
        result = 31 * result + (partitionKey != null ? partitionKey.hashCode() : 0);
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (retryCount != null ? retryCount.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (sentAt != null ? sentAt.hashCode() : 0);
        result = 31 * result + (shardId != null ? shardId.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "OutboxMessage{id=" + id + ", eventId='" + eventId + "', topic='" + topic + "', partitionKey='" + partitionKey + "', payload='" + payload + "', status='" + status + "', retryCount=" + retryCount + ", createdAt=" + createdAt + ", sentAt=" + sentAt + ", shardId=" + shardId + "}";
    }

}
