package com.orderflow.orderflowbackend.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment =
                SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
public class OrderConcurrencyTest {

    private static final Long SKU_ID = 1L;

    private static final int INITIAL_STOCK = 10;

    private static final int THREAD_COUNT = 50;

    private static final Pattern SUCCESS_PATTERN =
            Pattern.compile(
                    "\"code\"\\s*:\\s*200"
            );

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @BeforeEach
    void prepareData() {

        // 清理上一轮测试订单
        jdbcTemplate.update(
                "DELETE FROM order_item"
        );

        jdbcTemplate.update(
                "DELETE FROM orders"
        );

        // 重置库存
        jdbcTemplate.update("""
                UPDATE inventory
                SET available_stock = ?,
                    reserved_stock = 0
                WHERE sku_id = ?
                """,
                INITIAL_STOCK,
                SKU_ID
        );
    }


    @Test
    void concurrentCreateOrderShouldNotOversell()
            throws Exception {

        ExecutorService executorService =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(5)
                        )
                        .build();


        /*
         * readyLatch：
         *
         * 确保50个线程都准备好了
         */
        CountDownLatch readyLatch =
                new CountDownLatch(
                        THREAD_COUNT
                );


        /*
         * startLatch：
         *
         * 相当于发令枪。
         *
         * 所有线程先卡在这里，
         * main线程一声令下，
         * 50个一起冲。
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


        List<String> responses =
                new CopyOnWriteArrayList<>();


        for (int i = 0;
             i < THREAD_COUNT;
             i++) {

            executorService.submit(() -> {

                try {

                    /*
                     * 告诉main：
                     * 我这个线程准备好了
                     */
                    readyLatch.countDown();


                    /*
                     * 等发令枪
                     */
                    startLatch.await();


                    String requestBody = """
                            {
                              "userId": 1,
                              "items": [
                                {
                                  "skuId": %d,
                                  "quantity": 1
                                }
                              ]
                            }
                            """.formatted(SKU_ID);


                    HttpRequest request =
                            HttpRequest
                                    .newBuilder()
                                    .uri(
                                            URI.create(
                                                    "http://localhost:"
                                                            + port
                                                            + "/api/orders"
                                            )
                                    )
                                    .header(
                                            "Content-Type",
                                            "application/json"
                                    )
                                    .POST(
                                            HttpRequest.BodyPublishers
                                                    .ofString(
                                                            requestBody
                                                    )
                                    )
                                    .build();


                    HttpResponse<String> response =
                            httpClient.send(
                                    request,
                                    HttpResponse.BodyHandlers
                                            .ofString()
                            );


                    String body =
                            response.body();

                    responses.add(body);


                    /*
                     * 注意：
                     *
                     * 你目前BusinessException
                     * 很可能HTTP status仍然都是200。
                     *
                     * 所以不能只判断：
                     *
                     * response.statusCode() == 200
                     *
                     * 要判断你Result里面的code。
                     */

                    if (SUCCESS_PATTERN
                            .matcher(body)
                            .find()) {

                        successCount
                                .incrementAndGet();

                    } else {

                        failureCount
                                .incrementAndGet();
                    }

                } catch (Exception e) {

                    failureCount
                            .incrementAndGet();

                    responses.add(
                            "EXCEPTION: "
                                    + e.getMessage()
                    );

                } finally {

                    doneLatch.countDown();
                }
            });
        }


        /*
         * 等50个线程全部到起跑线
         */
        readyLatch.await();


        long startTime =
                System.currentTimeMillis();


        /*
         * 发令枪！
         *
         * 50个请求一起开始
         */
        startLatch.countDown();


        /*
         * 等全部请求完成
         */
        boolean finished =
                doneLatch.await(
                        30,
                        TimeUnit.SECONDS
                );


        long elapsed =
                System.currentTimeMillis()
                        - startTime;


        executorService.shutdown();


        if (!finished) {
            throw new RuntimeException(
                    "并发测试30秒内未完成"
            );
        }


        System.out.println(
                "=============================="
        );

        System.out.println(
                "总请求数 = "
                        + THREAD_COUNT
        );

        System.out.println(
                "成功请求 = "
                        + successCount.get()
        );

        System.out.println(
                "失败请求 = "
                        + failureCount.get()
        );

        System.out.println(
                "耗时 = "
                        + elapsed
                        + " ms"
        );


        Integer availableStock =
                jdbcTemplate.queryForObject(
                        """
                        SELECT available_stock
                        FROM inventory
                        WHERE sku_id = ?
                        """,
                        Integer.class,
                        SKU_ID
                );


        Integer reservedStock =
                jdbcTemplate.queryForObject(
                        """
                        SELECT reserved_stock
                        FROM inventory
                        WHERE sku_id = ?
                        """,
                        Integer.class,
                        SKU_ID
                );


        Integer orderCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM orders
                        """,
                        Integer.class
                );


        Integer orderItemCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM order_item
                        WHERE sku_id = ?
                        """,
                        Integer.class,
                        SKU_ID
                );


        System.out.println(
                "available_stock = "
                        + availableStock
        );

        System.out.println(
                "reserved_stock = "
                        + reservedStock
        );

        System.out.println(
                "订单数量 = "
                        + orderCount
        );

        System.out.println(
                "订单明细数量 = "
                        + orderItemCount
        );

        System.out.println(
                "=============================="
        );


        /*
         * 最重要的几个断言
         */

        assertEquals(
                INITIAL_STOCK,
                successCount.get()
        );

        assertEquals(
                THREAD_COUNT - INITIAL_STOCK,
                failureCount.get()
        );

        assertEquals(
                0,
                availableStock
        );

        assertEquals(
                INITIAL_STOCK,
                reservedStock
        );

        assertEquals(
                INITIAL_STOCK,
                orderCount
        );

        assertEquals(
                INITIAL_STOCK,
                orderItemCount
        );
    }
}