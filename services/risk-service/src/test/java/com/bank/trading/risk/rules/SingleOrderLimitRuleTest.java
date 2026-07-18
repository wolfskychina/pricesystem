package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SingleOrderLimitRuleTest {

    private SingleOrderLimitRule rule;
    private RiskProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskProperties();
        properties.setDefaultSingleOrderQtyLimit(1000);
        rule = new SingleOrderLimitRule(properties);
    }

    private RiskCheckContext buildContext(String customerId, BigDecimal qty) {
        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setCustomerId(customerId);
        ctx.setQty(qty);
        return ctx;
    }

    @Test
    void qtyWithinLimit_shouldPass() {
        RiskCheckResult result = rule.check(buildContext("CUST001", BigDecimal.valueOf(500)));
        assertTrue(result.isPassed());
    }

    @Test
    void qtyEqualsLimit_shouldPass() {
        RiskCheckResult result = rule.check(buildContext("CUST001", BigDecimal.valueOf(1000)));
        assertTrue(result.isPassed());
    }

    @Test
    void qtyExceedsLimit_shouldReject() {
        RiskCheckResult result = rule.check(buildContext("CUST001", BigDecimal.valueOf(1001)));
        assertFalse(result.isPassed());
        assertEquals("SINGLE_ORDER_LIMIT_EXCEEDED", result.getRejectCode());
        assertEquals("SINGLE_ORDER_LIMIT", result.getRuleName());
    }

    @Test
    void nullQty_shouldPass() {
        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setQty(null);
        RiskCheckResult result = rule.check(ctx);
        assertTrue(result.isPassed());
    }

    @Test
    void customerSpecificLimit_shouldUseCustomerLimit() {
        RiskProperties.CustomerLimit custLimit = new RiskProperties.CustomerLimit();
        custLimit.setCustomerId("VIP001");
        custLimit.setSingleOrderQtyLimit(5000);
        properties.getCustomerLimits().add(custLimit);

        RiskCheckResult result = rule.check(buildContext("VIP001", BigDecimal.valueOf(3000)));
        assertTrue(result.isPassed());
    }

    @Test
    void customerSpecificLimit_exceeded_shouldReject() {
        RiskProperties.CustomerLimit custLimit = new RiskProperties.CustomerLimit();
        custLimit.setCustomerId("VIP001");
        custLimit.setSingleOrderQtyLimit(5000);
        properties.getCustomerLimits().add(custLimit);

        RiskCheckResult result = rule.check(buildContext("VIP001", BigDecimal.valueOf(5001)));
        assertFalse(result.isPassed());
    }

    @Test
    void getRuleName_shouldReturnCorrectName() {
        assertEquals("SINGLE_ORDER_LIMIT", rule.getRuleName());
    }
}
