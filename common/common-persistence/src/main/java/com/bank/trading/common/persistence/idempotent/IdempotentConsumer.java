package com.bank.trading.common.persistence.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 幂等消费者，保证 Kafka 消息的幂等消费。
 *
 * <p>由于 Outbox 中继采用"至少一次"（at-least-once）投递语义，消费者可能收到
 * 重复消息。本服务通过 processed_events 表记录已处理事件 ID，保证同一事件
 * 只被业务处理一次，实现幂等消费。</p>
 *
 * <p><b>幂等机制：</b>利用数据库主键唯一约束 + {@code INSERT OR IGNORE} 语法，
 * 在事务内先尝试插入 event_id：
 * <ul>
 *   <li>插入成功（影响行数=1）→ 事件首次处理，执行业务动作；</li>
 *   <li>插入失败（影响行数=0，主键冲突）→ 事件已处理过，跳过业务动作。</li>
 * </ul>
 * 插入与业务动作在同一事务内，保证"记录已处理"与"执行业务"原子性。</p>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * idempotentConsumer.consume(tradeEvent.getEventId(), () -> {
 *     positionService.updatePosition(tradeEvent);  // 业务动作
 *     return null;
 * });
 * </pre>
 *
 * @see ProcessedEventMapper
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentConsumer {

    private final ProcessedEventMapper processedEventMapper;

    /**
     * 幂等消费（带返回值版本）。
     *
     * <p>在事务内先尝试插入事件 ID，插入成功才执行业务动作并返回其结果；
     * 插入失败（重复事件）则跳过业务动作返回 null。</p>
     *
     * @param eventId 事件 ID
     * @param action  业务动作（有返回值）
     * @param <T>     返回值类型
     * @return 首次处理返回业务动作结果；重复事件返回 null
     */
    @Transactional
    public <T> T consume(String eventId, Supplier<T> action) {
        // 尝试插入事件 ID：返回 1 表示首次，返回 0 表示重复
        int inserted = processedEventMapper.insert(eventId, LocalDateTime.now());
        if (inserted == 0) {
            // 重复事件，跳过业务动作
            log.warn("Duplicate event skipped: {}", eventId);
            return null;
        }
        // 首次处理，执行业务动作
        return action.get();
    }

    /**
     * 幂等消费（无返回值版本）。
     *
     * <p>语义同 {@link #consume(String, Supplier)}，用于无返回值的业务动作。</p>
     *
     * @param eventId 事件 ID
     * @param action  业务动作（无返回值）
     */
    @Transactional
    public void consume(String eventId, Runnable action) {
        int inserted = processedEventMapper.insert(eventId, LocalDateTime.now());
        if (inserted == 0) {
            log.warn("Duplicate event skipped: {}", eventId);
            return;
        }
        action.run();
    }

    /**
     * 查询某事件是否已被处理过（不执行业务动作）。
     *
     * <p>用于在不触发业务逻辑的情况下预判事件是否重复，例如决定是否跳过
     * 较重的初始化操作。</p>
     *
     * @param eventId 事件 ID
     * @return 已处理返回 true，未处理返回 false
     */
    public boolean isProcessed(String eventId) {
        return processedEventMapper.exists(eventId) > 0;
    }
}
