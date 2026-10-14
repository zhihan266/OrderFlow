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
    Number(__ENV.VUS || 100);


const successfulResponses =
    new Counter('successful_responses');

const busyResponses =
    new Counter('busy_responses');

const unexpectedResponses =
    new Counter('unexpected_responses');

const unexpectedRate =
    new Rate('unexpected_rate');


export const options = {

    scenarios: {

        cache_breakdown: {

            /*
             * 100 个 VU
             * 每人只请求一次。
             *
             * 目的是让大量请求尽可能同时
             * 撞到刚刚被删除的热点缓存。
             */
            executor: 'per-vu-iterations',

            vus: VUS,

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
        ],

        /*
         * 正常情况下数据库重建远小于 1 秒，
         * 因此其他请求应该能够在 waitForCache
         * 的等待周期内拿到新缓存。
         */
        busy_responses: [
            'count==0'
        ]
    }
};


export default function () {

    const response = http.get(

        `${BASE_URL}/api/products/${PRODUCT_ID}`,

        {
            tags: {
                test_type: 'cache-breakdown'
            }
        }
    );


    let body = null;

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
     * 正常成功。
     */
    const success =
        response.status === 200 &&
        body?.data !== null &&
        body?.data !== undefined;


    if (success) {

        successfulResponses.add(1);

        unexpectedRate.add(false);

        check(response, {

            'product request succeeded':
                () => true
        });

        return;
    }


    /*
     * 对应你 ProductService.waitForCache()
     *
     * 20 × 50ms 等完以后还没拿到缓存时，
     * 会抛这个异常。
     */
    const busy =
        body?.code === 50010 ||

        body?.message ===
            '商品查询繁忙，请稍后再试' ||

        body?.msg ===
            '商品查询繁忙，请稍后再试';


    if (busy) {

        busyResponses.add(1);

        unexpectedRate.add(false);

        console.error(
            `Cache rebuild timeout:
body=${response.body}`
        );

        return;
    }


    unexpectedResponses.add(1);

    unexpectedRate.add(true);


    console.error(
        `Unexpected response:
status=${response.status}
body=${response.body}`
    );
}