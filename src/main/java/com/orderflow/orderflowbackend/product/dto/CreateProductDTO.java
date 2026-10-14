package com.orderflow.orderflowbackend.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jdk.jfr.Description;
import lombok.Data;

@Data
public class CreateProductDTO {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100,message = "商品名称不能超过100个字符")
    private String name ;

    @Size(max = 500,message = "商品描述不能超过500个字符")
    private String description;
}
