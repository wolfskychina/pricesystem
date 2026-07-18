package com.bank.trading.common.core.event;

import com.bank.trading.common.core.enums.EventType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 行情数据事件，封装某个合约某一时刻的市场行情快照。
 *
 * <p>行情是做市商报价、撮合、风控的基础输入。行情来源包括：
 * <ul>
 *   <li>外部交易所行情接入；</li>
 *   <li>模拟交易所（sim-exchange）生成的仿真行情。</li>
 * </ul>
 * 行情引擎按固定频率（如每秒 1-10 次）产生行情快照，封装为本事件后通过 Kafka
 * 分发，下游消费者包括报价引擎、风控服务、前端 WebSocket 推送等。</p>
 *
 * <p>事件以 symbol（合约符号）作为分区键，保证同一合约的行情事件按序消费，
 * 避免乱序导致报价错误。{@code timestamp} 为行情生成时间，用于行情时效性校验。</p>
 *
 * <p><b>字段语义（盘口数据）：</b>
 * <ul>
 *   <li>bidPrice/bidQty —— 买一价/买一量（市场上最高买价及其挂单量）；</li>
 *   <li>askPrice/askQty —— 卖一价/卖一量（市场上最低卖价及其挂单量）；</li>
 *   <li>lastPrice/lastQty —— 最新成交价/最新成交量；</li>
 *   <li>volume —— 累计成交量。</li>
 * </ul></p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MarketDataEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /** 合约符号，同时作为分区键 */
    private String symbol;
    /** 买一价：市场上最高买价 */
    private BigDecimal bidPrice;
    /** 卖一价：市场上最低卖价 */
    private BigDecimal askPrice;
    /** 最新成交价 */
    private BigDecimal lastPrice;
    /** 买一量：买一价对应的挂单量 */
    private BigDecimal bidQty;
    /** 卖一量：卖一价对应的挂单量 */
    private BigDecimal askQty;
    /** 最新成交量 */
    private BigDecimal lastQty;
    /** 累计成交量 */
    private Long volume;
    /** 行情生成时间戳（毫秒），用于行情时效性校验 */
    private Long timestamp;

    /** 反序列化用无参构造，事件类型固定为 MARKET_DATA */
    public MarketDataEvent() {
        super(EventType.MARKET_DATA, null);
    }

    /**
     * 业务构造函数，以合约符号作为分区键。
     *
     * @param symbol 合约符号，同时作为分区键保证同一合约行情有序
     */
    public MarketDataEvent(String symbol) {
        super(EventType.MARKET_DATA, symbol);
    }
}
