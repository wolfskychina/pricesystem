package com.bank.trading.oms.controller;

import com.bank.trading.common.core.dto.OrderCreateDTO;
import com.bank.trading.common.core.dto.OrderDTO;
import com.bank.trading.common.core.dto.TradeDTO;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.oms.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Result<OrderDTO> createOrder(@RequestBody OrderCreateDTO request) {
        try {
            OrderDTO order = orderService.createOrder(request);
            return Result.success(order);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    @DeleteMapping("/{orderId}")
    public Result<OrderDTO> cancelOrder(@PathVariable String orderId) {
        try {
            OrderDTO order = orderService.cancelOrder(orderId);
            return Result.success(order);
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.fail(409, e.getMessage());
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/{orderId}")
    public Result<OrderDTO> getOrder(@PathVariable String orderId) {
        OrderDTO order = orderService.getOrderByOrderId(orderId);
        if (order == null) {
            return Result.fail(404, "Order not found: " + orderId);
        }
        return Result.success(order);
    }

    @GetMapping
    public Result<List<OrderDTO>> getOrdersByCustomer(@RequestParam String customerId) {
        List<OrderDTO> orders = orderService.getOrdersByCustomer(customerId);
        return Result.success(orders);
    }

    @GetMapping("/{orderId}/trades")
    public Result<List<TradeDTO>> getTradesByOrder(@PathVariable String orderId) {
        List<TradeDTO> trades = orderService.getTradesByOrderId(orderId);
        return Result.success(trades);
    }

    @GetMapping("/trades")
    public Result<List<TradeDTO>> getTradesByCustomer(@RequestParam String customerId) {
        List<TradeDTO> trades = orderService.getTradesByCustomer(customerId);
        return Result.success(trades);
    }
}
