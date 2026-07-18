package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RiskCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean passed;
    private String rejectCode;
    private String rejectReason;
    private String ruleName;

    public static RiskCheckResult pass() {
        RiskCheckResult result = new RiskCheckResult();
        result.setPassed(true);
        return result;
    }

    public static RiskCheckResult reject(String code, String reason) {
        RiskCheckResult result = new RiskCheckResult();
        result.setPassed(false);
        result.setRejectCode(code);
        result.setRejectReason(reason);
        return result;
    }

    public static RiskCheckResult reject(String code, String reason, String ruleName) {
        RiskCheckResult result = reject(code, reason);
        result.setRuleName(ruleName);
        return result;
    }
}
