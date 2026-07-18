package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import java.io.Serializable;
import java.util.UUID;

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

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public Long getEventSeq() {
        return eventSeq;
    }

    public Long getOccurredAt() {
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

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public void setEventSeq(Long eventSeq) {
        this.eventSeq = eventSeq;
    }

    public void setOccurredAt(Long occurredAt) {
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
        BaseEvent that = (BaseEvent) o;
        if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) return false;
        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (partitionKey != null ? !partitionKey.equals(that.partitionKey) : that.partitionKey != null) return false;
        if (eventSeq != null ? !eventSeq.equals(that.eventSeq) : that.eventSeq != null) return false;
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
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (partitionKey != null ? partitionKey.hashCode() : 0);
        result = 31 * result + (eventSeq != null ? eventSeq.hashCode() : 0);
        result = 31 * result + (occurredAt != null ? occurredAt.hashCode() : 0);
        result = 31 * result + (producedBy != null ? producedBy.hashCode() : 0);
        result = 31 * result + (traceId != null ? traceId.hashCode() : 0);
        result = 31 * result + (shardId != null ? shardId.hashCode() : 0);
        return result;
    }

    @Override

    @Override

    @Override

    @Override

    @Override

    @Override

    protected BaseEvent() {
        this.eventId = UUID.randomUUID().toString().replace("-", "");
        this.occurredAt = System.currentTimeMillis();
    }

    protected BaseEvent(EventType eventType, String partitionKey) {
        this();
        this.eventType = eventType.getCode();
        this.partitionKey = partitionKey;
    }
    @Override
    public String toString() {
        return "BaseEvent{eventId='" + eventId + "', eventType='" + eventType + "', partitionKey='" + partitionKey + "', eventSeq=" + eventSeq + ", occurredAt=" + occurredAt + ", producedBy='" + producedBy + "', traceId='" + traceId + "', shardId=" + shardId + "}";
    }

}
