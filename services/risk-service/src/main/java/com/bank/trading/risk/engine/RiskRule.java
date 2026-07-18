package com.bank.trading.risk.engine;

import com.bank.trading.common.core.dto.RiskCheckResult;

public interface RiskRule {
    String getRuleName();
    RiskCheckResult check(RiskCheckContext context);
}
