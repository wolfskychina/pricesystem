package com.bank.trading.common.persistence.outbox;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Outbox 消息实体，映射 {@code outbox} 表的一行记录。
 *
 * <p>Outbox 模式是解决"数据库事务与消息投递原子性"的经典方案。业务事件先写入
 * outbox 表（与业务状态变更在同一本地事务），再由中继服务（{@link OutboxService}）
 * 异步将消息投递到 Kafka。这样保证了"要么业务成功且消息一定投递，要么都失败"，
 * 避免了业务成功但消息丢失的不一致问题。</p>
 *
 * <p><b>消息状态机：</b>
 * <pre>
 *   PENDING（待发送）──中继成功──→ SENT（已发送）★终态
 *           │
 *           └──发送失败──→ FAILED（失败，retry_count + 1）
 *                              │
 *                              └── retry_count < MAX_RETRY ──→ 重新进入 PENDING 队列重试
 *                              │
 *                              └── retry_count >= MAX_RETRY ──→ 永久 FAILED ★终态（需人工介入）
 * </pre>
 *
 * <p><b>注意：</b>当前实现中 FAILED 状态的消息不会自动回到 PENDING，需通过
 * 定时补偿任务或人工干预重新投递。</p>
 *
 * @see OutboxService
 * @see OutboxMapper
 */
@Data
public class OutboxMessage {

    /** 自增主键 ID */
    private Long id;
    /** 事件全局唯一 ID，用于幂等去重（与 event_store 的 event_id 对应） */
    private String eventId;
    /** Kafka 主题名（等于事件类型） */
    private String topic;
    /** 分区键，用于 Kafka 分区路由，保证同一聚合根消息有序 */
    private String partitionKey;
    /** 消息 JSON 载荷，即事件对象的序列化结果 */
    private String payload;
    /** 消息状态：PENDING / SENT / FAILED */
    private String status;
    /** 重试次数，发送失败时递增，超过 MAX_RETRY 则永久失败 */
    private Integer retryCount;
    /** 消息创建时间（即事件写入 outbox 的时间） */
    private LocalDateTime createdAt;
    /** 消息发送成功时间，用于监控投递延迟 */
    private LocalDateTime sentAt;
    /** 分片编号，消息持久化时写入，便于按分片查询待发送消息 */
    private Integer shardId;

    /** 状态常量：待发送 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态常量：已发送（终态） */
    public static final String STATUS_SENT = "SENT";
    /** 状态常量：发送失败（达到重试上限后为永久终态） */
    public static final String STATUS_FAILED = "FAILED";
}
