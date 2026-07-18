package com.bank.trading.simexchange.config;

import com.bank.trading.simexchange.ws.MarketDataWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类。
 * <p>
 * 该配置负责将行情 WebSocket 处理器注册到 Spring 的 WebSocket 端点体系，使客户端
 * 能够通过 {@code ws://host:port/ws/marketdata} 建立长连接，实时接收行情快照广播。
 * <p>
 * 在模拟交易所中，WebSocket 通道是行情推送给下游交易策略与做市模块的主要通路，
 * 与 REST 接口形成"推+拉"互补：REST 用于按需查询，WebSocket 用于实时推送。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    /** 行情 WebSocket 处理器，维护会话列表并周期性广播行情 */
    private final MarketDataWebSocketHandler marketDataHandler;

    /**
     * 构造函数，通过依赖注入获取行情 WebSocket 处理器。
     *
     * @param marketDataHandler 行情 WebSocket 处理器实例
     */
    public WebSocketConfig(MarketDataWebSocketHandler marketDataHandler) {
        this.marketDataHandler = marketDataHandler;
    }

    /**
     * 注册 WebSocket 处理器。
     * <p>
     * 将 {@link MarketDataWebSocketHandler} 绑定到路径 {@code /ws/marketdata}，并设置
     * 允许所有来源（{@code setAllowedOrigins("*")}）。开放全部来源是因为模拟交易所
     * 通常被多种前端/测试工具连接，跨域限制宽松便于集成；生产环境需根据安全策略收紧。
     *
     * @param registry WebSocket 处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(marketDataHandler, "/ws/marketdata")
                .setAllowedOrigins("*");
    }
}
