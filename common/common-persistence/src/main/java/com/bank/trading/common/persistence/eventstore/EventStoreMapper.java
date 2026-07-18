package com.bank.trading.common.persistence.eventstore;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EventStoreMapper {

    @Insert("INSERT INTO event_store (event_id, topic, partition_key, event_seq, aggregate_type, " +
            "aggregate_id, event_type, payload, occurred_at, produced_by, trace_id, shard_id) " +
            "VALUES (#{eventId}, #{topic}, #{partitionKey}, #{eventSeq}, #{aggregateType}, " +
            "#{aggregateId}, #{eventType}, #{payload}, #{occurredAt}, #{producedBy}, #{traceId}, #{shardId})")
    int insert(EventStoreRecord record);

    @Select("SELECT * FROM event_store WHERE partition_key = #{partitionKey} AND shard_id = #{shardId} " +
            "ORDER BY event_seq ASC")
    List<EventStoreRecord> findByPartitionKey(@Param("partitionKey") String partitionKey, @Param("shardId") int shardId);

    @Select("SELECT MAX(event_seq) FROM event_store WHERE partition_key = #{partitionKey} AND shard_id = #{shardId}")
    Long getMaxSeq(@Param("partitionKey") String partitionKey, @Param("shardId") int shardId);

    @Select("SELECT * FROM event_store WHERE aggregate_type = #{aggregateType} AND aggregate_id = #{aggregateId} " +
            "AND shard_id = #{shardId} ORDER BY event_seq ASC")
    List<EventStoreRecord> findByAggregate(@Param("aggregateType") String aggregateType,
                                           @Param("aggregateId") String aggregateId,
                                           @Param("shardId") int shardId);
}
