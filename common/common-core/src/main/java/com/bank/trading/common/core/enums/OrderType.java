package com.bank.trading.common.core.enums;

enum OrderType {

MARKET("MARKET", "市价单"),
LIMIT("LIMIT", "限价单");

    private final String code;
    private final String desc;

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    MARKET("MARKET", "市价单"),
    LIMIT("LIMIT", "限价单");

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
