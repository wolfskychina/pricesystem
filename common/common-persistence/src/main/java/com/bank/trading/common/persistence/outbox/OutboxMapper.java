package com.bank.trading.common.persistence.outbox;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxMapper {

    @Insert("INSERT INTO outbox (event_id, topic, partition_key, payload, status, retry_count, created_at, shard_id) " +
            "VALUES (#{eventId}, #{topic}, #{partitionKey}, #{payload}, #{status}, #{retryCount}, #{createdAt}, #{shardId})")
    int insert(OutboxMessage message);

    @Select("SELECT * FROM outbox WHERE status = 'PENDING' AND shard_id = #{shardId} ORDER BY id ASC LIMIT #{limit}")
    List<OutboxMessage> findPending(@Param("shardId") int shardId, @Param("limit") int limit);

    @Update("UPDATE outbox SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    @Update("UPDATE outbox SET status = 'FAILED', retry_count = retry_count + 1 WHERE id = #{id}")
    int markFailed(@Param("id") Long id);
}
