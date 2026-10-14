import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const businessErrorRate = new Rate('business_errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const SKU_ID = Number(__ENV.SKU_ID || 9);

const USER_ID = Number(__ENV.USER_ID || 10001);

const VUS = Number(__ENV.VUS || 10);

const DURATION = __ENV.DURATION || '30s';


export const options = {

    vus: VUS,

    duration: DURATION,

    thresholds: {

        // HTTP 层失败率低于 1%
        http_req_failed: [
            'rate<0.01'
        ],

        // 第一轮先设置一个比较宽松的目标
        http_req_duration: [
            'p(95)<1000',
            'p(99)<2000'
        ],

        // 业务失败率低于 1%
        business_errors: [
            'rate<0.01'
        ]
    }
};


export default function () {

    /*
     * 每一个真正的下单请求，都必须拥有不同的幂等 Key。
     *
     * __VU   = 当前虚拟用户编号
     * __ITER = 当前虚拟用户执行的第几次请求
     *
     * 加 Date.now() 是进一步避免重复。
     */
    const idempotencyKey =
        `perf-${__VU}-${__ITER}-${Date.now()}`;


    const payload = JSON.stringify({

        userId: USER_ID,

        items: [
            {
                skuId: SKU_ID,
                quantity: 1
            }
        ]
    });


    const params = {

        headers: {

            'Content-Type': 'application/json',

            'Idempotency-key': idempotencyKey
        },

        tags: {
            api: 'create-order'
        }
    };


    const response = http.post(
        `${BASE_URL}/api/orders`,
        payload,
        params
    );

    let body = null;
    let businessSuccess = false;

    try {
        body = response.json();

        businessSuccess =
            response.status === 200 &&
            body !== null &&
            body.data !== null &&
            body.data !== undefined &&
            body.data.orderId !== null &&
            body.data.orderId !== undefined;

    } catch (e) {
        businessSuccess = false;
    }

    check(response, {
        'status is 200': (r) => r.status === 200,

        'order created successfully': () => businessSuccess
    });

    businessErrorRate.add(!businessSuccess);


    // 如果前几个请求失败，打印响应方便排查
    if (!businessSuccess && __VU === 1 && __ITER < 3) {
        console.error(
            `business failed: status=${response.status}, body=${response.body}`
        );
    }
}