package com.bank.trading.risk.engine;

import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.risk.rules.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultRiskRuleEngine implements RiskRuleEngine {

    private final List<RiskRule> rules;

    public DefaultRiskRuleEngine(CreditLimitRule creditLimitRule,
                                 SingleOrderLimitRule singleOrderLimitRule,
                                 DailyAmountLimitRule dailyAmountLimitRule,
                                 PositionLimitRule positionLimitRule,
                                 PriceDeviationRule priceDeviationRule) {
        this.rules = new ArrayList<>();
        this.rules.add(singleOrderLimitRule);
        this.rules.add(priceDeviationRule);
        this.rules.add(creditLimitRule);
        this.rules.add(dailyAmountLimitRule);
        this.rules.add(positionLimitRule);
    }

    @Override
    public RiskCheckResult evaluate(RiskCheckContext context) {
        for (RiskRule rule : rules) {
            RiskCheckResult result = rule.check(context);
            if (!result.isPassed()) {
                return result;
            }
        }
        return RiskCheckResult.pass();
    }

    public List<RiskRule> getRules() {
        return rules;
    }
}
