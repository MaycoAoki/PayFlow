import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomUUID } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TRANSFER_URL = __ENV.TRANSFER_URL || 'http://localhost:8081';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(99)<2000'],
        http_req_failed:   ['rate<0.01'],
    },
};

function createAccount(ownerId, initialBalance) {
    const resp = http.post(`${BASE_URL}/accounts`, JSON.stringify({
        ownerId, initialBalance, currency: 'BRL',
    }), { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': randomUUID() } });
    check(resp, { 'account created': (r) => r.status === 201 });
    return resp.json('accountId');
}

export function setup() {
    const accounts = [];
    for (let i = 0; i < 20; i++) {
        const sourceId = createAccount(`source-${i}`, '10000.00');
        const targetId = createAccount(`target-${i}`, '0.00');
        if (sourceId && targetId) accounts.push({ sourceId, targetId });
    }
    return { accounts };
}

export default function (data) {
    const pair = data.accounts[Math.floor(Math.random() * data.accounts.length)];

    const resp = http.post(`${TRANSFER_URL}/transfers`, JSON.stringify({
        sourceAccountId: pair.sourceId,
        targetAccountId: pair.targetId,
        amount: '1.00',
        currency: 'BRL',
    }), { headers: { 'Content-Type': 'application/json', 'Idempotency-Key': randomUUID() } });

    check(resp, { 'transfer accepted': (r) => r.status === 202 });
    sleep(0.1);
}
