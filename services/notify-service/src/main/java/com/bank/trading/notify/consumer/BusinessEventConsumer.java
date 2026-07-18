package com.bank.trading.notify.consumer;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.common.core.event.HedgeFillEvent;
import com.bank.trading.common.core.event.TradeEvent;
import com.bank.trading.notify.dto.NotifyMessage;
import com.bank.trading.notify.session.ClientSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 业务事件消费者。
 * <p>
 * 订阅 trade-event 与 hedge-fill-event，反序列化后包装为 {@link NotifyMessage}
 * 推送给前端 WebSocket 客户端。
 * <p>
 * <b>消费组</b>：使用独立的 group-id "notify-service"，避免与 account-service /
 * position-service / execution-service 等业务消费组竞争消息。
 * <p>
 * <b>容错</b>：反序列化失败仅记录日志，不重试，避免毒丸消息阻塞推送。
 */
@Slf4j
@Component
public class BusinessEventConsumer {

    private final ClientSessionRegistry registry;

    @Value("${notify.trade-topic:trade-event}")
    private String tradeTopic;

    @Value("${notify.hedge-fill-topic:hedge-fill-event}")
    private String hedgeFillTopic;

    public BusinessEventConsumer(ClientSessionRegistry registry) {
        this.registry = registry;
    }

    /**
     * 消费客户成交事件，推送给前端。
     */
    @KafkaListener(topics = "${notify.trade-topic:trade-event}",
            groupId = "${spring.kafka.consumer.group-id:notify-service}")
    public void onTradeEvent(String message) {
        try {
            TradeEvent event = JSON.parseObject(message, TradeEvent.class);
            if (event == null || event.getTradeId() == null) {
                return;
            }
            log.debug("Notify received trade event: tradeId={}, customerId={}",
                    event.getTradeId(), event.getCustomerId());
            NotifyMessage notify = new NotifyMessage(
                    "trade",
                    event.getCustomerId(),
                    event.getSymbol(),
                    event);
            registry.push(notify);
        } catch (Exception e) {
            log.error("Notify failed to process trade event: message={}, error={}",
                    message, e.getMessage());
        }
    }

    /**
     * 消费对冲成交事件，推送给前端。
     */
    @KafkaListener(topics = "${notify.hedge-fill-topic:hedge-fill-event}",
            groupId = "${spring.kafka.consumer.group-id:notify-service}")
    public void onHedgeFillEvent(String message) {
        try {
            HedgeFillEvent event = JSON.parseObject(message, HedgeFillEvent.class);
            if (event == null || event.getHedgeTradeId() == null) {
                return;
            }
            log.debug("Notify received hedge-fill event: hedgeTradeId={}, symbol={}",
                    event.getHedgeTradeId(), event.getSymbol());
            // 对冲事件没有 customerId（做市商自身行为），按 symbol 维度推送
            NotifyMessage notify = new NotifyMessage(
                    "hedge-fill",
                    null,
                    event.getSymbol(),
                    event);
            registry.push(notify);
        } catch (Exception e) {
            log.error("Notify failed to process hedge-fill event: message={}, error={}",
                    message, e.getMessage());
        }
    }
}
