# OrderFlow Performance Test Summary

## 1. Test Target

Core API:

POST /api/orders

The test covers the real order creation path, including:

- idempotency check
- SKU query
- inventory query
- order creation
- inventory reservation
- order item creation
- MQ outbox record creation

All tests were executed locally with k6.

---

## 2. Single Hot-SKU Baseline

All concurrent requests purchase the same SKU.

This simulates a hot-product scenario where many users compete for the same inventory record.

### 20 VUs / 60s

- QPS: 313.41
- Avg latency: 63.61 ms
- P95: 112.59 ms
- P99: 151.71 ms
- HTTP error rate: 0%
- Business error rate: 0%

### 30 VUs / 60s

- QPS: 385.82
- Avg latency: 77.65 ms
- P95: 118.34 ms
- P99: 147.32 ms
- HTTP error rate: 0%
- Business error rate: 0%

### 50 VUs / 60s

- QPS: 377.87
- Avg latency: 132.06 ms
- P95: 176.36 ms
- P99: 198.25 ms
- HTTP error rate: 0%
- Business error rate: 0%

### Observation

From 30 VUs to 50 VUs:

- concurrency increased by about 66.7%
- throughput did not increase
- QPS changed from 385.82 to 377.87
- average latency increased from 77.65 ms to 132.06 ms
- P95 increased from 118.34 ms to 176.36 ms

Therefore, under the single hot-SKU workload, throughput entered a clear saturation region at approximately 380 QPS.

Increasing concurrency mainly increased request waiting time instead of system throughput.

---

## 3. Multi-SKU Comparison Test

To verify whether contention on a single inventory record was limiting throughput, the workload was distributed across multiple SKUs while keeping the main order creation logic unchanged.

### 30 VUs / 60s

- QPS: 1115.62
- Avg latency: 26.78 ms
- P95: 34.02 ms
- P99: 43.97 ms
- HTTP error rate: 0%
- Business error rate: 0%

### 50 VUs / 60s

- QPS: 1316.43
- Avg latency: 37.89 ms
- P95: 46.93 ms
- P99: 67.37 ms
- HTTP error rate: 0%
- Business error rate: 0%

---

## 4. Single-SKU vs Multi-SKU

At 50 VUs:

| Metric | Single Hot SKU | Multi SKU |
|---|---:|---:|
| QPS | 377.87 | 1316.43 |
| Avg latency | 132.06 ms | 37.89 ms |
| P95 | 176.36 ms | 46.93 ms |
| P99 | 198.25 ms | 67.37 ms |
| HTTP error rate | 0% | 0% |

The multi-SKU workload achieved approximately 3.48x the throughput of the single hot-SKU workload.

P95 latency decreased by approximately 73%.

---

## 5. Bottleneck Analysis

The comparison test indicates that contention on hot inventory records is one of the major bottlenecks in the order creation path.

In the single-SKU scenario, concurrent transactions attempt to update the same inventory record.

When requests are distributed across multiple SKU inventory records, contention is reduced significantly and throughput increases from approximately 380 QPS to more than 1300 QPS.

The multi-SKU test is used as a control experiment for bottleneck identification rather than as a business optimization.

In real hot-product scenarios, many users may still compete for the same SKU.

Further database lock-wait metrics can be collected to directly verify the internal lock contention mechanism.

---

## 6. Current Conclusion

Under the current local test environment:

- Single hot-SKU throughput enters saturation at approximately 380 QPS.
- At 50 VUs, single-SKU P95 latency is approximately 176 ms.
- Distributing requests across multiple SKUs increases throughput to approximately 1316 QPS.
- At 50 VUs, multi-SKU P95 latency is approximately 47 ms.
- No HTTP or business errors occurred in the valid performance test runs.
- Hot inventory record contention is identified as a major performance bottleneck.

## 7. Oversell Concurrency Test

### Test Goal

Verify that concurrent order creation cannot sell more inventory than is actually available.

### Test Setup

- Test SKU: 19
- Initial available stock: 100
- Initial reserved stock: 0
- Concurrent VUs: 100
- Total order requests: 500
- Quantity per request: 1

The test used k6 `shared-iterations` so that 100 virtual users concurrently executed a total of 500 order requests.

### Result

- Total requests: 500
- Successful orders: 100
- Stock-rejected requests: 400
- Unexpected errors: 0
- HTTP failure rate: 0%
- Business unexpected error rate: 0%

Database state after the test:

- available_stock: 0
- reserved_stock: 100
- successful order increment: 100

### Conclusion

The oversell protection test passed.

When 500 order requests competed for 100 units of inventory, exactly 100 orders were successfully created and the remaining 400 requests were rejected because inventory was exhausted.

No request caused the inventory to become negative and no more than 100 orders were created.

The concurrency safety is mainly guaranteed by an atomic conditional inventory update:

```sql
UPDATE inventory
SET
    available_stock = available_stock - ?,
    reserved_stock = reserved_stock + ?
WHERE
    sku_id = ?
    AND available_stock >= ?;

## 8. Idempotency Replay Test

### Test Goal

Verify that retrying an already completed order request with the same
Idempotency-key does not create duplicate orders or reserve inventory again.

### Test Setup

- SKU: 3
- Concurrent VUs: 50
- Replay requests: 200
- Initial request: 1
- Total HTTP requests: 201
- All requests used the same user, request body and Idempotency-key.

### Result

- Original order ID: 328307
- Replay responses returning the original order: 200
- Idempotency errors: 0
- Idempotency error rate: 0%
- HTTP failure rate: 0%
- Additional inventory reserved by 200 retries: 0
- Total inventory consumed by the whole test: 1

### Conclusion

The completed-request idempotency test passed.

After the first request successfully created an order, another 200 requests
using the same Idempotency-key all returned the original order instead of
creating new orders.

Although 201 HTTP requests were executed, only one real order was created and
inventory was reserved only once.

This verifies the SUCCESS replay path of the order idempotency mechanism.

## 9. Concurrent Idempotency Race Test

### Test Goal

Verify that multiple concurrent requests using the same previously unseen
Idempotency-key cannot execute the order creation logic multiple times.

### Test Setup

- SKU: 2
- Concurrent VUs: 50
- Requests per VU: 1
- Total requests: 50
- All requests used the same:
  - user
  - request body
  - Idempotency-key

Unlike the replay test, no order was created before the concurrent requests
started.

### Result

- Successful execution responses: 1
- PROCESSING responses: 49
- Unexpected responses: 0
- Unexpected error rate: 0%
- HTTP request failures: 0%

### Behavior

When the 50 requests concurrently attempted to use the same
Idempotency-key, only one request successfully obtained the right to execute
the order creation transaction.

The remaining 49 requests observed the idempotency record in the PROCESSING
state and were rejected from executing the business logic again.

Database verification confirmed:

- only one idempotency record was created
- only one real order was created
- inventory was reserved only once

### Conclusion

The concurrent idempotency protection test passed.

The combination of a unique idempotency record and PROCESSING/SUCCESS state
management prevents concurrent duplicate requests from creating duplicate
orders or reserving inventory multiple times.

## 10. Redis Product Cache Hit Test

### Test Goal

Verify that repeated product-detail requests are served from Redis after the cache is warmed up.

### Test Procedure

1. Deleted the existing Redis product cache.
2. Requested the product detail once.
3. Verified that the product data was written to Redis.
4. Repeatedly requested the same product under concurrent load.
5. Observed application logs to verify the request path.

### Result

- The first request caused a cache miss and loaded product data from MySQL.
- The product detail was successfully written to Redis.
- Subsequent requests hit Redis directly.
- Application logs confirmed repeated cache hits.
- No repeated database loads were observed during the cache-hit workload.

### Conclusion

The Redis product-detail cache hit path passed the test.

After cache warm-up, repeated requests were served directly from Redis,
reducing unnecessary MySQL queries.

## 11. Redis Cache Breakdown Protection Test

### Test Goal

Verify that concurrent requests for a hot product do not simultaneously
query the database when its Redis cache expires or is deleted.

### Test Setup

- Product ID: 3
- Concurrent VUs: 100
- Requests per VU: 1
- Product cache was deleted immediately before the test.

### Result

- Total requests: 100
- Successful responses: 100
- Busy responses: 0
- Unexpected responses: 0
- HTTP failure rate: 0%
- Database cache rebuild executions: 1
- Average response time: 499.38 ms
- P95 response time: 501.62 ms

### Conclusion

The cache breakdown protection test passed.

When 100 concurrent requests accessed the same product immediately after its
cache was removed, only one request acquired the Redis rebuild lock and
loaded the product base data from the database.

The remaining requests waited for the cache to be rebuilt and then returned
successfully.

This verifies the SETNX-based mutual exclusion, double-check and cache-wait
mechanism used to prevent a hot cache key from causing simultaneous database
fallback.

The test also demonstrates the latency trade-off of mutex-based cache
reconstruction: database pressure is protected at the cost of temporarily
higher latency while the hot cache is rebuilding.

## 12. RabbitMQ Order Timeout Cancellation Test

### Test Goal

Verify that an unpaid order is automatically cancelled after the configured timeout and that reserved inventory is released correctly.

### Test Setup

- Dedicated MQ test profile
- Order timeout: 10 seconds
- One unpaid order was created
- RabbitMQ delay queue used TTL + dead-letter routing

### Result

Before timeout:

- Order status: PENDING_PAYMENT
- Available stock decreased by 1
- Reserved stock increased by 1

After timeout:

- Order status changed to CANCELLED
- Available stock was restored
- Reserved stock was released
- Timeout message was successfully consumed and ACKed

### Conclusion

The RabbitMQ delayed order cancellation flow passed the test.

The system correctly handled:

order creation
→ inventory reservation
→ delayed timeout message
→ automatic cancellation
→ inventory release
→ message ACK.

## 14. RabbitMQ Consumer Failure / Dead Letter Test

### Test Goal

Verify that a timeout message is not silently lost when the consumer fails,
and that the failed message is routed to a dedicated error queue.

### Test Procedure

A controlled runtime exception was injected into the timeout consumer before
the order cancellation logic was executed.

The consumer used manual acknowledgement mode.

On failure it executed:

basicNack(deliveryTag, false, false)

The timeout queue was configured with a dead-letter exchange and routing key.

### Result

- Timeout message reached the consumer.
- The simulated consumer exception was caught.
- The message was NACKed without requeueing.
- The message disappeared from the timeout queue.
- The message appeared in the timeout error queue.
- The failed message was not ACKed.
- Because the failure occurred before business processing, the order remained
  PENDING_PAYMENT and its inventory remained reserved.

### Conclusion

The RabbitMQ consumer failure isolation test passed.

Failed timeout messages are not silently discarded or repeatedly returned to
the normal timeout queue. They are routed to a dedicated error queue for
subsequent recovery or manual intervention.