package com.bank.trading.oms.mapper;

import com.bank.trading.oms.entity.Trade;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TradeMapper {

    @Insert("INSERT INTO trades(trade_id, order_id, client_order_id, customer_id, symbol, side, qty, price, " +
            "amount, trade_type, trade_time) " +
            "VALUES(#{tradeId}, #{orderId}, #{clientOrderId}, #{customerId}, #{symbol}, #{side}, #{qty}, #{price}, " +
            "#{amount}, #{tradeType}, #{tradeTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Trade trade);

    @Select("SELECT * FROM trades WHERE order_id = #{orderId} ORDER BY trade_time")
    List<Trade> findByOrderId(String orderId);

    @Select("SELECT * FROM trades WHERE customer_id = #{customerId} ORDER BY trade_time DESC")
    List<Trade> findByCustomerId(String customerId);

    @Select("SELECT * FROM trades ORDER BY trade_time DESC LIMIT #{limit}")
    List<Trade> findRecent(int limit);
}
