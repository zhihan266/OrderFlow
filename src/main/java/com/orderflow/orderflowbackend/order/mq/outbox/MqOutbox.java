package com.orderflow.orderflowbackend.order.mq.outbox;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mq_outbox")
public class MqOutbox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private Long orderId;

    private String eventType;

    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime updatedAt;
}
