package com.bank.trading.common.core.enums;

import lombok.Getter;

/**
 * 订单类型枚举，区分市价单与限价单两种核心委托方式。
 *
 * <p>在做市商交易系统中，订单类型决定了撮合行为：
 * <ul>
 *   <li>{@link #MARKET} —— 市价单：不指定价格，按当前市场最优价格立即成交。
 *       优点是成交速度快，缺点是价格不可控（可能滑点）。做市商通常按自身报价
 *       或交易所盘口价格即时撮合市价单；</li>
 *   <li>{@link #LIMIT} —— 限价单：指定价格，只有当市场价格达到或优于指定价格时才成交。
 *       优点是价格可控，缺点是可能无法成交（价格未触及）。限价单会进入撮合队列
 *       等待价格触发。</li>
 * </ul></p>
 */
@Getter
public enum OrderType {
    /** 市价单：按当前市场价立即成交，不指定价格 */
    MARKET("MARKET", "市价单"),
    /** 限价单：指定价格，价格达到或优于指定值时才成交 */
    LIMIT("LIMIT", "限价单");

    /** 类型码，持久化与协议通信使用 */
    private final String code;
    /** 中文描述，用于日志与前端展示 */
    private final String desc;

    OrderType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据类型码字符串解析为枚举实例（大小写不敏感）。
     *
     * @param code 类型码字符串（"MARKET" 或 "LIMIT"）
     * @return 对应的枚举实例
     * @throws IllegalArgumentException 当 code 无法匹配时抛出
     */
    public static OrderType of(String code) {
        for (OrderType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid order type: " + code);
    }
}
