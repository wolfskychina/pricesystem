package com.bank.trading.execution.consumer;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.execution.service.ExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 客户成交事件消费者。
 * <p>
 * 监听 {@code trade-event} topic，当 oms-service 发布客户成交事件后，
 * 本消费者接收事件并调用 {@link ExecutionService#onTradeEvent} 发起对冲下单。
 * <p>
 * <b>幂等性</b>：若同一事件被重复投递（如 consumer 重启后重放），会生成多笔对冲单。
 * 生产环境应通过 tradeId 做幂等校验，本期模拟暂不实现（trade-event 由 outbox 保证至少一次投递）。
 */
@Component
public class TradeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);

    private final ExecutionService executionService;

    public TradeEventConsumer(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * 处理 trade-event 消息。
     * <p>
     * 将 JSON 反序列化为 {@link TradeEvent}，调用 ExecutionService 发起对冲。
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
                executionService.onTradeEvent(event);
            }
        } catch (Exception e) {
            log.error("Failed to process trade event: message={}, error={}", message, e.getMessage());
        }
    }
}
