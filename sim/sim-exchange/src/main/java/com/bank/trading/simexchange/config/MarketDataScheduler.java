package com.bank.trading.simexchange.config;

import com.bank.trading.simexchange.engine.MarketDataEngine;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataScheduler {

    private final MarketDataEngine marketDataEngine;
    private final SimExchangeProperties properties;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketDataScheduler.class);

    public MarketDataScheduler(MarketDataEngine marketDataEngine, SimExchangeProperties properties) {
        this.marketDataEngine = marketDataEngine;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        double intervalSeconds = properties.getIntervalMs() / 1000.0;
        marketDataEngine.init(properties.getSymbols(), intervalSeconds);
    }

    @Scheduled(fixedDelayString = "${sim-exchange.interval-ms:1000}")
    public void tick() {
        try {
            marketDataEngine.tick();
        } catch (Exception e) {
            log.error("Market data tick error", e);
        }
    }
}
