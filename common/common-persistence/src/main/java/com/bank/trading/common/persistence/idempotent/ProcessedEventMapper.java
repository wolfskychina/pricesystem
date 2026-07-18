package com.bank.trading.common.persistence.idempotent;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 已处理事件 MyBatis Mapper，提供 processed_events 表的操作。
 *
 * <p>processed_events 表记录已被消费处理的事件 ID，是幂等消费的去重依据。
 * 由于 Outbox 中继采用"至少一次"投递，消费者可能收到重复消息，需通过本表
 * 去重保证业务幂等。</p>
 *
 * <p><b>去重原理：</b>{@link #insert} 使用 {@code INSERT ... ON CONFLICT DO NOTHING}
 * 语法（SQLite 3.24+ 与 PostgreSQL 均支持），当 event_id 已存在时插入 0 行（不报错），
 * 以此判断是否重复。</p>
 *
 * @see IdempotentConsumer
 */
@Mapper
public interface ProcessedEventMapper {

    /**
     * 插入已处理事件记录（幂等：重复插入返回 0）。
     *
     * <p>使用 {@code ON CONFLICT (event_id) DO NOTHING} 语法，当 event_id 主键已存在时
     * 不报错而是返回影响行数 0，调用方据此判断事件是否已处理过。
     * 该语法同时兼容 SQLite 3.24+ 和 PostgreSQL。</p>
     *
     * @param eventId     事件 ID
     * @param processedAt 处理时间
     * @return 1 表示首次插入（事件未处理过）；0 表示已存在（重复事件）
     */
    @Insert("INSERT INTO processed_events (event_id, processed_at) VALUES (#{eventId}, #{processedAt}) ON CONFLICT (event_id) DO NOTHING")
    int insert(@Param("eventId") String eventId, @Param("processedAt") LocalDateTime processedAt);

    /**
     * 查询某事件是否已处理过。
     *
     * <p>返回记录数：>0 表示已处理，0 表示未处理。用于在不执行业务动作的前提下
     * 预判事件是否重复。</p>
     *
     * @param eventId 事件 ID
     * @return 已处理返回大于 0，未处理返回 0
     */
    @Select("SELECT COUNT(*) FROM processed_events WHERE event_id = #{eventId}")
    int exists(@Param("eventId") String eventId);
}
