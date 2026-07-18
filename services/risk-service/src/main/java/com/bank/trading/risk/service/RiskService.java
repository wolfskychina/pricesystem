package com.bank.trading.risk.service;

import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.engine.DefaultRiskRuleEngine;
import com.bank.trading.risk.engine.RiskCheckContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class RiskService {

    private final DefaultRiskRuleEngine ruleEngine;

    public RiskService(DefaultRiskRuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    public RiskCheckResult checkPreTrade(RiskCheckRequest request) {
        RiskCheckContext context = buildContext(request);
        RiskCheckResult result = ruleEngine.evaluate(context);
        log.info("Risk check result: customer={}, symbol={}, side={}, passed={}, rule={}, reason={}",
                request.getCustomerId(), request.getSymbol(), request.getSide(),
                result.isPassed(), result.getRuleName(), result.getRejectReason());
        return result;
    }

    private RiskCheckContext buildContext(RiskCheckRequest request) {
        RiskCheckContext context = new RiskCheckContext();
        context.setCustomerId(request.getCustomerId());
        context.setSymbol(request.getSymbol());
        context.setSide(request.getSide());
        context.setOrderType(request.getOrderType());
        context.setQty(request.getQty());
        context.setOrderPrice(request.getPrice());
        context.setMarketMidPrice(request.getMarketMidPrice());
        context.setUsedCredit(request.getUsedCredit() != null
                ? request.getUsedCredit() : BigDecimal.ZERO);
        context.setDailyUsedAmount(request.getDailyUsedAmount() != null
                ? request.getDailyUsedAmount() : BigDecimal.ZERO);
        context.setCurrentPosition(request.getCurrentPosition() != null
                ? request.getCurrentPosition() : BigDecimal.ZERO);
        context.setTraceId(request.getTraceId());
        return context;
    }
}
