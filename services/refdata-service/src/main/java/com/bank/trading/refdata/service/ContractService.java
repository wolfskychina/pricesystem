package com.bank.trading.refdata.service;

import com.bank.trading.common.core.dto.ContractDTO;
import com.bank.trading.common.core.exception.BusinessException;
import com.bank.trading.refdata.entity.Contract;
import com.bank.trading.refdata.mapper.ContractMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 合约主数据业务服务。
 * <p>
 * 该服务是参考数据服务（Reference Data Service）的核心业务层，封装了合约主数据
 * 的查询、新增、更新等业务逻辑，并负责实体（{@link Contract}）与 DTO
 * （{@link ContractDTO}）之间的相互转换。
 * </p>
 * <p>
 * 业务背景：合约（Contract）是银行做市商交易系统中所有交易活动的基础标的，订单、行情、
 * 报价、风控、清算等模块都依赖合约主数据。本服务对外提供合约的 CRUD 能力，
 * 并对数据一致性、唯一性等业务规则进行校验。
 * </p>
 * <p>
 * 异常处理：当合约不存在或违反唯一性约束时，抛出 {@link BusinessException}，
 * 由全局异常处理器统一转换为标准错误响应。
 * </p>
 */
@Service
public class ContractService {

    /** 合约数据访问层，基于 MyBatis 注解 SQL 实现合约表的增删改查 */
    private final ContractMapper contractMapper;

    /** 日志记录器，用于记录关键业务操作（如创建、更新合约等） */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContractService.class);

    /**
     * 通过构造器注入合约数据访问层实例。
     *
     * @param contractMapper 合约 Mapper 实例
     */
    public ContractService(ContractMapper contractMapper) {
        this.contractMapper = contractMapper;
    }

    /**
     * 查询全部活跃状态的合约。
     * <p>
     * "活跃"指 status = 'ACTIVE' 的合约，即当前可参与交易的合约。已到期（EXPIRED）
     * 或下市（DELISTED）等状态的合约不会被返回。
     * </p>
     * <p>
     * 业务场景：交易服务启动加载合约字典、行情服务订阅前获取标的清单等。
     * </p>
     *
     * @return 活跃合约 DTO 列表，按合约代码排序；若无数据则返回空列表
     */
    public List<ContractDTO> listActiveContracts() {
        return contractMapper.findAllActive().stream()
                .map(this::toDTO) // 将实体逐一转换为 DTO，避免对外暴露持久层模型
                .collect(Collectors.toList());
    }

    /**
     * 根据合约代码查询单个合约。
     * <p>
     * 合约代码是合约的业务唯一标识，交易链路（订单、行情、报价）通常以合约代码
     * 作为标的索引键。
     * </p>
     *
     * @param code 合约代码
     * @return 合约 DTO
     * @throws BusinessException 当指定代码的合约不存在时抛出（HTTP 404）
     */
    public ContractDTO getContractByCode(String code) {
        Contract contract = contractMapper.findByCode(code);
        if (contract == null) {
            // 合约不存在属于业务级异常，统一通过 BusinessException 抛出并由全局异常处理器转换
            throw new BusinessException(404, "Contract not found: " + code);
        }
        return toDTO(contract);
    }

    /**
     * 根据主键 ID 查询单个合约。
     * <p>
     * 主要用于后台管理或数据维护场景，业务交易链路通常使用合约代码查询。
     * </p>
     *
     * @param id 合约主键 ID
     * @return 合约 DTO
     * @throws BusinessException 当指定 ID 的合约不存在时抛出（HTTP 404）
     */
    public ContractDTO getContractById(Long id) {
        Contract contract = contractMapper.findById(id);
        if (contract == null) {
            throw new BusinessException(404, "Contract not found: " + id);
        }
        return toDTO(contract);
    }

    /**
     * 根据交易所代码查询活跃合约列表。
     * <p>
     * 用于多交易所接入场景，按交易所维度筛选可用合约，便于行情订阅、订单路由等
     * 模块按交易所分组处理。
     * </p>
     *
     * @param exchange 交易所代码（如 SHFE、CFFEX 等）
     * @return 该交易所下活跃合约 DTO 列表；若无数据则返回空列表
     */
    public List<ContractDTO> listByExchange(String exchange) {
        return contractMapper.findByExchange(exchange).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 新增合约主数据。
     * <p>
     * 业务规则：
     * <ul>
     *   <li>合约代码必须唯一，重复则抛出业务异常（HTTP 400）</li>
     *   <li>若未指定状态，默认置为 'ACTIVE'，即新建合约默认即可交易</li>
     *   <li>由服务端写入创建时间和更新时间，避免客户端篡改</li>
     * </ul>
     * </p>
     *
     * @param dto 合约数据传输对象
     * @return 新建后的合约 DTO，包含数据库生成的主键 ID
     * @throws BusinessException 当合约代码已存在时抛出（HTTP 400）
     */
    public ContractDTO createContract(ContractDTO dto) {
        // 通过合约代码进行唯一性校验，避免重复录入
        Contract existing = contractMapper.findByCode(dto.getCode());
        if (existing != null) {
            throw new BusinessException(400, "Contract already exists: " + dto.getCode());
        }
        Contract contract = toEntity(dto);
        // 由服务端写入时间戳，确保创建/更新时间一致且不被客户端伪造
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        // 未显式指定状态时默认置为活跃，使新建合约立即可用
        if (contract.getStatus() == null) {
            contract.setStatus("ACTIVE");
        }
        contractMapper.insert(contract); // useGeneratedKeys 会将数据库自增主键回填到 contract.id
        return toDTO(contract);
    }

    /**
     * 按主键 ID 更新合约主数据。
     * <p>
     * 采用"字段非空覆盖"的部分更新策略：仅当 DTO 中对应字段非空时才覆盖既有实体的字段值，
     * 从而支持部分字段更新，避免客户端因未传某字段而被误置空。
     * </p>
     * <p>
     * 不允许修改的字段：合约代码（code）与主键 ID 不在可更新范围内，以保证合约身份的稳定性。
     * </p>
     *
     * @param id  待更新合约的主键 ID
     * @param dto 包含待更新字段的合约 DTO
     * @return 更新后的合约 DTO
     * @throws BusinessException 当指定 ID 的合约不存在时抛出（HTTP 404）
     */
    public ContractDTO updateContract(Long id, ContractDTO dto) {
        Contract existing = contractMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(404, "Contract not found: " + id);
        }
        // 仅在 DTO 对应字段非空时才覆盖，实现"部分更新"语义
        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getExchange() != null) existing.setExchange(dto.getExchange());
        if (dto.getProduct() != null) existing.setProduct(dto.getProduct());
        if (dto.getMultiplier() != null) existing.setMultiplier(dto.getMultiplier());
        if (dto.getTickSize() != null) existing.setTickSize(dto.getTickSize());
        if (dto.getMinQty() != null) existing.setMinQty(dto.getMinQty());
        if (dto.getListedDate() != null) existing.setListedDate(dto.getListedDate());
        if (dto.getExpiryDate() != null) existing.setExpiryDate(dto.getExpiryDate());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        // 更新操作只刷新 updatedAt，保留原始 createdAt 不变
        existing.setUpdatedAt(LocalDateTime.now());
        contractMapper.update(existing);
        return toDTO(existing);
    }

    /**
     * 将合约实体转换为 DTO。
     * <p>
     * 实体与 DTO 分离的好处：DTO 仅暴露业务所需字段，避免持久层模型直接外泄，
     * 也便于后续对 DTO 增加展示字段或聚合其他来源数据。
     * </p>
     *
     * @param entity 合约实体
     * @return 合约 DTO
     */
    private ContractDTO toDTO(Contract entity) {
        ContractDTO dto = new ContractDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setExchange(entity.getExchange());
        dto.setProduct(entity.getProduct());
        dto.setMultiplier(entity.getMultiplier());
        dto.setTickSize(entity.getTickSize());
        dto.setMinQty(entity.getMinQty());
        dto.setListedDate(entity.getListedDate());
        dto.setExpiryDate(entity.getExpiryDate());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    /**
     * 将 DTO 转换为合约实体。
     * <p>
     * 用于接收前端/上游系统传入的 DTO 后，构建待持久化的实体对象。
     * 不拷贝时间戳字段，时间戳由 Service 层统一在创建/更新时写入。
     * </p>
     *
     * @param dto 合约 DTO
     * @return 合约实体（不含时间戳）
     */
    private Contract toEntity(ContractDTO dto) {
        Contract entity = new Contract();
        entity.setId(dto.getId());
        entity.setCode(dto.getCode());
        entity.setName(dto.getName());
        entity.setExchange(dto.getExchange());
        entity.setProduct(dto.getProduct());
        entity.setMultiplier(dto.getMultiplier());
        entity.setTickSize(dto.getTickSize());
        entity.setMinQty(dto.getMinQty());
        entity.setListedDate(dto.getListedDate());
        entity.setExpiryDate(dto.getExpiryDate());
        entity.setStatus(dto.getStatus());
        return entity;
    }
}
