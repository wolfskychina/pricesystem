package com.bank.trading.simexchange.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.simexchange.engine.MarketDataEngine;
import com.bank.trading.simexchange.model.MarketData;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/exchange/marketdata")
public class MarketDataController {

    private final MarketDataEngine marketDataEngine;

    public MarketDataController(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    public MarketDataController(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    public MarketDataController(MarketDataEngine marketDataEngine) {
        this.marketDataEngine = marketDataEngine;
    }

    @GetMapping("/{symbol}")
    public Result<MarketData> getMarketData(@PathVariable String symbol) {
        MarketData md = marketDataEngine.getLatest(symbol);
        if (md == null) {
            return Result.fail(404, "Symbol not found: " + symbol);
        }
        return Result.success(md);
    }

    @GetMapping("/list")
    public Result<List<MarketData>> listMarketData() {
        List<MarketData> list = marketDataEngine.getSymbols().stream()
                .map(marketDataEngine::getLatest)
                .filter(md -> md != null)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @GetMapping("/symbols")
    public Result<List<String>> listSymbols() {
        return Result.success(marketDataEngine.getSymbols());
    }
}
