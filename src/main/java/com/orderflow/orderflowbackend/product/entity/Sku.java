package com.orderflow.orderflowbackend.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sku")
public class Sku {

    @TableId(type=IdType.AUTO)
    private Long id;
    private Long productId;
    private String skuCode;
    private String spec;
    private BigDecimal price;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
