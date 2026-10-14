package com.orderflow.orderflowbackend.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long skuId;
    private String skuCode;
    private String skuSpec;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;
    private LocalDateTime createdAt;
}
