package com.bank.trading.refdata.mapper;

import com.bank.trading.refdata.entity.Contract;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 合约主数据 MyBatis Mapper。
 * <p>
 * 该接口是参考数据服务（Reference Data Service）的持久层，负责对 {@code contract}
 * 表执行增删改查操作。采用 MyBatis 注解方式定义 SQL，省去 XML 映射文件，
 * 适合本服务中 SQL 较简单的场景。
 * </p>
 * <p>
 * 业务背景：合约表存储所有可交易合约的主数据，是订单、行情、报价等模块的元数据基础。
 * 该 Mapper 仅被 {@code ContractService} 调用，不直接对外暴露。
 * </p>
 * <p>
 * 表结构对应关系（数据库列 -> 实体字段，遵循下划线转驼峰命名）：
 * code -> code, name -> name, exchange -> exchange, product -> product,
 * multiplier -> multiplier, tick_size -> tickSize, min_qty -> minQty,
 * listed_date -> listedDate, expiry_date -> expiryDate, status -> status,
 * created_at -> createdAt, updated_at -> updatedAt
 * </p>
 */
@Mapper
public interface ContractMapper {

    /**
     * 查询全部活跃合约。
     * <p>
     * 仅返回 status = 'ACTIVE' 的合约记录，并按合约代码升序排序，便于上层稳定展示与索引。
     * </p>
     *
     * @return 活跃合约实体列表
     */
    @Select("SELECT * FROM contract WHERE status = 'ACTIVE' ORDER BY code")
    List<Contract> findAllActive();

    /**
     * 根据合约代码精确查询单个合约。
     * <p>
     * 合约代码在系统中具有业务唯一性，本方法常用于：
     * <ul>
     *   <li>交易链路按合约代码加载合约主数据</li>
     *   <li>新增合约前的代码唯一性校验</li>
     * </ul>
     * </p>
     *
     * @param code 合约代码
     * @return 匹配的合约实体；若不存在返回 {@code null}
     */
    @Select("SELECT * FROM contract WHERE code = #{code}")
    Contract findByCode(@Param("code") String code);

    /**
     * 根据主键 ID 查询单个合约。
     * <p>
     * 主要用于后台维护、数据修复等场景。
     * </p>
     *
     * @param id 合约主键 ID
     * @return 匹配的合约实体；若不存在返回 {@code null}
     */
    @Select("SELECT * FROM contract WHERE id = #{id}")
    Contract findById(@Param("id") Long id);

    /**
     * 根据交易所代码查询该交易所下的活跃合约。
     * <p>
     * 同时限定 status = 'ACTIVE'，避免返回非活跃合约，并按合约代码升序排序，
     * 便于多交易所接入场景下按交易所分组加载合约。
     * </p>
     *
     * @param exchange 交易所代码
     * @return 该交易所下活跃合约实体列表
     */
    @Select("SELECT * FROM contract WHERE exchange = #{exchange} AND status = 'ACTIVE' ORDER BY code")
    List<Contract> findByExchange(@Param("exchange") String exchange);

    /**
     * 插入一条合约记录。
     * <p>
     * 通过 {@code @Options(useGeneratedKeys = true, keyProperty = "id")}
     * 让 MyBatis 将数据库自增主键回填到传入实体的 id 字段，便于调用方在插入后
     * 直接获取新合约的主键。
     * </p>
     *
     * @param contract 待插入的合约实体，需包含 code、name、exchange 等业务字段
     *                 以及 createdAt、updatedAt 时间戳
     * @return 受影响的行数，通常为 1
     */
    @Insert("INSERT INTO contract (id, code, name, exchange, product, multiplier, tick_size, min_qty, " +
            "listed_date, expiry_date, status, created_at, updated_at) " +
            "VALUES (#{id}, #{code}, #{name}, #{exchange}, #{product}, #{multiplier}, #{tickSize}, #{minQty}, " +
            "#{listedDate}, #{expiryDate}, #{status}, #{createdAt}, #{updatedAt})")
    int insert(Contract contract);

    /**
     * 根据主键 ID 更新合约主数据。
     * <p>
     * 全字段更新（除 id、code、createdAt 外）。注意：SQL 中并未更新 code 与 created_at，
     * 以保证合约身份的稳定性和创建时间的不可变性。updatedAt 由 Service 层在调用前写入。
     * </p>
     *
     * @param contract 待更新的合约实体，必须包含有效的主键 id
     * @return 受影响的行数，通常为 1；若主键不存在则返回 0
     */
    @Update("UPDATE contract SET name = #{name}, exchange = #{exchange}, product = #{product}, " +
            "multiplier = #{multiplier}, tick_size = #{tickSize}, min_qty = #{minQty}, " +
            "listed_date = #{listedDate}, expiry_date = #{expiryDate}, status = #{status}, " +
            "updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Contract contract);
}
