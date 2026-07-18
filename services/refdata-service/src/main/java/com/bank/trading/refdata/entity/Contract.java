package com.bank.trading.refdata.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Contract {

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getExchange() {
        return exchange;
    }

    public String getProduct() {
        return product;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }

    public BigDecimal getMinQty() {
        return minQty;
    }

    public LocalDate getListedDate() {
        return listedDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
    }

    public void setMinQty(BigDecimal minQty) {
        this.minQty = minQty;
    }

    public void setListedDate(LocalDate listedDate) {
        this.listedDate = listedDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contract that = (Contract) o;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (code != null ? !code.equals(that.code) : that.code != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (exchange != null ? !exchange.equals(that.exchange) : that.exchange != null) return false;
        if (product != null ? !product.equals(that.product) : that.product != null) return false;
        if (multiplier != null ? !multiplier.equals(that.multiplier) : that.multiplier != null) return false;
        if (tickSize != null ? !tickSize.equals(that.tickSize) : that.tickSize != null) return false;
        if (minQty != null ? !minQty.equals(that.minQty) : that.minQty != null) return false;
        if (listedDate != null ? !listedDate.equals(that.listedDate) : that.listedDate != null) return false;
        if (expiryDate != null ? !expiryDate.equals(that.expiryDate) : that.expiryDate != null) return false;
        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) return false;
        if (updatedAt != null ? !updatedAt.equals(that.updatedAt) : that.updatedAt != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (code != null ? code.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (exchange != null ? exchange.hashCode() : 0);
        result = 31 * result + (product != null ? product.hashCode() : 0);
        result = 31 * result + (multiplier != null ? multiplier.hashCode() : 0);
        result = 31 * result + (tickSize != null ? tickSize.hashCode() : 0);
        result = 31 * result + (minQty != null ? minQty.hashCode() : 0);
        result = 31 * result + (listedDate != null ? listedDate.hashCode() : 0);
        result = 31 * result + (expiryDate != null ? expiryDate.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (createdAt != null ? createdAt.hashCode() : 0);
        result = 31 * result + (updatedAt != null ? updatedAt.hashCode() : 0);
        return result;
    }
    @Override
    public String toString() {
        return "Contract{id=" + id + ", code='" + code + "', name='" + name + "', exchange='" + exchange + "', product='" + product + "', multiplier=" + multiplier + ", tickSize=" + tickSize + ", minQty=" + minQty + ", listedDate=" + listedDate + ", expiryDate=" + expiryDate + ", status='" + status + "', createdAt=" + createdAt + ", updatedAt=" + updatedAt + "}";
    }

}
