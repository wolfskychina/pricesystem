package com.bank.trading.execution.service;

import java.math.BigDecimal;

/**
 * 对冲持仓查询接口，提供当前合约的对冲持仓净头寸。
 * <p>
 * 用于 HedgeBatcher 在出桶前查询当前对冲持仓，判断是否需要截断以避免库存超限。
 * <p>
 * <b>方向语义</b>：
 * <ul>
 *   <li>{@code qty > 0}：对冲多头（净对冲买入）</li>
 *   <li>{@code qty < 0}：对冲空头（净对冲卖出）</li>
 *   <li>{@code qty = 0}：无对冲持仓</li>
 * </ul>
 */
public interface HedgePositionProvider {

    /**
     * 查询指定合约的当前对冲持仓净头寸。
     *
     * @param symbol 合约代码
     * @return 对冲持仓净头寸（正=多头，负=空头），不存在时返回 {@link BigDecimal#ZERO}
     */
    BigDecimal getHedgePosition(String symbol);
}