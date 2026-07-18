package com.bank.trading.common.core.enums;

import lombok.Getter;

/**
 * 订单方向枚举，标识一笔订单是买入还是卖出。
 *
 * <p>在做市商交易中，客户既可以买入（向做市商支付资金获取资产），
 * 也可以卖出（向做市商交付资产换取资金）。做市商则会根据客户方向
 * 进行反向对冲操作，以管理自身持仓风险。</p>
 *
 * <p>例如客户 BUY（买入）黄金期货，做市商在成交后会向交易所发送
 * SELL 方向的对冲单，以平掉因客户买入而产生的多头敞口。</p>
 */
@Getter
public enum OrderSide {
    /** 买入：客户买入资产，做市商卖出资产（做市商建立空头敞口需对冲买入） */
    BUY("BUY", "买入"),
    /** 卖出：客户卖出资产，做市商买入资产（做市商建立多头敞口需对冲卖出） */
    SELL("SELL", "卖出");

    /** 方向码，持久化与通信协议中使用 */
    private final String code;
    /** 中文描述，用于日志与前端展示 */
    private final String desc;

    OrderSide(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据方向码字符串解析为枚举实例（大小写不敏感）。
     *
     * @param code 方向码字符串（"BUY" 或 "SELL"）
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 当 code 无法匹配时抛出
     */
    public static OrderSide of(String code) {
        for (OrderSide side : values()) {
            if (side.code.equalsIgnoreCase(code)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Invalid order side: " + code);
    }

    /**
     * 返回当前方向的反方向。
     *
     * <p>主要用于对冲逻辑：客户买入时做市商需要对冲卖出，客户卖出时做市商需要对冲买入。
     * 即 BUY.opposite() = SELL，SELL.opposite() = BUY。</p>
     *
     * @return 反方向枚举实例
     */
    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
