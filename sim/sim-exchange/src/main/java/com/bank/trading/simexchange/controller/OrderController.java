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

    public OrderController(MatchingEngine matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

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
        private String clientOrderId;
        private String symbol;
        private String side;
        private String type;
        private BigDecimal qty;
        private BigDecimal price;

        public String getClientOrderId() {
            return clientOrderId;
        }

        public void setClientOrderId(String clientOrderId) {
            this.clientOrderId = clientOrderId;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getSide() {
            return side;
        }

        public void setSide(String side) {
            this.side = side;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public BigDecimal getQty() {
            return qty;
        }

        public void setQty(BigDecimal qty) {
            this.qty = qty;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}
