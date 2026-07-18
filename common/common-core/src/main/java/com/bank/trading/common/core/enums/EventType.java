package com.bank.trading.common.core.enums;

enum EventType {

ORDER_CREATED("ORDER_CREATED", "订单创建"),
ORDER_ACCEPTED("ORDER_ACCEPTED", "订单受理"),
ORDER_REJECTED("ORDER_REJECTED", "订单拒绝"),
ORDER_CANCELLED("ORDER_CANCELLED", "订单取消"),
TRADE_FILLED("TRADE_FILLED", "成交"),
HEDGE_ORDER_SENT("HEDGE_ORDER_SENT", "对冲单已发送"),
HEDGE_FILLED("HEDGE_FILLED", "对冲成交"),
MARKET_DATA("MARKET_DATA", "行情数据"),
CUSTOMER_QUOTE("CUSTOMER_QUOTE", "客户报价"),
POSITION_UPDATED("POSITION_UPDATED", "持仓更新"),
ACCOUNT_UPDATED("ACCOUNT_UPDATED", "账户更新");

    private final String code;
    private final String desc;

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    ORDER_CREATED("ORDER_CREATED", "订单创建"),
    ORDER_ACCEPTED("ORDER_ACCEPTED", "订单受理"),
    ORDER_REJECTED("ORDER_REJECTED", "订单拒绝"),
    ORDER_CANCELLED("ORDER_CANCELLED", "订单取消"),
    TRADE_FILLED("TRADE_FILLED", "成交"),
    HEDGE_ORDER_SENT("HEDGE_ORDER_SENT", "对冲单已发送"),
    HEDGE_FILLED("HEDGE_FILLED", "对冲成交"),
    MARKET_DATA("MARKET_DATA", "行情数据"),
    CUSTOMER_QUOTE("CUSTOMER_QUOTE", "客户报价"),
    POSITION_UPDATED("POSITION_UPDATED", "持仓更新"),
    ACCOUNT_UPDATED("ACCOUNT_UPDATED", "账户更新");

    EventType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
