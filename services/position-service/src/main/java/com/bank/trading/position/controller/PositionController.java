package com.bank.trading.position.controller;

import com.bank.trading.common.core.result.Result;
import com.bank.trading.position.entity.NetExposure;
import com.bank.trading.position.entity.Position;
import com.bank.trading.position.service.PositionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 持仓查询与敞口监控接口。
 * <p>
 * 供监控面板、风控系统、运维查询客户持仓与净敞口。所有接口只读，不修改状态。
 */
@RestController
@RequestMapping("/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    /**
     * 查询某客户的全部持仓。
     *
     * @param customerId 客户 ID
     * @return 持仓列表
     */
    @GetMapping("/{customerId}")
    public Result<List<Position>> listByCustomer(@PathVariable String customerId) {
        return Result.success(positionService.findByCustomer(customerId));
    }

    /**
     * 查询某客户某合约的持仓。
     *
     * @param customerId 客户 ID
     * @param symbol     合约代码
     * @return 持仓实体；不存在时 data 为 null
     */
    @GetMapping("/{customerId}/{symbol}")
    public Result<Position> getByCustomerAndSymbol(@PathVariable String customerId,
                                                   @PathVariable String symbol) {
        return Result.success(positionService.findByCustomerAndSymbol(customerId, symbol));
    }

    /**
     * 查询所有合约的净敞口（敞口监控）。
     * <p>
     * 净敞口 = 客户总头寸 − 对冲头寸。完全对冲时为 0，非零值表示存在未对冲敞口。
     *
     * @return 净敞口列表（每个合约一项）
     */
    @GetMapping("/exposure")
    public Result<List<NetExposure>> listNetExposure() {
        return Result.success(positionService.calculateNetExposure());
    }

    /**
     * 查询指定合约的净敞口。
     *
     * @param symbol 合约代码
     * @return 净敞口视图；客户与对冲均不存在时 data 为 null
     */
    @GetMapping("/exposure/{symbol}")
    public Result<NetExposure> getNetExposure(@PathVariable String symbol) {
        return Result.success(positionService.calculateNetExposure(symbol));
    }
}
