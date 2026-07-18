package com.bank.trading.common.core.dto;

import java.io.Serializable;

public class RiskCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;
    private boolean passed;
    private String rejectCode;
    private String rejectReason;
    private String ruleName;

    public boolean isPassed() {
        return passed;
    }

    public String getRejectCode() {
        return rejectCode;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public void setRejectCode(String rejectCode) {
        this.rejectCode = rejectCode;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RiskCheckResult that = (RiskCheckResult) o;
        if (passed != that.passed) return false;
        if (rejectCode != null ? !rejectCode.equals(that.rejectCode) : that.rejectCode != null) return false;
        if (rejectReason != null ? !rejectReason.equals(that.rejectReason) : that.rejectReason != null) return false;
        if (ruleName != null ? !ruleName.equals(that.ruleName) : that.ruleName != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (passed ? 1 : 0);
        result = 31 * result + (rejectCode != null ? rejectCode.hashCode() : 0);
        result = 31 * result + (rejectReason != null ? rejectReason.hashCode() : 0);
        result = 31 * result + (ruleName != null ? ruleName.hashCode() : 0);
        return result;
    }

    @Override

    @Override

    @Override

    @Override

    @Override

    @Override

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
    @Override
    public String toString() {
        return "RiskCheckResult{passed=" + passed + ", rejectCode='" + rejectCode + "', rejectReason='" + rejectReason + "', ruleName='" + ruleName + "'}";
    }

}
