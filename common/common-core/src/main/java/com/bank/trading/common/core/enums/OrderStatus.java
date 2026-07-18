package com.bank.trading.common.core.enums;

import lombok.Getter;

/**
 * 订单状态枚举，定义了订单从创建到终态的完整生命周期状态机。
 *
 * <p>在银行做市商交易系统中，一个订单会经历多个状态流转，本枚举是
 * 订单状态机的核心定义。状态流转关系如下：</p>
 *
 * <pre>
 *   NEW（新建）
 *     │
 *     ├─ 风控校验 ──→ PENDING_RISK（风控中） ──→ ACCEPTED（已受理）
 *     │                                          │
 *     │                                          ├─ 部分成交 → PARTIALLY_FILLED（部分成交）
 *     │                                          │                │
 *     │                                          │                └─ 全部成交 → FILLED（全部成交）★终态
 *     │                                          │
 *     │                                          └─ 用户撤单 → CANCEL_PENDING（撤单中）→ CANCELLED（已取消）★终态
 *     │
 *     └─ 风控失败 ──→ REJECTED（已拒绝）★终态
 * </pre>
 *
 * <p><b>终态（Final State）</b>：FILLED / REJECTED / CANCELLED，进入终态后订单不可再变更，
 * 通过 {@link #isFinal()} 判断。</p>
 *
 * <p><b>可撤单状态</b>：NEW / PENDING_RISK / ACCEPTED / PARTIALLY_FILLED，
 * 这些状态下的订单允许客户发起撤单请求，通过 {@link #canCancel()} 判断。</p>
 */
@Getter
public enum OrderStatus {
    /** 新建：订单刚创建，尚未提交风控校验 */
    NEW("NEW", "新建"),
    /** 风控中：订单已提交风控服务，等待风控审核结果 */
    PENDING_RISK("PENDING_RISK", "风控中"),
    /** 已受理：风控通过，订单已被交易系统接受，等待撮合 */
    ACCEPTED("ACCEPTED", "已受理"),
    /** 部分成交：订单已成交一部分，仍有剩余数量待成交 */
    PARTIALLY_FILLED("PARTIALLY_FILLED", "部分成交"),
    /** 全部成交（终态）：订单全部数量已成交，不可再变更 */
    FILLED("FILLED", "全部成交"),
    /** 已拒绝（终态）：风控未通过或系统拒绝，订单作废 */
    REJECTED("REJECTED", "已拒绝"),
    /** 已取消（终态）：客户撤单成功，订单终止 */
    CANCELLED("CANCELLED", "已取消"),
    /** 撤单中：客户已发起撤单请求，系统正在处理中（中间态） */
    CANCEL_PENDING("CANCEL_PENDING", "撤单中");

    /** 状态码，持久化到数据库时使用此字符串值 */
    private final String code;
    /** 状态中文描述，用于日志、监控与前端展示 */
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码字符串解析为枚举实例（大小写不敏感）。
     *
     * <p>用于从数据库记录、Kafka 消息、HTTP 请求等外部输入中还原订单状态。</p>
     *
     * @param code 状态码字符串
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 当传入的 code 无法匹配任何状态时抛出
     */
    public static OrderStatus of(String code) {
        for (OrderStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid order status: " + code);
    }

    /**
     * 判断当前状态是否为终态（不可再变更）。
     *
     * <p>终态包括 FILLED（全部成交）、REJECTED（已拒绝）、CANCELLED（已取消）。
     * 处于终态的订单不再接受任何状态变更操作，事件溯源链路也到此结束。</p>
     *
     * @return 如果是终态返回 true
     */
    public boolean isFinal() {
        return this == FILLED || this == REJECTED || this == CANCELLED;
    }

    /**
     * 判断当前状态是否允许撤单。
     *
     * <p>仅 NEW / PENDING_RISK / ACCEPTED / PARTIALLY_FILLED 状态可撤单。
     * 已进入终态（FILLED/REJECTED/CANCELLED）或撤单处理中（CANCEL_PENDING）的订单不可再撤单。</p>
     *
     * @return 如果允许撤单返回 true
     */
    public boolean canCancel() {
        return this == NEW || this == PENDING_RISK || this == ACCEPTED || this == PARTIALLY_FILLED;
    }
}
