package com.orderflow.orderflowbackend.order.vo;


import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailVO {

    private Long orderId;

    private String orderNo;

    private Long userId;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime expireAt;

    private List<OrderItemVO> items;


    @Data
    public static class OrderItemVO{

        private Long skuId;

        private String skuCode;

        private String skuSpec;

        private BigDecimal unitPrice;

        private Integer quantity;

        private BigDecimal lineAmount;
    }

}
