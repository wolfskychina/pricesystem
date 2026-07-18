package com.bank.trading.common.core.enums;

import lombok.Getter;

@Getter
public enum OrderType {
    MARKET("MARKET", "市价单"),
    LIMIT("LIMIT", "限价单");

    private final String code;
    private final String desc;

    OrderType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderType of(String code) {
        for (OrderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid order type: " + code);
    }
}
