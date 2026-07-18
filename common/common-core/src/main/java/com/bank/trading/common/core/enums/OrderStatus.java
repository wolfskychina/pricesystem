package com.bank.trading.common.core.enums;

import lombok.Getter;

@Getter
public enum OrderStatus {
    NEW("NEW", "新建"),
    PENDING_RISK("PENDING_RISK", "风控中"),
    ACCEPTED("ACCEPTED", "已受理"),
    PARTIALLY_FILLED("PARTIALLY_FILLED", "部分成交"),
    FILLED("FILLED", "全部成交"),
    REJECTED("REJECTED", "已拒绝"),
    CANCELLED("CANCELLED", "已取消"),
    CANCEL_PENDING("CANCEL_PENDING", "撤单中");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus of(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid order status: " + code);
    }

    public boolean isFinal() {
        return this == FILLED || this == REJECTED || this == CANCELLED;
    }

    public boolean canCancel() {
        return this == NEW || this == PENDING_RISK || this == ACCEPTED || this == PARTIALLY_FILLED;
    }
}
