package com.bank.trading.marketdata.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bank.trading.common.core.dto.MarketDataDTO;
import com.bank.trading.marketdata.service.MarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class SimExchangeWebSocketClient extends TextWebSocketHandler {

    @Value("${market-data.sim-exchange.ws-url:ws://localhost:9000/ws/marketdata}")
    private String wsUrl;

    @Value("${market-data.sim-exchange.reconnect-delay:5000}")
    private long reconnectDelay;

    private final MarketDataService marketDataService;

    private WebSocketSession session;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public SimExchangeWebSocketClient(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @PostConstruct
    public void init() {
        running.set(true);
        connect();
    }

    @PreDestroy
    public void destroy() {
        running.set(false);
        closeSession();
    }

    public void connect() {
        if (connected.get()) {
            return;
        }
        try {
            WebSocketClient client = new StandardWebSocketClient();
            client.doHandshake(this, null, URI.create(wsUrl));
            log.info("Connecting to sim-exchange WebSocket: {}", wsUrl);
        } catch (Exception e) {
            log.error("Failed to connect to sim-exchange WebSocket: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${market-data.sim-exchange.reconnect-delay:5000}")
    public void reconnectIfNeeded() {
        if (running.get() && !connected.get()) {
            log.info("Attempting to reconnect to sim-exchange WebSocket...");
            connect();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.session = session;
        connected.set(true);
        log.info("Connected to sim-exchange WebSocket successfully");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            List<MarketDataDTO> marketDataList = parseMarketData(payload);
            if (!marketDataList.isEmpty()) {
                marketDataService.onMarketData(marketDataList);
            }
        } catch (Exception e) {
            log.warn("Failed to parse market data message: {}", e.getMessage());
        }
    }

    private List<MarketDataDTO> parseMarketData(String payload) {
        List<MarketDataDTO> result = new ArrayList<>();
        try {
            JSONArray array = JSON.parseArray(payload);
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                MarketDataDTO dto = new MarketDataDTO();
                dto.setSymbol(obj.getString("symbol"));
                dto.setBidPrice(obj.getBigDecimal("bidPrice"));
                dto.setAskPrice(obj.getBigDecimal("askPrice"));
                dto.setLastPrice(obj.getBigDecimal("lastPrice"));
                dto.setBidQty(obj.getBigDecimal("bidQty"));
                dto.setAskQty(obj.getBigDecimal("askQty"));
                dto.setLastQty(obj.getBigDecimal("lastQty"));
                dto.setVolume(obj.getLong("volume"));
                dto.setTimestamp(obj.getLong("timestamp"));
                result.add(dto);
            }
        } catch (Exception e) {
            log.warn("Parse market data error: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connected.set(false);
        log.warn("Disconnected from sim-exchange WebSocket: {}", status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        connected.set(false);
        log.error("WebSocket transport error: {}", exception.getMessage());
    }

    private void closeSession() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Error closing WebSocket session: {}", e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return connected.get();
    }
}
