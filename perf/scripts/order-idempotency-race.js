import http from 'k6/http';
import { check } from 'k6';
import {
    Counter,
    Rate
} from 'k6/metrics';


const BASE_URL =
    __ENV.BASE_URL || 'http://localhost:8080';

const SKU_ID =
    Number(__ENV.SKU_ID || 3);

const USER_ID =
    Number(__ENV.USER_ID || 20001);


const successResponses =
    new Counter('success_responses');

const processingResponses =
    new Counter('processing_responses');

const unexpectedResponses =
    new Counter('unexpected_responses');

const unexpectedRate =
    new Rate('unexpected_rate');


export const options = {

    scenarios: {

        idempotency_race: {

            /*
             * 50 个 VU，
             * 每个只请求一次。
             *
             * 所有人使用同一个 Idempotency-key。
             */
            executor: 'per-vu-iterations',

            vus: 50,

            iterations: 1,

            maxDuration: '30s'
        }
    },


    thresholds: {

        unexpected_rate: [
            'rate==0'
        ],

        unexpected_responses: [
            'count==0'
        ]
    }
};


/*
 * setup 不创建订单。
 *
 * 它只生成所有 VU 共用的 key 和请求体。
 *
 * 因此真正的第一个订单创建
 * 会发生在并发测试过程中。
 */
export function setup() {

    const idempotencyKey =
        `idempotency-race-${Date.now()}`;


    const payload = JSON.stringify({

        userId: USER_ID,

        items: [
            {
                skuId: SKU_ID,
                quantity: 1
            }
        ]
    });


    console.log(
        `IDEMPOTENCY_KEY=${idempotencyKey}`
    );


    return {

        idempotencyKey:
            idempotencyKey,

        payload:
            payload
    };
}


export default function (data) {

    const params = {

        headers: {

            'Content-Type':
                'application/json',

            'Idempotency-key':
                data.idempotencyKey
        }
    };


    const response = http.post(

        `${BASE_URL}/api/orders`,

        data.payload,

        params
    );


    let body;

    try {

        body = response.json();

    } catch (e) {

        unexpectedResponses.add(1);

        unexpectedRate.add(true);

        console.error(
            `Invalid JSON:
status=${response.status}
body=${response.body}`
        );

        return;
    }


    /*
     * 情况 1：
     *
     * 请求成功。
     *
     * 可能是：
     *
     * 1. 真正创建订单的第一个请求
     * 2. 第一个请求已经完成后，
     *    后续请求直接返回原订单
     */
    const success =
        response.status === 200 &&
        body?.data?.orderId !== undefined &&
        body?.data?.orderId !== null;


    if (success) {

        successResponses.add(1);

        unexpectedRate.add(false);

        check(response, {

            'successful idempotent response':
                () => true
        });

        return;
    }


    /*
     * 情况 2：
     *
     * 第一个请求还在执行，
     * 当前请求观察到了 PROCESSING。
     */
    const processing =
        body?.code === 40902 ||

        body?.message ===
            '订单请求正在处理中' ||

        body?.msg ===
            '订单请求正在处理中';


    if (processing) {

        processingResponses.add(1);

        unexpectedRate.add(false);

        check(response, {

            'request detected processing state':
                () => true
        });

        return;
    }


    /*
     * 其他结果都是异常。
     */
    unexpectedResponses.add(1);

    unexpectedRate.add(true);


    console.error(
        `Unexpected response:
status=${response.status}
body=${response.body}`
    );
}