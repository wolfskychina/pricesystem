package com.bank.trading.common.core.enums;

import lombok.Getter;

/**
 * 事件类型枚举，定义了事件溯源体系中所有业务事件的类型。
 *
 * <p>本系统采用事件溯源（Event Sourcing）架构，所有状态变更都通过事件记录。
 * 每个事件类型对应一类业务动作，事件会被持久化到事件存储（Event Store），
 * 并通过 Kafka 分发到下游消费者（如持仓更新、账户更新、风控审计等）。</p>
 *
 * <p>事件类型按业务域划分：
 * <ul>
 *   <li><b>订单域</b>：ORDER_CREATED / ORDER_ACCEPTED / ORDER_REJECTED / ORDER_CANCELLED，
 *       记录订单生命周期的关键节点；</li>
 *   <li><b>成交域</b>：TRADE_FILLED，记录订单撮合成交结果；</li>
 *   <li><b>对冲域</b>：HEDGE_ORDER_SENT / HEDGE_FILLED，记录做市商对冲单的发送与成交；</li>
 *   <li><b>行情域</b>：MARKET_DATA，记录市场行情快照变更；</li>
 *   <li><b>报价域</b>：CUSTOMER_QUOTE，记录向客户下发的报价；</li>
 *   <li><b>持仓账户域</b>：POSITION_UPDATED / ACCOUNT_UPDATED，由下游消费者产生，
 *       记录持仓与账户余额的变更结果。</li>
 * </ul></p>
 */
@Getter
public enum EventType {
    /** 订单创建：客户下单成功，订单进入 NEW 状态 */
    ORDER_CREATED("ORDER_CREATED", "订单创建"),
    /** 订单受理：风控通过，订单进入 ACCEPTED 状态，可参与撮合 */
    ORDER_ACCEPTED("ORDER_ACCEPTED", "订单受理"),
    /** 订单拒绝：风控未通过，订单进入 REJECTED 终态 */
    ORDER_REJECTED("ORDER_REJECTED", "订单拒绝"),
    /** 订单取消：客户撤单成功，订单进入 CANCELLED 终态 */
    ORDER_CANCELLED("ORDER_CANCELLED", "订单取消"),
    /** 成交：订单撮合成交，生成成交记录 */
    TRADE_FILLED("TRADE_FILLED", "成交"),
    /** 对冲单已发送：做市商向交易所发送对冲订单 */
    HEDGE_ORDER_SENT("HEDGE_ORDER_SENT", "对冲单已发送"),
    /** 对冲成交：做市商的对冲订单在交易所成交，敞口已平 */
    HEDGE_FILLED("HEDGE_FILLED", "对冲成交"),
    /** 行情数据：市场行情快照更新（来自行情引擎或外部数据源） */
    MARKET_DATA("MARKET_DATA", "行情数据"),
    /** 客户报价：做市商向客户下发的买卖报价 */
    CUSTOMER_QUOTE("CUSTOMER_QUOTE", "客户报价"),
    /** 持仓更新：客户/做市商持仓发生变更（由成交或对冲触发） */
    POSITION_UPDATED("POSITION_UPDATED", "持仓更新"),
    /** 账户更新：客户账户余额发生变更（由成交扣款或入金触发） */
    ACCOUNT_UPDATED("ACCOUNT_UPDATED", "账户更新");

    /** 事件类型码，持久化到 event_store 表与 Kafka 消息 header */
    private final String code;
    /** 中文描述，用于日志与监控 */
    private final String desc;

    EventType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
