package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * 所有业务事件的抽象基类，是事件溯源（Event Sourcing）体系的核心载体。
 *
 * <p>本系统采用事件溯源架构：所有业务状态变更（下单、成交、对冲、报价等）
 * 都以事件的形式持久化到事件存储（Event Store），再通过 Kafka 分发给下游消费者。
 * 当前状态可通过重放事件序列重建。{@code BaseEvent} 封装了所有事件共有的元数据。</p>
 *
 * <p><b>核心字段说明：</b>
 * <ul>
 *   <li>{@code eventId} —— 事件全局唯一 ID（32 位无横线 UUID），用于幂等去重；</li>
 *   <li>{@code eventType} —— 事件类型码（见 {@link EventType}），标识事件业务语义；</li>
 *   <li>{@code partitionKey} —— 分区键，同时也是分片路由键，保证同一聚合根的事件
 *       落到同一分片与同一 Kafka 分区，从而实现有序消费；</li>
 *   <li>{@code eventSeq} —— 事件序号，在<b>同一分片 + 同一分区键</b>内单调递增，
 *       用于事件排序与并发控制；</li>
 *   <li>{@code occurredAt} —— 事件发生时间戳（毫秒），由构造时自动生成；</li>
 *   <li>{@code producedBy} —— 事件生产者标识（如服务名 + 实例），用于溯源审计；</li>
 *   <li>{@code traceId} —— 分布式链路追踪 ID，串联一次请求跨服务的所有事件；</li>
 *   <li>{@code shardId} —— 分片编号，事件持久化时由 ShardRouter 计算并写入，
 *       便于按分片查询与统计。</li>
 * </ul></p>
 *
 * <p><b>事件溯源数据流：</b>
 * <pre>
 *   业务操作 → 产生 Event → 写入 Event Store（分片）→ Outbox 中继 → Kafka → 下游消费者
 *                                  ↓
 *                           重建当前状态（重放事件序列）
 * </pre>
 * 事件持久化与业务状态变更在<b>同一本地事务</b>中完成，配合 Outbox 模式保证
 * "至少一次"投递，从而实现分布式环境下的最终一致性。</p>
 *
 * @see EventType
 */
@Data
public abstract class BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件全局唯一 ID（32 位无横线 UUID），用于幂等去重与事件追溯 */
    private String eventId;
    /** 事件类型码，对应 {@link EventType} 的 code 值 */
    private String eventType;
    /**
     * 分区键 / 分片路由键，通常为客户 ID 或聚合根 ID。
     * 保证同一聚合根的所有事件落到同一分片与同一 Kafka 分区，实现有序消费。
     */
    private String partitionKey;
    /** 事件序号，在"同一分片 + 同一分区键"内单调递增，用于事件排序与乐观并发控制 */
    private Long eventSeq;
    /** 事件发生时间戳（毫秒），构造时自动生成 */
    private Long occurredAt;
    /** 事件生产者标识（如服务名 + 实例 ID），用于溯源审计 */
    private String producedBy;
    /** 分布式链路追踪 ID，串联一次请求跨服务的所有事件 */
    private String traceId;
    /** 分片编号，持久化时由 ShardRouter 计算写入，便于按分片查询与统计 */
    private Integer shardId;

    /**
     * 默认构造函数，自动生成 eventId 与 occurredAt。
     *
     * <p>用于反序列化（如从数据库/Kafka 还原事件）时调用，eventId 会被新生成的
     * UUID 覆盖，但反序列化框架会随后通过 setter 写入持久化的真实值。</p>
     */
    protected BaseEvent() {
        // 生成无横线的 32 位 UUID，节省存储空间
        this.eventId = UUID.randomUUID().toString().replace("-", "");
        this.occurredAt = System.currentTimeMillis();
    }

    /**
     * 业务构造函数，设置事件类型与分区键。
     *
     * <p>生产事件时使用本构造函数。partitionKey 通常为客户 ID，确保同一客户
     * 的所有事件路由到同一分片与 Kafka 分区，保证事件顺序性。</p>
     *
     * @param eventType    事件类型枚举
     * @param partitionKey 分区键/分片路由键，通常为客户 ID 或聚合根 ID
     */
    protected BaseEvent(EventType eventType, String partitionKey) {
        // 先调用默认构造生成 eventId 与 occurredAt
        this();
        this.eventType = eventType.getCode();
        this.partitionKey = partitionKey;
    }
}
