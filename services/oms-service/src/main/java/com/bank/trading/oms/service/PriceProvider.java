package com.bank.trading.oms.service;

import com.bank.trading.common.core.dto.QuoteDTO;

import java.math.BigDecimal;

/**
 * 报价提供者接口。
 *
 * <p>封装对 pricing-service 的调用，提供报价查询功能。
 * 支持获取执行价格和完整报价信息，用于下单时的价格校验和有效期验证。
 */
public interface PriceProvider {

    /**
     * 获取指定合约、指定方向的执行价格。
     *
     * @param symbol 合约代码
     * @param side   买卖方向（BUY/SELL）
     * @return 执行价格，若无有效报价则返回 null
     */
    BigDecimal getExecutionPrice(String symbol, String side);

    /**
     * 获取指定合约的完整报价信息（含有效期）。
     *
     * @param symbol 合约代码
     * @return 完整报价信息，若无有效报价则返回 null
     */
    QuoteDTO getQuote(String symbol);
}
