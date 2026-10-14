package com.orderflow.orderflowbackend.order.service;

import com.orderflow.orderflowbackend.order.dto.CreateOrderDTO;
import com.orderflow.orderflowbackend.order.dto.CreateOrderItemDTO;
import com.orderflow.orderflowbackend.product.dto.CreateSkuDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Service
public class OrderIdempotencyService {

    private static final String PROCESSING_PREFIX = "PROCESSING:";
    private static final String SUCCESS_PREFIX = "SUCCESS:";

    private final StringRedisTemplate redisTemplate;


    public OrderIdempotencyService(
            StringRedisTemplate redisTemplate

    ){
        this.redisTemplate = redisTemplate;

    }

    public String buildKey(
            Long userId,
            String requestId
    ){

        return "order:idempotency:"
                +userId +":" + requestId;
    }

    public boolean tryAcquire(
            String key,
            String requestHash
            ){

        String value =
                PROCESSING_PREFIX + requestHash;

        boolean success = redisTemplate.opsForValue().setIfAbsent(
                key,
                value,
                Duration.ofMinutes(10)
        );

        return Boolean.TRUE.equals(success);
    }

    public void markSuccess(
            String key,
            String requestHash,
            Long orderId
    ){

        String value =
                SUCCESS_PREFIX
                        + requestHash
                        + ":"
                        + orderId;

        redisTemplate.opsForValue().set(
                key,
                value,
                Duration.ofHours(24)
        );
    }

    public void release(String key){

        redisTemplate.delete(key);
    }


    public String getValue(String key) {

        return redisTemplate
                .opsForValue()
                .get(key);
    }

    public boolean isProcessing(String value) {

        return value != null
                && value.startsWith(PROCESSING_PREFIX);
    }

    public boolean isSuccess(String value) {

        return value != null
                && value.startsWith(SUCCESS_PREFIX);
    }

    public String getProcessingRequestHash(
            String value
    ) {

        return value.substring(
                PROCESSING_PREFIX.length()
        );
    }

    /**
     * 从 SUCCESS:<hash>:<orderId>
     * 中提取 requestHash。
     */
    public String getSuccessRequestHash(
            String value
    ) {

        String content =
                value.substring(
                        SUCCESS_PREFIX.length()
                );

        int separatorIndex =
                content.lastIndexOf(":");

        if (separatorIndex <= 0) {
            throw new IllegalStateException(
                    "Redis幂等SUCCESS状态格式错误"
            );
        }

        return content.substring(
                0,
                separatorIndex
        );
    }


    /**
     * 从 SUCCESS:<hash>:<orderId>
     * 中提取 orderId。
     */
    public Long getSuccessOrderId(
            String value
    ) {

        String content =
                value.substring(
                        SUCCESS_PREFIX.length()
                );

        int separatorIndex =
                content.lastIndexOf(":");

        if (separatorIndex <= 0
                || separatorIndex
                == content.length() - 1) {

            throw new IllegalStateException(
                    "Redis幂等SUCCESS状态格式错误"
            );
        }

        return Long.valueOf(
                content.substring(
                        separatorIndex + 1
                )
        );
    }





    public String buildRequestHash(
            CreateOrderDTO dto
    ){
        /*
         * 先把items按照skuId排序，
         * 保证不同排列顺序代表同一个业务请求。
         */
        String itemsText =
                dto.getItems()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        CreateOrderItemDTO::getSkuId
                                )
                        )
                        .map(
                                item ->
                                        item.getSkuId()
                                                + ":"
                                                + item.getQuantity()
                        )
                        .collect(
                                Collectors.joining(",")
                        );


        String raw =
                dto.getUserId()
                        + "|"
                        + itemsText;


        return sha256(raw);
    }


    private String sha256(
            String raw
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            raw.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );


            return HexFormat
                    .of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256算法不可用",
                    e
            );
        }
    }





}
