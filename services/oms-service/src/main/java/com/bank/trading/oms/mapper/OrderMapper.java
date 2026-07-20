package com.bank.trading.oms.mapper;

import com.bank.trading.oms.entity.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO orders(id, order_id, client_order_id, customer_id, symbol, side, type, qty, filled_qty, " +
            "price, avg_price, status, reject_reason, trace_id, created_at, updated_at) " +
            "VALUES(#{id}, #{orderId}, #{clientOrderId}, #{customerId}, #{symbol}, #{side}, #{type}, #{qty}, #{filledQty}, " +
            "#{price}, #{avgPrice}, #{status}, #{rejectReason}, #{traceId}, #{createdAt}, #{updatedAt})")
    int insert(Order order);

    @Update("UPDATE orders SET filled_qty=#{filledQty}, avg_price=#{avgPrice}, status=#{status}, " +
            "reject_reason=#{rejectReason}, updated_at=#{updatedAt} WHERE order_id=#{orderId}")
    int updateByOrderId(Order order);

    @Select("SELECT * FROM orders WHERE order_id = #{orderId}")
    Order findByOrderId(String orderId);

    @Select("SELECT * FROM orders WHERE client_order_id = #{clientOrderId} AND customer_id = #{customerId}")
    Order findByClientOrderId(@Param("clientOrderId") String clientOrderId, @Param("customerId") String customerId);

    @Select("SELECT * FROM orders WHERE customer_id = #{customerId} ORDER BY created_at DESC")
    List<Order> findByCustomerId(String customerId);

    @Select("SELECT * FROM orders ORDER BY created_at DESC LIMIT #{limit}")
    List<Order> findRecent(int limit);

    @Select("SELECT COUNT(*) FROM orders WHERE status = #{status}")
    int countByStatus(String status);
}
