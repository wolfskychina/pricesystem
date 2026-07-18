package com.bank.trading.common.persistence.eventstore;

import java.time.LocalDateTime;

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

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public Long getEventSeq() {
        return eventSeq;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getProducedBy() {
        return producedBy;
    }

    public String getTraceId() {
        return traceId;
    }

    public Integer getShardId() {
        return shardId;
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

    public void setEventSeq(Long eventSeq) {
        this.eventSeq = eventSeq;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void setProducedBy(String producedBy) {
        this.producedBy = producedBy;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void setShardId(Integer shardId) {
        this.shardId = shardId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventStoreRecord that = (EventStoreRecord) o;
        if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) return false;
        if (topic != null ? !topic.equals(that.topic) : that.topic != null) return false;
        if (partitionKey != null ? !partitionKey.equals(that.partitionKey) : that.partitionKey != null) return false;
        if (eventSeq != null ? !eventSeq.equals(that.eventSeq) : that.eventSeq != null) return false;
        if (aggregateType != null ? !aggregateType.equals(that.aggregateType) : that.aggregateType != null) return false;
        if (aggregateId != null ? !aggregateId.equals(that.aggregateId) : that.aggregateId != null) return false;
        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (payload != null ? !payload.equals(that.payload) : that.payload != null) return false;
        if (occurredAt != null ? !occurredAt.equals(that.occurredAt) : that.occurredAt != null) return false;
        if (producedBy != null ? !producedBy.equals(that.producedBy) : that.producedBy != null) return false;
        if (traceId != null ? !traceId.equals(that.traceId) : that.traceId != null) return false;
        if (shardId != null ? !shardId.equals(that.shardId) : that.shardId != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (eventId != null ? eventId.hashCode() : 0);
        result = 31 * result + (topic != null ? topic.hashCode() : 0);
        result = 31 * result + (partitionKey != null ? partitionKey.hashCode() : 0);
        result = 31 * result + (eventSeq != null ? eventSeq.hashCode() : 0);
        result = 31 * result + (aggregateType != null ? aggregateType.hashCode() : 0);
        result = 31 * result + (aggregateId != null ? aggregateId.hashCode() : 0);
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (payload != null ? payload.hashCode() : 0);
        result = 31 * result + (occurredAt != null ? occurredAt.hashCode() : 0);
        result = 31 * result + (producedBy != null ? producedBy.hashCode() : 0);
        result = 31 * result + (traceId != null ? traceId.hashCode() : 0);
        result = 31 * result + (shardId != null ? shardId.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "EventStoreRecord{eventId='" + eventId + "', topic='" + topic + "', partitionKey='" + partitionKey + "', eventSeq=" + eventSeq + ", aggregateType='" + aggregateType + "', aggregateId='" + aggregateId + "', eventType='" + eventType + "', payload='" + payload + "', occurredAt=" + occurredAt + ", producedBy='" + producedBy + "', traceId='" + traceId + "', shardId=" + shardId + "}";
    }

}
