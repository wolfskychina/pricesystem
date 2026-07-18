package com.bank.trading.risk.engine;

import com.bank.trading.common.core.dto.RiskCheckResult;

public interface RiskRuleEngine {
    RiskCheckResult evaluate(RiskCheckContext context);
}
