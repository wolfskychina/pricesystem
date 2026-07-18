package com.bank.trading.common.core.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应包装类，所有 REST 接口返回值的统一外壳。
 *
 * <p>本类是系统 API 响应的标准格式，保证所有接口返回结构一致，
 * 便于前端统一解析、错误处理与国际化。响应体包含以下字段：
 * <ul>
 *   <li>{@code code} —— 业务状态码，200 表示成功，其它表示各类业务错误；</li>
 *   <li>{@code message} —— 描述信息，成功为 "success"，失败为具体错误描述；</li>
 *   <li>{@code data} —— 业务数据载荷，泛型 T 具体类型由接口决定；</li>
 *   <li>{@code timestamp} —— 响应生成时间戳（毫秒），用于客户端判断响应时效。</li>
 * </ul></p>
 *
 * <p><b>设计要点：</b>业务错误（如余额不足）HTTP 状态码仍为 200，通过 code 字段区分，
 * 这样前端只需检查 code == 200 判断业务成功，避免与 HTTP 层错误码混淆。
 * HTTP 4xx/5xx 仅用于真正的网络/服务异常（由 {@link com.bank.trading.common.core.exception.GlobalExceptionHandler} 处理）。</p>
 *
 * @param <T> 业务数据载荷类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，200 表示成功，其它为业务错误码 */
    private int code;
    /** 描述信息，成功为 "success"，失败为错误描述 */
    private String message;
    /** 业务数据载荷 */
    private T data;
    /** 响应生成时间戳（毫秒） */
    private long timestamp;

    /** 默认构造，自动填充当前时间戳 */
    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 全参构造。
     *
     * @param code    业务状态码
     * @param message 描述信息
     * @param data    业务数据
     */
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构建无数据的成功响应。
     *
     * @param <T> 数据载荷类型
     * @return code=200 的成功响应
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /**
     * 构建携带数据的成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据载荷类型
     * @return code=200 且携带数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /**
     * 构建携带自定义消息与数据的成功响应。
     *
     * @param message 自定义成功消息
     * @param data    业务数据
     * @param <T>     数据载荷类型
     * @return code=200 的成功响应
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /**
     * 构建默认错误码（500）的失败响应。
     *
     * @param message 错误描述
     * @param <T>     数据载荷类型
     * @return code=500 的失败响应
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 构建指定错误码的失败响应。
     *
     * @param code    业务错误码
     * @param message 错误描述
     * @param <T>     数据载荷类型
     * @return 携带指定错误码的失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 构建携带数据的失败响应（较少使用，通常错误响应不携带数据）。
     *
     * @param code    业务错误码
     * @param message 错误描述
     * @param data    额外数据（如校验错误字段详情）
     * @param <T>     数据载荷类型
     * @return 携带数据的失败响应
     */
    public static <T> Result<T> fail(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    /**
     * 判断业务是否成功。
     *
     * <p>前端统一通过此方法或直接判断 code == 200 决定是否展示数据。</p>
     *
     * @return code 为 200 时返回 true
     */
    public boolean isSuccess() {
        return code == 200;
    }
}
