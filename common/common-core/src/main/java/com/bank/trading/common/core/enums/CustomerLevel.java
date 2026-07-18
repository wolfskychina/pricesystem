package com.bank.trading.common.core.enums;

import lombok.Getter;

@Getter
public enum CustomerLevel {
    NORMAL("NORMAL", "普通客户"),
    VIP("VIP", "VIP客户"),
    INSTITUTION("INSTITUTION", "机构客户");

    private final String code;
    private final String desc;

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
