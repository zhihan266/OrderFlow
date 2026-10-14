package com.orderflow.orderflowbackend.product.cache;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuCacheData {

    private Long skuId;
    private String skuCode;
    private String spec;
    private BigDecimal price;
}
