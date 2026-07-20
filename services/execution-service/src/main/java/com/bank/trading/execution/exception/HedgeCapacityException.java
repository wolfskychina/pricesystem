package com.bank.trading.execution.exception;

public class HedgeCapacityException extends RuntimeException {

    private final String reason;
    private final String symbol;
    private final long suggestedRetryAfter;

    public HedgeCapacityException(String message, String reason, String symbol) {
        super(message);
        this.reason = reason;
        this.symbol = symbol;
        this.suggestedRetryAfter = 30000;
    }

    public HedgeCapacityException(String message, String reason, String symbol, long suggestedRetryAfter) {
        super(message);
        this.reason = reason;
        this.symbol = symbol;
        this.suggestedRetryAfter = suggestedRetryAfter;
    }

    public String getReason() { return reason; }
    public String getSymbol() { return symbol; }
    public long getSuggestedRetryAfter() { return suggestedRetryAfter; }
}