package com.orderflow.orderflowbackend.order.controller;

import com.orderflow.orderflowbackend.common.result.PageResult;
import com.orderflow.orderflowbackend.common.result.Result;
import com.orderflow.orderflowbackend.order.dto.CreateOrderDTO;
import com.orderflow.orderflowbackend.order.vo.CreateOrderVO;
import com.orderflow.orderflowbackend.order.service.OrderCommandService;
import com.orderflow.orderflowbackend.order.service.OrderQueryService;
import com.orderflow.orderflowbackend.order.vo.OrderDetailVO;
import com.orderflow.orderflowbackend.order.vo.OrderSummaryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.orderflow.orderflowbackend.order.service.OrderSubmissionService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;
    private final OrderSubmissionService orderSubmissionService;

    public OrderController(OrderCommandService orderCommandService,
                           OrderQueryService orderQueryService,
                           OrderSubmissionService orderSubmissionService){
        this.orderSubmissionService = orderSubmissionService;
        this.orderCommandService = orderCommandService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public Result<CreateOrderVO> createOrder (
            @RequestHeader("Idempotency-key")
            String requestId,
            @Valid
            @RequestBody CreateOrderDTO dto
    ){
        return Result.success(
              orderSubmissionService.submit(
                      dto,
                      requestId
              )
        );
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancelOrder(
            @PathVariable Long orderId
            ){

        orderCommandService.cancelOrderId(orderId);

        return Result.success();
    }

    @PostMapping("/{orderId}/pay")
    public Result<Void> payOrder(
            @PathVariable Long orderId
    ){
        orderCommandService.payOrder(orderId);

        return Result.success();
    }

    @GetMapping("/{orderId}")
    public Result<OrderDetailVO> getOrderDetail(
            @PathVariable Long orderId
    ){

        OrderDetailVO result = orderQueryService.getOrderDetail(orderId);

        return Result.success(result);
    }
    @GetMapping
    public Result<PageResult<OrderSummaryVO>> listOrders(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize
    ){

        return Result.success(
                orderQueryService.listUserOrders(
                        userId,
                        page,
                        pageSize
                )
        );
    }
}
