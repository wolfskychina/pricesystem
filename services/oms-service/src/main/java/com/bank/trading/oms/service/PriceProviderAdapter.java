package com.bank.trading.oms.service;

import com.bank.trading.common.core.dto.QuoteDTO;
import com.bank.trading.oms.client.PricingServiceClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 报价提供者适配器，将 pricing-service 客户端调用适配为 PriceProvider 接口。
 *
 * <p>通过 PricingServiceClient 调用远程报价服务，获取执行价格和完整报价信息，
 * 用于订单撮合时的价格获取与有效期校验。
 */
@Component
public class PriceProviderAdapter implements PriceProvider {

    private final PricingServiceClient pricingServiceClient;

    public PriceProviderAdapter(PricingServiceClient pricingServiceClient) {
        this.pricingServiceClient = pricingServiceClient;
    }

    /**
     * 获取指定合约、指定方向的执行价格。
     * 买方向使用客户卖价（customerAskPrice），卖方向使用客户买价（customerBidPrice）。
     *
     * @param symbol 合约代码
     * @param side   买卖方向（BUY/SELL）
     * @return 执行价格，若无有效报价则返回 null
     */
    @Override
    public BigDecimal getExecutionPrice(String symbol, String side) {
        return pricingServiceClient.getExecutionPrice(symbol, side);
    }

    /**
     * 获取指定合约的完整报价信息（含有效期）。
     *
     * @param symbol 合约代码
     * @return 完整报价信息，若无有效报价则返回 null
     */
    @Override
    public QuoteDTO getQuote(String symbol) {
        return pricingServiceClient.getQuote(symbol);
    }
}
