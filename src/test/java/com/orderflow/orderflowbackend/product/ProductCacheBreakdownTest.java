package com.orderflow.orderflowbackend.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
public class ProductCacheBreakdownTest {

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate redisTemplate;


    private static final Long PRODUCT_ID = 1L;

    private static final int THREAD_COUNT = 50;

    private static final String CACHE_KEY =
            "product:detail:" + PRODUCT_ID;

    private static final String LOCK_KEY =
            "product:detail:lock:" + PRODUCT_ID;


    @Test
    void shouldPreventCacheBreakdown()
            throws Exception {

        // ========================================
        // 1. 测试前先删除商品缓存和锁
        // ========================================

        redisTemplate.delete(CACHE_KEY);
        redisTemplate.delete(LOCK_KEY);


        System.out.println(
                "===== Redis cache cleared ====="
        );

        System.out.println(
                "cacheKey = " + CACHE_KEY
        );


        // ========================================
        // 2. 创建真正的 HTTP Client
        // ========================================

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(5)
                        )
                        .build();


        String url =
                "http://localhost:"
                        + port
                        + "/api/products/"
                        + PRODUCT_ID;


        // ========================================
        // 3. 创建50个线程
        // ========================================

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );


        /*
         * readyLatch：
         *
         * 等待50个线程全部准备好
         */
        CountDownLatch readyLatch =
                new CountDownLatch(
                        THREAD_COUNT
                );


        /*
         * startLatch：
         *
         * 主线程统一发令，
         * 让50个线程尽可能同时请求
         */
        CountDownLatch startLatch =
                new CountDownLatch(1);


        /*
         * doneLatch：
         *
         * 等待50个请求全部结束
         */
        CountDownLatch doneLatch =
                new CountDownLatch(
                        THREAD_COUNT
                );


        AtomicInteger successCount =
                new AtomicInteger();

        AtomicInteger failureCount =
                new AtomicInteger();


        List<String> failureMessages =
                new CopyOnWriteArrayList<>();


        // ========================================
        // 4. 提交50个并发任务
        // ========================================

        for (int i = 0;
             i < THREAD_COUNT;
             i++) {

            executor.submit(() -> {

                try {

                    // 当前线程已经准备好
                    readyLatch.countDown();


                    // 等待统一开始
                    startLatch.await();


                    HttpRequest request =
                            HttpRequest
                                    .newBuilder()
                                    .uri(
                                            URI.create(url)
                                    )
                                    .GET()
                                    .timeout(
                                            Duration.ofSeconds(5)
                                    )
                                    .build();


                    HttpResponse<String> response =
                            client.send(
                                    request,
                                    HttpResponse
                                            .BodyHandlers
                                            .ofString()
                            );


                    String body =
                            response.body();


                    /*
                     * 你的 Result<T>：
                     *
                     * {
                     *   "code": 200,
                     *   "message": "success",
                     *   "data": ...
                     * }
                     *
                     * 所以这里不能只看 HTTP status。
                     */
                    if (response.statusCode() == 200
                            &&
                            body.contains(
                                    "\"code\":200"
                            )) {

                        successCount.incrementAndGet();

                    } else {

                        failureCount.incrementAndGet();

                        failureMessages.add(
                                body
                        );
                    }


                } catch (Exception e) {

                    failureCount.incrementAndGet();

                    failureMessages.add(
                            e.getClass()
                                    .getSimpleName()
                                    +
                                    ": "
                                    +
                                    e.getMessage()
                    );

                } finally {

                    doneLatch.countDown();
                }
            });
        }


        // ========================================
        // 5. 等所有线程都准备好
        // ========================================

        readyLatch.await();


        long startTime =
                System.currentTimeMillis();


        System.out.println(
                "===== 50 requests start ====="
        );


        // 一声令下
        startLatch.countDown();


        // ========================================
        // 6. 等待所有请求完成
        // ========================================

        boolean completed =
                doneLatch.await(
                        15,
                        TimeUnit.SECONDS
                );


        long elapsed =
                System.currentTimeMillis()
                        - startTime;


        executor.shutdown();


        // ========================================
        // 7. 查看Redis缓存结果
        // ========================================

        String cached =
                redisTemplate
                        .opsForValue()
                        .get(CACHE_KEY);


        Long ttl =
                redisTemplate
                        .getExpire(
                                CACHE_KEY,
                                TimeUnit.SECONDS
                        );


        // ========================================
        // 8. 输出测试结果
        // ========================================

        System.out.println();
        System.out.println(
                "========== Cache Breakdown Test =========="
        );

        System.out.println(
                "total requests = "
                        + THREAD_COUNT
        );

        System.out.println(
                "success = "
                        + successCount.get()
        );

        System.out.println(
                "failure = "
                        + failureCount.get()
        );

        System.out.println(
                "elapsed = "
                        + elapsed
                        + " ms"
        );

        System.out.println(
                "cache exists = "
                        + (cached != null)
        );

        System.out.println(
                "cache ttl = "
                        + ttl
                        + " seconds"
        );


        if (!failureMessages.isEmpty()) {

            System.out.println();
            System.out.println(
                    "===== Failures ====="
            );

            failureMessages.forEach(
                    System.out::println
            );
        }


        // ========================================
        // 9. 断言
        // ========================================

        assertEquals(
                true,
                completed,
                "并发请求没有在规定时间内全部完成"
        );


        assertEquals(
                THREAD_COUNT,
                successCount.get(),
                "应该50个请求全部成功"
        );


        assertEquals(
                0,
                failureCount.get(),
                "不应该有失败请求"
        );


        assertNotNull(
                cached,
                "并发请求结束以后，Redis缓存应该已经重建"
        );
    }
}