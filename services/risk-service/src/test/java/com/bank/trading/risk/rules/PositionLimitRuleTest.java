package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PositionLimitRuleTest {

    private PositionLimitRule rule;
    private RiskProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskProperties();
        properties.setDefaultPositionLimit(5000);
        rule = new PositionLimitRule(properties);
    }

    private RiskCheckContext buildContext(String side, BigDecimal qty, BigDecimal currentPos) {
        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setCustomerId("CUST001");
        ctx.setSide(side);
        ctx.setQty(qty);
        ctx.setCurrentPosition(currentPos);
        return ctx;
    }

    @Test
    void buy_withinLimit_shouldPass() {
        RiskCheckResult result = rule.check(buildContext("BUY",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(3000)));
        assertTrue(result.isPassed());
    }

    @Test
    void buy_exceedLimit_shouldReject() {
        RiskCheckResult result = rule.check(buildContext("BUY",
                BigDecimal.valueOf(2500),
                BigDecimal.valueOf(3000)));
        assertFalse(result.isPassed());
        assertEquals("POSITION_LIMIT_EXCEEDED", result.getRejectCode());
    }

    @Test
    void sell_withinLimit_shouldPass() {
        RiskCheckResult result = rule.check(buildContext("SELL",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(3000)));
        assertTrue(result.isPassed());
    }

    @Test
    void sell_exceedLimit_shouldReject() {
        RiskCheckResult result = rule.check(buildContext("SELL",
                BigDecimal.valueOf(5001),
                BigDecimal.valueOf(0)));
        assertFalse(result.isPassed());
    }

    @Test
    void nullQty_shouldPass() {
        RiskCheckContext ctx = buildContext("BUY", null, BigDecimal.valueOf(3000));
        RiskCheckResult result = rule.check(ctx);
        assertTrue(result.isPassed());
    }

    @Test
    void nullPosition_shouldPass() {
        RiskCheckContext ctx = buildContext("BUY", BigDecimal.valueOf(100), null);
        RiskCheckResult result = rule.check(ctx);
        assertTrue(result.isPassed());
    }

    @Test
    void getRuleName_shouldReturnCorrectName() {
        assertEquals("POSITION_LIMIT", rule.getRuleName());
    }
}
