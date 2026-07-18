package com.bank.trading.account.controller;

import com.bank.trading.account.dto.CreditInfo;
import com.bank.trading.account.entity.Customer;
import com.bank.trading.account.service.AccountService;
import com.bank.trading.common.core.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户账户管理接口。
 * <p>
 * 提供客户主数据 CRUD 与信用额度查询能力，供运营后台、风控服务（risk-service）
 * 与监控面板调用。
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 创建客户。
     *
     * @param customer 客户实体（必须包含 customerId 与 creditLimit）
     * @return 已创建的客户实体
     */
    @PostMapping
    public Result<Customer> create(@RequestBody Customer customer) {
        return Result.success(accountService.createCustomer(customer));
    }

    /**
     * 查询全部客户。
     *
     * @return 客户列表
     */
    @GetMapping
    public Result<List<Customer>> list() {
        return Result.success(accountService.findAll());
    }

    /**
     * 查询指定客户。
     *
     * @param customerId 客户 ID
     * @return 客户实体；不存在时 data 为 null
     */
    @GetMapping("/{customerId}")
    public Result<Customer> get(@PathVariable String customerId) {
        return Result.success(accountService.findByCustomerId(customerId));
    }

    /**
     * 更新客户主数据（name/level/status/credit_limit）。
     * <p>
     * 仅传入的字段会更新，未传入字段保留原值（不支持 PATCH 语义的细粒度更新，
     * 调用方需传入完整主数据）。
     *
     * @param customerId 客户 ID
     * @param customer   客户实体
     * @return 更新后的客户实体
     */
    @PutMapping("/{customerId}")
    public Result<Customer> update(@PathVariable String customerId,
                                   @RequestBody Customer customer) {
        customer.setCustomerId(customerId);
        return Result.success(accountService.updateCustomer(customer));
    }

    /**
     * 删除客户（物理删除）。
     *
     * @param customerId 客户 ID
     * @return 是否删除成功
     */
    @DeleteMapping("/{customerId}")
    public Result<Boolean> delete(@PathVariable String customerId) {
        return Result.success(accountService.deleteCustomer(customerId));
    }

    /**
     * 查询客户信用额度信息。
     * <p>
     * 供 risk-service 事前风控调用：判断 available_credit 是否足够覆盖新订单的成交金额。
     *
     * @param customerId 客户 ID
     * @return 信用额度视图（credit_limit / used_credit / available_credit）；不存在时 data 为 null
     */
    @GetMapping("/{customerId}/credit")
    public Result<CreditInfo> getCredit(@PathVariable String customerId) {
        return Result.success(accountService.getCreditInfo(customerId));
    }
}
