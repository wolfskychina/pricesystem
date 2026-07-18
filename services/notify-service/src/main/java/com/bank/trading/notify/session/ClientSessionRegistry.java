package com.bank.trading.notify.session;

import com.bank.trading.notify.dto.NotifyMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端会话注册表。
 * <p>
 * 维护每个 WebSocket 会话的订阅条件（customerId + 类型集合），消息推送时按订阅过滤。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>会话按 sessionId 索引，并发安全（ConcurrentHashMap + CopyOnWriteArraySet）</li>
 *   <li>支持按 customerId 过滤（前端面板只关心自己客户的成交）</li>
 *   <li>支持按 type 过滤（前端只订阅 trade / 不订阅 market-data）</li>
 *   <li>会话异常或断开时自动移除，避免内存泄漏</li>
 * </ul>
 */
@Slf4j
@Component
public class ClientSessionRegistry {

    /** 按 sessionId 索引的会话条目 */
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    /** 推送总数计数（监控用） */
    private final AtomicLong totalPushed = new AtomicLong(0);

    /** 推送失败计数 */
    private final AtomicLong totalFailed = new AtomicLong(0);

    /**
     * 注册新会话。
     *
     * @param session    WebSocket 会话
     * @param customerId 客户 ID 过滤器（null 表示不过滤，订阅全部客户）
     * @param types      订阅的事件类型集合（null 或空表示订阅全部类型）
     */
    public void register(WebSocketSession session, String customerId, Set<String> types) {
        SessionEntry entry = new SessionEntry();
        entry.session = session;
        entry.customerId = customerId;
        entry.types = types;
        sessions.put(session.getId(), entry);
        log.info("WebSocket session registered: id={}, customerId={}, types={}",
                session.getId(), customerId, types);
    }

    /**
     * 注销会话。
     */
    public void unregister(String sessionId) {
        SessionEntry removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("WebSocket session unregistered: id={}", sessionId);
        }
    }

    /**
     * 推送消息给所有订阅匹配的会话。
     * <p>
     * 匹配规则：
     * <ul>
     *   <li>若会话订阅了 customerId，则消息 customerId 必须匹配；会话 customerId 为 null 表示订阅全部</li>
     *   <li>若会话订阅了 types，则消息 type 必须在集合中；types 为空表示订阅全部</li>
     * </ul>
     *
     * @param message 待推送消息
     */
    public void push(NotifyMessage message) {
        if (sessions.isEmpty()) {
            return;
        }
        sessions.values().forEach(entry -> {
            if (!matchCustomer(entry, message)) {
                return;
            }
            if (!matchType(entry, message)) {
                return;
            }
            send(entry, message);
        });
    }

    private boolean matchCustomer(SessionEntry entry, NotifyMessage message) {
        if (entry.customerId == null || entry.customerId.isBlank()) {
            return true;   // 未设置过滤 → 全部
        }
        return entry.customerId.equals(message.getCustomerId());
    }

    private boolean matchType(SessionEntry entry, NotifyMessage message) {
        if (entry.types == null || entry.types.isEmpty()) {
            return true;   // 未设置过滤 → 全部
        }
        return entry.types.contains(message.getType());
    }

    private void send(SessionEntry entry, NotifyMessage message) {
        try {
            if (!entry.session.isOpen()) {
                sessions.remove(entry.session.getId());
                return;
            }
            String json = com.alibaba.fastjson2.JSON.toJSONString(message);
            entry.session.sendMessage(new TextMessage(json));
            totalPushed.incrementAndGet();
        } catch (IOException e) {
            totalFailed.incrementAndGet();
            log.warn("Push failed for session {}: {}", entry.session.getId(), e.getMessage());
            sessions.remove(entry.session.getId());
        }
    }

    /** 当前在线会话数 */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /** 累计推送成功次数 */
    public long getTotalPushed() {
        return totalPushed.get();
    }

    /** 累计推送失败次数 */
    public long getTotalFailed() {
        return totalFailed.get();
    }

    /** 会话条目：持有会话与订阅条件 */
    private static class SessionEntry {
        WebSocketSession session;
        String customerId;
        Set<String> types;
    }
}
