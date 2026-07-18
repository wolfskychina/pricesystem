package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 客户报价事件，记录做市商向客户下发的买卖报价快照。
 *
 * <p>做市商的核心职责是持续向客户提供买卖双边报价。报价引擎根据市场行情、
 * 客户等级、库存敞口等因素计算点差后，生成客户专属报价并下发。本事件
 * 记录每次报价的完整信息，用于：
 * <ul>
 *   <li>报价审计：追溯任一时刻向某客户报出的价格；</li>
 *   <li>成交校验：客户成交时校验价格是否在报价有效期内；</li>
 *   <li>合规检查：监管要求报价点差在合理范围内。</li>
 * </ul></p>
 *
 * <p>事件以 customerId 作为分区键，保证同一客户的报价事件按序消费。
 * {@link #validUntil} 字段标识报价有效期，超期报价不可成交。</p>
 *
 * <p><b>价差计算：</b>{@code spread} = 客户卖价 - 客户买价，反映做市商的盈利空间。
 * 点差大小与客户等级、市场波动率、库存风险相关。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerQuoteEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /** 合约符号 */
    private String symbol;
    /** 客户 ID，同时作为分区键 */
    private String customerId;
    /** 客户买价：客户以此价格向做市商卖出资产 */
    private BigDecimal customerBidPrice;
    /** 客户卖价：客户以此价格向做市商买入资产 */
    private BigDecimal customerAskPrice;
    /** 市场买价（参考行情），用于计算点差与风控 */
    private BigDecimal marketBidPrice;
    /** 市场卖价（参考行情），用于计算点差与风控 */
    private BigDecimal marketAskPrice;
    /** 报价价差 = customerAskPrice - customerBidPrice，做市商盈利空间 */
    private BigDecimal spread;
    /** 报价 ID，用于唯一标识一次报价，成交时关联校验 */
    private Long quoteId;
    /** 报价有效期截止时间戳（毫秒），超期不可成交 */
    private Long validUntil;

    /** 反序列化用无参构造，事件类型固定为 CUSTOMER_QUOTE */
    public CustomerQuoteEvent() {
        super(EventType.CUSTOMER_QUOTE, null);
    }

    /**
     * 业务构造函数，以客户 ID 作为分区键。
     *
     * @param symbol     合约符号
     * @param customerId 客户 ID，同时作为分区键保证同一客户报价事件有序
     */
    public CustomerQuoteEvent(String symbol, String customerId) {
        super(EventType.CUSTOMER_QUOTE, customerId);
        this.symbol = symbol;
        this.customerId = customerId;
    }
}
