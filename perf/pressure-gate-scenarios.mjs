export const pressureGateThresholds = {
  maxFailureRate: 0.01,
  maxP95Ms: 800,
  maxP99Ms: 1500,
  maxFailedApiCalls: 10,
  maxBusinessErrorRate: 0.05,
};

export const pressureGateScenarios = {
  peakMix: {
    name: 'daily_200_peak_mix',
    executor: 'ramping-vus',
    gracefulRampDown: '30s',
  },
  readFlow: {
    name: 'workspace-read-flow',
    steps: [
      {
        domainStep: 'Workspace reads',
        method: 'GET',
        path: '/auth/me',
        label: 'GET /auth/me',
      },
      {
        domainStep: 'Workspace reads',
        method: 'GET',
        path: '/modules',
        label: 'GET /modules',
      },
      {
        domainStep: 'Inventory reads',
        method: 'GET',
        path: '/inventory/balances?page=1&size=20',
        label: 'GET /inventory/balances',
      },
      {
        domainStep: 'Inventory reads',
        method: 'GET',
        path: '/inventory/events?page=1&size=20',
        label: 'GET /inventory/events',
      },
      {
        domainStep: 'Workspace reads',
        method: 'GET',
        path: '/purchase-orders?page=1&size=20',
        label: 'GET /purchase-orders',
      },
      {
        domainStep: 'Workspace reads',
        method: 'GET',
        path: '/receiving-orders?page=1&size=20',
        label: 'GET /receiving-orders',
      },
      {
        domainStep: 'Operational Closure reads',
        method: 'GET',
        path: '/operational-closure/overview',
        label: 'GET /operational-closure/overview',
      },
      {
        domainStep: 'Operational Closure reads',
        method: 'GET',
        path: '/operational-closure/lists/shortage-reminder?page=1&size=20',
        label: 'GET /operational-closure/lists/shortage-reminder',
      },
    ],
  },
  writeFlow: {
    name: 'concurrent-write-flow',
    everyIterations: 5,
    steps: [
      {
        domainStep: 'Shortage generation',
        method: 'POST',
        path: '/operational-closure/shortage/generate',
        label: 'POST /operational-closure/shortage/generate',
        body: {
          minQty: 1,
          currentQty: 0,
          replenishQty: 1,
        },
      },
    ],
  },
};

export function resolvePressureWriteFlow(optionData) {
  const deptName = optionData?.departments?.find((item) => item?.deptName)?.deptName;
  const productCode = optionData?.products?.find((item) => item?.productCode)?.productCode;
  if (!deptName || !productCode) {
    throw new Error('Operational Closure options must include an enabled department and product for write pressure.');
  }
  return {
    ...pressureGateScenarios.writeFlow,
    steps: pressureGateScenarios.writeFlow.steps.map((step) => ({
      ...step,
      body: {
        ...step.body,
        deptName,
        productCode,
      },
    })),
  };
}

export const pressureGateEvidenceExpectations = [
  {
    gate: 'node-smoke-read',
    adapter: 'perf/spd-concurrency-smoke.mjs',
    expects: ['failureRate <= 0.01', 'p95Ms <= 800', 'firstFailures empty'],
  },
  {
    gate: 'node-smoke-write',
    adapter: 'perf/spd-concurrency-smoke.mjs',
    expects: ['failureRate <= 0.01', 'p95Ms <= 800', 'shortage generation succeeds'],
  },
  {
    gate: 'k6-deployment-pressure',
    adapter: 'perf/k6-spd-concurrency.js',
    expects: [
      'http_req_failed rate < 0.01',
      'http_req_duration p(95) < 800',
      'http_req_duration p(99) < 1500',
      'failed_api_calls count < 10',
      'business_errors rate < 0.05',
      'mutating_flow_requests count > 0 when mutating flows are required',
    ],
  },
];
