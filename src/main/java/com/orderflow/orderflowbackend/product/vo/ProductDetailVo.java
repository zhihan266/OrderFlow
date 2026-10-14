package com.orderflow.orderflowbackend.product.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProductDetailVo {
    private Long id;
    private String name;
    private String description;
    private Integer status;
    private List<SkuDetailVo> skus;
}
