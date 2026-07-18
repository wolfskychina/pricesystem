package com.bank.trading.common.persistence.eventstore;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.BaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件存储服务，是事件溯源（Event Sourcing）架构的核心业务服务。
 *
 * <p>本服务负责将业务事件持久化到事件存储表，并维护事件序号的单调递增性。
 * 事件存储是事件溯源的"真相来源"（Source of Truth）——所有业务状态的变更
 * 都以事件形式不可变地记录于此，当前状态可通过重放事件序列重建。</p>
 *
 * <p><b>事件序号（eventSeq）分配机制：</b>
 * <ul>
 *   <li>序号在"同一分片 + 同一分区键"内严格单调递增，保证事件顺序；</li>
 *   <li>采用内存 {@link AtomicLong} 计数器分配序号，首次使用某分区键时从数据库
 *       加载当前最大序号作为起点（懒加载）；</li>
 *   <li>序号用于事件排序、乐观并发控制与 Kafka 分区内有序投递。</li>
 * </ul>
 * <b>注意：</b>内存计数器在多实例部署下可能产生序号冲突，生产环境需配合
 * 数据库唯一约束（partition_key + shard_id + event_seq）兜底，或使用分布式序号分配。</p>
 *
 * <p><b>事务边界：</b>{@link #appendEvent} 标注 {@code @Transactional}，事件持久化
 * 与业务状态变更在同一本地事务中完成。配合 Outbox 模式（{@link com.bank.trading.common.persistence.outbox.OutboxService}），
 * 保证事件"至少一次"投递到 Kafka，实现分布式最终一致性。</p>
 *
 * @see EventStoreMapper
 * @see EventStoreRecord
 */
@Service
public class EventStoreService {

    private static final Logger log = LoggerFactory.getLogger(EventStoreService.class);

    /**
     * 内存序号计数器表：key = "partitionKey_shardId"，value = 当前已分配的最大序号。
     *
     * <p>首次使用某 partitionKey+shardId 组合时，从数据库加载最大序号初始化；
     * 后续每次追加事件递增此计数器。{@link ConcurrentHashMap} + {@link AtomicLong}
     * 保证多线程并发安全。</p>
     */
    private final Map<String, AtomicLong> seqCounters = new ConcurrentHashMap<>();
    /** 事件存储 Mapper */
    private final EventStoreMapper eventStoreMapper;
    /** 本机主机名，作为事件生产者标识写入 producedBy 字段，用于溯源审计 */
    private String hostname;

    /**
     * 构造事件存储服务，初始化主机名。
     *
     * @param eventStoreMapper 事件存储 Mapper
     */
    public EventStoreService(EventStoreMapper eventStoreMapper) {
        this.eventStoreMapper = eventStoreMapper;
        // 获取本机主机名作为生产者标识，获取失败时兜底为 "unknown"
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }
    }

    /**
     * 追加一个业务事件到事件存储。
     *
     * <p>本方法是事件溯源的写入入口，完成以下工作：
     * <ol>
     *   <li>分配事件序号（内存计数器递增，首次从数据库加载最大值初始化）；</li>
     *   <li>将事件对象序列化为 JSON 载荷；</li>
     *   <li>组装 {@link EventStoreRecord} 并插入数据库。</li>
     * </ol>
     * 整个操作在事务内执行，与业务状态变更同事务，保证事件与状态的一致性。</p>
     *
     * <p><b>调用前提：</b>调用方需已通过 {@link com.bank.trading.common.persistence.sharding.ShardContextHolder#setShardId(int)}
     * 设置正确的分片 ID，使数据源路由到目标分片库。</p>
     *
     * @param event         业务事件对象
     * @param aggregateType 聚合根类型（如 ORDER、TRADE）
     * @param aggregateId   聚合根 ID
     * @param shardId       分片编号
     */
    @Transactional
    public void appendEvent(BaseEvent event, String aggregateType, String aggregateId, int shardId) {
        // 构造计数器 key：分区键 + 分片号，保证不同分片的序号独立递增
        String key = event.getPartitionKey() + "_" + shardId;
        // 懒加载：首次使用该组合时从数据库查询当前最大序号作为计数器起点
        AtomicLong counter = seqCounters.computeIfAbsent(key, k -> {
            Long max = eventStoreMapper.getMaxSeq(event.getPartitionKey(), shardId);
            return new AtomicLong(max != null ? max : 0);
        });
        // 递增分配新序号（原子操作，线程安全）
        long seq = counter.incrementAndGet();
        event.setEventSeq(seq);

        // 组装事件存储记录
        EventStoreRecord record = new EventStoreRecord();
        record.setEventId(event.getEventId());
        record.setTopic(event.getEventType());
        record.setPartitionKey(event.getPartitionKey());
        record.setEventSeq(seq);
        record.setAggregateType(aggregateType);
        record.setAggregateId(aggregateId);
        record.setEventType(event.getEventType());
        // 将完整事件对象序列化为 JSON，存储为 payload
        record.setPayload(JSON.toJSONString(event));
        // 毫秒时间戳转本地日期时间，便于 SQL 查询与展示
        record.setOccurredAt(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(event.getOccurredAt()),
                ZoneId.systemDefault()));
        record.setProducedBy(hostname);
        record.setTraceId(event.getTraceId());
        record.setShardId(shardId);

        eventStoreMapper.insert(record);
    }

    /**
     * 按客户 ID（分区键）查询其全部事件序列，用于重放重建该客户的状态。
     *
     * @param customerId 客户 ID（即分区键）
     * @param shardId    分片编号
     * @return 按事件序号升序排列的事件记录列表
     */
    public List<EventStoreRecord> findByCustomer(String customerId, int shardId) {
        return eventStoreMapper.findByPartitionKey(customerId, shardId);
    }

    /**
     * 按聚合根类型与 ID 查询事件序列，用于重建单个聚合根（如某订单）的状态。
     *
     * @param aggregateType 聚合根类型（如 ORDER、TRADE）
     * @param aggregateId   聚合根 ID
     * @param shardId       分片编号
     * @return 按事件序号升序排列的事件记录列表
     */
    public List<EventStoreRecord> findByAggregate(String aggregateType, String aggregateId, int shardId) {
        return eventStoreMapper.findByAggregate(aggregateType, aggregateId, shardId);
    }
}
