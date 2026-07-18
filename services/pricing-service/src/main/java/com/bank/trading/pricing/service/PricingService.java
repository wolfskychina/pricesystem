package com.bank.trading.pricing.service;

import com.bank.trading.common.core.dto.QuoteDTO;
import com.bank.trading.common.core.enums.CustomerLevel;
import com.bank.trading.common.core.event.CustomerQuoteEvent;
import com.bank.trading.common.persistence.eventstore.EventStoreService;
import com.bank.trading.common.persistence.outbox.OutboxService;
import com.bank.trading.pricing.config.PricingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class PricingService {

    private final PricingProperties pricingProperties;
    private final EventStoreService eventStoreService;
    private final OutboxService outboxService;

    private final Map<String, BigDecimal> marketBidPrices = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> marketAskPrices = new ConcurrentHashMap<>();
    private final Map<String, QuoteDTO> latestQuotes = new ConcurrentHashMap<>();
    private final AtomicLong quoteIdGenerator = new AtomicLong(0);

    private static final long QUOTE_VALIDITY_MS = 30_000L;
    private static final int BPS_DIVISOR = 10000;

    public PricingService(PricingProperties pricingProperties,
                          EventStoreService eventStoreService,
                          OutboxService outboxService) {
        this.pricingProperties = pricingProperties;
        this.eventStoreService = eventStoreService;
        this.outboxService = outboxService;
    }

    public void onMarketData(String symbol, BigDecimal bidPrice, BigDecimal askPrice) {
        if (symbol == null || bidPrice == null || askPrice == null) {
            return;
        }
        marketBidPrices.put(symbol, bidPrice);
        marketAskPrices.put(symbol, askPrice);
        generateQuote(symbol);
    }

    private void generateQuote(String symbol) {
        BigDecimal marketBid = marketBidPrices.get(symbol);
        BigDecimal marketAsk = marketAskPrices.get(symbol);
        if (marketBid == null || marketAsk == null) {
            return;
        }

        int bidSpreadBps = getBidSpreadBps(symbol);
        int askSpreadBps = getAskSpreadBps(symbol);

        BigDecimal midPrice = marketBid.add(marketAsk).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        BigDecimal customerBid = calculateCustomerBid(midPrice, bidSpreadBps);
        BigDecimal customerAsk = calculateCustomerAsk(midPrice, askSpreadBps);
        BigDecimal spread = customerAsk.subtract(customerBid);

        long quoteId = quoteIdGenerator.incrementAndGet();
        long now = System.currentTimeMillis();
        long validUntil = now + QUOTE_VALIDITY_MS;

        QuoteDTO quote = new QuoteDTO();
        quote.setSymbol(symbol);
        quote.setMarketBidPrice(marketBid);
        quote.setMarketAskPrice(marketAsk);
        quote.setCustomerBidPrice(customerBid);
        quote.setCustomerAskPrice(customerAsk);
        quote.setSpread(spread);
        quote.setQuoteId(quoteId);
        quote.setValidUntil(validUntil);
        quote.setTimestamp(now);

        latestQuotes.put(symbol, quote);

        publishQuoteEvent(quote);
        log.debug("Generated quote for {}: bid={}, ask={}, spread={}", symbol, customerBid, customerAsk, spread);
    }

    private BigDecimal calculateCustomerBid(BigDecimal marketAskPrice, int spreadBps) {
        BigDecimal spread = marketAskPrice.multiply(BigDecimal.valueOf(spreadBps))
                .divide(BigDecimal.valueOf(BPS_DIVISOR), 6, RoundingMode.HALF_UP);
        return marketAskPrice.subtract(spread);
    }

    private BigDecimal calculateCustomerAsk(BigDecimal marketBidPrice, int spreadBps) {
        BigDecimal spread = marketBidPrice.multiply(BigDecimal.valueOf(spreadBps))
                .divide(BigDecimal.valueOf(BPS_DIVISOR), 6, RoundingMode.HALF_UP);
        return marketBidPrice.add(spread);
    }

    private int getBidSpreadBps(String symbol) {
        for (PricingProperties.SpreadConfig config : pricingProperties.getSpreadConfigs()) {
            if (symbol.equals(config.getSymbol())) {
                return config.getBidSpreadBps();
            }
        }
        return pricingProperties.getDefaultBidSpreadBps();
    }

    private int getAskSpreadBps(String symbol) {
        for (PricingProperties.SpreadConfig config : pricingProperties.getSpreadConfigs()) {
            if (symbol.equals(config.getSymbol())) {
                return config.getAskSpreadBps();
            }
        }
        return pricingProperties.getDefaultAskSpreadBps();
    }

    private void publishQuoteEvent(QuoteDTO quote) {
        try {
            CustomerQuoteEvent event = new CustomerQuoteEvent(quote.getSymbol(), "DEFAULT");
            event.setCustomerBidPrice(quote.getCustomerBidPrice());
            event.setCustomerAskPrice(quote.getCustomerAskPrice());
            event.setMarketBidPrice(quote.getMarketBidPrice());
            event.setMarketAskPrice(quote.getMarketAskPrice());
            event.setSpread(quote.getSpread());
            event.setQuoteId(quote.getQuoteId());
            event.setValidUntil(quote.getValidUntil());

            int shardId = 0;
            eventStoreService.appendEvent(event, "QUOTE", quote.getSymbol(), shardId);
            outboxService.saveEvent(pricingProperties.getQuoteTopic(), event, shardId);
        } catch (Exception e) {
            log.warn("Failed to publish quote event for symbol: {}, error: {}", quote.getSymbol(), e.getMessage());
        }
    }

    public QuoteDTO getLatestQuote(String symbol) {
        QuoteDTO quote = latestQuotes.get(symbol);
        if (quote == null) {
            return null;
        }
        if (quote.getValidUntil() != null && quote.getValidUntil() < System.currentTimeMillis()) {
            return null;
        }
        return quote;
    }

    public QuoteDTO generateRfqQuote(String symbol, CustomerLevel customerLevel) {
        BigDecimal marketBid = marketBidPrices.get(symbol);
        BigDecimal marketAsk = marketAskPrices.get(symbol);
        if (marketBid == null || marketAsk == null) {
            return null;
        }

        int spreadMultiplier = getSpreadMultiplier(customerLevel);
        int baseBidSpread = getBidSpreadBps(symbol) * spreadMultiplier / 100;
        int baseAskSpread = getAskSpreadBps(symbol) * spreadMultiplier / 100;

        BigDecimal midPrice = marketBid.add(marketAsk).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        BigDecimal customerBid = calculateCustomerBid(midPrice, baseBidSpread);
        BigDecimal customerAsk = calculateCustomerAsk(midPrice, baseAskSpread);
        BigDecimal spread = customerAsk.subtract(customerBid);

        long quoteId = quoteIdGenerator.incrementAndGet();
        long now = System.currentTimeMillis();
        long validUntil = now + QUOTE_VALIDITY_MS;

        QuoteDTO quote = new QuoteDTO();
        quote.setSymbol(symbol);
        quote.setMarketBidPrice(marketBid);
        quote.setMarketAskPrice(marketAsk);
        quote.setCustomerBidPrice(customerBid);
        quote.setCustomerAskPrice(customerAsk);
        quote.setSpread(spread);
        quote.setQuoteId(quoteId);
        quote.setValidUntil(validUntil);
        quote.setTimestamp(now);

        return quote;
    }

    private int getSpreadMultiplier(CustomerLevel level) {
        if (level == null) {
            return 100;
        }
        switch (level) {
            case INSTITUTION:
                return 50;
            case VIP:
                return 70;
            case NORMAL:
            default:
                return 100;
        }
    }

    public boolean hasMarketData(String symbol) {
        return marketBidPrices.containsKey(symbol) && marketAskPrices.containsKey(symbol);
    }

    public int getSymbolCount() {
        return latestQuotes.size();
    }
}
