package com.bank.trading.common.core.enums;

import lombok.Getter;

@Getter
public enum OrderSide {
    BUY("BUY", "买入"),
    SELL("SELL", "卖出");

    private final String code;
    private final String desc;

    OrderSide(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderSide of(String code) {
        for (OrderSide side : values()) {
            if (side.code.equalsIgnoreCase(code)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Invalid order side: " + code);
    }

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
