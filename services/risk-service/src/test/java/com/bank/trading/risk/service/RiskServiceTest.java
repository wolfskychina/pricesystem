package com.bank.trading.risk.service;

import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.DefaultRiskRuleEngine;
import com.bank.trading.risk.rules.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RiskServiceTest {

    private RiskService riskService;

    @BeforeEach
    void setUp() {
        RiskProperties properties = new RiskProperties();
        properties.setDefaultCreditLimit(BigDecimal.valueOf(10_000_000));
        properties.setDefaultSingleOrderQtyLimit(1000);
        properties.setDefaultDailyTradeAmountLimit(BigDecimal.valueOf(5_000_000));
        properties.setDefaultPositionLimit(5000);
        properties.setDefaultPriceDeviationBps(200);

        CreditLimitRule creditLimitRule = new CreditLimitRule(properties);
        SingleOrderLimitRule singleOrderLimitRule = new SingleOrderLimitRule(properties);
        DailyAmountLimitRule dailyAmountLimitRule = new DailyAmountLimitRule(properties);
        PositionLimitRule positionLimitRule = new PositionLimitRule(properties);
        PriceDeviationRule priceDeviationRule = new PriceDeviationRule(properties);

        DefaultRiskRuleEngine engine = new DefaultRiskRuleEngine(
                creditLimitRule, singleOrderLimitRule, dailyAmountLimitRule,
                positionLimitRule, priceDeviationRule);

        riskService = new RiskService(engine);
    }

    private RiskCheckRequest buildNormalRequest() {
        RiskCheckRequest req = new RiskCheckRequest();
        req.setCustomerId("CUST001");
        req.setSymbol("AU2406");
        req.setSide("BUY");
        req.setOrderType("LIMIT");
        req.setQty(BigDecimal.valueOf(100));
        req.setPrice(BigDecimal.valueOf(520.00));
        req.setMarketMidPrice(BigDecimal.valueOf(520.25));
        req.setUsedCredit(BigDecimal.valueOf(1_000_000));
        req.setDailyUsedAmount(BigDecimal.valueOf(500_000));
        req.setCurrentPosition(BigDecimal.valueOf(2000));
        return req;
    }

    @Test
    void normalOrder_shouldPass() {
        RiskCheckResult result = riskService.checkPreTrade(buildNormalRequest());
        assertTrue(result.isPassed());
        assertNull(result.getRejectCode());
    }

    @Test
    void largeQty_shouldRejectSingleOrderLimit() {
        RiskCheckRequest req = buildNormalRequest();
        req.setQty(BigDecimal.valueOf(2000));

        RiskCheckResult result = riskService.checkPreTrade(req);
        assertFalse(result.isPassed());
        assertEquals("SINGLE_ORDER_LIMIT_EXCEEDED", result.getRejectCode());
    }

    @Test
    void largePosition_shouldRejectPositionLimit() {
        RiskCheckRequest req = buildNormalRequest();
        req.setCurrentPosition(BigDecimal.valueOf(4500));
        req.setQty(BigDecimal.valueOf(600));

        RiskCheckResult result = riskService.checkPreTrade(req);
        assertFalse(result.isPassed());
        assertEquals("POSITION_LIMIT_EXCEEDED", result.getRejectCode());
    }

    @Test
    void highCreditUsage_shouldRejectCreditLimit() {
        RiskCheckRequest req = buildNormalRequest();
        req.setUsedCredit(BigDecimal.valueOf(9_500_000));
        req.setQty(BigDecimal.valueOf(2000));
        req.setPrice(BigDecimal.valueOf(500));

        RiskCheckResult result = riskService.checkPreTrade(req);
        assertFalse(result.isPassed());
        assertEquals("SINGLE_ORDER_LIMIT_EXCEEDED", result.getRejectCode());
    }

    @Test
    void extremePriceDeviation_shouldReject() {
        RiskCheckRequest req = buildNormalRequest();
        req.setPrice(BigDecimal.valueOf(600.00));
        req.setMarketMidPrice(BigDecimal.valueOf(520.00));

        RiskCheckResult result = riskService.checkPreTrade(req);
        assertFalse(result.isPassed());
        assertEquals("PRICE_DEVIATION_EXCEEDED", result.getRejectCode());
    }

    @Test
    void nullOptionalFields_shouldNotFail() {
        RiskCheckRequest req = new RiskCheckRequest();
        req.setCustomerId("CUST001");
        req.setSymbol("AU2406");
        req.setSide("BUY");
        req.setOrderType("LIMIT");
        req.setQty(BigDecimal.valueOf(10));
        req.setPrice(BigDecimal.valueOf(520.00));

        RiskCheckResult result = riskService.checkPreTrade(req);
        assertTrue(result.isPassed());
    }

    @Test
    void marketOrder_noMarketPrice_shouldPass() {
        RiskCheckRequest req = buildNormalRequest();
        req.setOrderType("MARKET");
        req.setPrice(null);
        req.setMarketMidPrice(null);

        RiskCheckResult result = riskService.checkPreTrade(req);
        assertTrue(result.isPassed());
    }
}
