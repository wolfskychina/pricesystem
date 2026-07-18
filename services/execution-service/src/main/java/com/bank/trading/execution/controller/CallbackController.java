package com.bank.trading.execution.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.execution.dto.ExchangeOrderResponse;
import com.bank.trading.execution.dto.ExchangeTradeNotification;
import com.bank.trading.execution.service.ExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易所 Webhook 回调接收接口。
 * <p>
 * 模拟真实做市商系统中 CTP SDK 的回调处理器（{@code OnRtnOrder} / {@code OnRtnTrade}）。
 * sim-exchange 在撮合完成后会主动 POST 到本接口，推送两类回报：
 * <ul>
 *   <li>{@code POST /execution/callback/order}：订单状态回报（NEW → ACCEPTED → FILLED/REJECTED）</li>
 *   <li>{@code POST /execution/callback/trade}：成交通知（含成交价、成交量、成交 ID）</li>
 * </ul>
 * <p>
 * 注册地址通过 {@code POST /exchange/callbacks/register} 在服务启动时自动注册到 sim-exchange。
 */
@RestController
@RequestMapping("/execution/callback")
public class CallbackController {

    private static final Logger log = LoggerFactory.getLogger(CallbackController.class);

    private final ExecutionService executionService;

    public CallbackController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * 接收订单状态回报（模拟 CTP OnRtnOrder）。
     * <p>
     * 当 sim-exchange 订单状态变更（如 NEW → ACCEPTED → FILLED/REJECTED）时推送。
     * 本接口更新本地对冲订单状态。
     *
     * @param order 交易所推送的订单状态对象
     * @return 处理结果
     */
    @PostMapping("/order")
    public Result<Void> onOrderCallback(@RequestBody ExchangeOrderResponse order) {
        try {
            log.info("Received order callback: exchangeOrderId={}, status={}",
                    order.getOrderId(), order.getStatus());
            executionService.onOrderNotification(order);
            return Result.success(null);
        } catch (Exception e) {
            log.error("Failed to process order callback: {}", e.getMessage());
            return Result.fail("Failed to process order callback: " + e.getMessage());
        }
    }

    /**
     * 接收成交通知（模拟 CTP OnRtnTrade）。
     * <p>
     * 当 sim-exchange 撮合成交后推送。本接口持久化成交流水，更新对冲订单状态为 FILLED，
     * 并发布 hedge-fill-event 到 Kafka。
     *
     * @param notification 交易所推送的成交通知
     * @return 处理结果
     */
    @PostMapping("/trade")
    public Result<Void> onTradeCallback(@RequestBody ExchangeTradeNotification notification) {
        try {
            log.info("Received trade callback: exchangeTradeId={}, orderId={}, price={}, qty={}",
                    notification.getTradeId(), notification.getOrderId(),
                    notification.getPrice(), notification.getQty());
            executionService.onTradeNotification(notification);
            return Result.success(null);
        } catch (Exception e) {
            log.error("Failed to process trade callback: {}", e.getMessage());
            return Result.fail("Failed to process trade callback: " + e.getMessage());
        }
    }
}
