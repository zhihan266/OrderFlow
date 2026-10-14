package com.orderflow.orderflowbackend.order.entity.idempotency;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderRequestMapper
         extends BaseMapper<OrderRequest> {

    @Insert("""
            INSERT IGNORE INTO order_request
                   (
                   user_id,
                   idempotency_key,
                   request_hash,
                   status
                   )
                   VALUES 
                   (
                   #{userId},
                   #{idempotencyKey},
                   #{requestHash},
                   'PROCESSING'
                   )
            """)
    int tryCreate(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotency,
            @Param("requestHash") String requestHash
    );

    @Update("""
           UPDATE order_request
           SET status = 'SUCCESS',
               order_id = #{orderId}
           WHERE user_id = #{userId}
             AND idempotency_key = #{idempotencyKey}
             AND status = 'PROCESSING'
           
           """)
    int markSuccess(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("orderId") Long orderId
    );
}
