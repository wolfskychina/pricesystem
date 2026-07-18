package com.bank.trading.simexchange.ws;

import com.alibaba.fastjson2.JSON;
import com.bank.trading.simexchange.engine.MarketDataEngine;
import com.bank.trading.simexchange.model.MarketData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 行情 WebSocket 处理器。
 * <p>
 * 该处理器负责维护当前所有已连接的 WebSocket 会话，并按调度周期（与行情 tick 同步）
 * 将全部合约的最新行情快照以 JSON 数组形式广播给所有客户端。
 * <p>
 * 业务背景：在模拟交易所中，下游交易策略、做市模块、前端行情面板通过 WebSocket
 * 长连接订阅行情，实现低延迟的实时推送。广播频率由 {@code sim-exchange.interval-ms}
 * 决定（默认 1000ms），与行情 tick 频率保持一致。
 * <p>
 * 线程安全：会话列表使用 {@link CopyOnWriteArrayList}，保证广播遍历与连接/断开事件
 * 并发修改时的安全性。
 */
@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    /** 行情引擎，提供最新行情快照与合约清单 */
    private final MarketDataEngine marketDataEngine;

    /** 日志器 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketDataWebSocketHandler.class);

    /**
     * 构造函数，通过依赖注入获取行情引擎。
     *
     * @param marketDataEngine 行情引擎实例
     */
    public MarketDataWebSocketHandler(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    /** 当前所有已建立的 WebSocket 会话；使用 CopyOnWriteArrayList 保证遍历时并发安全 */
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    /**
     * 连接建立回调。
     * <p>
     * 业务逻辑：将新会话加入会话列表，下次广播时即会向其推送行情。
     *
     * @param session 新建立的 WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connected: {}, total sessions: {}", session.getId(), sessions.size());
    }

    /**
     * 连接关闭回调。
     * <p>
     * 业务逻辑：从会话列表移除已关闭的会话，避免后续广播对失效会话写入造成异常。
     *
     * @param session 已关闭的 WebSocket 会话
     * @param status  关闭状态码与原因
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}, total sessions: {}", session.getId(), sessions.size());
    }

    /**
     * 处理客户端发送的文本消息。
     * <p>
     * 业务逻辑：模拟交易所行情 WebSocket 是单向推送通道，客户端上行消息仅做调试日志
     * 记录，不解析也不响应。如需订阅过滤等协议可在此扩展。
     *
     * @param session 客户端会话
     * @param message 客户端发来的文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from {}: {}", session.getId(), payload);
    }

    /**
     * 周期性广播行情快照。
     * <p>
     * 业务逻辑：
     * <ol>
     *   <li>若当前无在线会话或无合约，直接返回，避免无意义构造 JSON。</li>
     *   <li>遍历所有合约，取出最新行情快照，用 StringBuilder 手工拼装成 JSON 数组
     *       字符串（数组元素为各合约行情的 JSON 对象）。手工拼装而非调用库统一序列化
     *       整个列表，便于按需控制逗号分隔与空元素跳过。</li>
     *   <li>遍历所有在线会话，逐一发送文本消息；单个会话发送异常仅记录告警，
     *       不影响其他会话的广播。</li>
     *   <li>整体异常被捕获并记录，避免调度器因异常终止后续广播。</li>
     * </ol>
     * <p>
     * 调度策略：使用 {@code fixedDelay}（与行情 tick 同步），确保广播的总是
     * 最新一次 tick 生成的行情。
     */
    @Scheduled(fixedDelayString = "${sim-exchange.interval-ms:1000}")
    public void broadcast() {
        // 无连接或无合约时直接返回，避免无意义 JSON 构造
        if (sessions.isEmpty()) {
            return;
        }
        List<String> symbols = marketDataEngine.getSymbols();
        if (symbols.isEmpty()) {
            return;
        }
        try {
            // 手工拼装 JSON 数组：每个元素是一个合约行情的 JSON 对象
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (String symbol : symbols) {
                MarketData md = marketDataEngine.getLatest(symbol);
                if (md != null) {
                    // 第一个元素前不加逗号，后续元素前加逗号，保证 JSON 合法
                    if (!first) {
                        sb.append(",");
                    }
                    sb.append(JSON.toJSONString(md));
                    first = false;
                }
            }
            sb.append("]");
            String json = sb.toString();

            // 遍历所有在线会话逐个发送；单会话异常不影响其他会话
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        // 单个会话发送失败仅告警，不中断其他会话的广播
                        log.warn("Failed to send market data to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // 兜底捕获，避免调度器因广播异常终止后续周期
            log.error("Broadcast market data error", e);
        }
    }
}
