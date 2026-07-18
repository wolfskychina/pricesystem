package com.bank.trading.execution.mapper;

import com.bank.trading.execution.entity.HedgeOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 对冲订单 Mapper，提供对冲订单的持久化与查询能力。
 * <p>
 * 采用 MyBatis 注解式 SQL，与系统其他服务风格保持一致。
 */
@Mapper
public interface HedgeOrderMapper {

    /**
     * 插入对冲订单。
     *
     * @param order 对冲订单实体
     * @return 影响行数
     */
    @Insert("INSERT INTO hedge_orders(hedge_order_id, exchange_order_id, original_trade_id, customer_id, " +
            "symbol, side, type, qty, price, filled_qty, avg_price, status, is_batched, batch_item_count, " +
            "created_at, updated_at) " +
            "VALUES(#{hedgeOrderId}, #{exchangeOrderId}, #{originalTradeId}, #{customerId}, " +
            "#{symbol}, #{side}, #{type}, #{qty}, #{price}, #{filledQty}, #{avgPrice}, #{status}, " +
            "#{isBatched}, #{batchItemCount}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HedgeOrder order);

    /**
     * 根据对冲订单内部 ID 查询。
     *
     * @param hedgeOrderId 对冲订单内部 ID
     * @return 对冲订单；不存在返回 null
     */
    @Select("SELECT * FROM hedge_orders WHERE hedge_order_id = #{hedgeOrderId}")
    HedgeOrder findByHedgeOrderId(String hedgeOrderId);

    /**
     * 根据交易所订单 ID 查询（用于 Webhook 回调时定位对冲订单）。
     *
     * @param exchangeOrderId 交易所订单 ID
     * @return 对冲订单；不存在返回 null
     */
    @Select("SELECT * FROM hedge_orders WHERE exchange_order_id = #{exchangeOrderId}")
    HedgeOrder findByExchangeOrderId(String exchangeOrderId);

    /**
     * 更新对冲订单状态与成交信息。
     *
     * @param order 对冲订单实体（按 exchangeOrderId 定位，更新 status/filledQty/avgPrice/updatedAt）
     * @return 影响行数
     */
    @Update("UPDATE hedge_orders SET status=#{status}, filled_qty=#{filledQty}, avg_price=#{avgPrice}, " +
            "updated_at=#{updatedAt} WHERE exchange_order_id=#{exchangeOrderId}")
    int updateByExchangeOrderId(HedgeOrder order);

    /**
     * 查询最近的对冲订单列表。
     *
     * @param limit 返回条数上限
     * @return 对冲订单列表（按创建时间倒序）
     */
    @Select("SELECT * FROM hedge_orders ORDER BY created_at DESC LIMIT #{limit}")
    List<HedgeOrder> findRecent(int limit);

    /**
     * 按状态统计对冲订单数（用于监控）。
     *
     * @param status 订单状态
     * @return 该状态下的对冲订单数
     */
    @Select("SELECT COUNT(*) FROM hedge_orders WHERE status = #{status}")
    int countByStatus(String status);
}
