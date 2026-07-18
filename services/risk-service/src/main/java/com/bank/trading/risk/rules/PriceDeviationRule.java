package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import com.bank.trading.risk.engine.RiskRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PriceDeviationRule implements RiskRule {

    private final RiskProperties riskProperties;

    public PriceDeviationRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    @Override
    public String getRuleName() {
        return "PRICE_DEVIATION";
    }

    @Override
    public RiskCheckResult check(RiskCheckContext context) {
        if (context.getOrderPrice() == null || context.getMarketMidPrice() == null
                || context.getMarketMidPrice().compareTo(BigDecimal.ZERO) == 0) {
            return RiskCheckResult.pass();
        }

        int maxDeviationBps = riskProperties.getDefaultPriceDeviationBps();

        BigDecimal deviation = context.getOrderPrice().subtract(context.getMarketMidPrice()).abs();
        BigDecimal deviationBps = deviation.multiply(BigDecimal.valueOf(10000))
                .divide(context.getMarketMidPrice(), 4, RoundingMode.HALF_UP);

        if (deviationBps.compareTo(BigDecimal.valueOf(maxDeviationBps)) > 0) {
            return RiskCheckResult.reject("PRICE_DEVIATION_EXCEEDED",
                    String.format("Order price deviation %.2f bps exceeds limit %d bps",
                            deviationBps, maxDeviationBps), getRuleName());
        }
        return RiskCheckResult.pass();
    }
}
