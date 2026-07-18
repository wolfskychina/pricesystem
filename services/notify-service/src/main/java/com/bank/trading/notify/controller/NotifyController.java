package com.bank.trading.notify.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.notify.session.ClientSessionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知服务管理接口。
 * <ul>
 *   <li>GET  /notify/health    —— 健康检查</li>
 *   <li>GET  /notify/stats     —— 在线会话数 + 推送计数</li>
 *   <li>POST /notify/broadcast —— 手动广播消息（联调测试用）</li>
 * </ul>
 */
@RestController
@RequestMapping("/notify")
public class NotifyController {

    private final ClientSessionRegistry registry;

    public NotifyController(ClientSessionRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("component", "notify-service");
        return Result.success(data);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeSessions", registry.getActiveSessionCount());
        data.put("totalPushed", registry.getTotalPushed());
        data.put("totalFailed", registry.getTotalFailed());
        return Result.success(data);
    }
}
