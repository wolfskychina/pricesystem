package com.bank.trading.notify.dto;

import lombok.Data;

/**
 * 推送给前端的通知消息。
 * <p>
 * 统一包装格式：type 标识事件类别，customerId 用于客户端按客户过滤，payload 为业务数据。
 */
@Data
public class NotifyMessage {

    /** 事件类别：trade / hedge-fill / quote / market-data */
    private String type;

    /** 关联客户 ID（行情类可为空） */
    private String customerId;

    /** 关联合约代码 */
    private String symbol;

    /** 事件发生时间戳（毫秒） */
    private Long timestamp;

    /** 业务负载（JSON 字符串或原始对象） */
    private Object payload;

    public NotifyMessage() {
    }

    public NotifyMessage(String type, String customerId, String symbol, Object payload) {
        this.type = type;
        this.customerId = customerId;
        this.symbol = symbol;
        this.timestamp = System.currentTimeMillis();
        this.payload = payload;
    }
}
