package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import com.bank.trading.risk.engine.RiskRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CreditLimitRule implements RiskRule {

    private final RiskProperties riskProperties;

    public CreditLimitRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    @Override
    public String getRuleName() {
        return "CREDIT_LIMIT";
    }

    @Override
    public RiskCheckResult check(RiskCheckContext context) {
        BigDecimal orderAmount = calculateOrderAmount(context);
        BigDecimal usedCredit = context.getUsedCredit() != null
                ? context.getUsedCredit() : BigDecimal.ZERO;
        BigDecimal creditLimit = riskProperties.getCreditLimit(context.getCustomerId());

        BigDecimal totalAfter = usedCredit.add(orderAmount);

        if (totalAfter.compareTo(creditLimit) > 0) {
            return RiskCheckResult.reject("CREDIT_LIMIT_EXCEEDED",
                    String.format("Order amount %.2f plus used credit %.2f exceeds credit limit %.2f",
                            orderAmount, usedCredit, creditLimit), getRuleName());
        }
        return RiskCheckResult.pass();
    }

    private BigDecimal calculateOrderAmount(RiskCheckContext context) {
        BigDecimal qty = context.getQty() != null ? context.getQty() : BigDecimal.ZERO;
        BigDecimal price = context.getOrderPrice() != null ? context.getOrderPrice()
                : (context.getMarketMidPrice() != null ? context.getMarketMidPrice() : BigDecimal.ZERO);
        return qty.multiply(price);
    }
}
