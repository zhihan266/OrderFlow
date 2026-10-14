package com.orderflow.orderflowbackend.product.cache;


import lombok.Data;

import java.util.List;

@Data
public class ProductDetailCacheData {
    private Long id;
    private String name;
    private String description;
    private Integer status;
    private List<SkuCacheData> skus;
}
