package com.bank.trading.notify.ws;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.notify.session.ClientSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * WebSocket 连接处理器。
 * <p>
 * 协议：客户端连接 URL 携带 query 参数：
 * <ul>
 *   <li>{@code customerId=xxx} —— 只订阅该客户的事件（可选）</li>
 *   <li>{@code types=trade,hedge-fill} —— 只订阅这些类型（可选，逗号分隔）</li>
 * </ul>
 * 不传参数则订阅全部事件。
 * <p>
 * 客户端只需保持连接，服务端会主动推送 JSON 消息（{@code NotifyMessage}）。
 */
@Slf4j
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private final ClientSessionRegistry registry;

    @Autowired
    public NotifyWebSocketHandler(ClientSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SubParams params = parseQuery(session);
        registry.register(session, params.customerId, params.types);
        // 连接建立后发个 ack 让客户端确认
        try {
            AckMessage ack = new AckMessage("connected", "session " + session.getId());
            session.sendMessage(new TextMessage(JSON.toJSONString(ack)));
        } catch (Exception e) {
            log.warn("Send ack failed for session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端可以发心跳 "ping"，服务端回 "pong"
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload)) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception e) {
                log.warn("Send pong failed: {}", e.getMessage());
            }
        }
    }

    private SubParams parseQuery(WebSocketSession session) {
        SubParams params = new SubParams();
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return params;
        }
        String query = uri.getQuery();
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = kv.substring(0, eq);
            String v = kv.substring(eq + 1);
            if ("customerId".equals(k)) {
                params.customerId = v;
            } else if ("types".equals(k) && !v.isBlank()) {
                params.types = new HashSet<>(Arrays.asList(v.split(",")));
            }
        }
        return params;
    }

    private static class SubParams {
        String customerId;
        Set<String> types = Collections.emptySet();
    }

    /** 连接建立后发送的 ACK 消息 */
    private static class AckMessage {
        String type;
        String message;
        AckMessage(String type, String message) {
            this.type = type;
            this.message = message;
        }
    }
}
