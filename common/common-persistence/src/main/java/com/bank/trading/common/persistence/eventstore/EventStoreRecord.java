package com.bank.trading.common.persistence.eventstore;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件存储记录实体，映射 {@code event_store} 表的一行记录。
 *
 * <p>事件存储（Event Store）是事件溯源架构的持久化载体。系统产生的每个业务事件
 * 都会以一条记录的形式持久化到此表，记录包含事件元数据与完整 JSON 载荷。
 * 通过重放某聚合根/客户的按序事件序列，可重建其当前状态。</p>
 *
 * <p><b>表结构设计要点：</b>
 * <ul>
 *   <li>{@code event_id} —— 事件唯一 ID，作为幂等去重主键；</li>
 *   <li>{@code partition_key} + {@code shard_id} + {@code event_seq} —— 联合确定事件
 *       在"某分片某分区"内的全局顺序，event_seq 单调递增；</li>
 *   <li>{@code aggregate_type} + {@code aggregate_id} —— 聚合根类型与 ID，
 *       支持按聚合根维度查询事件序列（如重建某订单状态）；</li>
 *   <li>{@code payload} —— 事件完整 JSON，反序列化后即为 {@link com.bank.trading.common.core.event.BaseEvent} 子类。</li>
 * </ul></p>
 *
 * @see EventStoreMapper
 * @see EventStoreService
 */
@Data
public class EventStoreRecord {

    /** 事件全局唯一 ID（32 位 UUID），用于幂等去重 */
    private String eventId;
    /** 主题（等于事件类型），对应 Kafka topic 名 */
    private String topic;
    /** 分区键/分片路由键，通常为客户 ID 或聚合根 ID */
    private String partitionKey;
    /** 事件序号，在"同一分片+同一分区键"内单调递增，保证事件顺序 */
    private Long eventSeq;
    /** 聚合根类型（如 ORDER、TRADE、POSITION），用于按聚合维度查询 */
    private String aggregateType;
    /** 聚合根 ID（如订单 ID、成交 ID） */
    private String aggregateId;
    /** 事件类型码，对应 {@link com.bank.trading.common.core.enums.EventType} */
    private String eventType;
    /** 事件完整 JSON 载荷，反序列化后为 BaseEvent 子类实例 */
    private String payload;
    /** 事件发生时间（本地日期时间），由事件时间戳转换 */
    private LocalDateTime occurredAt;
    /** 事件生产者标识（主机名），用于溯源审计 */
    private String producedBy;
    /** 分布式链路追踪 ID */
    private String traceId;
    /** 分片编号，事件持久化时写入，便于按分片查询与统计 */
    private Integer shardId;
}
