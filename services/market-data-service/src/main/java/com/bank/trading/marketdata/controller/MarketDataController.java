package com.bank.trading.marketdata.controller;

import com.bank.trading.common.core.dto.MarketDataDTO;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.marketdata.service.MarketDataService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/marketdata")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping
    public Result<List<MarketDataDTO>> listAll() {
        return Result.success(marketDataService.getAllLatest());
    }

    @GetMapping("/{symbol}")
    public Result<MarketDataDTO> getBySymbol(@PathVariable String symbol) {
        MarketDataDTO md = marketDataService.getLatest(symbol);
        if (md == null) {
            return Result.fail(404, "Market data not found for symbol: " + symbol);
        }
        return Result.success(md);
    }

    @GetMapping("/{symbol}/price")
    public Result<BigDecimal> getLastPrice(@PathVariable String symbol) {
        BigDecimal price = marketDataService.getLastPrice(symbol);
        if (price == null) {
            return Result.fail(404, "Market data not found for symbol: " + symbol);
        }
        return Result.success(price);
    }
}
