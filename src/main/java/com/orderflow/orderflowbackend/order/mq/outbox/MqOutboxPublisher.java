package com.orderflow.orderflowbackend.order.mq.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orderflow.orderflowbackend.order.mq.OrderTimeoutProducer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class MqOutboxPublisher {

    private static final int MAX_RETRY_COUNT = 5;

    private final MqOutboxMapper outboxMapper;

    private final OrderTimeoutProducer orderTimeoutProducer;


    public MqOutboxPublisher(
            MqOutboxMapper outboxMapper,
            OrderTimeoutProducer orderTimeoutProducer
    ) {
        this.outboxMapper = outboxMapper;
        this.orderTimeoutProducer =
                orderTimeoutProducer;
    }


    //每隔2s扫描数据库
    @Scheduled(fixedDelay = 2000)
    public void publishPendingMessage(){

        LocalDateTime now = LocalDateTime.now();

        List<MqOutbox> messages= outboxMapper.selectList(
                new LambdaQueryWrapper<MqOutbox>()
                        .eq(
                                MqOutbox::getStatus,
                                "PENDING"
                        )
                        .and(
                                wrapper -> wrapper
                                        .isNull(
                                                MqOutbox::getNextRetryAt
                                        )
                                        .or()
                                        .le(
                                                MqOutbox::getNextRetryAt,
                                                now
                                        )
                        )
                        .last("LIMIT 100")
        );

        for (MqOutbox message : messages){

            publishOne(message);
        }
    }

    private void publishOne(MqOutbox message){

        boolean success = orderTimeoutProducer.sendOrderTimeoutMessage(
                message.getOrderId(),
                message.getMessageId()
        );


        if (success){

            int rows =
                    outboxMapper.markSent(
                            message.getId()
                    );

            if (rows == 0) {
                System.out.println(
                        "Outbox状态更新失败，id="
                                + message.getId()
                );
            }
            return;
        }

        //发送失败
        //失败次数+1
        int nextRetryCount = message.getRetryCount()+1;

        //已经到达了最大重试次数
        if(nextRetryCount >= MAX_RETRY_COUNT){

            int rows = outboxMapper.markFailed(
                    message.getId(),
                    "RabbitMQ publish failed after"
                    +nextRetryCount
                    +"attempts"
            );

            System.out.println(
                    "Outbox最终失败，id="
                            + message.getId()
                            + ", retryCount="
                            + nextRetryCount
                            + ", affectedRows="
                            + rows
            );

            return;
        }

        //还可以继续重试
        LocalDateTime nextRetryAt =calculateNextRetryAt(nextRetryCount);

        int rows = outboxMapper.markRetry(
                message.getId(),
                nextRetryAt,
                "RabbitMQ publish failed"
        );

        System.out.println(
                "Outbox发送失败，等待重试，id="
                        + message.getId()
                        + ", retryCount="
                        + nextRetryCount
                        + ", nextRetryAt="
                        + nextRetryAt
                        + ", affectedRows="
                        + rows
        );
    }

    private LocalDateTime calculateNextRetryAt(int retryCount){

        LocalDateTime now = LocalDateTime.now();

        return switch (retryCount){

            case 1 ->
                    now.plusSeconds(1);

            case 2 ->
                    now.plusSeconds(3);

            case 3 ->
                    now.plusMinutes(1);

            case 4 ->
                    now.plusMinutes(5);

            default ->
                    now.plusMinutes(10);
        };
    }
}
