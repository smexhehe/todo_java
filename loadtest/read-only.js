import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://todo:7540';

export const options = {
  stages: __ENV.SMOKE === '1' ? undefined : [
    { duration: '30s', target: 5 },
    { duration: '3m', target: 10 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  vus: __ENV.SMOKE === '1' ? 1 : undefined,
  duration: __ENV.SMOKE === '1' ? '10s' : undefined,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  if (!__ENV.TODO_PASSWORD) {
    return { token: '' };
  }

  const response = http.post(
    `${baseUrl}/api/signin`,
    JSON.stringify({ password: __ENV.TODO_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/signin' } },
  );
  if (response.status !== 200) {
    throw new Error(`Sign-in failed: HTTP ${response.status}`);
  }
  return { token: response.json('token') };
}

export default function (data) {
  const taskParams = { tags: { name: 'GET /api/tasks' } };
  if (data.token) {
    taskParams.cookies = { token: data.token };
  }

  const tasks = http.get(`${baseUrl}/api/tasks`, taskParams);
  check(tasks, {
    'tasks: HTTP 200 and array': (response) =>
      response.status === 200 && Array.isArray(response.json('tasks')),
  });

  const nextDate = http.get(
    `${baseUrl}/api/nextdate?now=20240126&date=20240113&repeat=d%207`,
    { tags: { name: 'GET /api/nextdate' } },
  );
  check(nextDate, {
    'nextdate: expected response': (response) =>
      response.status === 200 && response.body === '20240127',
  });

  sleep(1);
}
