package com.bank.trading.pricing.controller;

import com.bank.trading.common.core.dto.QuoteDTO;
import com.bank.trading.common.core.enums.CustomerLevel;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.pricing.service.PricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class QuoteControllerTest {

    private QuoteController quoteController;
    private MockPricingService mockPricingService;

    static class MockPricingService extends PricingService {
        QuoteDTO latestQuote;
        QuoteDTO rfqQuote;

        public MockPricingService() {
            super(null, null, null);
        }

        @Override
        public QuoteDTO getLatestQuote(String symbol) {
            return latestQuote;
        }

        @Override
        public QuoteDTO generateRfqQuote(String symbol, CustomerLevel customerLevel) {
            return rfqQuote;
        }
    }

    @BeforeEach
    void setUp() {
        mockPricingService = new MockPricingService();
        quoteController = new QuoteController(mockPricingService);
    }

    private QuoteDTO createQuote(String symbol) {
        QuoteDTO quote = new QuoteDTO();
        quote.setSymbol(symbol);
        quote.setMarketBidPrice(BigDecimal.valueOf(520.00));
        quote.setMarketAskPrice(BigDecimal.valueOf(520.50));
        quote.setCustomerBidPrice(BigDecimal.valueOf(519.50));
        quote.setCustomerAskPrice(BigDecimal.valueOf(521.00));
        quote.setSpread(BigDecimal.valueOf(1.50));
        quote.setQuoteId(1L);
        quote.setValidUntil(System.currentTimeMillis() + 30000);
        quote.setTimestamp(System.currentTimeMillis());
        return quote;
    }

    @Test
    void getQuote_exist_shouldReturnQuote() {
        QuoteDTO quote = createQuote("AU2406");
        mockPricingService.latestQuote = quote;

        Result<QuoteDTO> result = quoteController.getQuote("AU2406");

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("AU2406", result.getData().getSymbol());
    }

    @Test
    void getQuote_notExist_shouldReturn404() {
        mockPricingService.latestQuote = null;

        Result<QuoteDTO> result = quoteController.getQuote("NOT_EXIST");

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(404, result.getCode());
        assertTrue(result.getMessage().contains("NOT_EXIST"));
    }

    @Test
    void requestForQuote_withCustomerLevel_shouldReturnQuote() {
        QuoteDTO quote = createQuote("AU2406");
        mockPricingService.rfqQuote = quote;

        Result<QuoteDTO> result = quoteController.requestForQuote("AU2406", CustomerLevel.VIP);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("AU2406", result.getData().getSymbol());
    }

    @Test
    void requestForQuote_nullLevel_shouldUseNormal() {
        QuoteDTO quote = createQuote("AU2406");
        mockPricingService.rfqQuote = quote;

        Result<QuoteDTO> result = quoteController.requestForQuote("AU2406", null);

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    void requestForQuote_noMarketData_shouldReturn404() {
        mockPricingService.rfqQuote = null;

        Result<QuoteDTO> result = quoteController.requestForQuote("NOT_EXIST", CustomerLevel.NORMAL);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(404, result.getCode());
    }
}
