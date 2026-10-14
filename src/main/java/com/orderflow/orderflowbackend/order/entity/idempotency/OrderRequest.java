package com.orderflow.orderflowbackend.order.entity.idempotency;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_request")
public class OrderRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String idempotencyKey;

    private String requestHash;

    private String status;

    private Long orderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
