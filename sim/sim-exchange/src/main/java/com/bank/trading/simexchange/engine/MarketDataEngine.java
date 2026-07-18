package com.bank.trading.simexchange.engine;

import com.bank.trading.simexchange.model.MarketData;
import com.bank.trading.simexchange.model.SymbolConfig;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MarketDataEngine {


    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketDataEngine.class);

    private final Map<String, SymbolConfig> symbolConfigs = new ConcurrentHashMap<>();
    private final Map<String, GbmPriceGenerator> generators = new ConcurrentHashMap<>();
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();
    private final Map<String, MarketData> latestMarketData = new ConcurrentHashMap<>();
    private final Map<String, Long> volumes = new ConcurrentHashMap<>();

    private final List<String> symbols = new CopyOnWriteArrayList<>();

    public void init(List<SymbolConfig> configs, double intervalSeconds) {
        for (SymbolConfig config : configs) {
            symbolConfigs.put(config.getCode(), config);
            generators.put(config.getCode(),
                    new GbmPriceGenerator(config.getDrift(), config.getVolatility(), intervalSeconds));
            currentPrices.put(config.getCode(), config.getInitialPrice().doubleValue());
            volumes.put(config.getCode(), 0L);
            symbols.add(config.getCode());
            generateMarketData(config.getCode());
        }
        log.info("MarketDataEngine initialized with {} symbols: {}", symbols.size(), symbols);
    }

    public void tick() {
        for (String symbol : symbols) {
            generateMarketData(symbol);
        }
    }

    private void generateMarketData(String symbol) {
        SymbolConfig config = symbolConfigs.get(symbol);
        GbmPriceGenerator gen = generators.get(symbol);
        double currentPrice = currentPrices.get(symbol);

        double newPrice = gen.nextPrice(currentPrice);
        currentPrices.put(symbol, newPrice);

        BigDecimal tickSize = config.getTickSize();
        BigDecimal lastPrice = roundToTick(newPrice, tickSize);

        BigDecimal spread = tickSize.multiply(BigDecimal.valueOf(1 + (int)(Math.random() * 3)));
        BigDecimal bidPrice = lastPrice.subtract(spread).max(tickSize);
        BigDecimal askPrice = lastPrice.add(spread);

        BigDecimal lastQty = BigDecimal.valueOf(config.getMinQty().intValue() * (1 + (int)(Math.random() * 10)));
        BigDecimal bidQty = BigDecimal.valueOf(config.getMinQty().intValue() * (5 + (int)(Math.random() * 20)));
        BigDecimal askQty = BigDecimal.valueOf(config.getMinQty().intValue() * (5 + (int)(Math.random() * 20)));

        long volume = volumes.get(symbol) + lastQty.longValue();
        volumes.put(symbol, volume);

        MarketData md = new MarketData();
        md.setSymbol(symbol);
        md.setBidPrice(bidPrice);
        md.setAskPrice(askPrice);
        md.setLastPrice(lastPrice);
        md.setBidQty(bidQty);
        md.setAskQty(askQty);
        md.setLastQty(lastQty);
        md.setVolume(volume);
        md.setTimestamp(System.currentTimeMillis());

        latestMarketData.put(symbol, md);
    }

    private BigDecimal roundToTick(double price, BigDecimal tickSize) {
        BigDecimal bd = BigDecimal.valueOf(price);
        BigDecimal ticks = bd.divide(tickSize, 0, RoundingMode.HALF_UP);
        return ticks.multiply(tickSize).setScale(tickSize.scale(), RoundingMode.HALF_UP);
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public MarketData getLatest(String symbol) {
        return latestMarketData.get(symbol);
    }

    public BigDecimal getLastPrice(String symbol) {
        MarketData md = latestMarketData.get(symbol);
        return md != null ? md.getLastPrice() : null;
    }
}
