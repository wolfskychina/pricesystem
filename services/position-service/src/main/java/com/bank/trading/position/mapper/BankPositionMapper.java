package com.bank.trading.position.mapper;

import com.bank.trading.position.entity.BankPosition;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 银行自身持仓 Mapper，提供 bank_position 表的持久化与查询能力。
 * <p>
 * 银行自身头寸按合约维度唯一（symbol UNIQUE），由 trade-event 同步更新。
 * 与客户持仓（Position）在同一事务内写入，保证一致性。
 */
@Mapper
public interface BankPositionMapper {

    /**
     * 插入新银行自身持仓记录。
     *
     * @param position 银行自身持仓实体
     * @return 影响行数
     */
    @Insert("INSERT INTO bank_position(id, symbol, qty, avg_cost, realized_pnl, version, created_at, updated_at) " +
            "VALUES(#{id}, #{symbol}, #{qty}, #{avgCost}, #{realizedPnl}, #{version}, #{createdAt}, #{updatedAt})")
    int insert(BankPosition position);

    /**
     * 根据合约代码查询银行自身持仓。
     *
     * @param symbol 合约代码
     * @return 银行自身持仓实体；不存在返回 null
     */
    @Select("SELECT * FROM bank_position WHERE symbol = #{symbol}")
    BankPosition findBySymbol(String symbol);

    /**
     * 查询所有银行自身持仓。
     *
     * @return 全部银行自身持仓列表
     */
    @Select("SELECT * FROM bank_position ORDER BY symbol")
    List<BankPosition> findAll();

    /**
     * 更新银行自身持仓（按 id 定位，version 自增）。
     *
     * @param position 银行自身持仓实体
     * @return 影响行数
     */
    @Update("UPDATE bank_position SET qty=#{qty}, avg_cost=#{avgCost}, realized_pnl=#{realizedPnl}, " +
            "version=version+1, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(BankPosition position);
}