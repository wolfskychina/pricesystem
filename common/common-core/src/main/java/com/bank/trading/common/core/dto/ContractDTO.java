package com.bank.trading.common.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContractDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private String exchange;
    private String product;
    private BigDecimal multiplier;
    private BigDecimal tickSize;
    private BigDecimal minQty;
    private LocalDate listedDate;
    private LocalDate expiryDate;
    private String status;
}
