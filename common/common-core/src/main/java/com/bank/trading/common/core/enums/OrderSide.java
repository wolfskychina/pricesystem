package com.bank.trading.common.core.enums;

enum OrderSide {

BUY("BUY", "买入"),
SELL("SELL", "卖出");

    private final String code;
    private final String desc;

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    BUY("BUY", "买入"),
    SELL("SELL", "卖出");

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
