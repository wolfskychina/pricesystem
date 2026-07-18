package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DailyAmountLimitRuleTest {

    private DailyAmountLimitRule rule;
    private RiskProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskProperties();
        properties.setDefaultDailyTradeAmountLimit(BigDecimal.valueOf(5_000_000));
        rule = new DailyAmountLimitRule(properties);
    }

    private RiskCheckContext buildContext(BigDecimal qty, BigDecimal price, BigDecimal dailyUsed) {
        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setCustomerId("CUST001");
        ctx.setQty(qty);
        ctx.setOrderPrice(price);
        ctx.setDailyUsedAmount(dailyUsed);
        return ctx;
    }

    @Test
    void withinLimit_shouldPass() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(1_000_000)));
        assertTrue(result.isPassed());
    }

    @Test
    void exceedLimit_shouldReject() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(4_600_000)));
        assertFalse(result.isPassed());
        assertEquals("DAILY_AMOUNT_LIMIT_EXCEEDED", result.getRejectCode());
    }

    @Test
    void nullDailyUsed_shouldTreatAsZero() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(500),
                null));
        assertTrue(result.isPassed());
    }

    @Test
    void getRuleName_shouldReturnCorrectName() {
        assertEquals("DAILY_AMOUNT_LIMIT", rule.getRuleName());
    }
}
