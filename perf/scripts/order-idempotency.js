import http from 'k6/http';
import { check } from 'k6';
import {
    Counter,
    Rate
} from 'k6/metrics';


const BASE_URL =
    __ENV.BASE_URL || 'http://localhost:8080';

const SKU_ID =
    Number(__ENV.SKU_ID || 9);

const USER_ID =
    Number(__ENV.USER_ID || 20001);


const sameOrderResponses =
    new Counter('same_order_responses');

const idempotencyErrors =
    new Counter('idempotency_errors');

const idempotencyErrorRate =
    new Rate('idempotency_error_rate');


export const options = {

    scenarios: {

        idempotency_replay: {

            executor: 'shared-iterations',

            vus: 50,

            iterations: 200,

            maxDuration: '30s'
        }
    },

    thresholds: {

        idempotency_error_rate: [
            'rate==0'
        ],

        idempotency_errors: [
            'count==0'
        ]
    }
};


/*
 * setup() 只执行一次。
 *
 * 我们先正常创建一个订单，
 * 等它完全成功以后，
 * 再让后面的 200 次请求重复使用相同的 key。
 */
export function setup() {

    const idempotencyKey =
        `idempotency-replay-${Date.now()}`;


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

        throw new Error(
            `Initial order response is not JSON: ${response.body}`
        );
    }


    if (
        response.status !== 200 ||
        body?.data?.orderId === undefined ||
        body?.data?.orderId === null
    ) {

        throw new Error(
            `Initial order creation failed:
status=${response.status}
body=${response.body}`
        );
    }


    const orderId =
        body.data.orderId;


    console.log(
        `IDEMPOTENCY_KEY=${idempotencyKey}`
    );

    console.log(
        `ORIGINAL_ORDER_ID=${orderId}`
    );


    return {

        idempotencyKey:
            idempotencyKey,

        orderId:
            orderId,

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

        idempotencyErrors.add(1);

        idempotencyErrorRate.add(true);

        console.error(
            `Invalid JSON:
status=${response.status}
body=${response.body}`
        );

        return;
    }


    const sameOrder =
        response.status === 200 &&
        body?.data?.orderId !== undefined &&
        body?.data?.orderId !== null &&
        String(body.data.orderId) ===
        String(data.orderId);


    check(response, {

        'status is 200':
            r => r.status === 200,

        'same original order returned':
            () => sameOrder
    });


    if (sameOrder) {

        sameOrderResponses.add(1);

        idempotencyErrorRate.add(false);

        return;
    }


    idempotencyErrors.add(1);

    idempotencyErrorRate.add(true);


    console.error(
        `Idempotency violation:
originalOrderId=${data.orderId}
status=${response.status}
body=${response.body}`
    );
}