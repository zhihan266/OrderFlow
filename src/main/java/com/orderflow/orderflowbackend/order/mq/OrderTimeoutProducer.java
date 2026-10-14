package com.orderflow.orderflowbackend.order.mq;


import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class OrderTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderTimeoutProducer(
            RabbitTemplate rabbitTemplate
    ){
        this.rabbitTemplate = rabbitTemplate;
    }

    public boolean sendOrderTimeoutMessage(
            Long orderId,
            String messageId
    ){

        //messageId用来将生产者发送的消息和MQ返回的confirm对应起来

        CorrelationData correlationData = new CorrelationData(messageId);

        try{
            rabbitTemplate.convertAndSend(
                    OrderMqConstants.ORDER_DELAY_EXCHANGE,
                    OrderMqConstants.ORDER_DELAY_ROUTING_KEY,
                    orderId,
                    correlationData
            );
            //最多等待5s，等待MQ confirm

            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(
                            5,
                            TimeUnit.SECONDS
                    );

            //Broker 没有确认成功

            if(!confirm.ack()){

                System.out.println(
                        "RabbitMQ NACK, messageId="
                        + messageId
                        + ", reason="
                        + confirm.reason()
                );

                return false ;
            }

            /*
            Broker虽然ACK
            但是消息没有路由到任何Queue
             */

            ReturnedMessage returned = correlationData.getReturned();

            if (returned != null) {

                System.out.println(
                        "RabbitMQ消息无法路由，messageId="
                                + messageId
                                + ", reason="
                                + returned.getReplyText()
                );

                return false;
            }
            System.out.println(
                    "RabbitMQ发送成功，messageId="
                            + messageId
            );

            return true;

        }catch (Exception e){

            System.out.println(
                    "RabbitMQ发送异常，messageId="
                            + messageId
                            + ", error="
                            + e.getMessage()
            );

            return false;
        }

    }
}
