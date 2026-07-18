package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CreditLimitRuleTest {

    private CreditLimitRule rule;
    private RiskProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RiskProperties();
        properties.setDefaultCreditLimit(BigDecimal.valueOf(1_000_000));
        rule = new CreditLimitRule(properties);
    }

    private RiskCheckContext buildContext(BigDecimal qty, BigDecimal price, BigDecimal usedCredit) {
        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setCustomerId("CUST001");
        ctx.setQty(qty);
        ctx.setOrderPrice(price);
        ctx.setUsedCredit(usedCredit);
        return ctx;
    }

    @Test
    void withinLimit_shouldPass() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(500_000)));
        assertTrue(result.isPassed());
    }

    @Test
    void exceedLimit_shouldReject() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(600_000)));
        assertFalse(result.isPassed());
        assertEquals("CREDIT_LIMIT_EXCEEDED", result.getRejectCode());
        assertEquals("CREDIT_LIMIT", result.getRuleName());
    }

    @Test
    void nullUsedCredit_shouldTreatAsZero() {
        RiskCheckResult result = rule.check(buildContext(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(500),
                null));
        assertTrue(result.isPassed());
    }

    @Test
    void usesMarketPriceWhenOrderPriceNull() {
        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setCustomerId("CUST001");
        ctx.setQty(BigDecimal.valueOf(3000));
        ctx.setOrderPrice(null);
        ctx.setMarketMidPrice(BigDecimal.valueOf(500));
        ctx.setUsedCredit(BigDecimal.ZERO);

        RiskCheckResult result = rule.check(ctx);
        assertFalse(result.isPassed());
    }

    @Test
    void customerSpecificLimit_shouldUseCustomerLimit() {
        RiskProperties.CustomerLimit custLimit = new RiskProperties.CustomerLimit();
        custLimit.setCustomerId("VIP001");
        custLimit.setCreditLimit(BigDecimal.valueOf(10_000_000));
        properties.getCustomerLimits().add(custLimit);

        RiskCheckContext ctx = new RiskCheckContext();
        ctx.setCustomerId("VIP001");
        ctx.setQty(BigDecimal.valueOf(1000));
        ctx.setOrderPrice(BigDecimal.valueOf(500));
        ctx.setUsedCredit(BigDecimal.valueOf(5_000_000));

        RiskCheckResult result = rule.check(ctx);
        assertTrue(result.isPassed());
    }
}
