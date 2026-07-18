package com.bank.trading.notify.session;

import com.bank.trading.notify.dto.NotifyMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClientSessionRegistry 单元测试。
 * <p>
 * 使用手写 StubSession（不依赖 Mockito，避免 Java 25 兼容性问题）。
 * 覆盖：注册/注销、customerId 过滤、types 过滤、空会话跳过、统计计数。
 */
class ClientSessionRegistryTest {

    private ClientSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ClientSessionRegistry();
    }

    @Test
    @DisplayName("push 无会话时不报错且不计数")
    void push_noSession_doesNothing() {
        registry.push(msg("trade", "C1", "AU2406"));
        assertEquals(0, registry.getTotalPushed());
        assertEquals(0, registry.getActiveSessionCount());
    }

    @Test
    @DisplayName("会话订阅全部 → 任意消息都能收到")
    void push_noFilter_pushesToAll() {
        StubSession session = new StubSession("s1");
        registry.register(session, null, null);

        registry.push(msg("trade", "C1", "AU2406"));
        registry.push(msg("hedge-fill", null, "AG2406"));

        assertEquals(2, session.received.size());
        assertEquals(2, registry.getTotalPushed());
    }

    @Test
    @DisplayName("会话订阅 customerId=C1 → 只收到 C1 的消息")
    void push_customerFilter_onlyMatched() {
        StubSession s1 = new StubSession("s1");
        registry.register(s1, "C1", null);

        registry.push(msg("trade", "C1", "AU2406"));
        registry.push(msg("trade", "C2", "AU2406"));

        assertEquals(1, s1.received.size());
        assertTrue(s1.received.get(0).contains("\"customerId\":\"C1\""));
    }

    @Test
    @DisplayName("会话订阅 types=[trade] → 只收到 trade 类型")
    void push_typeFilter_onlyMatched() {
        StubSession s1 = new StubSession("s1");
        Set<String> types = new HashSet<>();
        types.add("trade");
        registry.register(s1, null, types);

        registry.push(msg("trade", "C1", "AU2406"));
        registry.push(msg("hedge-fill", null, "AU2406"));

        assertEquals(1, s1.received.size());
        assertTrue(s1.received.get(0).contains("\"type\":\"trade\""));
    }

    @Test
    @DisplayName("unregister 后不再收到推送")
    void unregister_stopsPush() {
        StubSession s1 = new StubSession("s1");
        registry.register(s1, null, null);
        registry.push(msg("trade", "C1", "AU2406"));
        assertEquals(1, s1.received.size());

        registry.unregister("s1");
        registry.push(msg("trade", "C1", "AU2406"));

        assertEquals(1, s1.received.size(), "注销后不应再收到新消息");
        assertEquals(0, registry.getActiveSessionCount());
    }

    @Test
    @DisplayName("会话已关闭 → 自动移除，failed 不增加（直接 skip）")
    void push_closedSession_autoRemoved() {
        StubSession s1 = new StubSession("s1");
        s1.open = false;
        registry.register(s1, null, null);

        registry.push(msg("trade", "C1", "AU2406"));

        assertEquals(0, registry.getTotalPushed());
        assertEquals(0, registry.getActiveSessionCount(), "已关闭会话应被自动移除");
    }

    @Test
    @DisplayName("会话发送抛 IOException → failed +1，会话被移除")
    void push_ioException_incrementsFailed() {
        StubSession s1 = new StubSession("s1");
        s1.throwOnSend = true;
        registry.register(s1, null, null);

        registry.push(msg("trade", "C1", "AU2406"));

        assertEquals(1, registry.getTotalFailed());
        assertEquals(0, registry.getActiveSessionCount(), "发送失败的会话应被移除");
    }

    private NotifyMessage msg(String type, String customerId, String symbol) {
        return new NotifyMessage(type, customerId, symbol, "payload");
    }

    /**
     * 手写 WebSocketSession 桩，实现完整接口。
     */
    static class StubSession implements WebSocketSession {
        final String id;
        boolean open = true;
        boolean throwOnSend = false;
        final List<String> received = new ArrayList<>();
        final Map<String, Object> attrs = new HashMap<>();

        StubSession(String id) {
            this.id = id;
        }

        @Override public String getId() { return id; }
        @Override public boolean isOpen() { return open; }
        @Override public void sendMessage(WebSocketMessage<?> message) throws IOException {
            if (throwOnSend) {
                throw new IOException("simulated send error");
            }
            received.add(((TextMessage) message).getPayload());
        }
        @Override public void close() {}
        @Override public void close(CloseStatus status) {}

        // 以下方法本测试不使用
        @Override public URI getUri() { return null; }
        @Override public HttpHeaders getHandshakeHeaders() { return new HttpHeaders(); }
        @Override public Map<String, Object> getAttributes() { return attrs; }
        @Override public Principal getPrincipal() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int i) {}
        @Override public int getTextMessageSizeLimit() { return 8192; }
        @Override public void setBinaryMessageSizeLimit(int i) {}
        @Override public int getBinaryMessageSizeLimit() { return 8192; }
        @Override public List<WebSocketExtension> getExtensions() { return new ArrayList<>(); }
    }
}
