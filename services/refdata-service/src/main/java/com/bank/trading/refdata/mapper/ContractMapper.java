package com.bank.trading.refdata.mapper;

import com.bank.trading.refdata.entity.Contract;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ContractMapper {

    @Select("SELECT * FROM contract WHERE status = 'ACTIVE' ORDER BY code")
    List<Contract> findAllActive();

    @Select("SELECT * FROM contract WHERE code = #{code}")
    Contract findByCode(@Param("code") String code);

    @Select("SELECT * FROM contract WHERE id = #{id}")
    Contract findById(@Param("id") Long id);

    @Select("SELECT * FROM contract WHERE exchange = #{exchange} AND status = 'ACTIVE' ORDER BY code")
    List<Contract> findByExchange(@Param("exchange") String exchange);

    @Insert("INSERT INTO contract (code, name, exchange, product, multiplier, tick_size, min_qty, " +
            "listed_date, expiry_date, status, created_at, updated_at) " +
            "VALUES (#{code}, #{name}, #{exchange}, #{product}, #{multiplier}, #{tickSize}, #{minQty}, " +
            "#{listedDate}, #{expiryDate}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Contract contract);

    @Update("UPDATE contract SET name = #{name}, exchange = #{exchange}, product = #{product}, " +
            "multiplier = #{multiplier}, tick_size = #{tickSize}, min_qty = #{minQty}, " +
            "listed_date = #{listedDate}, expiry_date = #{expiryDate}, status = #{status}, " +
            "updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Contract contract);
}
