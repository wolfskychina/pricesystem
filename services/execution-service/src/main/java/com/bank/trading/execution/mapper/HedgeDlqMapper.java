package com.bank.trading.execution.mapper;

import com.bank.trading.execution.entity.HedgeDlq;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HedgeDlqMapper {

    @Insert("INSERT INTO hedge_dlq (id, hedge_order_id, original_trade_id, customer_id, " +
            "symbol, side, qty, reason, retry_count, max_retry_count, status, created_at, recovered_at) " +
            "VALUES (#{id}, #{hedgeOrderId}, #{originalTradeId}, #{customerId}, " +
            "#{symbol}, #{side}, #{qty}, #{reason}, #{retryCount}, #{maxRetryCount}, #{status}, #{createdAt}, #{recoveredAt})")
    void insert(HedgeDlq dlq);

    @Select("SELECT * FROM hedge_dlq WHERE status = #{status} ORDER BY created_at ASC")
    List<HedgeDlq> findByStatus(String status);

    @Select("SELECT * FROM hedge_dlq WHERE hedge_order_id = #{hedgeOrderId}")
    HedgeDlq findByHedgeOrderId(String hedgeOrderId);

    @Update("UPDATE hedge_dlq SET status = #{status}, retry_count = #{retryCount}, " +
            "recovered_at = #{recoveredAt} WHERE id = #{id}")
    void update(HedgeDlq dlq);

    @Select("SELECT COUNT(*) FROM hedge_dlq WHERE status = 'PENDING'")
    int countPending();
}