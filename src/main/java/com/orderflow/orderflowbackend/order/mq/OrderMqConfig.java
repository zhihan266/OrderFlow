package com.orderflow.orderflowbackend.order.mq;


import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderMqConfig {

    @Value("${order.timeout-seconds:1800}")
    private long orderTimeoutSeconds;

    //延迟交换机
    @Bean
    public DirectExchange orderDelayExchange(){
        return new DirectExchange(
                OrderMqConstants.ORDER_DELAY_EXCHANGE
        );
    }

    //真正处理超时订单的交换机
    @Bean
    public DirectExchange orderTimeoutExchange(){
        return new DirectExchange(
                OrderMqConstants.ORDER_TIMEOUT_EXCHANGE
        );
    }

    //延迟队列
    @Bean
    public Queue orderDelayQueue(){
        return QueueBuilder.durable(OrderMqConstants.ORDER_DELAY_QUEUE)
        //消息队列到这里停留orderTimeouSeconds
                .withArgument(
                        "x-message-ttl",
                        orderTimeoutSeconds * 1000
                )
        //到期后发给timeout的Exchange
                .withArgument(
                        "x-dead-letter-exchange",
                        OrderMqConstants.ORDER_TIMEOUT_EXCHANGE
                )
        //到期后使用timeout的routing key
                .withArgument(
                        "x-dead-letter-routing-key",
                        OrderMqConstants.ORDER_TIMEOUT_ROUTING_KEY
                )
                .build();
    }

    //延迟队列绑定
    @Bean
    public Binding orderDelayBinding(
            Queue orderDelayQueue,
            DirectExchange orderDelayExchange
    ){
        return BindingBuilder
                .bind(orderDelayQueue)
                .to(orderDelayExchange)
                .with(
                        OrderMqConstants.ORDER_DELAY_ROUTING_KEY
                );
    }

    //真正的消费队列
    @Bean
    public Queue orderTimeoutQueue(){

        return QueueBuilder
                .durable(OrderMqConstants.ORDER_TIMEOUT_QUEUE)

                //timeout消息队列消费失败并且NACK时，发送到失败交换机
                .deadLetterExchange(
                        OrderMqConstants.ORDER_TIMEOUT_ERROR_EXCHANGE
                )
                .deadLetterRoutingKey(
                        OrderMqConstants.ORDER_TIMEOUT_ERROR_ROUTING_KEY
                ).build();

    }

    //消息队列绑定
    @Bean
    public Binding orderTimeoutBinding(
            Queue orderTimeoutQueue,
            DirectExchange orderTimeoutExchange
    ){

        return BindingBuilder
                .bind(orderTimeoutQueue)
                .to(orderTimeoutExchange)
                .with(OrderMqConstants.ORDER_TIMEOUT_ROUTING_KEY);
    }

    //失败队列的配置
    @Bean
    public DirectExchange orderTimeoutErrorExchange(){

        return new DirectExchange(

                OrderMqConstants.ORDER_TIMEOUT_ERROR_EXCHANGE
        );
    }

    @Bean
    public Queue orderTimeoutErrorQueue(){

        return QueueBuilder.durable(
                OrderMqConstants.ORDER_TIMEOUT_ERROR_QUEUE
        ).build();
    }

    @Bean
    public Binding orderTimeoutErrorBinding(
            Queue orderTimeoutErrorQueue,
            DirectExchange orderTimeoutErrorExchange
    ){

        return BindingBuilder
                .bind(orderTimeoutErrorQueue)
                .to(orderTimeoutErrorExchange)
                .with(OrderMqConstants.ORDER_TIMEOUT_ERROR_ROUTING_KEY);
    }
}
