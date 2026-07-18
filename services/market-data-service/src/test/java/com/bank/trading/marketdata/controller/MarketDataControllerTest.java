package com.bank.trading.marketdata.controller;

import com.bank.trading.common.core.dto.MarketDataDTO;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.marketdata.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataControllerTest {

    private MarketDataController marketDataController;
    private MockMarketDataService mockMarketDataService;

    static class MockMarketDataService extends MarketDataService {
        List<MarketDataDTO> allData = new ArrayList<>();
        MarketDataDTO singleData;
        BigDecimal lastPrice;

        public MockMarketDataService() {
            super(null, null);
        }

        @Override
        public List<MarketDataDTO> getAllLatest() {
            return allData;
        }

        @Override
        public MarketDataDTO getLatest(String symbol) {
            return singleData;
        }

        @Override
        public BigDecimal getLastPrice(String symbol) {
            return lastPrice;
        }
    }

    @BeforeEach
    void setUp() {
        mockMarketDataService = new MockMarketDataService();
        marketDataController = new MarketDataController(mockMarketDataService);
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
    void listAll_shouldReturnAllMarketData() {
        List<MarketDataDTO> list = new ArrayList<>();
        list.add(createMarketData("AU2406", 520.50));
        list.add(createMarketData("AG2406", 6280.0));
        mockMarketDataService.allData = list;

        Result<List<MarketDataDTO>> result = marketDataController.listAll();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }

    @Test
    void getBySymbol_exist_shouldReturnData() {
        MarketDataDTO md = createMarketData("AU2406", 520.50);
        mockMarketDataService.singleData = md;

        Result<MarketDataDTO> result = marketDataController.getBySymbol("AU2406");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("AU2406", result.getData().getSymbol());
        assertEquals(0, new BigDecimal("520.50").compareTo(result.getData().getLastPrice()));
    }

    @Test
    void getBySymbol_notExist_shouldReturn404() {
        mockMarketDataService.singleData = null;

        Result<MarketDataDTO> result = marketDataController.getBySymbol("NOT_EXIST");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(404, result.getCode());
        assertTrue(result.getMessage().contains("NOT_EXIST"));
    }

    @Test
    void getLastPrice_exist_shouldReturnPrice() {
        mockMarketDataService.lastPrice = BigDecimal.valueOf(520.50);

        Result<BigDecimal> result = marketDataController.getLastPrice("AU2406");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, new BigDecimal("520.50").compareTo(result.getData()));
    }

    @Test
    void getLastPrice_notExist_shouldReturn404() {
        mockMarketDataService.lastPrice = null;

        Result<BigDecimal> result = marketDataController.getLastPrice("NOT_EXIST");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(404, result.getCode());
    }
}
