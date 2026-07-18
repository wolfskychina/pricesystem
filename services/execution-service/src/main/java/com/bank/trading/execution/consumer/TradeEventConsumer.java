package com.bank.trading.execution.consumer;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.execution.service.HedgeBatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 客户成交事件消费者。
 * <p>
 * 监听 {@code trade-event} topic，当 oms-service 发布客户成交事件后，
 * 本消费者接收事件并调用 {@link HedgeBatcher#enqueue} 入桶等待聚合对冲。
 * <p>
 * 若 batching 关闭，HedgeBatcher 内部直接调用 ExecutionService 单笔立即对冲；
 * 若 batching 开启，事件进入聚合桶，由时间窗口或数量阈值触发出桶提交。
 * <p>
 * <b>幂等性</b>：若同一事件被重复投递，HedgeBatcher 通过 original_trade_id 做幂等校验。
 */
@Component
public class TradeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);

    private final HedgeBatcher hedgeBatcher;

    public TradeEventConsumer(HedgeBatcher hedgeBatcher) {
        this.hedgeBatcher = hedgeBatcher;
    }

    /**
     * 处理 trade-event 消息。
     * <p>
     * 将 JSON 反序列化为 {@link TradeEvent}，调用 HedgeBatcher 入桶。
     * 反序列化失败仅记录日志，不重试（避免毒丸消息阻塞消费）。
     *
     * @param message Kafka 消息（JSON 字符串）
     */
    @KafkaListener(topics = "${execution.trade-topic:trade-event}",
            groupId = "${spring.kafka.consumer.group-id:execution-service}")
    public void onMessage(String message) {
        try {
            TradeEvent event = JSON.parseObject(message, TradeEvent.class);
            if (event != null && event.getTradeId() != null) {
                log.info("Received trade event: tradeId={}, symbol={}, side={}, qty={}",
                        event.getTradeId(), event.getSymbol(), event.getSide(), event.getQty());
                hedgeBatcher.enqueue(event);
            }
        } catch (Exception e) {
            log.error("Failed to process trade event: message={}, error={}", message, e.getMessage());
        }
    }
}
