Scenario:
Create Order / Single Hot SKU

SKU:
9

Duration:
60s

20 VU:
QPS: 313.41
Avg: 63.61ms
P95: 112.59ms
P99: 151.71ms
Error: 0%

30 VU:
QPS: 385.82
Avg: 77.65ms
P95: 118.34ms
P99: 147.32ms
Error: 0%

50 VU:
QPS: 377.87
Avg: 132.06ms
P95: 176.36ms
P99: 198.25ms
Error: 0%

The throughput plateaued at approximately 380 QPS
between 30 and 50 VUs, while latency continued to
increase, indicating saturation under a single hot-SKU workload.