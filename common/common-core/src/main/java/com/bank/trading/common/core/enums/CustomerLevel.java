package com.bank.trading.common.core.enums;

import lombok.Getter;

/**
 * 客户等级枚举，用于区分不同类型的交易客户，影响报价点差与风控额度。
 *
 * <p>在做市商业务中，不同等级的客户享受不同的交易条件：
 * <ul>
 *   <li>{@link #NORMAL} —— 普通客户，点差最大，风控额度最低；</li>
 *   <li>{@link #VIP} —— VIP 客户，享受优惠点差，风控额度较高；</li>
 *   <li>{@link #INSTITUTION} —— 机构客户，点差最小（甚至可能为负点差返佣），
 *       风控额度最高，通常为银行、基金等大型机构。</li>
 * </ul>
 * 客户等级会作为报价引擎计算买卖价差的输入参数之一。</p>
 */
@Getter
public enum CustomerLevel {
    /** 普通客户：点差最大，风控额度最低 */
    NORMAL("NORMAL", "普通客户"),
    /** VIP 客户：享受优惠点差，风控额度较高 */
    VIP("VIP", "VIP客户"),
    /** 机构客户：点差最小，风控额度最高（银行/基金等大型机构） */
    INSTITUTION("INSTITUTION", "机构客户");

    /** 等级码，持久化到数据库 customer 表 */
    private final String code;
    /** 中文描述，用于前端展示 */
    private final String desc;

    CustomerLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据等级码字符串解析为枚举实例（大小写不敏感）。
     *
     * <p>与其它枚举不同，本方法在无法匹配时<b>不抛异常</b>，而是兜底返回
     * {@link #NORMAL}。这是因为在客户等级缺失或脏数据场景下，业务上更倾向于
     * 按最保守的普通客户处理，而非中断流程。</p>
     *
     * @param code 等级码字符串
     * @return 对应的枚举实例；无法匹配时返回 NORMAL
     */
    public static CustomerLevel of(String code) {
        for (CustomerLevel level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        // 兜底返回普通客户，保证脏数据不中断主流程
        return NORMAL;
    }
}
