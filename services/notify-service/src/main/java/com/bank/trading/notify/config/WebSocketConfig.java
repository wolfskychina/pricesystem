package com.bank.trading.notify.config;

import com.bank.trading.notify.ws.NotifyWebSocketHandler;
import com.bank.trading.notify.session.ClientSessionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点配置。
 * <p>
 * 暴露 {@code /ws/notify} 端点，前端通过
 * {@code new WebSocket('ws://localhost:8087/ws/notify?customerId=CUST001&types=trade,hedge-fill')}
 * 连接并接收推送。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClientSessionRegistry registry;

    public WebSocketConfig(ClientSessionRegistry registry) {
        this.registry = registry;
    }

    @Bean
    public NotifyWebSocketHandler notifyWebSocketHandler() {
        return new NotifyWebSocketHandler(registry);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notifyWebSocketHandler(), "/ws/notify")
                .setAllowedOrigins("*");
    }
}
