package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import com.bank.trading.risk.engine.RiskRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SingleOrderLimitRule implements RiskRule {

    private final RiskProperties riskProperties;

    public SingleOrderLimitRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    @Override
    public String getRuleName() {
        return "SINGLE_ORDER_LIMIT";
    }

    @Override
    public RiskCheckResult check(RiskCheckContext context) {
        if (context.getQty() == null) {
            return RiskCheckResult.pass();
        }

        int limit = riskProperties.getSingleOrderQtyLimit(context.getCustomerId());
        BigDecimal limitDecimal = BigDecimal.valueOf(limit);

        if (context.getQty().compareTo(limitDecimal) > 0) {
            return RiskCheckResult.reject("SINGLE_ORDER_LIMIT_EXCEEDED",
                    String.format("Order qty %s exceeds single order limit %d",
                            context.getQty(), limit), getRuleName());
        }
        return RiskCheckResult.pass();
    }
}
