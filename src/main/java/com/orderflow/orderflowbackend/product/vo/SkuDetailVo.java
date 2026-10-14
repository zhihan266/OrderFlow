package com.orderflow.orderflowbackend.product.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuDetailVo {
    private Long skuId;
    private String skuCode;
    private String spec;
    private BigDecimal price;
    private Integer availableStock;
    private Integer reservedStock;
}
