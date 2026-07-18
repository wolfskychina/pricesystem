package com.bank.trading.marketdata.service;

import com.bank.trading.common.core.dto.MarketDataDTO;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataServiceTest {

    private MarketDataService marketDataService;
    private MockEventStoreService eventStoreService;
    private MockOutboxService outboxService;

    static class MockEventStoreService implements EventStoreService {
        int appendCount = 0;
        String lastAggregateType;
        String lastAggregateId;
        int lastShardId;

        @Override
        public void appendEvent(com.bank.trading.common.core.event.BaseEvent event,
                                String aggregateType, String aggregateId, int shardId) {
            appendCount++;
            lastAggregateType = aggregateType;
            lastAggregateId = aggregateId;
            lastShardId = shardId;
        }

        @Override
        public List<com.bank.trading.common.persistence.eventstore.EventStoreRecord> findByCustomer(String customerId, int shardId) {
            return null;
        }

        @Override
        public List<com.bank.trading.common.persistence.eventstore.EventStoreRecord> findByAggregate(String aggregateType, String aggregateId, int shardId) {
            return null;
        }
    }

    static class MockOutboxService implements OutboxService {
        int saveCount = 0;
        String lastTopic;
        int lastShardId;
        boolean shouldThrow = false;

        @Override
        public void saveEvent(String topic, com.bank.trading.common.core.event.BaseEvent event, int shardId) {
            if (shouldThrow) {
                throw new RuntimeException("DB error");
            }
            saveCount++;
            lastTopic = topic;
            lastShardId = shardId;
        }

        @Override
        public boolean isSent(String eventId) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        eventStoreService = new MockEventStoreService();
        outboxService = new MockOutboxService();
        marketDataService = new MarketDataService(eventStoreService, outboxService);
        try {
            java.lang.reflect.Field field = MarketDataService.class.getDeclaredField("marketDataTopic");
            field.setAccessible(true);
            field.set(marketDataService, "market-data");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MarketDataDTO createMarketData(String symbol, double lastPrice) {
        MarketDataDTO dto = new MarketDataDTO();
        dto.setSymbol(symbol);
        dto.setBidPrice(BigDecimal.valueOf(lastPrice - 0.01));
        dto.setAskPrice(BigDecimal.valueOf(lastPrice + 0.01));
        dto.setLastPrice(BigDecimal.valueOf(lastPrice));
        dto.setBidQty(BigDecimal.valueOf(100));
        dto.setAskQty(BigDecimal.valueOf(200));
        dto.setLastQty(BigDecimal.valueOf(10));
        dto.setVolume(1000L);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }

    @Test
    void onMarketData_nullList_shouldNotThrow() {
        assertDoesNotThrow(() -> marketDataService.onMarketData(null));
        assertEquals(0, marketDataService.getSymbolCount());
    }

    @Test
    void onMarketData_emptyList_shouldNotThrow() {
        assertDoesNotThrow(() -> marketDataService.onMarketData(new ArrayList<>()));
        assertEquals(0, marketDataService.getSymbolCount());
    }

    @Test
    void onMarketData_singleSymbol_shouldStoreAndPublish() {
        MarketDataDTO md = createMarketData("AU2406", 520.50);
        List<MarketDataDTO> list = new ArrayList<>();
        list.add(md);

        marketDataService.onMarketData(list);

        assertTrue(marketDataService.hasData("AU2406"));
        assertEquals(1, marketDataService.getSymbolCount());
        assertEquals(0, new BigDecimal("520.50").compareTo(marketDataService.getLastPrice("AU2406")));
        assertEquals(1, eventStoreService.appendCount);
        assertEquals("MARKET_DATA", eventStoreService.lastAggregateType);
        assertEquals("AU2406", eventStoreService.lastAggregateId);
        assertEquals(1, outboxService.saveCount);
        assertEquals("market-data", outboxService.lastTopic);
    }

    @Test
    void onMarketData_multipleSymbols_shouldStoreAll() {
        List<MarketDataDTO> list = new ArrayList<>();
        list.add(createMarketData("AU2406", 520.50));
        list.add(createMarketData("AG2406", 6280.0));
        list.add(createMarketData("CU2406", 72500.0));

        marketDataService.onMarketData(list);

        assertEquals(3, marketDataService.getSymbolCount());
        assertTrue(marketDataService.hasData("AU2406"));
        assertTrue(marketDataService.hasData("AG2406"));
        assertTrue(marketDataService.hasData("CU2406"));
        assertEquals(0, new BigDecimal("6280.0").compareTo(marketDataService.getLastPrice("AG2406")));
        assertEquals(3, eventStoreService.appendCount);
        assertEquals(3, outboxService.saveCount);
    }

    @Test
    void onMarketData_nullSymbol_shouldSkip() {
        MarketDataDTO md = createMarketData("AU2406", 520.50);
        md.setSymbol(null);
        List<MarketDataDTO> list = new ArrayList<>();
        list.add(md);

        marketDataService.onMarketData(list);

        assertEquals(0, marketDataService.getSymbolCount());
        assertEquals(0, eventStoreService.appendCount);
        assertEquals(0, outboxService.saveCount);
    }

    @Test
    void onMarketData_updateExistingSymbol_shouldOverwrite() {
        List<MarketDataDTO> list1 = new ArrayList<>();
        list1.add(createMarketData("AU2406", 520.50));
        marketDataService.onMarketData(list1);
        assertEquals(0, new BigDecimal("520.50").compareTo(marketDataService.getLastPrice("AU2406")));

        List<MarketDataDTO> list2 = new ArrayList<>();
        list2.add(createMarketData("AU2406", 521.00));
        marketDataService.onMarketData(list2);

        assertEquals(1, marketDataService.getSymbolCount());
        assertEquals(0, new BigDecimal("521.00").compareTo(marketDataService.getLastPrice("AU2406")));
        assertEquals(2, eventStoreService.appendCount);
    }

    @Test
    void getLatest_notExist_shouldReturnNull() {
        assertNull(marketDataService.getLatest("NOT_EXIST"));
    }

    @Test
    void getLastPrice_notExist_shouldReturnNull() {
        assertNull(marketDataService.getLastPrice("NOT_EXIST"));
    }

    @Test
    void getAllLatest_empty_shouldReturnEmptyList() {
        List<MarketDataDTO> all = marketDataService.getAllLatest();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    void getAllLatest_withData_shouldReturnAll() {
        List<MarketDataDTO> list = new ArrayList<>();
        list.add(createMarketData("AU2406", 520.50));
        list.add(createMarketData("AG2406", 6280.0));
        marketDataService.onMarketData(list);

        List<MarketDataDTO> all = marketDataService.getAllLatest();
        assertEquals(2, all.size());
    }

    @Test
    void onMarketData_eventStoreError_shouldNotThrow() {
        outboxService.shouldThrow = true;

        MarketDataDTO md = createMarketData("AU2406", 520.50);
        List<MarketDataDTO> list = new ArrayList<>();
        list.add(md);

        assertDoesNotThrow(() -> marketDataService.onMarketData(list));
        assertTrue(marketDataService.hasData("AU2406"));
    }
}
