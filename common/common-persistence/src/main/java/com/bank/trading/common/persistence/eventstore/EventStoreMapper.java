package com.bank.trading.common.persistence.eventstore;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 事件存储 MyBatis Mapper，提供事件存储表的增删查操作。
 *
 * <p>本 Mapper 是事件溯源持久化的数据访问层，支持事件追加与按分区键/聚合根查询。
 * 所有查询都带 {@code shard_id} 条件，因为每个分片库只存储属于该分片的事件
 * （分片本地性），避免跨分片查询。</p>
 *
 * <p><b>关键查询说明：</b>
 * <ul>
 *   <li>{@link #findByPartitionKey} —— 按分区键（客户 ID）查询事件序列，
 *       用于重放某客户的全部事件重建状态；</li>
 *   <li>{@link #findByAggregate} —— 按聚合根类型+ID 查询事件序列，
 *       用于重建单个聚合根（如某订单）的状态；</li>
 *   <li>{@link #getMaxSeq} —— 查询某分区键当前最大序号，用于分配新事件序号。</li>
 * </ul>
 * 所有查询结果按 {@code event_seq ASC} 排序，保证事件顺序性。</p>
 *
 * @see EventStoreRecord
 * @see EventStoreService
 */
@Mapper
public interface EventStoreMapper {

    /**
     * 插入一条事件记录。
     *
     * <p>调用方需保证 event_id 唯一（主键冲突时插入失败），
     * event_seq 已通过 {@link EventStoreService#appendEvent} 分配。</p>
     *
     * @param record 事件记录
     * @return 影响行数（1 表示成功）
     */
    @Insert("INSERT INTO event_store (event_id, topic, partition_key, event_seq, aggregate_type, " +
            "aggregate_id, event_type, payload, occurred_at, produced_by, trace_id, shard_id) " +
            "VALUES (#{eventId}, #{topic}, #{partitionKey}, #{eventSeq}, #{aggregateType}, " +
            "#{aggregateId}, #{eventType}, #{payload}, #{occurredAt}, #{producedBy}, #{traceId}, #{shardId})")
    int insert(EventStoreRecord record);

    /**
     * 按分区键查询事件序列（按序号升序），用于重放某客户/聚合的事件重建状态。
     *
     * @param partitionKey 分区键（通常是客户 ID）
     * @param shardId      分片编号
     * @return 按事件序号升序排列的事件记录列表
     */
    @Select("SELECT * FROM event_store WHERE partition_key = #{partitionKey} AND shard_id = #{shardId} " +
            "ORDER BY event_seq ASC")
    List<EventStoreRecord> findByPartitionKey(@Param("partitionKey") String partitionKey, @Param("shardId") int shardId);

    /**
     * 查询某分区键当前的最大事件序号。
     *
     * <p>用于 {@link EventStoreService} 在内存中初始化序号计数器，
     * 保证新分配的 eventSeq 在该分区内严格递增。</p>
     *
     * @param partitionKey 分区键
     * @param shardId      分片编号
     * @return 最大序号；无记录时返回 null
     */
    @Select("SELECT MAX(event_seq) FROM event_store WHERE partition_key = #{partitionKey} AND shard_id = #{shardId}")
    Long getMaxSeq(@Param("partitionKey") String partitionKey, @Param("shardId") int shardId);

    /**
     * 按聚合根类型与 ID 查询事件序列（按序号升序），用于重建单个聚合根状态。
     *
     * <p>例如查询某订单的全部事件：aggregateType=ORDER, aggregateId=订单ID。
     * 同一聚合根的事件都落到同一分片（因为分片键=分区键），所以只需单分片查询。</p>
     *
     * @param aggregateType 聚合根类型（如 ORDER、TRADE）
     * @param aggregateId   聚合根 ID
     * @param shardId       分片编号
     * @return 按事件序号升序排列的事件记录列表
     */
    @Select("SELECT * FROM event_store WHERE aggregate_type = #{aggregateType} AND aggregate_id = #{aggregateId} " +
            "AND shard_id = #{shardId} ORDER BY event_seq ASC")
    List<EventStoreRecord> findByAggregate(@Param("aggregateType") String aggregateType,
                                           @Param("aggregateId") String aggregateId,
                                           @Param("shardId") int shardId);
}
