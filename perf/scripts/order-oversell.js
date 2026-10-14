import http from 'k6/http';
import { check } from 'k6';
import {
    Counter,
    Rate
} from 'k6/metrics';


const BASE_URL =
    __ENV.BASE_URL || 'http://localhost:8080';

const SKU_ID =
    Number(__ENV.SKU_ID || 19);

const USER_ID =
    Number(__ENV.USER_ID || 20001);

const VUS =
    Number(__ENV.VUS || 500);


/*
 * 成功创建的订单数量
 */
const successfulOrders =
    new Counter('successful_orders');


/*
 * 因库存不足而被正确拒绝的请求
 */
const stockRejectedOrders =
    new Counter('stock_rejected_orders');


/*
 * 非预期错误
 */
const unexpectedErrors =
    new Counter('unexpected_errors');


/*
 * 非预期错误率
 */
const unexpectedErrorRate =
    new Rate('unexpected_error_rate');


export const options = {

    scenarios: {

        oversell_test: {

            executor: 'shared-iterations',

            // 同时保持 100 个并发请求
            vus: 100,

            // 一共仍然发送 500 个请求
            iterations: 500,

            maxDuration: '30s'
        }
    },

    thresholds: {

        unexpected_error_rate: [
            'rate<0.01'
        ],

        successful_orders: [
            'count<=100'
        ]
    }
};


export default function () {

    /*
     * 每个请求必须是独立订单，
     * 所以幂等 Key 必须不同。
     */
    const idempotencyKey =
        `oversell-${__VU}-${Date.now()}`;


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

            'Content-Type':
                'application/json',

            'Idempotency-key':
                idempotencyKey
        },

        tags: {
            test_type: 'oversell'
        }
    };


    const response = http.post(

        `${BASE_URL}/api/orders`,

        payload,

        params
    );


    let body;

    try {

        body = response.json();

    } catch (e) {

        unexpectedErrors.add(1);

        unexpectedErrorRate.add(true);

        console.error(
            `Invalid JSON: status=${response.status}, body=${response.body}`
        );

        return;
    }


    /*
     * 情况 1：
     *
     * 成功创建订单
     */
    const orderCreated =
        response.status === 200 &&
        body !== null &&
        body.data !== null &&
        body.data !== undefined &&
        body.data.orderId !== null &&
        body.data.orderId !== undefined;


    if (orderCreated) {

        successfulOrders.add(1);

        unexpectedErrorRate.add(false);

        check(response, {

            'order created':
                () => true
        });

        return;
    }


    /*
     * 情况 2：
     *
     * 库存不足。
     *
     * 这是防超卖测试中的正常结果，
     * 不应该算系统错误。
     *
     * 你的 BusinessException 中库存不足
     * 使用的是 code = 40013。
     *
     * 同时保留 message 判断，
     * 方便 Result 格式稍有变化时继续工作。
     */
    const stockRejected =

        body?.code === 40013 ||

        body?.message === '商品库存不足' ||

        body?.msg === '商品库存不足';


    if (stockRejected) {

        stockRejectedOrders.add(1);

        unexpectedErrorRate.add(false);

        check(response, {

            'request rejected because stock exhausted':
                () => true
        });

        return;
    }


    /*
     * 情况 3：
     *
     * 既不是成功订单，
     * 也不是正常库存不足。
     *
     * 这种才属于真正异常。
     */
    unexpectedErrors.add(1);

    unexpectedErrorRate.add(true);


    console.error(

        `Unexpected response:
        status=${response.status}
        body=${response.body}`
    );
}