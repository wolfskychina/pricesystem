package com.bank.trading.simexchange.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.simexchange.callback.CallbackRegistry;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook 回调注册接口。
 * <p>
 * 模拟真实交易所中做市商系统建立会话连接的过程。做市商 execution-service 启动时
 * 调用本接口注册回调地址，后续 sim-exchange 撮合完成后会主动向该地址推送
 * 订单状态回报与成交通知。
 * <p>
 * 业务背景：在 CTP 中，做市商通过 {@code RegisterFront} + {@code RegisterSpi} 建立
 * 会话并注册回调处理器；本接口是这一机制的 REST 模拟。
 */
@RestController
@RequestMapping("/exchange/callbacks")
public class CallbackController {

    /** 回调注册表 */
    private final CallbackRegistry callbackRegistry;

    /**
     * 构造函数，通过依赖注入获取回调注册表。
     *
     * @param callbackRegistry 回调注册表实例
     */
    public CallbackController(CallbackRegistry callbackRegistry) {
        this.callbackRegistry = callbackRegistry;
    }

    /**
     * 注册 Webhook 回调地址。
     * <p>
     * 做市商 EMS 启动时调用，传入自身的回调接收 baseUrl。sim-exchange 会在
     * 撮合完成后向 {@code {baseUrl}/order} 推送订单状态回报，向 {@code {baseUrl}/trade}
     * 推送成交通知。
     *
     * @param request 包含 url 字段的请求体
     * @return 成功响应
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterRequest request) {
        callbackRegistry.register(request.getUrl());
        return Result.success(null);
    }

    /**
     * 回调注册请求体。
     */
    public static class RegisterRequest {
        /** 回调接收基础地址，例如 {@code http://execution-service:8086/execution/callback} */
        private String url;

        /** 获取回调地址 */
        public String getUrl() {
            return url;
        }

        /** 设置回调地址 */
        public void setUrl(String url) {
            this.url = url;
        }
    }
}
