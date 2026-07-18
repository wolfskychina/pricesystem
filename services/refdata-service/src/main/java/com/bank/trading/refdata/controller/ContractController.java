package com.bank.trading.refdata.controller;

import com.bank.trading.common.core.dto.ContractDTO;
import com.bank.trading.common.core.result.Result;
import com.bank.trading.refdata.service.ContractService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 合约主数据 REST 控制器。
 * <p>
 * 该控制器是银行做市商交易系统中"参考数据服务"（Reference Data Service）对外暴露的 HTTP 入口，
 * 负责管理合约（Contract）主数据的 CRUD 操作。
 * </p>
 * <p>
 * 业务背景：合约是交易的基础标的，订单、行情、报价、风控等模块都依赖合约主数据进行
 * 标的识别、乘数换算、最小变动价位匹配等。前端、交易服务、行情服务等通过本控制器
 * 提供的接口查询和维护合约信息。
 * </p>
 * <p>
 * 所有接口统一返回 {@link Result} 包装对象，便于上游统一处理成功/失败结果。
 * 基础路径为 {@code /refdata/contracts}。
 * </p>
 */
@RestController
@RequestMapping("/refdata/contracts")
public class ContractController {

    /** 合约业务服务，封装合约主数据的核心业务逻辑 */
    private final ContractService contractService;

    /**
     * 通过构造器注入合约服务实例。
     * <p>
     * 采用构造器注入而非字段注入，便于依赖管理、单元测试与不可变性保证。
     * </p>
     *
     * @param contractService 合约业务服务实例
     */
    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    /**
     * 查询全部处于活跃状态（ACTIVE）的合约列表。
     * <p>
     * 通常用于交易启动时加载合约字典、行情订阅前获取标的清单等场景。
     * </p>
     *
     * @return 包含活跃合约 DTO 列表的统一响应结果
     */
    @GetMapping
    public Result<List<ContractDTO>> listActiveContracts() {
        return Result.success(contractService.listActiveContracts());
    }

    /**
     * 根据合约代码查询单个合约详情。
     * <p>
     * 合约代码是合约的业务唯一标识（如 IF2406、CU2406 等），交易系统在订单/行情处理中
     * 通常以合约代码作为主键进行索引。
     * </p>
     *
     * @param code 合约代码，由路径参数提供
     * @return 包含合约 DTO 的统一响应结果；若不存在则由 Service 层抛出业务异常
     */
    @GetMapping("/{code}")
    public Result<ContractDTO> getContractByCode(@PathVariable String code) {
        return Result.success(contractService.getContractByCode(code));
    }

    /**
     * 根据合约主键 ID 查询单个合约详情。
     * <p>
     * 主要用于内部管理场景（如后台维护、数据修复），业务交易链路通常使用合约代码而非 ID。
     * </p>
     *
     * @param id 合约主键 ID，由路径参数提供
     * @return 包含合约 DTO 的统一响应结果；若不存在则由 Service 层抛出业务异常
     */
    @GetMapping("/id/{id}")
    public Result<ContractDTO> getContractById(@PathVariable Long id) {
        return Result.success(contractService.getContractById(id));
    }

    /**
     * 根据交易所代码查询活跃合约列表。
     * <p>
     * 多交易所接入场景下，交易服务在订阅行情或路由订单前需要先按交易所筛选可用合约。
     * 仅返回该交易所下状态为 ACTIVE 的合约。
     * </p>
     *
     * @param exchange 交易所代码（如 SHFE、CFFEX、INE 等），由路径参数提供
     * @return 包含匹配合约 DTO 列表的统一响应结果
     */
    @GetMapping("/exchange/{exchange}")
    public Result<List<ContractDTO>> listByExchange(@PathVariable String exchange) {
        return Result.success(contractService.listByExchange(exchange));
    }

    /**
     * 新增合约主数据。
     * <p>
     * 新合约上市前由运营或上游系统调用本接口录入。Service 层会校验合约代码唯一性，
     * 并对未指定状态的合约默认置为 ACTIVE。
     * </p>
     *
     * @param dto 合约数据传输对象，包含合约的完整字段信息
     * @return 包含新建后合约 DTO 的统一响应结果（含数据库生成的主键 ID）
     */
    @PostMapping
    public Result<ContractDTO> createContract(@RequestBody ContractDTO dto) {
        return Result.success(contractService.createContract(dto));
    }

    /**
     * 更新合约主数据。
     * <p>
     * 用于合约信息变更（如修改最小变动价位、调整状态为 EXPIRED 等）。Service 层采用
     * 按字段非空覆盖的策略实现部分更新，避免传入空值覆盖既有数据。
     * </p>
     *
     * @param id  待更新合约的主键 ID，由路径参数提供
     * @param dto 包含更新字段的合约 DTO，由请求体提供
     * @return 包含更新后合约 DTO 的统一响应结果
     */
    @PutMapping("/{id}")
    public Result<ContractDTO> updateContract(@PathVariable Long id, @RequestBody ContractDTO dto) {
        return Result.success(contractService.updateContract(id, dto));
    }
}
