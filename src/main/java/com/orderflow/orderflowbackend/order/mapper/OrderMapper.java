package com.orderflow.orderflowbackend.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orderflow.orderflowbackend.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;


@Mapper
public interface OrderMapper
        extends BaseMapper<Order>{

    @Update("""
            UPDATE orders
            SET status = 'CANCELLED'
            WHERE id = #{orderId}
              AND status = 'PENDING_PAYMENT'
""")

    int cancelPendingOrder(
            @Param("orderId") Long orderId
    );

    @Update("""
            UPDATE orders
            SET status = 'PAID'
            WHERE id = #{orderId}
              AND status = 'PENDING_PAYMENT'
              AND expire_at > NOW()
            """)
    int markPaidIfPending(
            @Param("orderId") Long orderId
    );

    @Update("""
            UPDATE orders
            SET status = 'CANCELLED'
            WHERE id = #{orderId}
               AND status = 'PENDING_PAYMENT'
               AND expire_at <= NOW()
            """)
    int cancelExpiredOrder(
            @Param("orderId") Long orderId
    );
}
