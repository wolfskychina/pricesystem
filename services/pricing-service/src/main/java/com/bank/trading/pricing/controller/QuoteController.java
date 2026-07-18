package com.bank.trading.pricing.controller;

import com.bank.trading.common.core.dto.QuoteDTO;
import com.bank.trading.common.core.enums.CustomerLevel;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.pricing.service.PricingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/quotes")
public class QuoteController {

    private final PricingService pricingService;

    public QuoteController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/{symbol}")
    public Result<QuoteDTO> getQuote(@PathVariable String symbol) {
        QuoteDTO quote = pricingService.getLatestQuote(symbol);
        if (quote == null) {
            return Result.fail(404, "Quote not available for symbol: " + symbol);
        }
        return Result.success(quote);
    }

    @PostMapping("/rfq")
    public Result<QuoteDTO> requestForQuote(@RequestParam String symbol,
                                            @RequestParam(required = false) CustomerLevel customerLevel) {
        QuoteDTO quote = pricingService.generateRfqQuote(symbol,
                customerLevel != null ? customerLevel : CustomerLevel.NORMAL);
        if (quote == null) {
            return Result.fail(404, "Quote not available for symbol: " + symbol);
        }
        return Result.success(quote);
    }
}
