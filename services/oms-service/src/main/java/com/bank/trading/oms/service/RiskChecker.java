package com.bank.trading.oms.service;

import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;

public interface RiskChecker {
    RiskCheckResult check(RiskCheckRequest request);
}
