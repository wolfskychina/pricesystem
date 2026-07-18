package com.bank.trading.position.consumer;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.position.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 客户成交事件消费者。
 * <p>
 * 监听 {@code trade-event} topic，当 oms-service 发布客户成交事件后，
 * 本消费者接收事件并调用 {@link PositionService#onTradeEvent} 更新客户持仓。
 * <p>
 * <b>幂等性</b>：PositionService 内部通过 processed_events 表做幂等校验，
 * 重复消费不会导致持仓被重复累加。
 * <p>
 * <b>顺序性</b>：TradeEvent 以 customerId 作为分区键，同一客户的成交事件
 * 落到同一 Kafka 分区，单分区单消费者保证顺序消费，从而正确累加持仓。
 */
@Component
public class TradeEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeEventConsumer.class);

    private final PositionService positionService;

    public TradeEventConsumer(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * 处理 trade-event 消息。
     * <p>
     * 将 JSON 反序列化为 {@link TradeEvent}，调用 PositionService 更新持仓。
     * 反序列化失败仅记录日志，不重试（避免毒丸消息阻塞消费）。
     *
     * @param message Kafka 消息（JSON 字符串）
     */
    @KafkaListener(topics = "${position.trade-topic:trade-event}",
            groupId = "${spring.kafka.consumer.group-id:position-service}")
    public void onMessage(String message) {
        try {
            TradeEvent event = JSON.parseObject(message, TradeEvent.class);
            if (event != null && event.getTradeId() != null) {
                log.info("Received trade event: tradeId={}, customerId={}, symbol={}, side={}, qty={}",
                        event.getTradeId(), event.getCustomerId(), event.getSymbol(),
                        event.getSide(), event.getQty());
                positionService.onTradeEvent(event);
            }
        } catch (Exception e) {
            log.error("Failed to process trade event: message={}, error={}", message, e.getMessage());
        }
    }
}
