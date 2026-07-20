package com.bank.trading.account.mapper;

import com.bank.trading.account.entity.Customer;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 客户账户 Mapper，提供 customer 表的持久化与查询能力。
 * <p>
 * 采用 MyBatis 注解式 SQL，与系统其他服务风格保持一致。
 */
@Mapper
public interface CustomerMapper {

    /**
     * 插入新客户记录。
     *
     * @param customer 客户实体
     * @return 影响行数
     */
    @Insert("INSERT INTO customer(id, customer_id, name, level, status, credit_limit, " +
            "used_credit, version, created_at, updated_at) " +
            "VALUES(#{id}, #{customerId}, #{name}, #{level}, #{status}, #{creditLimit}, " +
            "#{usedCredit}, #{version}, #{createdAt}, #{updatedAt})")
    int insert(Customer customer);

    /**
     * 根据客户 ID 查询。
     *
     * @param customerId 客户 ID
     * @return 客户实体；不存在返回 null
     */
    @Select("SELECT * FROM customer WHERE customer_id = #{customerId}")
    Customer findByCustomerId(String customerId);

    /**
     * 查询全部客户。
     *
     * @return 客户列表
     */
    @Select("SELECT * FROM customer ORDER BY customer_id")
    List<Customer> findAll();

    /**
     * 更新客户主数据（name/level/status/credit_limit），不影响 used_credit。
     *
     * @param customer 客户实体（按 customerId 定位）
     * @return 影响行数
     */
    @Update("UPDATE customer SET name=#{name}, level=#{level}, status=#{status}, " +
            "credit_limit=#{creditLimit}, version=version+1, updated_at=#{updatedAt} " +
            "WHERE customer_id=#{customerId}")
    int update(Customer customer);

    /**
     * 更新已用额度（按 customerId 定位，version 自增）。
     * <p>
     * 由 trade-event 消费触发，仅修改 used_credit 字段。
     *
     * @param customer 客户实体（按 customerId 定位，更新 used_credit）
     * @return 影响行数
     */
    @Update("UPDATE customer SET used_credit=#{usedCredit}, version=version+1, " +
            "updated_at=#{updatedAt} WHERE customer_id=#{customerId}")
    int updateUsedCredit(Customer customer);

    /**
     * 删除客户（按 customerId 定位）。
     *
     * @param customerId 客户 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM customer WHERE customer_id = #{customerId}")
    int deleteByCustomerId(String customerId);
}
