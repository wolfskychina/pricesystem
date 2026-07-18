package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 成交事件，记录一笔订单撮合成交的完整信息，是成交域事件溯源的核心。
 *
 * <p>当订单在撮合引擎中成交（无论全部成交还是部分成交）时，会产生一个
 * {@code TradeEvent}。该事件会被持仓服务消费以更新客户持仓，被账户服务
 * 消费以扣减/增加资金，被对冲服务消费以触发做市商反向对冲。</p>
 *
 * <p>事件以 customerId 作为分区键，保证同一客户的成交事件按序消费，
 * 从而正确累加持仓与计算资金。成交金额 amount = qty × price，由生产者计算。</p>
 *
 * <p><b>下游消费链路：</b>
 * <pre>
 *   TradeEvent → 持仓服务（更新持仓）→ POSITION_UPDATED
 *             → 账户服务（扣减资金）→ ACCOUNT_UPDATED
 *             → 对冲服务（反向对冲）→ HEDGE_ORDER_SENT → HEDGE_FILLED
 * </pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /** 成交 ID，系统全局唯一 */
    private String tradeId;
    /** 关联的订单 ID */
    private String orderId;
    /** 客户端订单 ID，用于客户端对账 */
    private String clientOrderId;
    /** 客户 ID，同时作为分区键保证同一客户成交事件有序 */
    private String customerId;
    /** 合约符号 */
    private String symbol;
    /** 成交方向（BUY/SELL），继承自订单方向 */
    private String side;
    /** 成交数量 */
    private BigDecimal qty;
    /** 成交价格 */
    private BigDecimal price;
    /** 成交金额 = qty × price，由生产者计算后填入 */
    private BigDecimal amount;
    /** 成交时间戳（毫秒） */
    private Long tradeTime;

    /** 反序列化用无参构造，事件类型固定为 TRADE_FILLED */
    public TradeEvent() {
        super(EventType.TRADE_FILLED, null);
    }

    /**
     * 业务构造函数，以客户 ID 作为分区键。
     *
     * @param customerId 客户 ID，同时作为分区键保证同一客户成交事件有序消费
     */
    public TradeEvent(String customerId) {
        super(EventType.TRADE_FILLED, customerId);
        this.customerId = customerId;
    }
}
