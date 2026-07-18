package com.bank.trading.position.consumer;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.HedgeFillEvent;
import com.bank.trading.position.service.PositionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 对冲成交事件消费者。
 * <p>
 * 监听 {@code hedge-fill-event} topic，当 execution-service 发布对冲成交事件后，
 * 本消费者接收事件并调用 {@link PositionService#onHedgeFillEvent} 更新做市商对冲头寸。
 * <p>
 * <b>幂等性</b>：PositionService 内部通过 processed_events 表做幂等校验，
 * 重复消费不会导致对冲头寸被重复累加。
 * <p>
 * <b>顺序性</b>：HedgeFillEvent 以 symbol 作为分区键，同一合约的对冲事件
 * 落到同一 Kafka 分区，单分区单消费者保证顺序消费，从而正确累加对冲头寸。
 */
@Component
public class HedgeFillEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(HedgeFillEventConsumer.class);

    private final PositionService positionService;

    public HedgeFillEventConsumer(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * 处理 hedge-fill-event 消息。
     * <p>
     * 将 JSON 反序列化为 {@link HedgeFillEvent}，调用 PositionService 更新对冲持仓。
     * 反序列化失败仅记录日志，不重试（避免毒丸消息阻塞消费）。
     *
     * @param message Kafka 消息（JSON 字符串）
     */
    @KafkaListener(topics = "${position.hedge-fill-topic:hedge-fill-event}",
            groupId = "${spring.kafka.consumer.group-id:position-service}")
    public void onMessage(String message) {
        try {
            HedgeFillEvent event = JSON.parseObject(message, HedgeFillEvent.class);
            if (event != null && event.getHedgeTradeId() != null) {
                log.info("Received hedge fill event: hedgeTradeId={}, symbol={}, side={}, qty={}",
                        event.getHedgeTradeId(), event.getSymbol(),
                        event.getSide(), event.getQty());
                positionService.onHedgeFillEvent(event);
            }
        } catch (Exception e) {
            log.error("Failed to process hedge fill event: message={}, error={}", message, e.getMessage());
        }
    }
}
