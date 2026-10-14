package com.orderflow.orderflowbackend.product.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProductDTO {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100)
    private String name ;

    @Size(max = 500)
    private String description;

    @NotNull
    private Integer status;
}
