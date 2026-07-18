package com.bank.trading.oms.service;

import com.bank.trading.common.core.dto.RiskCheckRequest;
import com.bank.trading.common.core.dto.RiskCheckResult;
import com.bank.trading.oms.client.RiskServiceClient;
import org.springframework.stereotype.Component;

@Component
public class RiskCheckerAdapter implements RiskChecker {

    private final RiskServiceClient riskServiceClient;

    public RiskCheckerAdapter(RiskServiceClient riskServiceClient) {
        this.riskServiceClient = riskServiceClient;
    }

    @Override
    public RiskCheckResult check(RiskCheckRequest request) {
        return riskServiceClient.checkPreTrade(request);
    }
}
