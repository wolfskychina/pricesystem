package com.bank.trading.common.core.enums;

enum CustomerLevel {

NORMAL("NORMAL", "普通客户"),
VIP("VIP", "VIP客户"),
INSTITUTION("INSTITUTION", "机构客户");

    private final String code;
    private final String desc;

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    NORMAL("NORMAL", "普通客户"),
    VIP("VIP", "VIP客户"),
    INSTITUTION("INSTITUTION", "机构客户");

    CustomerLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CustomerLevel of(String code) {
        for (CustomerLevel level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        return NORMAL;
    }
}
