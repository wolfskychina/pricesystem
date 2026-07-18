package com.bank.trading.common.core.exception;

public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final int code;

    public BusinessException(int code) {
        this.code = code;
    }

    public BusinessException(int code) {
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
