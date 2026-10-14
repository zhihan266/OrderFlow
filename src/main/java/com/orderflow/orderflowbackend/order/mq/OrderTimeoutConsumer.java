package com.orderflow.orderflowbackend.order.mq;


import com.orderflow.orderflowbackend.order.service.OrderCommandService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

import java.io.IOException;


@Component
public class OrderTimeoutConsumer {

    private final OrderCommandService orderCommandService;

    public OrderTimeoutConsumer(
            OrderCommandService orderCommandService
    ){

        this.orderCommandService = orderCommandService;
    }

    @RabbitListener(
            queues = OrderMqConstants.ORDER_TIMEOUT_QUEUE
    )
    public void handleOrderTimeout(
            Long orderId,
            Message message,
            Channel channel
    ) throws IOException {

        //每一条rabbitmq都有自己的deliveryTag
        //ACK/NACK时用它告诉RabbitMQ：我确认的是哪一条消息

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            System.out.println(
                    "收到订单超时消息，orderId"
                            + orderId
            );

//            //强制报错测试，直接进入error队列
//            if (true) {
//                throw new RuntimeException(
//                        "模拟消费失败"
//                );
//            }

            boolean cancelled = orderCommandService.cancelOrderIfExpired(orderId);

            if(cancelled){

                System.out.println(
                        "订单已自动取消，orderId："
                                + orderId
                );
            }else {

                System.out.println(
                        "订单无需取消，orderId："
                                + orderId
                );
            }

            //业务处理成功

            channel.basicAck(
                    deliveryTag,
                    false
            );

            System.out.println(
                    "消息ACK成功，orderId："
                    + orderId
            );

        }catch (Exception e){

            System.out.println(
                    "订单超时消息处理失败，orderId："
                    +orderId
                    +",error"
                    +e.getMessage()
            );


            //业务处理失败
            //deliver ->哪一条消息 fales ->1,不批量NACK 2,不重新放回当前队列，
            //因为timeout queue配置了DLX，所以消息进入error queue

            channel.basicNack(
                    deliveryTag,
                    false,
                    false
            );
        }




    }
}
