package com.bank.trading.pricing.service;

import com.bank.trading.common.core.dto.QuoteDTO;
import com.bank.trading.common.core.enums.CustomerLevel;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.outbox.OutboxService;
import com.bank.trading.pricing.config.PricingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private PricingService pricingService;
    private MockEventStoreService eventStoreService;
    private MockOutboxService outboxService;
    private PricingProperties pricingProperties;

    static class MockEventStoreService implements EventStoreService {
        int appendCount = 0;

        @Override
        public void appendEvent(com.bank.trading.common.core.event.BaseEvent event,
                                String aggregateType, String aggregateId, int shardId) {
            appendCount++;
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

        @Override
        public void saveEvent(String topic, com.bank.trading.common.core.event.BaseEvent event, int shardId) {
            saveCount++;
        }

        @Override
        public boolean isSent(String eventId) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        pricingProperties = new PricingProperties();
        pricingProperties.setDefaultBidSpreadBps(5);
        pricingProperties.setDefaultAskSpreadBps(5);

        List<PricingProperties.SpreadConfig> configs = new ArrayList<>();
        PricingProperties.SpreadConfig auConfig = new PricingProperties.SpreadConfig();
        auConfig.setSymbol("AU2406");
        auConfig.setBidSpreadBps(3);
        auConfig.setAskSpreadBps(3);
        configs.add(auConfig);
        pricingProperties.setSpreadConfigs(configs);

        eventStoreService = new MockEventStoreService();
        outboxService = new MockOutboxService();
        pricingService = new PricingService(pricingProperties, eventStoreService, outboxService);
    }

    @Test
    void onMarketData_nullSymbol_shouldNotThrow() {
        assertDoesNotThrow(() -> pricingService.onMarketData(null, BigDecimal.valueOf(100), BigDecimal.valueOf(101)));
        assertEquals(0, pricingService.getSymbolCount());
    }

    @Test
    void onMarketData_nullPrice_shouldNotThrow() {
        assertDoesNotThrow(() -> pricingService.onMarketData("AU2406", null, BigDecimal.valueOf(101)));
        assertEquals(0, pricingService.getSymbolCount());
    }

    @Test
    void onMarketData_validData_shouldGenerateQuote() {
        BigDecimal bid = new BigDecimal("520.00");
        BigDecimal ask = new BigDecimal("520.50");

        pricingService.onMarketData("AU2406", bid, ask);

        assertTrue(pricingService.hasMarketData("AU2406"));
        QuoteDTO quote = pricingService.getLatestQuote("AU2406");
        assertNotNull(quote);
        assertEquals("AU2406", quote.getSymbol());
        assertEquals(0, bid.compareTo(quote.getMarketBidPrice()));
        assertEquals(0, ask.compareTo(quote.getMarketAskPrice()));
        assertNotNull(quote.getCustomerBidPrice());
        assertNotNull(quote.getCustomerAskPrice());
        assertNotNull(quote.getSpread());
        assertNotNull(quote.getQuoteId());
        assertNotNull(quote.getValidUntil());
        assertTrue(quote.getCustomerBidPrice().compareTo(quote.getCustomerAskPrice()) < 0,
                "customer bid should be less than customer ask");
        assertEquals(1, eventStoreService.appendCount);
        assertEquals(1, outboxService.saveCount);
    }

    @Test
    void onMarketData_defaultSpread_shouldUseDefaultBps() {
        BigDecimal bid = new BigDecimal("100.00");
        BigDecimal ask = new BigDecimal("100.20");

        pricingService.onMarketData("UNKNOWN_SYMBOL", bid, ask);

        QuoteDTO quote = pricingService.getLatestQuote("UNKNOWN_SYMBOL");
        assertNotNull(quote);
        assertTrue(quote.getSpread().compareTo(BigDecimal.ZERO) > 0,
                "spread should be positive");
    }

    @Test
    void onMarketData_customSpread_shouldBeSmallerThanDefault() {
        BigDecimal bid = new BigDecimal("520.00");
        BigDecimal ask = new BigDecimal("520.50");

        pricingService.onMarketData("AU2406", bid, ask);
        QuoteDTO customQuote = pricingService.getLatestQuote("AU2406");

        pricingService.onMarketData("UNKNOWN", bid, ask);
        QuoteDTO defaultQuote = pricingService.getLatestQuote("UNKNOWN");

        assertTrue(customQuote.getSpread().compareTo(defaultQuote.getSpread()) < 0,
                "configured symbol (3 bps) should have smaller spread than default (5 bps)");
    }

    @Test
    void onMarketData_updateMarketData_shouldGenerateNewQuote() {
        pricingProperties.setThrottleWindowMs(0);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote1 = pricingService.getLatestQuote("AU2406");
        long quoteId1 = quote1.getQuoteId();

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(521.00), BigDecimal.valueOf(521.50));
        QuoteDTO quote2 = pricingService.getLatestQuote("AU2406");

        assertNotEquals(quoteId1, quote2.getQuoteId());
        assertEquals(0, new BigDecimal("521.00").compareTo(quote2.getMarketBidPrice()));
        assertEquals(2, eventStoreService.appendCount);
    }

    @Test
    void getLatestQuote_notExist_shouldReturnNull() {
        assertNull(pricingService.getLatestQuote("NOT_EXIST"));
    }

    @Test
    void generateRfqQuote_noMarketData_shouldReturnNull() {
        QuoteDTO quote = pricingService.generateRfqQuote("NOT_EXIST", CustomerLevel.NORMAL);
        assertNull(quote);
    }

    @Test
    void generateRfqQuote_normalCustomer_shouldUseNormalSpread() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));

        QuoteDTO normalQuote = pricingService.generateRfqQuote("AU2406", CustomerLevel.NORMAL);
        QuoteDTO vipQuote = pricingService.generateRfqQuote("AU2406", CustomerLevel.VIP);

        assertNotNull(normalQuote);
        assertNotNull(vipQuote);
        assertTrue(vipQuote.getSpread().compareTo(normalQuote.getSpread()) < 0,
                "VIP spread should be smaller than normal spread");
    }

    @Test
    void generateRfqQuote_vipCustomer_shouldHaveTighterSpreadThanNormal() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));

        QuoteDTO vip = pricingService.generateRfqQuote("AU2406", CustomerLevel.VIP);
        QuoteDTO normal = pricingService.generateRfqQuote("AU2406", CustomerLevel.NORMAL);

        assertTrue(vip.getSpread().compareTo(normal.getSpread()) < 0,
                "VIP spread should be smaller than normal spread");
    }

    @Test
    void generateRfqQuote_institutionCustomer_shouldHaveTightestSpread() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));

        QuoteDTO institution = pricingService.generateRfqQuote("AU2406", CustomerLevel.INSTITUTION);
        QuoteDTO vip = pricingService.generateRfqQuote("AU2406", CustomerLevel.VIP);
        QuoteDTO normal = pricingService.generateRfqQuote("AU2406", CustomerLevel.NORMAL);

        assertTrue(institution.getSpread().compareTo(vip.getSpread()) < 0,
                "institution spread should be smaller than vip spread");
        assertTrue(vip.getSpread().compareTo(normal.getSpread()) < 0,
                "vip spread should be smaller than normal spread");
    }

    @Test
    void generateRfqQuote_nullLevel_shouldUseNormal() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));

        QuoteDTO nullLevel = pricingService.generateRfqQuote("AU2406", null);
        QuoteDTO normal = pricingService.generateRfqQuote("AU2406", CustomerLevel.NORMAL);

        assertEquals(0, nullLevel.getSpread().compareTo(normal.getSpread()));
    }

    @Test
    void hasMarketData_noData_shouldReturnFalse() {
        assertFalse(pricingService.hasMarketData("AU2406"));
    }

    @Test
    void hasMarketData_withData_shouldReturnTrue() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        assertTrue(pricingService.hasMarketData("AU2406"));
    }

    @Test
    void getSymbolCount_multipleSymbols_shouldReturnCorrectCount() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        pricingService.onMarketData("AG2406", BigDecimal.valueOf(6280.0), BigDecimal.valueOf(6285.0));

        assertEquals(2, pricingService.getSymbolCount());
    }

    @Test
    void customerBid_shouldBeLowerThanMidPrice() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote = pricingService.getLatestQuote("AU2406");

        BigDecimal midPrice = quote.getMarketBidPrice().add(quote.getMarketAskPrice())
                .divide(BigDecimal.valueOf(2), 6, BigDecimal.ROUND_HALF_UP);
        assertTrue(quote.getCustomerBidPrice().compareTo(midPrice) < 0,
                "customer bid should be lower than mid price");
    }

    @Test
    void customerAsk_shouldBeHigherThanMidPrice() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote = pricingService.getLatestQuote("AU2406");

        BigDecimal midPrice = quote.getMarketBidPrice().add(quote.getMarketAskPrice())
                .divide(BigDecimal.valueOf(2), 6, BigDecimal.ROUND_HALF_UP);
        assertTrue(quote.getCustomerAskPrice().compareTo(midPrice) > 0,
                "customer ask should be higher than mid price");
    }

    @Test
    void quoteValidity_shouldBe3SecondsByDefault() {
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote = pricingService.getLatestQuote("AU2406");

        assertNotNull(quote);
        assertNotNull(quote.getValidUntil());
        assertNotNull(quote.getTimestamp());

        long validityMs = quote.getValidUntil() - quote.getTimestamp();
        assertEquals(3000L, validityMs, "Quote validity should be 3 seconds (3000ms)");
    }

    @Test
    void quoteValidity_shouldBeConfigurable() {
        pricingProperties.setQuoteTtlSeconds(5);
        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote = pricingService.getLatestQuote("AU2406");

        assertNotNull(quote);
        long validityMs = quote.getValidUntil() - quote.getTimestamp();
        assertEquals(5000L, validityMs, "Quote validity should be 5 seconds when configured");
    }

    @Test
    void throttle_withinWindow_shouldNotGenerateNewQuote() {
        pricingProperties.setThrottleWindowMs(1000);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote1 = pricingService.getLatestQuote("AU2406");
        long quoteId1 = quote1.getQuoteId();
        int eventCount1 = eventStoreService.appendCount;

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(521.00), BigDecimal.valueOf(521.50));
        QuoteDTO quote2 = pricingService.getLatestQuote("AU2406");

        assertEquals(quoteId1, quote2.getQuoteId(),
                "Quote ID should not change within throttle window");
        assertEquals(eventCount1, eventStoreService.appendCount,
                "No new event should be published within throttle window");
        assertEquals(0, new BigDecimal("520.00").compareTo(quote2.getMarketBidPrice()),
                "Market price should not be updated in quote within throttle window");
    }

    @Test
    void throttle_afterWindow_shouldGenerateNewQuote() throws InterruptedException {
        pricingProperties.setThrottleWindowMs(50);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote1 = pricingService.getLatestQuote("AU2406");
        long quoteId1 = quote1.getQuoteId();

        Thread.sleep(60);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(521.00), BigDecimal.valueOf(521.50));
        QuoteDTO quote2 = pricingService.getLatestQuote("AU2406");

        assertNotEquals(quoteId1, quote2.getQuoteId(),
                "Quote ID should change after throttle window expires");
        assertEquals(0, new BigDecimal("521.00").compareTo(quote2.getMarketBidPrice()),
                "Market price should be updated after throttle window");
    }

    @Test
    void throttle_differentSymbols_shouldBeIndependent() {
        pricingProperties.setThrottleWindowMs(1000);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        pricingService.onMarketData("AG2406", BigDecimal.valueOf(6280.0), BigDecimal.valueOf(6285.0));

        assertEquals(2, eventStoreService.appendCount,
                "Different symbols should not throttle each other");
        assertEquals(2, pricingService.getSymbolCount());
    }

    @Test
    void rfqQuote_shouldNotBeThrottled() {
        pricingProperties.setThrottleWindowMs(1000);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote1 = pricingService.getLatestQuote("AU2406");
        long quoteId1 = quote1.getQuoteId();

        QuoteDTO rfqQuote = pricingService.generateRfqQuote("AU2406", CustomerLevel.NORMAL);

        assertNotNull(rfqQuote);
        assertNotEquals(quoteId1, rfqQuote.getQuoteId(),
                "RFQ quote should have different quote ID (not throttled)");
    }

    @Test
    void getLatestQuote_expiredQuote_shouldReturnNull() {
        pricingProperties.setQuoteTtlSeconds(1);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO quote = pricingService.getLatestQuote("AU2406");
        assertNotNull(quote);

        quote.setValidUntil(System.currentTimeMillis() - 1000);

        QuoteDTO expiredQuote = pricingService.getLatestQuote("AU2406");
        assertNull(expiredQuote, "Expired quote should return null");
    }

    @Test
    void rfqQuote_validity_shouldMatchConfiguredTtl() {
        pricingProperties.setQuoteTtlSeconds(10);

        pricingService.onMarketData("AU2406", BigDecimal.valueOf(520.00), BigDecimal.valueOf(520.50));
        QuoteDTO rfqQuote = pricingService.generateRfqQuote("AU2406", CustomerLevel.VIP);

        assertNotNull(rfqQuote);
        assertNotNull(rfqQuote.getValidUntil());
        assertNotNull(rfqQuote.getTimestamp());

        long validityMs = rfqQuote.getValidUntil() - rfqQuote.getTimestamp();
        assertEquals(10000L, validityMs,
                "RFQ quote validity should match configured TTL");
    }
}
