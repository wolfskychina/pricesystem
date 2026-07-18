package com.bank.trading.execution.mapper;

import com.bank.trading.execution.entity.HedgeTrade;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 对冲成交流水 Mapper，提供对冲成交记录的持久化与查询能力。
 */
@Mapper
public interface HedgeTradeMapper {

    /**
     * 插入对冲成交流水。
     *
     * @param trade 对冲成交实体
     * @return 影响行数
     */
    @Insert("INSERT INTO hedge_trades(hedge_order_id, exchange_order_id, exchange_trade_id, original_trade_id, " +
            "symbol, side, qty, price, amount, trade_time, created_at) " +
            "VALUES(#{hedgeOrderId}, #{exchangeOrderId}, #{exchangeTradeId}, #{originalTradeId}, " +
            "#{symbol}, #{side}, #{qty}, #{price}, #{amount}, #{tradeTime}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(HedgeTrade trade);

    /**
     * 根据对冲订单内部 ID 查询全部成交流水。
     *
     * @param hedgeOrderId 对冲订单内部 ID
     * @return 成交流水列表
     */
    @Select("SELECT * FROM hedge_trades WHERE hedge_order_id = #{hedgeOrderId} ORDER BY trade_time")
    List<HedgeTrade> findByHedgeOrderId(String hedgeOrderId);

    /**
     * 根据交易所成交 ID 查询（用于幂等去重，避免重复处理同一成交通知）。
     *
     * @param exchangeTradeId 交易所成交 ID
     * @return 成交流水；不存在返回 null
     */
    @Select("SELECT * FROM hedge_trades WHERE exchange_trade_id = #{exchangeTradeId}")
    HedgeTrade findByExchangeTradeId(String exchangeTradeId);

    /**
     * 查询最近的成交流水。
     *
     * @param limit 返回条数上限
     * @return 成交流水列表
     */
    @Select("SELECT * FROM hedge_trades ORDER BY trade_time DESC LIMIT #{limit}")
    List<HedgeTrade> findRecent(int limit);
}
