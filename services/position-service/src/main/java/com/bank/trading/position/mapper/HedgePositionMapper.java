package com.bank.trading.position.mapper;

import com.bank.trading.position.entity.HedgePosition;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 对冲持仓 Mapper，提供 hedge_position 表的持久化与查询能力。
 * <p>
 * 对冲头寸按合约维度唯一（symbol UNIQUE），由 hedge-fill-event 累加更新。
 */
@Mapper
public interface HedgePositionMapper {

    /**
     * 插入新对冲持仓记录。
     *
     * @param position 对冲持仓实体
     * @return 影响行数
     */
    @Insert("INSERT INTO hedge_position(symbol, qty, avg_cost, version, updated_at) " +
            "VALUES(#{symbol}, #{qty}, #{avgCost}, #{version}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HedgePosition position);

    /**
     * 根据合约代码查询对冲持仓。
     *
     * @param symbol 合约代码
     * @return 对冲持仓实体；不存在返回 null
     */
    @Select("SELECT * FROM hedge_position WHERE symbol = #{symbol}")
    HedgePosition findBySymbol(String symbol);

    /**
     * 查询所有对冲持仓（用于敞口聚合）。
     *
     * @return 全部对冲持仓列表
     */
    @Select("SELECT * FROM hedge_position ORDER BY symbol")
    List<HedgePosition> findAll();

    /**
     * 更新对冲持仓（按 id 定位，version 自增）。
     *
     * @param position 对冲持仓实体
     * @return 影响行数
     */
    @Update("UPDATE hedge_position SET qty=#{qty}, avg_cost=#{avgCost}, " +
            "version=version+1, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(HedgePosition position);
}
