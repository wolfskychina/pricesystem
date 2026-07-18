package com.bank.trading.execution.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.execution.entity.HedgeOrder;
import com.bank.trading.execution.entity.HedgeTrade;
import com.bank.trading.execution.service.ExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对冲执行查询接口，供监控面板与运维查看对冲订单与成交流水。
 */
@RestController
@RequestMapping("/execution")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * 查询最近的对冲订单列表。
     *
     * @param limit 返回条数，默认 50
     * @return 对冲订单列表
     */
    @GetMapping("/orders")
    public Result<List<HedgeOrder>> listHedgeOrders(@RequestParam(defaultValue = "50") int limit) {
        return Result.success(executionService.findRecentHedgeOrders(limit));
    }

    /**
     * 查询指定对冲订单的成交流水。
     *
     * @param hedgeOrderId 对冲订单内部 ID
     * @return 成交流水列表
     */
    @GetMapping("/orders/{hedgeOrderId}/trades")
    public Result<List<HedgeTrade>> listHedgeTrades(@PathVariable String hedgeOrderId) {
        return Result.success(executionService.findHedgeTrades(hedgeOrderId));
    }
}
