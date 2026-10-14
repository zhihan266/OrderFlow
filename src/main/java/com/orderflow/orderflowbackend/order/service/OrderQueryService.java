package com.orderflow.orderflowbackend.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.orderflow.orderflowbackend.common.result.PageResult;
import com.orderflow.orderflowbackend.exception.BusinessException;
import com.orderflow.orderflowbackend.order.entity.Order;
import com.orderflow.orderflowbackend.order.entity.OrderItem;
import com.orderflow.orderflowbackend.order.mapper.OrderMapper;
import com.orderflow.orderflowbackend.order.mapper.OrderItemMapper;
import com.orderflow.orderflowbackend.order.vo.OrderDetailVO;
import com.orderflow.orderflowbackend.order.vo.OrderSummaryVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQueryService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    public OrderQueryService(
            OrderMapper orderMapper,
            OrderItemMapper orderItemMapper
    ){
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
    }

    public OrderDetailVO getOrderDetail(Long orderId){
        //查询订单主表
        Order order = orderMapper.selectById(orderId);

        if (order == null){

            throw new BusinessException(
                    40410,
                    "订单不存在"
            );
        }

        //查询订单明细
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(
                                OrderItem::getOrderId,
                                orderId
                        )
                        .orderByAsc(
                                OrderItem::getId
                        )
        );

        //组装VO
        OrderDetailVO vo =
                new OrderDetailVO();

        vo.setOrderId(
                order.getId()
        );

        vo.setOrderNo(
                order.getOrderNo()
        );

        vo.setUserId(
                order.getUserId()
        );

        vo.setTotalAmount(
                order.getTotalAmount()
        );

        vo.setStatus(
                order.getStatus()
        );

        vo.setCreatedAt(
                order.getCreatedAt()
        );
        //  组装订单明细
        List<OrderDetailVO.OrderItemVO> itemVos =
                items.stream()
                        .map(item -> {

                            OrderDetailVO.OrderItemVO itemVo =
                                    new OrderDetailVO.OrderItemVO();

                            itemVo.setSkuId(
                                    item.getSkuId()
                            );

                            itemVo.setSkuCode(
                                    item.getSkuCode()
                            );

                            itemVo.setSkuSpec(
                                    item.getSkuSpec()
                            );

                            itemVo.setUnitPrice(
                                    item.getUnitPrice()
                            );

                            itemVo.setQuantity(
                                    item.getQuantity()
                            );

                            itemVo.setLineAmount(
                                    item.getLineAmount()
                            );

                            return itemVo;

                        })
                        .toList();


        vo.setItems(itemVos);

        return vo;



    }

    public PageResult<OrderSummaryVO> listUserOrders(
            Long userId,
            long page,
            long pageSize
    ){

        if (page < 1){

            throw new BusinessException(
                    40020,
                    "page必须大于一"
            );
        }
        if(pageSize < 1|| pageSize >100){

            throw new BusinessException(
                    40021,
                    "pageSize必须在1到100之间"
            );
        }

        Page<Order> pageRequest = new Page<>(
                page,
                pageSize
        );

        Page<Order> orderPage = orderMapper.selectPage(
                pageRequest,
                new LambdaQueryWrapper<Order>()
                        .eq(
                                Order::getUserId,
                                userId
                        )
                        .orderByDesc(
                                Order::getCreatedAt
                        )
        );

        List<OrderSummaryVO> records =
                orderPage.getRecords()
                        .stream().map(order -> {

                            OrderSummaryVO vo =
                                    new OrderSummaryVO();

                            vo.setOrderId(
                                    order.getId()
                            );

                            vo.setOrderNo(
                                    order.getOrderNo()
                            );

                            vo.setUserId(
                                    order.getUserId()
                            );

                            vo.setTotalAmount(
                                    order.getTotalAmount()
                            );

                            vo.setStatus(
                                    order.getStatus()
                            );

                            vo.setCreatedAt(
                                    order.getCreatedAt()
                            );
                            return vo;
                        })
                        .toList();

        return new PageResult<>(
                records,
                orderPage.getTotal(),
                orderPage.getCurrent(),
                orderPage.getSize(),
                orderPage.getPages()
        );
    }

}
