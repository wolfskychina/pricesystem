package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 对冲成交事件，记录做市商对冲订单在交易所的成交结果。
 *
 * <p>做市商在与客户成交后，会因承担对手方角色而产生库存敞口（如客户买入黄金，
 * 做市商则持有空头敞口）。为管理风险敞口，做市商会向外部交易所发送<b>反向对冲单</b>
 * 将敞口平掉。本事件记录对冲单在交易所成交的完整信息。</p>
 *
 * <p><b>对冲业务流程：</b>
 * <pre>
 *   客户 BUY → 做市商 SELL（建立空头敞口）→ 发送对冲 BUY 单到交易所 → HEDGE_ORDER_SENT
 *                                                                     → 交易所成交 → HEDGE_FILLED（敞口平掉）
 * </pre>
 * 对冲完成后，做市商的净持仓回归零，仅赚取报价点差，实现市场中性。</p>
 *
 * <p>事件以 symbol（合约符号）作为分区键，因为对冲是按合约维度管理的，
 * 同一合约的对冲事件需要有序消费以正确计算净敞口。{@code originalTradeId}
 * 关联原始客户成交，用于对冲盈亏归因分析。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HedgeFillEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /** 对冲成交 ID，交易所返回的成交编号 */
    private String hedgeTradeId;
    /** 合约符号，同时作为分区键 */
    private String symbol;
    /** 对冲方向（与客户成交方向相反） */
    private String side;
    /** 对冲成交数量 */
    private BigDecimal qty;
    /** 对冲成交价格 */
    private BigDecimal price;
    /** 对冲成交金额 = qty × price */
    private BigDecimal amount;
    /** 对冲成交时间戳（毫秒） */
    private Long tradeTime;
    /** 原始客户成交 ID，用于对冲盈亏归因分析（关联 TradeEvent.tradeId） */
    private String originalTradeId;

    /** 反序列化用无参构造，事件类型固定为 HEDGE_FILLED */
    public HedgeFillEvent() {
        super(EventType.HEDGE_FILLED, null);
    }

    /**
     * 业务构造函数，以合约符号作为分区键。
     *
     * <p>对冲事件以 symbol 作为分区键（而非 customerId），因为对冲是做市商
     * 自身的操作，按合约维度管理净敞口，同一合约的对冲事件需有序消费。</p>
     *
     * @param symbol 合约符号，同时作为分区键
     */
    public HedgeFillEvent(String symbol) {
        super(EventType.HEDGE_FILLED, symbol);
    }
}
