package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 风控检查结果 DTO，是风控服务对 {@link RiskCheckRequest} 的响应。
 *
 * <p>风控服务根据请求中的客户、合约、订单信息，逐条匹配风控规则，
 * 任一规则不通过即整体拒绝。结果包含是否通过、拒绝码、拒绝原因与命中的规则名，
 * 便于交易服务生成 ORDER_REJECTED 事件并告知客户具体原因。</p>
 *
 * <p>提供静态工厂方法 {@link #pass()} 与 {@link #reject(String, String)} 简化构造，
 * 保证语义清晰且避免非法状态（如 passed=true 却携带 rejectReason）。</p>
 */
@Data
public class RiskCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 风控是否通过：true 表示全部规则通过，false 表示至少一条规则拒绝 */
    private boolean passed;
    /** 拒绝码，标识被哪一类风控规则拒绝（如 POSITION_LIMIT、SINGLE_ORDER_LIMIT） */
    private String rejectCode;
    /** 拒绝原因描述，向客户展示的可读信息 */
    private String rejectReason;
    /** 命中的风控规则名称，用于风控日志与审计定位 */
    private String ruleName;

    /**
     * 构建风控通过结果。
     *
     * @return passed=true 的风控结果
     */
    public static RiskCheckResult pass() {
        RiskCheckResult result = new RiskCheckResult();
        result.setPassed(true);
        return result;
    }

    /**
     * 构建风控拒绝结果。
     *
     * @param code   拒绝码
     * @param reason 拒绝原因
     * @return passed=false 的风控结果
     */
    public static RiskCheckResult reject(String code, String reason) {
        RiskCheckResult result = new RiskCheckResult();
        result.setPassed(false);
        result.setRejectCode(code);
        result.setRejectReason(reason);
        return result;
    }

    /**
     * 构建风控拒绝结果并携带命中的规则名（用于审计定位）。
     *
     * @param code     拒绝码
     * @param reason   拒绝原因
     * @param ruleName 命中的风控规则名称
     * @return passed=false 的风控结果
     */
    public static RiskCheckResult reject(String code, String reason, String ruleName) {
        RiskCheckResult result = reject(code, reason);
        result.setRuleName(ruleName);
        return result;
    }
}
