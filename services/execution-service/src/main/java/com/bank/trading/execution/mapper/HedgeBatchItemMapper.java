package com.bank.trading.execution.mapper;

import com.bank.trading.execution.entity.HedgeBatchItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 对冲聚合子项 Mapper，提供聚合子项的持久化与查询能力。
 */
@Mapper
public interface HedgeBatchItemMapper {

    @Insert("INSERT INTO hedge_batch_items(id, hedge_order_id, original_trade_id, customer_id, " +
            "symbol, side, qty, status, filled_qty, avg_price, created_at, updated_at) " +
            "VALUES(#{id}, #{hedgeOrderId}, #{originalTradeId}, #{customerId}, " +
            "#{symbol}, #{side}, #{qty}, #{status}, #{filledQty}, #{avgPrice}, " +
            "#{createdAt}, #{updatedAt})")
    int insert(HedgeBatchItem item);

    @Select("SELECT * FROM hedge_batch_items WHERE hedge_order_id = #{hedgeOrderId} ORDER BY id")
    List<HedgeBatchItem> findByHedgeOrderId(String hedgeOrderId);

    @Select("SELECT * FROM hedge_batch_items WHERE original_trade_id = #{originalTradeId}")
    HedgeBatchItem findByOriginalTradeId(String originalTradeId);

    @Update("UPDATE hedge_batch_items SET status=#{status}, hedge_order_id=#{hedgeOrderId}, " +
            "filled_qty=#{filledQty}, avg_price=#{avgPrice}, updated_at=#{updatedAt} " +
            "WHERE id = #{id}")
    int update(HedgeBatchItem item);

    @Select("SELECT COUNT(*) FROM hedge_batch_items WHERE status = #{status}")
    int countByStatus(String status);
}
