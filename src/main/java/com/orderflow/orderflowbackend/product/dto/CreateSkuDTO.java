package com.orderflow.orderflowbackend.product.dto;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateSkuDTO {

    @NotBlank
    private String skuCode;

    @NotBlank
    private String spec;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer initialStock;

    @NotNull
    private Integer status;
}
