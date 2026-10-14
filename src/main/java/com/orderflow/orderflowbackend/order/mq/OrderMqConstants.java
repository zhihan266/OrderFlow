package com.orderflow.orderflowbackend.order.mq;

public class OrderMqConstants {

    //创建订单后，消息先发在这里
    public static final String ORDER_DELAY_EXCHANGE =
            "order.delay.exchange";

    public static final String ORDER_DELAY_QUEUE =
            "order.delay.queue";

    public static final String ORDER_DELAY_ROUTING_KEY =
            "order.delay";

    //TTL到期后，消息转到这里
    public static final String ORDER_TIMEOUT_EXCHANGE =
            "order.timeout.exchange";

    public static final String ORDER_TIMEOUT_QUEUE =
            "order.timeout.queuq";

    public static final String ORDER_TIMEOUT_ROUTING_KEY =
            "order.timeout";

    //timeout执行失败后转入失败队列
    public static final String ORDER_TIMEOUT_ERROR_EXCHANGE =
            "order.timeout.error.exchange";

    public static final String ORDER_TIMEOUT_ERROR_QUEUE =
            "order.timeout.error.queue";

    public static final String ORDER_TIMEOUT_ERROR_ROUTING_KEY =
            "order.timeout.error";
}
