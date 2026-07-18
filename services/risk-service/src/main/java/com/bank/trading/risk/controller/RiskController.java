package com.bank.trading.risk.controller;

import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.risk.service.RiskService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/risk")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @PostMapping("/pre-trade")
    public Result<RiskCheckResult> checkPreTrade(@RequestBody RiskCheckRequest request) {
        try {
            RiskCheckResult result = riskService.checkPreTrade(request);
            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(500, "Risk check error: " + e.getMessage());
        }
    }
}
