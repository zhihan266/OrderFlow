package com.orderflow.orderflowbackend.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderVO {

    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
}
