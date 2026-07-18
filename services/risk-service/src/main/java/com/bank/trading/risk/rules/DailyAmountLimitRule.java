package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import com.bank.trading.risk.engine.RiskRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DailyAmountLimitRule implements RiskRule {

    private final RiskProperties riskProperties;

    public DailyAmountLimitRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    @Override
    public String getRuleName() {
        return "DAILY_AMOUNT_LIMIT";
    }

    @Override
    public RiskCheckResult check(RiskCheckContext context) {
        BigDecimal orderAmount = calculateOrderAmount(context);
        BigDecimal dailyUsed = context.getDailyUsedAmount() != null
                ? context.getDailyUsedAmount() : BigDecimal.ZERO;
        BigDecimal limit = riskProperties.getDailyTradeAmountLimit(context.getCustomerId());

        BigDecimal totalAfter = dailyUsed.add(orderAmount);

        if (totalAfter.compareTo(limit) > 0) {
            return RiskCheckResult.reject("DAILY_AMOUNT_LIMIT_EXCEEDED",
                    String.format("Order amount %.2f plus daily used %.2f exceeds daily limit %.2f",
                            orderAmount, dailyUsed, limit), getRuleName());
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
