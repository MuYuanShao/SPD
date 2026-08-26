import { performance } from 'node:perf_hooks';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { pressureGateScenarios, pressureGateThresholds, resolvePressureWriteFlow } from './pressure-gate-scenarios.mjs';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:1818/api';
const username = process.env.SPD_USERNAME || process.env.SPD_E2E_USERNAME || 'admin';
const password = process.env.SPD_PASSWORD || process.env.SPD_E2E_PASSWORD || 'admin123';
const vus = Number(process.env.VUS || 20);
const durationSeconds = Number(process.env.DURATION_SECONDS || 60);
const thinkTimeMs = Number(process.env.THINK_TIME_MS || 250);
const mutatingFlows = process.env.MUTATING_FLOWS === '1';
const resultFile = process.env.RESULT_FILE;
const readFlow = pressureGateScenarios.readFlow.steps;

const samples = [];
const failures = [];

const token = await login();
const writeFlow = mutatingFlows ? await loadWriteFlow(token) : pressureGateScenarios.writeFlow;
const deadline = performance.now() + durationSeconds * 1000;
await Promise.all(Array.from({ length: vus }, (_, index) => virtualUser(index + 1, token, deadline)));

const sorted = samples.toSorted((a, b) => a.durationMs - b.durationMs);
const summary = {
  baseUrl,
  vus,
  durationSeconds,
  mutatingFlows,
  requests: samples.length,
  failures: failures.length,
  failureRate: samples.length === 0 ? 1 : Number((failures.length / samples.length).toFixed(4)),
  p50Ms: percentile(sorted, 50),
  p95Ms: percentile(sorted, 95),
  p99Ms: percentile(sorted, 99),
  maxMs: sorted.length ? Math.round(sorted.at(-1).durationMs) : 0,
  byEndpoint: endpointSummary(samples),
  firstFailures: failures.slice(0, 10),
};

console.log(JSON.stringify(summary, null, 2));

if (resultFile) {
  await mkdir(dirname(resultFile), { recursive: true });
  await writeFile(resultFile, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
}

if (summary.failureRate > Number(process.env.MAX_FAILURE_RATE || pressureGateThresholds.maxFailureRate)) {
  process.exitCode = 1;
}
if (summary.p95Ms > Number(process.env.MAX_P95_MS || pressureGateThresholds.maxP95Ms)) {
  process.exitCode = 1;
}

async function login() {
  const result = await request('POST', '/auth/login', undefined, { username, password });
  const token = result.body?.data?.token;
  if (!token) {
    throw new Error(`Login did not return a token: status=${result.response?.status ?? 0} body=${JSON.stringify(result.body)}`);
  }
  return token;
}

async function loadWriteFlow(token) {
  const result = await request('GET', '/operational-closure/options', token);
  if (!result.response?.ok || result.body?.code !== 0) {
    throw new Error(
      `Operational Closure options unavailable: status=${result.response?.status ?? 0} code=${result.body?.code ?? 'unknown'}`,
    );
  }
  return resolvePressureWriteFlow(result.body?.data);
}

async function virtualUser(vu, token, deadline) {
  let iteration = 0;
  while (performance.now() < deadline) {
    iteration += 1;
    for (const { method, path } of readFlow) {
      if (performance.now() >= deadline) {
        break;
      }
      await request(method, path, token);
    }
    if (mutatingFlows && iteration % writeFlow.everyIterations === 0) {
      for (const step of writeFlow.steps) {
        await request(step.method, step.path, token, step.body);
      }
    }
    await delay(thinkTimeMs + (vu % 5) * 25);
  }
}

async function request(method, path, token, body) {
  const started = performance.now();
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  let response;
  let parsed;
  let error;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    parsed = await response.json().catch(() => undefined);
    if (!response.ok || parsed?.code !== 0) {
      error = `${method} ${path} status=${response.status} code=${parsed?.code} message=${parsed?.message ?? ''}`;
    }
  } catch (caught) {
    error = `${method} ${path} ${caught instanceof Error ? caught.message : String(caught)}`;
  }
  const sample = {
    method,
    path,
    durationMs: performance.now() - started,
    status: response?.status ?? 0,
    ok: !error,
  };
  samples.push(sample);
  if (error) {
    failures.push({ ...sample, error });
  }
  return { response, body: parsed };
}

function percentile(sortedSamples, percentileValue) {
  if (sortedSamples.length === 0) {
    return 0;
  }
  const index = Math.ceil((percentileValue / 100) * sortedSamples.length) - 1;
  return Math.round(sortedSamples[Math.max(0, Math.min(index, sortedSamples.length - 1))].durationMs);
}

function endpointSummary(allSamples) {
  const endpoints = new Map();
  for (const sample of allSamples) {
    const key = `${sample.method} ${sample.path}`;
    const entry = endpoints.get(key) ?? { count: 0, failures: 0, durations: [] };
    entry.count += 1;
    entry.failures += sample.ok ? 0 : 1;
    entry.durations.push(sample.durationMs);
    endpoints.set(key, entry);
  }
  return Object.fromEntries([...endpoints.entries()].map(([key, entry]) => {
    const durations = entry.durations.toSorted((a, b) => a - b).map((duration) => ({ durationMs: duration }));
    return [key, {
      count: entry.count,
      failures: entry.failures,
      p95Ms: percentile(durations, 95),
    }];
  }));
}
