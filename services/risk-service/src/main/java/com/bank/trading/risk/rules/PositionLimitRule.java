package com.bank.trading.risk.rules;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.config.RiskProperties;
import com.bank.trading.risk.engine.RiskCheckContext;
import com.bank.trading.risk.engine.RiskRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PositionLimitRule implements RiskRule {

    private final RiskProperties riskProperties;

    public PositionLimitRule(RiskProperties riskProperties) {
        this.riskProperties = riskProperties;
    }

    @Override
    public String getRuleName() {
        return "POSITION_LIMIT";
    }

    @Override
    public RiskCheckResult check(RiskCheckContext context) {
        if (context.getQty() == null || context.getCurrentPosition() == null) {
            return RiskCheckResult.pass();
        }

        int limit = riskProperties.getPositionLimit(context.getCustomerId());
        BigDecimal positionAfter;

        if ("BUY".equalsIgnoreCase(context.getSide())) {
            positionAfter = context.getCurrentPosition().add(context.getQty());
        } else {
            positionAfter = context.getCurrentPosition().subtract(context.getQty()).abs();
        }

        if (positionAfter.compareTo(BigDecimal.valueOf(limit)) > 0) {
            return RiskCheckResult.reject("POSITION_LIMIT_EXCEEDED",
                    String.format("Position after trade %s exceeds position limit %d",
                            positionAfter, limit), getRuleName());
        }
        return RiskCheckResult.pass();
    }
}
