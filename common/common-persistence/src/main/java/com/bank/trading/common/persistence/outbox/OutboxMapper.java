package com.bank.trading.common.persistence.outbox;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Outbox 消息 MyBatis Mapper，提供 outbox 表的增删改查操作。
 *
 * <p>本 Mapper 是 Outbox 模式持久化的数据访问层。outbox 表与 event_store 表
 * 配合使用：事件同时写入两张表（同事务），event_store 作为事件溯源的永久记录，
 * outbox 作为消息投递的临时队列（投递成功后可归档/清理）。</p>
 *
 * <p><b>关键操作说明：</b>
 * <ul>
 *   <li>{@link #insert} —— 业务事务内写入待发送消息（status=PENDING）；</li>
 *   <li>{@link #findPending} —— 中继服务定时拉取待发送消息，按 id 升序保证投递顺序；</li>
 *   <li>{@link #markSent} —— Kafka 投递成功后标记为 SENT；</li>
 *   <li>{@link #markFailed} —— 投递失败时标记 FAILED 并递增重试计数。</li>
 * </ul></p>
 *
 * @see OutboxMessage
 * @see OutboxService
 */
@Mapper
public interface OutboxMapper {

    /**
     * 插入一条待发送的 outbox 消息。
     *
     * <p>在业务事务内调用，与事件存储、业务状态变更同事务。
     * status 初始为 PENDING，retry_count 初始为 0。</p>
     *
     * @param message outbox 消息
     * @return 影响行数（1 表示成功）
     */
    @Insert("INSERT INTO outbox (event_id, topic, partition_key, payload, status, retry_count, created_at, shard_id) " +
            "VALUES (#{eventId}, #{topic}, #{partitionKey}, #{payload}, #{status}, #{retryCount}, #{createdAt}, #{shardId})")
    int insert(OutboxMessage message);

    /**
     * 查询指定分片的待发送消息（按 id 升序，保证投递顺序）。
     *
     * <p>按 id 升序保证先写入的消息先投递，维持事件顺序性。
     * 分片维度查询避免跨分片扫描，每个分片独立中继。</p>
     *
     * @param shardId 分片编号
     * @param limit   单次拉取上限（批量投递，控制内存与延迟）
     * @return 待发送消息列表
     */
    @Select("SELECT * FROM outbox WHERE status = 'PENDING' AND shard_id = #{shardId} ORDER BY id ASC LIMIT #{limit}")
    List<OutboxMessage> findPending(@Param("shardId") int shardId, @Param("limit") int limit);

    /**
     * 标记消息为已发送（SENT），记录发送时间。
     *
     * <p>Kafka 投递成功后调用，消息进入终态。</p>
     *
     * @param id 消息主键 ID
     * @return 影响行数
     */
    @Update("UPDATE outbox SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    /**
     * 标记消息为发送失败（FAILED）并递增重试计数。
     *
     * <p>每次投递失败调用，retry_count 递增。中继服务会检查 retry_count 是否达到
     * 上限（MAX_RETRY），达到则不再重试，需人工介入。</p>
     *
     * @param id 消息主键 ID
     * @return 影响行数
     */
    @Update("UPDATE outbox SET status = 'FAILED', retry_count = retry_count + 1 WHERE id = #{id}")
    int markFailed(@Param("id") Long id);
}
