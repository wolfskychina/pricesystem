package com.bank.trading.marketdata.service;

import com.bank.trading.common.core.dto.MarketDataDTO;
import com.bank.trading.common.core.event.MarketDataEvent;
import com.bank.trading.common.core.enums.EventType;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final Map<String, MarketDataDTO> latestQuotes = new ConcurrentHashMap<>();

    private final EventStoreService eventStoreService;
    private final OutboxService outboxService;

    @Value("${market-data.topic:market-data}")
    private String marketDataTopic;

    public void onMarketData(List<MarketDataDTO> marketDataList) {
        if (marketDataList == null || marketDataList.isEmpty()) {
            return;
        }
        for (MarketDataDTO md : marketDataList) {
            if (md.getSymbol() == null) {
                continue;
            }
            latestQuotes.put(md.getSymbol(), md);
            publishEvent(md);
        }
        log.debug("Updated market data for {} symbols", marketDataList.size());
    }

    private void publishEvent(MarketDataDTO md) {
        try {
            MarketDataEvent event = new MarketDataEvent(md.getSymbol());
            event.setSymbol(md.getSymbol());
            event.setBidPrice(md.getBidPrice());
            event.setAskPrice(md.getAskPrice());
            event.setLastPrice(md.getLastPrice());
            event.setBidQty(md.getBidQty());
            event.setAskQty(md.getAskQty());
            event.setLastQty(md.getLastQty());
            event.setVolume(md.getVolume());
            event.setTimestamp(md.getTimestamp());

            int shardId = 0;
            eventStoreService.appendEvent(event, "MARKET_DATA", md.getSymbol(), shardId);
            outboxService.saveEvent(marketDataTopic, event, shardId);
        } catch (Exception e) {
            log.warn("Failed to publish market data event for symbol: {}, error: {}",
                    md.getSymbol(), e.getMessage());
        }
    }

    public MarketDataDTO getLatest(String symbol) {
        return latestQuotes.get(symbol);
    }

    public BigDecimal getLastPrice(String symbol) {
        MarketDataDTO md = latestQuotes.get(symbol);
        return md != null ? md.getLastPrice() : null;
    }

    public List<MarketDataDTO> getAllLatest() {
        return new ArrayList<>(latestQuotes.values());
    }

    public boolean hasData(String symbol) {
        return latestQuotes.containsKey(symbol);
    }

    public int getSymbolCount() {
        return latestQuotes.size();
    }
}
