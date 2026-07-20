package com.bank.trading.execution.mapper;

import com.bank.trading.execution.entity.HedgeFailureExposure;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HedgeFailureExposureMapper {

    @Insert("INSERT INTO hedge_failure_exposure (id, customer_id, symbol, side, pending_qty, " +
            "exposure_amount, status, retry_count, last_retry_at, original_trade_id, " +
            "hedge_order_id, created_at, resolved_at) " +
            "VALUES (#{id}, #{customerId}, #{symbol}, #{side}, #{pendingQty}, " +
            "#{exposureAmount}, #{status}, #{retryCount}, #{lastRetryAt}, #{originalTradeId}, " +
            "#{hedgeOrderId}, #{createdAt}, #{resolvedAt})")
    void insert(HedgeFailureExposure exposure);

    @Select("SELECT * FROM hedge_failure_exposure WHERE status = #{status} ORDER BY created_at DESC")
    List<HedgeFailureExposure> findByStatus(String status);

    @Select("SELECT * FROM hedge_failure_exposure WHERE symbol = #{symbol} AND status = #{status} ORDER BY created_at DESC")
    List<HedgeFailureExposure> findBySymbolAndStatus(String symbol, String status);

    @Select("SELECT * FROM hedge_failure_exposure WHERE original_trade_id = #{originalTradeId}")
    HedgeFailureExposure findByOriginalTradeId(String originalTradeId);

    @Update("UPDATE hedge_failure_exposure SET status = #{status}, resolved_at = #{resolvedAt} " +
            "WHERE original_trade_id = #{originalTradeId}")
    void updateStatusByTradeId(String originalTradeId, String status, Long resolvedAt);

    @Update("UPDATE hedge_failure_exposure SET status = #{status}, retry_count = #{retryCount}, " +
            "last_retry_at = #{lastRetryAt} WHERE id = #{id}")
    void update(HedgeFailureExposure exposure);

    @Select("SELECT SUM(pending_qty) FROM hedge_failure_exposure WHERE status = 'PENDING'")
    java.math.BigDecimal sumPendingQty();

    @Select("SELECT SUM(exposure_amount) FROM hedge_failure_exposure WHERE status = 'PENDING'")
    java.math.BigDecimal sumExposureAmount();

    @Select("SELECT symbol, SUM(pending_qty) as pending_qty, SUM(exposure_amount) as exposure_amount " +
            "FROM hedge_failure_exposure WHERE status = 'PENDING' GROUP BY symbol")
    List<HedgeFailureExposureSummary> getSummaryBySymbol();

    interface HedgeFailureExposureSummary {
        String getSymbol();
        java.math.BigDecimal getPendingQty();
        java.math.BigDecimal getExposureAmount();
    }
}