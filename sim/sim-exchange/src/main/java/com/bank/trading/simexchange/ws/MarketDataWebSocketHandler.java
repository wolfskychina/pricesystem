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

@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private final MarketDataEngine marketDataEngine;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketDataWebSocketHandler.class);

    public MarketDataWebSocketHandler(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    public MarketDataWebSocketHandler(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    public MarketDataWebSocketHandler(MarketDataEngine marketDataEngine, List<WebSocketSession> sessions) {
        this.marketDataEngine = marketDataEngine;
        this.sessions = sessions;
    }

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connected: {}, total sessions: {}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}, total sessions: {}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message from {}: {}", session.getId(), payload);
    }

    @Scheduled(fixedDelayString = "${sim-exchange.interval-ms:1000}")
    public void broadcast() {
        if (sessions.isEmpty()) {
            return;
        }
        List<String> symbols = marketDataEngine.getSymbols();
        if (symbols.isEmpty()) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (String symbol : symbols) {
                MarketData md = marketDataEngine.getLatest(symbol);
                if (md != null) {
                    if (!first) {
                        sb.append(",");
                    }
                    sb.append(JSON.toJSONString(md));
                    first = false;
                }
            }
            sb.append("]");
            String json = sb.toString();

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        log.warn("Failed to send market data to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Broadcast market data error", e);
        }
    }
}
