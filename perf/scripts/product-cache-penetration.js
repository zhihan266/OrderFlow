import http from 'k6/http';
import {
    Counter,
    Rate
} from 'k6/metrics';


const BASE_URL =
    __ENV.BASE_URL || 'http://localhost:8080';

const PRODUCT_ID =
    Number(__ENV.PRODUCT_ID || 99999999);

const VUS =
    Number(__ENV.VUS || 50);

const DURATION =
    __ENV.DURATION || '30s';


const expectedNotFound =
    new Counter('expected_not_found');

const unexpectedResponses =
    new Counter('unexpected_responses');

const unexpectedRate =
    new Rate('unexpected_rate');


export const options = {

    vus: VUS,

    duration: DURATION,

    thresholds: {

        unexpected_rate: [
            'rate<0.01'
        ]
    }
};


export default function () {

    const response = http.get(
        `${BASE_URL}/api/products/${PRODUCT_ID}`,
        {
            tags: {
                test_type: 'cache-penetration'
            }
        }
    );


    let body = null;

    try {

        body = response.json();

    } catch (e) {

        unexpectedResponses.add(1);

        unexpectedRate.add(true);

        return;
    }


    /*
     * 不存在商品属于预期结果。
     *
     * 不同项目可能：
     *
     * HTTP 404
     *
     * 或者：
     * HTTP 200 + 业务错误码
     *
     * 所以这里先同时兼容。
     */
    const productNotFound =

        response.status === 404 ||

        body?.code === 404 ||

        body?.code === 40401 ||

        body?.message === '商品不存在' ||

        body?.msg === '商品不存在';


    if (productNotFound) {

        expectedNotFound.add(1);

        unexpectedRate.add(false);

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