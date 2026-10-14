package com.orderflow.orderflowbackend.order.task;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orderflow.orderflowbackend.order.entity.Order;
import com.orderflow.orderflowbackend.order.enums.OrderStatus;
import com.orderflow.orderflowbackend.order.mapper.OrderMapper;
import com.orderflow.orderflowbackend.order.service.OrderCommandService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ExpiredOrderScanner {

    private final OrderMapper orderMapper;

    private final OrderCommandService
            orderCommandService;


    public ExpiredOrderScanner(
            OrderMapper orderMapper,
            OrderCommandService orderCommandService
    ) {
        this.orderMapper = orderMapper;
        this.orderCommandService =
                orderCommandService;
    }

    @Scheduled(fixedDelay = 30000)
    public void scanExpiredOrders(){

        List<Order> expiredOrders =
            orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .eq(
                                    Order::getStatus,
                                    OrderStatus.PENDING_PAYMENT.name()
                            )
                            .le(
                                    Order::getExpireAt,
                                    LocalDateTime.now()
                            )
                            .orderByAsc(
                                    Order::getExpireAt
                            )
                            .last("LIMIT 100")
            );

        for (Order order :expiredOrders){

            try{

                boolean cancelled = orderCommandService.cancelOrderIfExpired(
                        order.getId()
                );

                if (cancelled){

                    System.out.println(
                            "数据库兜底关闭，orderId"
                            +order.getId()
                    );
                }
            }catch (Exception e){

                /*
                 * 一笔订单失败，
                 * 不应该阻止后面的99笔继续处理。
                 */
                System.out.println(
                        "兜底关单失败，orderId="
                                + order.getId()
                                + ", error="
                                + e.getMessage()
                );
            }
        }
    }
}
