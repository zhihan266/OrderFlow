package com.orderflow.orderflowbackend.order.service;

import com.orderflow.orderflowbackend.exception.BusinessException;
import com.orderflow.orderflowbackend.order.dto.CreateOrderDTO;
import com.orderflow.orderflowbackend.order.vo.CreateOrderVO;
import org.springframework.stereotype.Service;

@Service
public class OrderSubmissionService {

    private final OrderCommandService orderCommandService;

    private final OrderIdempotencyService idempotencyService;

    public OrderSubmissionService(
            OrderCommandService orderCommandService,
            OrderIdempotencyService idempotencyService
            ){
        this.orderCommandService = orderCommandService;
        this.idempotencyService = idempotencyService;

    }

    public CreateOrderVO submit(
            CreateOrderDTO dto,
            String requestId
    ) {

        String requestHash =
                idempotencyService.buildRequestHash(dto);

        String redisKey =
                idempotencyService.buildKey(
                        dto.getUserId(),
                        requestId
                );


        boolean acquired =
                idempotencyService.tryAcquire(
                        redisKey,
                        requestHash
                );


        // =========================
        // 没抢到Redis执行权
        // =========================
        if (!acquired) {

            String value =
                    idempotencyService.getValue(redisKey);


            // PROCESSING
            if (idempotencyService.isProcessing(value)) {

                String oldRequestHash =
                        idempotencyService
                                .getProcessingRequestHash(value);


                if (!requestHash.equals(oldRequestHash)) {

                    throw new BusinessException(
                            40901,
                            "幂等键已被不同请求使用"
                    );
                }


                CreateOrderVO existingResult =
                        orderCommandService
                                .findCommittedIdempotentResult(
                                        dto.getUserId(),
                                        requestId,
                                        requestHash
                                );


                if (existingResult != null) {

                    try {

                        idempotencyService.markSuccess(
                                redisKey,
                                requestHash,
                                existingResult.getOrderId()
                        );

                    } catch (RuntimeException e) {

                        // 后续改成日志
                    }


                    return existingResult;
                }


                throw new BusinessException(
                        40902,
                        "订单请求正在处理中"
                );
            }


            // SUCCESS
            if (idempotencyService.isSuccess(value)) {

                String oldRequestHash =
                        idempotencyService
                                .getSuccessRequestHash(value);


                if (!requestHash.equals(oldRequestHash)) {

                    throw new BusinessException(
                            40901,
                            "幂等键已被不同请求使用"
                    );
                }


                Long orderId =
                        idempotencyService
                                .getSuccessOrderId(value);


                return orderCommandService
                        .getExistingOrderResult(orderId);
            }


            throw new BusinessException(
                    50035,
                    "Redis幂等状态异常"
            );
        }


        // =========================
        // 第一次请求
        // =========================
        CreateOrderVO result;

        try {
            result =
                    orderCommandService.createOrder(
                            dto,
                            requestId,
                            requestHash
                    );

        } catch (RuntimeException e) {

            idempotencyService.release(redisKey);

            throw e;
        }


        // =========================
        // DB已经成功，尝试同步Redis
        // =========================
        try {

            idempotencyService.markSuccess(
                    redisKey,
                    requestHash,
                    result.getOrderId()
            );

        } catch (RuntimeException e) {

            // 后续改成log.error
        }


        return result;
    }
}
