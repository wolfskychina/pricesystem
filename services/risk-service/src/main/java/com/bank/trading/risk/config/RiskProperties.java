package com.bank.trading.risk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "risk")
public class RiskProperties {

    private BigDecimal defaultCreditLimit = BigDecimal.valueOf(10_000_000);
    private int defaultSingleOrderQtyLimit = 1000;
    private BigDecimal defaultDailyTradeAmountLimit = BigDecimal.valueOf(5_000_000);
    private int defaultPositionLimit = 5000;
    private int defaultPriceDeviationBps = 200;

    private List<CustomerLimit> customerLimits = new ArrayList<>();

    @Data
    public static class CustomerLimit {
        private String customerId;
        private BigDecimal creditLimit;
        private Integer singleOrderQtyLimit;
        private BigDecimal dailyTradeAmountLimit;
        private Integer positionLimit;
    }

    public BigDecimal getCreditLimit(String customerId) {
        CustomerLimit limit = findCustomerLimit(customerId);
        if (limit != null && limit.getCreditLimit() != null) {
            return limit.getCreditLimit();
        }
        return defaultCreditLimit;
    }

    public int getSingleOrderQtyLimit(String customerId) {
        CustomerLimit limit = findCustomerLimit(customerId);
        if (limit != null && limit.getSingleOrderQtyLimit() != null) {
            return limit.getSingleOrderQtyLimit();
        }
        return defaultSingleOrderQtyLimit;
    }

    public BigDecimal getDailyTradeAmountLimit(String customerId) {
        CustomerLimit limit = findCustomerLimit(customerId);
        if (limit != null && limit.getDailyTradeAmountLimit() != null) {
            return limit.getDailyTradeAmountLimit();
        }
        return defaultDailyTradeAmountLimit;
    }

    public int getPositionLimit(String customerId) {
        CustomerLimit limit = findCustomerLimit(customerId);
        if (limit != null && limit.getPositionLimit() != null) {
            return limit.getPositionLimit();
        }
        return defaultPositionLimit;
    }

    private CustomerLimit findCustomerLimit(String customerId) {
        if (customerId == null) {
            return null;
        }
        return customerLimits.stream()
                .filter(cl -> customerId.equals(cl.getCustomerId()))
                .findFirst()
                .orElse(null);
    }
}
