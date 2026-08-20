import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { pressureGateScenarios, pressureGateThresholds, resolvePressureWriteFlow } from './pressure-gate-scenarios.mjs';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:1820/api';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'admin123';
const MUTATING_FLOWS = (__ENV.MUTATING_FLOWS || '0') === '1';
const SUMMARY_FILE = __ENV.K6_SUMMARY_FILE || 'perf/results/k6-deployment-summary.json';

const failedApiCalls = new Counter('failed_api_calls');
const mutatingFlowRequests = new Counter('mutating_flow_requests');
const businessErrors = new Rate('business_errors');

export const options = {
  scenarios: {
    [pressureGateScenarios.peakMix.name]: {
      executor: pressureGateScenarios.peakMix.executor,
      stages: [
        { duration: __ENV.RAMP_UP || '2m', target: Number(__ENV.PEAK_VUS || 20) },
        { duration: __ENV.HOLD || '8m', target: Number(__ENV.PEAK_VUS || 20) },
        { duration: __ENV.RAMP_DOWN || '1m', target: 0 },
      ],
      gracefulRampDown: pressureGateScenarios.peakMix.gracefulRampDown,
    },
  },
  thresholds: {
    http_req_failed: [`rate<${pressureGateThresholds.maxFailureRate}`],
    http_req_duration: [`p(95)<${pressureGateThresholds.maxP95Ms}`, `p(99)<${pressureGateThresholds.maxP99Ms}`],
    failed_api_calls: [`count<${pressureGateThresholds.maxFailedApiCalls}`],
    business_errors: [`rate<${pressureGateThresholds.maxBusinessErrorRate}`],
  },
};

export function handleSummary(data) {
  const summary = {
    generatedAt: new Date().toISOString(),
    adapter: 'k6 deployment pressure',
    profile: {
      baseUrl: BASE_URL,
      mutatingFlows: MUTATING_FLOWS,
      peakVus: Number(__ENV.PEAK_VUS || 20),
      rampUp: __ENV.RAMP_UP || '2m',
      hold: __ENV.HOLD || '8m',
      rampDown: __ENV.RAMP_DOWN || '1m',
      thinkTimeSeconds: Number(__ENV.THINK_TIME_SECONDS || 1),
    },
    thresholds: data.thresholds,
    metrics: pickMetrics(data.metrics, [
      'checks',
      'http_req_failed',
      'http_req_duration',
      'http_reqs',
      'vus_max',
      'iterations',
      'failed_api_calls',
      'mutating_flow_requests',
      'business_errors',
    ]),
  };

  return {
    stdout: `${JSON.stringify(summary, null, 2)}\n`,
    [SUMMARY_FILE]: JSON.stringify(summary, null, 2),
  };
}

export function setup() {
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: USERNAME,
    password: PASSWORD,
  }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /auth/login' },
  });
  checkApi(res, 'login');
  const data = { token: res.json('data.token') };
  if (MUTATING_FLOWS) {
    const headers = { Authorization: `Bearer ${data.token}` };
    const optionResponse = http.get(`${BASE_URL}/operational-closure/options`, {
      headers,
      tags: { name: 'GET /operational-closure/options' },
    });
    checkApi(optionResponse, 'operational closure options');
    data.writeFlow = resolvePressureWriteFlow(optionResponse.json('data'));
  }
  return data;
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };

  group(pressureGateScenarios.readFlow.name, () => {
    for (const step of pressureGateScenarios.readFlow.steps) {
      get(headers, step.path, step.label);
    }
  });

  const writeFlow = data.writeFlow || pressureGateScenarios.writeFlow;
  if (MUTATING_FLOWS && __ITER % writeFlow.everyIterations === 0) {
    group(writeFlow.name, () => {
      mutatingFlowRequests.add(1);
      for (const step of writeFlow.steps) {
        post(headers, step.path, step.body, step.label);
      }
    });
  }

  sleep(Number(__ENV.THINK_TIME_SECONDS || 1));
}

function get(headers, path, name) {
  const res = http.get(`${BASE_URL}${path}`, { headers, tags: { name } });
  checkApi(res, name);
  return res;
}

function post(headers, path, body, name) {
  const res = http.post(`${BASE_URL}${path}`, JSON.stringify(body), { headers, tags: { name } });
  checkApi(res, name);
  return res;
}

function checkApi(res, name) {
  const ok = check(res, {
    [`${name} status is 2xx`]: (r) => r.status >= 200 && r.status < 300,
    [`${name} code is 0`]: (r) => {
      try {
        return r.json('code') === 0;
      } catch {
        return false;
      }
    },
  });
  if (!ok) {
    failedApiCalls.add(1);
  }
  businessErrors.add(!ok);
}

function pickMetrics(metrics, names) {
  return Object.fromEntries(names
    .filter((name) => metrics[name])
    .map((name) => [name, metrics[name]]));
}
