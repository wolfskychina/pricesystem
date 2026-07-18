package com.bank.trading.oms.service;

import java.math.BigDecimal;

public interface PriceProvider {
    BigDecimal getExecutionPrice(String symbol, String side);
}
