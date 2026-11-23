import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:18080').replace(/\/$/, '');
const PROFILE = __ENV.PROFILE || 'smoke';
const TARGET_RPS = Number(__ENV.TARGET_RPS || 50);
const DURATION = __ENV.DURATION || '1m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(10, TARGET_RPS));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(50, TARGET_RPS * 4));

const applicationErrors = new Rate('application_errors');

const profiles = {
  smoke: {
    executor: 'constant-vus',
    vus: Number(__ENV.VUS || 2),
    duration: __ENV.DURATION || '20s',
  },
  load: {
    executor: 'ramping-arrival-rate',
    startRate: Math.max(1, Math.floor(TARGET_RPS / 10)),
    timeUnit: '1s',
    preAllocatedVUs: PRE_ALLOCATED_VUS,
    maxVUs: MAX_VUS,
    stages: [
      { target: TARGET_RPS, duration: __ENV.RAMP_UP || '30s' },
      { target: TARGET_RPS, duration: DURATION },
      { target: 0, duration: __ENV.RAMP_DOWN || '15s' },
    ],
  },
  tps: {
    executor: 'constant-arrival-rate',
    rate: TARGET_RPS,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: PRE_ALLOCATED_VUS,
    maxVUs: MAX_VUS,
  },
};

if (!profiles[PROFILE]) {
  throw new Error(`Unknown PROFILE=${PROFILE}; use smoke, load, or tps`);
}

export const options = {
  scenarios: { api_read: profiles[PROFILE] },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    checks: ['rate>0.99'],
    application_errors: ['rate<0.01'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    dropped_iterations: ['count==0'],
  },
};

function authHeaders() {
  if (__ENV.ACCESS_TOKEN) {
    return { Authorization: `Bearer ${__ENV.ACCESS_TOKEN}` };
  }
  return {};
}

export function setup() {
  const health = http.get(`${BASE_URL}/api/health`, {
    tags: { name: 'GET /api/health (setup)' },
  });
  if (health.status !== 200) {
    fail(`Target is not healthy: ${BASE_URL} returned ${health.status}`);
  }

  let accessToken = __ENV.ACCESS_TOKEN || '';
  if (!accessToken && __ENV.AUTH_MODE === 'demo') {
    const response = http.post(
      `${BASE_URL}/api/auth/demo`,
      JSON.stringify({ role: __ENV.DEMO_ROLE || 'VIEWER' }),
      {
        headers: { 'Content-Type': 'application/json' },
        responseType: 'text',
        tags: { name: 'POST /api/auth/demo (setup)' },
      },
    );
    if (response.status !== 200) fail(`Demo login failed with ${response.status}`);
    accessToken = response.json('accessToken');
  } else if (!accessToken && __ENV.USERNAME && __ENV.PASSWORD) {
    const response = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ username: __ENV.USERNAME, password: __ENV.PASSWORD }),
      {
        headers: { 'Content-Type': 'application/json' },
        responseType: 'text',
        tags: { name: 'POST /api/auth/login (setup)' },
      },
    );
    if (response.status !== 200) fail(`Login failed with ${response.status}`);
    accessToken = response.json('accessToken');
  }
  return { accessToken };
}

const requests = [
  { weight: 30, path: '/api/events?page=0&size=20', name: 'GET /api/events' },
  { weight: 25, path: '/api/transactions?page=0&size=20', name: 'GET /api/transactions' },
  { weight: 20, path: '/api/events/stats', name: 'GET /api/events/stats' },
  { weight: 15, path: '/api/events/stats/trend', name: 'GET /api/events/stats/trend' },
  { weight: 10, path: '/api/detection/rules', name: 'GET /api/detection/rules' },
];

function chooseRequest() {
  let cursor = Math.random() * 100;
  for (const request of requests) {
    cursor -= request.weight;
    if (cursor < 0) return request;
  }
  return requests[0];
}

export default function (data) {
  const request = chooseRequest();
  const headers = data.accessToken
    ? { Authorization: `Bearer ${data.accessToken}` }
    : authHeaders();
  const response = http.get(`${BASE_URL}${request.path}`, {
    headers,
    tags: { name: request.name },
  });
  const ok = check(response, {
    [`${request.name} returns 200`]: (r) => r.status === 200,
  });
  applicationErrors.add(!ok, { endpoint: request.name });
}
