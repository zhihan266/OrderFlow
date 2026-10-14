package com.orderflow.orderflowbackend.order.vo;


import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderSummaryVO {

    private Long orderId;

    private String orderNo;

    private Long userId;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime createdAt;
}
