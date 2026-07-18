package com.bank.trading.common.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.bank.trading.common.core.result.Result;

/**
 * 全局异常处理器，统一拦截 Controller 层抛出的异常并转换为标准 API 响应。
 *
 * <p>本处理器是系统错误处理链的统一出口，将各类异常转换为
 * {@link Result} 格式的响应体，保证 API 错误响应结构的一致性，
 * 便于前端统一处理与国际化。</p>
 *
 * <p><b>异常处理策略（按优先级）：</b>
 * <ol>
 *   <li>{@link BusinessException} —— 业务异常：HTTP 200 + 业务错误码。
 *       业务上可预期的错误不占用 HTTP 错误状态码语义，客户端通过响应体 code 判断；</li>
 *   <li>{@link IllegalArgumentException} —— 参数非法：HTTP 400 + 错误码 400。
 *       通常由参数校验失败触发；</li>
 *   <li>{@link Exception} —— 兜底处理：HTTP 500 + 错误码 500。
 *       所有未捕获的异常统一处理，避免堆栈泄露给客户端。</li>
 * </ol>
 *
 * <p><b>日志策略：</b>
 * <ul>
 *   <li>业务异常与参数异常用 WARN 级别（预期内错误，无需告警）；</li>
 *   <li>系统异常用 ERROR 级别并打印完整堆栈（需排查与告警）。</li>
 * </ul></p>
 *
 * @see Result
 * @see BusinessException
 */
@RestControllerAdvice
public class GlobalExceptionHandler {


    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常，转换为 HTTP 200 + 业务错误码响应。
     *
     * <p>业务异常是预期内的错误（如余额不足、状态非法），HTTP 层面视为请求成功处理，
     * 通过响应体的 code 字段区分业务结果，避免前端误判为网络/服务异常。</p>
     *
     * @param e 业务异常
     * @return 包含业务错误码与消息的统一响应体
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        // 业务异常为预期内错误，WARN 级别记录即可
        log.warn("Business exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 处理参数非法异常，转换为 HTTP 400 响应。
     *
     * <p>通常由请求参数校验失败（如枚举值非法、必填字段缺失）触发，
     * 提示客户端修正请求参数后重试。</p>
     *
     * @param e 参数非法异常
     * @return 包含 400 错误码与消息的统一响应体
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(400, e.getMessage()));
    }

    /**
     * 兜底处理所有未捕获异常，转换为 HTTP 500 响应。
     *
     * <p>为避免向客户端泄露内部堆栈信息（安全风险），统一返回"Internal server error"，
     * 完整异常堆栈仅记录到服务端日志用于排查。</p>
     *
     * @param e 未捕获的异常
     * @return 包含 500 错误码的统一响应体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        // 系统异常需完整堆栈用于排查，ERROR 级别触发告警
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "Internal server error"));
    }
}
