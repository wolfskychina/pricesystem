package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单事件，记录订单生命周期各节点的状态变更，是订单域事件溯源的核心。
 *
 * <p>订单从创建到终态（成交/拒绝/取消）的每一次状态流转，都会产生一个
 * {@code OrderEvent} 并持久化到事件存储。通过重放某客户/订单的事件序列，
 * 可以完整重建订单的当前状态与历史轨迹。</p>
 *
 * <p>本事件支持以下 {@link EventType}：
 * <ul>
 *   <li>{@link EventType#ORDER_CREATED} —— 订单创建（NEW）；</li>
 *   <li>{@link EventType#ORDER_ACCEPTED} —— 风控通过，订单受理（ACCEPTED）；</li>
 *   <li>{@link EventType#ORDER_REJECTED} —— 风控拒绝（REJECTED）；</li>
 *   <li>{@link EventType#ORDER_CANCELLED} —— 客户撤单（CANCELLED）。</li>
 * </ul>
 * 事件以 customerId 作为分区键，保证同一客户的订单事件有序消费。</p>
 *
 * <p><b>字段语义：</b>本事件携带订单的快照字段（数量、已成交数量、均价、状态等），
 * 消费者既可基于事件增量更新状态，也可直接使用快照字段覆盖式更新。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /** 订单 ID，系统全局唯一 */
    private String orderId;
    /** 客户端订单 ID，由客户端生成，用于幂等防重与客户端对账 */
    private String clientOrderId;
    /** 客户 ID，同时也是分区键/分片路由键 */
    private String customerId;
    /** 合约符号，如 AU2412（黄金期货） */
    private String symbol;
    /** 订单方向，BUY/SELL（见 {@link com.bank.trading.common.core.enums.OrderSide}） */
    private String side;
    /** 订单类型，MARKET/LIMIT（见 {@link com.bank.trading.common.core.enums.OrderType}） */
    private String type;
    /** 委托数量 */
    private BigDecimal qty;
    /** 已成交数量，随成交累加 */
    private BigDecimal filledQty;
    /** 委托价格（限价单有效，市价单为 null） */
    private BigDecimal price;
    /** 成交均价，按成交加权计算 */
    private BigDecimal avgPrice;
    /** 订单当前状态（见 {@link com.bank.trading.common.core.enums.OrderStatus}） */
    private String status;
    /** 拒绝原因（仅 ORDER_REJECTED 事件使用） */
    private String rejectReason;

    /** 反序列化用无参构造 */
    public OrderEvent() {
        super();
    }

    /**
     * 业务构造函数，以客户 ID 作为分区键。
     *
     * @param eventType  事件类型（ORDER_CREATED/ORDER_ACCEPTED/ORDER_REJECTED/ORDER_CANCELLED）
     * @param customerId 客户 ID，同时作为分区键保证同一客户事件有序
     */
    public OrderEvent(EventType eventType, String customerId) {
        super(eventType, customerId);
        this.customerId = customerId;
    }
}
