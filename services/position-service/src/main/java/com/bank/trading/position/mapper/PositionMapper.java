package com.bank.trading.position.mapper;

import com.bank.trading.position.entity.Position;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 客户持仓 Mapper，提供 position 表的持久化与查询能力。
 * <p>
 * 采用 MyBatis 注解式 SQL，与系统其他服务风格保持一致。
 * 使用 {@code INSERT ON CONFLICT}（SQLite 方言）实现 upsert，
 * 保证并发场景下 (customer_id, symbol) 唯一约束不冲突。
 */
@Mapper
public interface PositionMapper {

    /**
     * 插入新持仓记录。
     *
     * @param position 持仓实体
     * @return 影响行数
     */
    @Insert("INSERT INTO position(customer_id, symbol, qty, avg_cost, realized_pnl, version, " +
            "created_at, updated_at) " +
            "VALUES(#{customerId}, #{symbol}, #{qty}, #{avgCost}, #{realizedPnl}, #{version}, " +
            "#{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Position position);

    /**
     * 根据 (customerId, symbol) 查询持仓。
     *
     * @param customerId 客户 ID
     * @param symbol     合约代码
     * @return 持仓实体；不存在返回 null
     */
    @Select("SELECT * FROM position WHERE customer_id = #{customerId} AND symbol = #{symbol}")
    Position findByCustomerAndSymbol(@Param("customerId") String customerId,
                                     @Param("symbol") String symbol);

    /**
     * 查询某客户的所有持仓。
     *
     * @param customerId 客户 ID
     * @return 持仓列表
     */
    @Select("SELECT * FROM position WHERE customer_id = #{customerId} ORDER BY symbol")
    List<Position> findByCustomer(String customerId);

    /**
     * 查询所有持仓（用于敞口聚合）。
     *
     * @return 全部持仓列表
     */
    @Select("SELECT * FROM position ORDER BY customer_id, symbol")
    List<Position> findAll();

    /**
     * 按合约汇总所有客户的持仓数量之和（用于敞口计算）。
     *
     * @return 每个合约的客户总头寸列表（仅 symbol、qty 字段有效）
     */
    @Select("SELECT symbol, SUM(qty) AS qty FROM position GROUP BY symbol")
    List<Position> sumQtyBySymbol();

    /**
     * 更新持仓（全字段更新，按 id 定位）。
     *
     * @param position 持仓实体
     * @return 影响行数
     */
    @Update("UPDATE position SET qty=#{qty}, avg_cost=#{avgCost}, realized_pnl=#{realizedPnl}, " +
            "version=version+1, updated_at=#{updatedAt} WHERE id=#{id}")
    int update(Position position);
}
