import http from 'k6/http';
import { check } from 'k6';
import {
    Counter,
    Rate
} from 'k6/metrics';


const BASE_URL =
    __ENV.BASE_URL || 'http://localhost:8080';

const PRODUCT_ID =
    Number(__ENV.PRODUCT_ID || 3);

const VUS =
    Number(__ENV.VUS || 50);

const DURATION =
    __ENV.DURATION || '30s';


const cacheRequestErrors =
    new Counter('cache_request_errors');

const cacheErrorRate =
    new Rate('cache_error_rate');


export const options = {

    vus: VUS,

    duration: DURATION,

    thresholds: {

        cache_error_rate: [
            'rate<0.01'
        ],

        http_req_duration: [
            'p(95)<500'
        ]
    }
};


/*
 * setup() 只执行一次。
 *
 * 正式压测之前先访问一次商品，
 * 确保缓存已经预热。
 *
 * 后续 default() 测到的主要就是缓存命中路径。
 */
export function setup() {

    const response = http.get(

        `${BASE_URL}/api/products/${PRODUCT_ID}`
    );


    if (response.status !== 200) {

        throw new Error(
            `Cache warmup failed:
status=${response.status}
body=${response.body}`
        );
    }


    console.log(
        `Product ${PRODUCT_ID} cache warmed up`
    );
}


export default function () {

    const response = http.get(

        `${BASE_URL}/api/products/${PRODUCT_ID}`,

        {
            tags: {
                test_type: 'product-cache-hit'
            }
        }
    );


    let validResponse = false;


    try {

        const body = response.json();

        validResponse =
            response.status === 200 &&
            body !== null &&
            body.data !== null &&
            body.data !== undefined;

    } catch (e) {

        validResponse = false;
    }


    check(response, {

        'status is 200':
            r => r.status === 200,

        'product data exists':
            () => validResponse
    });


    if (validResponse) {

        cacheErrorRate.add(false);

        return;
    }


    cacheRequestErrors.add(1);

    cacheErrorRate.add(true);


    console.error(
        `Cache request failed:
status=${response.status}
body=${response.body}`
    );
}