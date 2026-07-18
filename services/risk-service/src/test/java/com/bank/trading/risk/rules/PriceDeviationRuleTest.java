package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PriceDeviationRuleTest {

    private PriceDeviationRule rule;
    private RiskProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskProperties();
        properties.setDefaultPriceDeviationBps(200);
        rule = new PriceDeviationRule(properties);
    }

    private RiskCheckContext buildContext(BigDecimal orderPrice, BigDecimal midPrice) {
        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setOrderPrice(orderPrice);
        ctx.setMarketMidPrice(midPrice);
        return ctx;
    }

    @Test
    void withinDeviation_shouldPass() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(100.50),
                BigDecimal.valueOf(100.00)));
        assertTrue(result.isPassed());
    }

    @Test
    void exactlyAtLimit_shouldPass() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(102.00),
                BigDecimal.valueOf(100.00)));
        assertTrue(result.isPassed());
    }

    @Test
    void exceedDeviation_shouldReject() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(103.00),
                BigDecimal.valueOf(100.00)));
        assertFalse(result.isPassed());
        assertEquals("PRICE_DEVIATION_EXCEEDED", result.getRejectCode());
        assertEquals("PRICE_DEVIATION", result.getRuleName());
    }

    @Test
    void belowDeviationExceeded_shouldReject() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(97.00),
                BigDecimal.valueOf(100.00)));
        assertFalse(result.isPassed());
    }

    @Test
    void nullOrderPrice_shouldPass() {
        RiskCheckResult result = rule.check(buildContext(null, BigDecimal.valueOf(100)));
        assertTrue(result.isPassed());
    }

    @Test
    void nullMarketPrice_shouldPass() {
        RiskCheckResult result = rule.check(buildContext(BigDecimal.valueOf(100), null));
        assertTrue(result.isPassed());
    }

    @Test
    void zeroMarketPrice_shouldPass() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(100),
                BigDecimal.ZERO));
        assertTrue(result.isPassed());
    }
}
