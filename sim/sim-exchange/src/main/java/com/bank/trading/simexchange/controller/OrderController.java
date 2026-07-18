package com.bank.trading.simexchange.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.simexchange.engine.MatchingEngine;
import com.bank.trading.simexchange.model.ExchangeOrder;
import com.bank.trading.simexchange.model.TradeFill;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/exchange/orders")
public class OrderController {

    private final MatchingEngine matchingEngine;
    private String clientOrderId;
    private String symbol;
    private String side;
    private String type;
    private BigDecimal qty;
    private BigDecimal price;

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    public MatchingEngine getMatchingEngine() {
        return matchingEngine;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSide() {
        return side;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderController that = (OrderController) o;
        if (matchingEngine != null ? !matchingEngine.equals(that.matchingEngine) : that.matchingEngine != null) return false;
        if (clientOrderId != null ? !clientOrderId.equals(that.clientOrderId) : that.clientOrderId != null) return false;
        if (symbol != null ? !symbol.equals(that.symbol) : that.symbol != null) return false;
        if (side != null ? !side.equals(that.side) : that.side != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (qty != null ? !qty.equals(that.qty) : that.qty != null) return false;
        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (matchingEngine != null ? matchingEngine.hashCode() : 0);
        result = 31 * result + (clientOrderId != null ? clientOrderId.hashCode() : 0);
        result = 31 * result + (symbol != null ? symbol.hashCode() : 0);
        result = 31 * result + (side != null ? side.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (qty != null ? qty.hashCode() : 0);
        result = 31 * result + (price != null ? price.hashCode() : 0);
        return result;
    }

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @Override

    @Override

    @Override

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @Override

    @Override

    @Override

    @PostMapping
    public Result<ExchangeOrder> submitOrder(@RequestBody OrderRequest request) {
        ExchangeOrder order = matchingEngine.submitOrder(
                request.getClientOrderId(),
                request.getSymbol(),
                request.getSide(),
                request.getType(),
                request.getQty(),
                request.getPrice()
        );
        return Result.success(order);
    }

    @GetMapping("/{orderId}")
    public Result<ExchangeOrder> getOrder(@PathVariable String orderId) {
        ExchangeOrder order = matchingEngine.getOrder(orderId);
        if (order == null) {
            return Result.fail(404, "Order not found: " + orderId);
        }
        return Result.success(order);
    }

    @GetMapping("/trades")
    public Result<List<TradeFill>> getTrades() {
        return Result.success(matchingEngine.getTradeFills());
    }

    public static class OrderRequest {
    }
    @Override
    public String toString() {
        return "OrderController{matchingEngine=" + matchingEngine + ", clientOrderId='" + clientOrderId + "', symbol='" + symbol + "', side='" + side + "', type='" + type + "', qty=" + qty + ", price=" + price + "}";
    }

}
