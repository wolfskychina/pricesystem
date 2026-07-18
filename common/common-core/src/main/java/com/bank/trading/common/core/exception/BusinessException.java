package com.bank.trading.common.core.exception;

import lombok.Getter;

/**
 * 业务异常，用于表示业务规则校验失败等可预期的异常情况。
 *
 * <p>与系统异常（如 NullPointerException）不同，业务异常是业务逻辑层面
 * 预期会发生的"正常"错误，例如：
 * <ul>
 *   <li>客户余额不足无法下单；</li>
 *   <li>订单状态不允许撤单；</li>
 *   <li>报价已过期无法成交；</li>
 *   <li>合约不存在或已停牌。</li>
 * </ul>
 * 业务异常会被 {@link GlobalExceptionHandler} 捕获并转换为友好的 API 响应，
 * HTTP 状态码仍为 200，但响应体的 code 字段携带具体业务错误码。</p>
 *
 * <p><b>错误码约定：</b>
 * <ul>
 *   <li>4xx —— 客户端错误（参数校验、业务规则不满足）；</li>
 *   <li>5xx —— 服务端错误（默认 500）。</li>
 * </ul></p>
 *
 * @see GlobalExceptionHandler
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码，通过 {@link com.bank.trading.common.core.result.Result#getCode()} 返回给客户端 */
    private final int code;

    /**
     * 构造业务异常，错误码默认 500。
     *
     * @param message 错误描述信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 构造业务异常，指定错误码。
     *
     * @param code    业务错误码
     * @param message 错误描述信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造业务异常，携带原因链，错误码默认 500。
     *
     * @param message 错误描述信息
     * @param cause   原始异常
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    /**
     * 构造业务异常，指定错误码并携带原因链。
     *
     * @param code    业务错误码
     * @param message 错误描述信息
     * @param cause   原始异常
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
