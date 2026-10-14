package com.orderflow.orderflowbackend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
public class CreateOrderDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotEmpty(message = "订单商品不能为空")
    @Size(
            max = 50,
            message = "单个订单最多包含50种商品"
    )
    private List<@Valid CreateOrderItemDTO> items;
}
