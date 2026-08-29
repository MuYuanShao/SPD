import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { delimiter, dirname } from 'node:path';
import { spawn } from 'node:child_process';
import { buildReleaseEvidenceChecklist } from './server-signoff-evidence.mjs';

const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const mvnCmd = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';
const dockerCmd = process.env.DOCKER_CMD || (process.platform === 'win32' ? 'docker.exe' : 'docker');
const k6Cmd = process.env.K6_CMD || (process.platform === 'win32' ? 'k6.exe' : 'k6');

const resultFile = process.env.RELEASE_REPORT_FILE || 'perf/results/release-verification.json';
const deploymentConfigReportFile = process.env.DEPLOYMENT_CONFIG_REPORT_FILE || 'perf/results/deployment-config-check.json';
const signoffLocalReportFile = process.env.SIGNOFF_LOCAL_REPORT_FILE || 'perf/results/signoff-local-verification.json';
const requireDeploymentTools = process.env.REQUIRE_DEPLOYMENT_TOOLS === '1';
const runDeploymentPressure = process.env.RUN_DEPLOYMENT_PRESSURE === '1';
const runComposeUp = process.env.RUN_DOCKER_COMPOSE_UP === '1';
const runComposeConfig = process.env.RUN_DOCKER_COMPOSE_CONFIG !== '0';
const deploymentHealthUrl = process.env.DEPLOYMENT_HEALTH_URL || 'http://127.0.0.1:1820/api/health';
const k6SummaryFile = process.env.K6_SUMMARY_FILE || 'perf/results/k6-deployment-summary.json';
const deploymentPressureMutatingFlows = process.env.DEPLOYMENT_MUTATING_FLOWS || process.env.MUTATING_FLOWS || '1';
const localUsername = process.env.SPD_USERNAME || process.env.SPD_E2E_USERNAME || '';
const localPassword = process.env.SPD_PASSWORD || process.env.SPD_E2E_PASSWORD || '';
const deploymentUsername = process.env.DEPLOYMENT_USERNAME || localUsername;
const deploymentPassword = process.env.DEPLOYMENT_PASSWORD || localPassword;

const report = {
  generatedAt: new Date().toISOString(),
  pressureGateInterface: 'release verification',
  environment: {
    node: process.version,
    platform: process.platform,
    arch: process.arch,
    baseUrl: process.env.BASE_URL || 'http://127.0.0.1:1818/api',
    requireDeploymentTools,
    runComposeUp,
    runDeploymentPressure,
    deploymentHealthUrl,
    k6SummaryFile,
    deploymentPressureMutatingFlows,
    deploymentUsername,
    deploymentConfigReportFile,
    signoffLocalReportFile,
  },
  gates: [],
  deploymentReadiness: {
    dockerComposeStartup: 'not-run',
    k6DeploymentPressure: 'not-run',
    ready: false,
    reason: 'Docker Compose startup and k6 deployment pressure evidence are required for server deployment readiness.',
  },
};

await gate('text-encoding', npmCmd, ['run', 'verify:text-encoding'], {
  skip: process.env.SKIP_TEXT_ENCODING === '1',
});

await gate('vue-sfc-structure', npmCmd, ['run', 'verify:vue-sfc-structure'], {
  skip: process.env.SKIP_VUE_SFC_STRUCTURE === '1',
});

await gate('rbac-permissions', npmCmd, ['run', 'verify:rbac-permissions'], {
  skip: process.env.SKIP_RBAC_PERMISSIONS === '1',
});

await gate('flyway-migration-integrity', npmCmd, ['run', 'verify:flyway-migrations']);

await gate('backend-tests', mvnCmd, ['test'], {
  skip: process.env.SKIP_BACKEND_TESTS === '1',
  cwd: 'backend',
});

await gate('frontend-build', npmCmd, ['--prefix', 'frontend', 'run', 'build'], {
  skip: process.env.SKIP_FRONTEND_BUILD === '1',
});

await gate('deployment-config', npmCmd, ['run', 'verify:deployment-config'], {
  skip: process.env.SKIP_DEPLOYMENT_CONFIG === '1',
  env: {
    DEPLOYMENT_CONFIG_REPORT_FILE: deploymentConfigReportFile,
    REQUIRE_PRODUCTION_CONFIG: '1',
  },
  resultJson: deploymentConfigReportFile,
});

await gate('signoff-local', npmCmd, ['run', 'verify:signoff-local'], {
  skip: process.env.SKIP_SIGNOFF_LOCAL === '1',
  env: {
    SIGNOFF_LOCAL_REPORT_FILE: signoffLocalReportFile,
  },
  resultJson: signoffLocalReportFile,
});

if (!runComposeUp) {
  await runNodeSmokeGates();
}

const dockerVersion = await gate('docker-tool', dockerCmd, ['--version'], {
  optional: !requireDeploymentTools,
});
const k6Version = await gate('k6-tool', k6Cmd, ['version'], {
  optional: !requireDeploymentTools,
});

if (dockerVersion.ok && runComposeConfig) {
  await gate('docker-compose-config', dockerCmd, ['compose', '-f', 'deploy/docker-compose.yml', 'config', '--quiet'], {
    optional: !requireDeploymentTools,
  });
}

if (runComposeUp && dockerVersion.ok) {
  const composeUp = await gate('docker-compose-up', dockerCmd, ['compose', '-f', 'deploy/docker-compose.yml', 'up', '--build', '-d'], {
    optional: !requireDeploymentTools,
  });
  if (composeUp.ok) {
    const health = await httpGate('docker-compose-health', deploymentHealthUrl, {
      optional: !requireDeploymentTools,
      attempts: Number(process.env.DEPLOYMENT_HEALTH_ATTEMPTS || 30),
      delayMs: Number(process.env.DEPLOYMENT_HEALTH_DELAY_MS || 2000),
    });
    report.deploymentReadiness.dockerComposeStartup = health.ok ? 'passed' : 'failed';
    if (health.ok) {
      await gate('docker-compose-ps', dockerCmd, ['compose', '-f', 'deploy/docker-compose.yml', 'ps', '--format', 'json'], {
        optional: !requireDeploymentTools,
      });
    }
  } else {
    report.deploymentReadiness.dockerComposeStartup = 'failed';
  }
} else if (runComposeUp) {
  report.deploymentReadiness.dockerComposeStartup = 'missing-docker';
}

if (runComposeUp) {
  await runNodeSmokeGates({
    skip: report.deploymentReadiness.dockerComposeStartup !== 'passed',
    baseUrl: process.env.DEPLOYMENT_BASE_URL || process.env.BASE_URL || 'http://127.0.0.1:1820/api',
  });
}

if (runDeploymentPressure && k6Version.ok) {
  const k6Gate = await gate('k6-deployment-pressure', k6Cmd, ['run', 'perf/k6-spd-concurrency.js'], {
    optional: !requireDeploymentTools,
    env: {
      BASE_URL: process.env.DEPLOYMENT_BASE_URL || process.env.BASE_URL || 'http://127.0.0.1:1820/api',
      USERNAME: deploymentUsername,
      PASSWORD: deploymentPassword,
      MUTATING_FLOWS: deploymentPressureMutatingFlows,
      K6_SUMMARY_FILE: k6SummaryFile,
    },
    resultJson: k6SummaryFile,
  });
  if (
    k6Gate.ok
    && k6Gate.result?.profile?.mutatingFlows === true
    && metricCount(k6Gate.result?.metrics?.mutating_flow_requests) > 0
  ) {
    report.deploymentReadiness.k6DeploymentPressure = 'passed';
  } else if (k6Gate.ok) {
    report.deploymentReadiness.k6DeploymentPressure = 'passed-read-only';
  } else {
    report.deploymentReadiness.k6DeploymentPressure = 'failed';
  }
} else if (!runDeploymentPressure) {
  report.deploymentReadiness.k6DeploymentPressure = 'not-run';
} else {
  report.deploymentReadiness.k6DeploymentPressure = 'missing-k6';
}

const failedRequired = report.gates.filter((entry) => entry.required && !entry.ok);
const localGatesOk = report.gates
  .filter((entry) => ['text-encoding', 'vue-sfc-structure', 'rbac-permissions', 'backend-tests', 'frontend-build', 'deployment-config', 'signoff-local', 'node-smoke-read', 'node-smoke-write'].includes(entry.name))
  .every((entry) => entry.ok && !entry.skipped);
report.evidenceChecklist = buildReleaseEvidenceChecklist({
  resultFile,
  deploymentConfigReportFile,
  signoffLocalReportFile,
  readResultFile: process.env.READ_RESULT_FILE || 'perf/results/release-read-smoke.json',
  writeResultFile: process.env.WRITE_RESULT_FILE || 'perf/results/release-write-smoke.json',
  k6SummaryFile,
  gateOk,
  deploymentReadiness: report.deploymentReadiness,
});

report.summary = {
  ok: failedRequired.length === 0,
  localGatesOk,
  failedRequired: failedRequired.map((entry) => entry.name),
  missingDeploymentEvidence: [
    report.deploymentReadiness.dockerComposeStartup === 'passed' ? null : 'docker-compose-startup',
    report.deploymentReadiness.k6DeploymentPressure === 'passed' ? null : 'k6-deployment-pressure',
  ].filter(Boolean),
};

if (
  report.deploymentReadiness.dockerComposeStartup === 'passed'
  && report.deploymentReadiness.k6DeploymentPressure === 'passed'
) {
  report.deploymentReadiness.ready = true;
  report.deploymentReadiness.reason = 'Docker Compose startup and k6 deployment pressure evidence are present.';
}

await mkdir(dirname(resultFile), { recursive: true });
await writeFile(resultFile, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(`\nRelease verification report written to ${resultFile}`);
console.log(JSON.stringify(report.summary, null, 2));

if (!report.summary.ok) {
  process.exitCode = 1;
}

function gateOk(name) {
  const entry = report.gates.find((gateEntry) => gateEntry.name === name);
  return entry?.ok === true && !entry.skipped;
}

async function gate(name, command, args, options = {}) {
  const entry = {
    name,
    command: [command, ...args].join(' '),
    required: !options.optional && !options.skip,
    optional: Boolean(options.optional),
    skipped: Boolean(options.skip),
    ok: false,
    startedAt: new Date().toISOString(),
  };
  report.gates.push(entry);

  if (options.skip) {
    entry.ok = true;
    entry.finishedAt = new Date().toISOString();
    entry.durationMs = 0;
    return entry;
  }

  const started = Date.now();
  console.log(`\n[verify-release] ${name}`);
  const result = await run(command, args, options.env, options.cwd);
  entry.finishedAt = new Date().toISOString();
  entry.durationMs = Date.now() - started;
  entry.exitCode = result.exitCode;
  entry.ok = result.exitCode === 0;
  entry.stdoutTail = tail(result.stdout);
  entry.stderrTail = tail(result.stderr);

  if (options.resultJson && entry.ok) {
    entry.result = await readJson(options.resultJson);
  }

  if (options.optional && !entry.ok) {
    entry.reason = 'optional gate unavailable or failed in this environment';
  }

  return entry;
}

async function httpGate(name, url, options = {}) {
  const entry = {
    name,
    url,
    required: !options.optional,
    optional: Boolean(options.optional),
    skipped: false,
    ok: false,
    startedAt: new Date().toISOString(),
  };
  report.gates.push(entry);

  const started = Date.now();
  console.log(`\n[verify-release] ${name}`);
  const attempts = options.attempts ?? 10;
  const delayMs = options.delayMs ?? 1000;
  let lastError = '';
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url);
      const body = await response.text();
      entry.lastStatus = response.status;
      entry.stdoutTail = tail(body);
      if (response.ok && body.includes('"code":0')) {
        entry.ok = true;
        break;
      }
      lastError = `status=${response.status} body=${body.slice(0, 300)}`;
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    if (attempt < attempts) {
      await delay(delayMs);
    }
  }

  entry.finishedAt = new Date().toISOString();
  entry.durationMs = Date.now() - started;
  if (!entry.ok) {
    entry.stderrTail = lastError;
  }
  if (options.optional && !entry.ok) {
    entry.reason = 'optional gate unavailable or failed in this environment';
  }
  return entry;
}

function run(command, args, extraEnv = {}, cwd = process.cwd()) {
  return new Promise((resolve) => {
    const usesCmdShim = process.platform === 'win32' && /\.(cmd|bat)$/i.test(command);
    const spawnCommand = usesCmdShim ? 'cmd.exe' : command;
    const spawnArgs = usesCmdShim
      ? ['/d', '/s', '/c', quoteCommand(command, args)]
      : args;
    const child = spawn(spawnCommand, spawnArgs, {
      cwd,
      env: normalizeEnv({ ...process.env, ...extraEnv }),
      shell: false,
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => {
      const text = chunk.toString();
      stdout += text;
      process.stdout.write(text);
    });
    child.stderr.on('data', (chunk) => {
      const text = chunk.toString();
      stderr += text;
      process.stderr.write(text);
    });
    child.on('error', (error) => {
      stderr += error.message;
      resolve({ exitCode: 127, stdout, stderr });
    });
    child.on('close', (exitCode) => {
      resolve({ exitCode: exitCode ?? 1, stdout, stderr });
    });
  });
}

function quoteCommand(command, args) {
  return [command, ...args].map((part) => {
    const value = String(part);
    return /[\s&()^|<>"]/.test(value) ? `"${value.replace(/"/g, '\\"')}"` : value;
  }).join(' ');
}

function normalizeEnv(env) {
  if (process.platform !== 'win32') {
    return env;
  }

  const normalized = {};
  const seen = new Map();
  for (const [key, value] of Object.entries(env)) {
    const lower = key.toLowerCase();
    if (seen.has(lower)) {
      delete normalized[seen.get(lower)];
    }
    const canonicalKey = lower === 'path' ? 'Path' : key;
    normalized[canonicalKey] = value;
    seen.set(lower, canonicalKey);
  }
  prependPathDirs(normalized, [dirname(dockerCmd), dirname(k6Cmd)]);
  return normalized;
}

function prependPathDirs(env, dirs) {
  const pathKey = process.platform === 'win32' ? 'Path' : 'PATH';
  const current = env[pathKey] || '';
  const currentParts = current.split(delimiter).filter(Boolean);
  const additions = dirs
    .filter((dir) => dir && dir !== '.')
    .filter((dir) => !currentParts.some((part) => part.toLowerCase() === dir.toLowerCase()));
  if (additions.length > 0) {
    env[pathKey] = [...additions, ...currentParts].join(delimiter);
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function metricCount(metric) {
  return Number(metric?.values?.count || metric?.count || 0);
}

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch (error) {
    return { error: error instanceof Error ? error.message : String(error) };
  }
}

function tail(text, max = 4000) {
  return text.length > max ? text.slice(-max) : text;
}

async function runNodeSmokeGates(options = {}) {
  const skip = process.env.SKIP_NODE_SMOKE === '1' || Boolean(options.skip);
  const baseUrl = options.baseUrl || process.env.BASE_URL || 'http://127.0.0.1:1818/api';
  await gate('node-smoke-read', npmCmd, ['run', 'perf:smoke:read'], {
    skip,
    env: {
      RESULT_FILE: process.env.READ_RESULT_FILE || 'perf/results/release-read-smoke.json',
      BASE_URL: baseUrl,
      SPD_USERNAME: localUsername,
      SPD_PASSWORD: localPassword,
    },
    resultJson: process.env.READ_RESULT_FILE || 'perf/results/release-read-smoke.json',
  });

  await gate('node-smoke-write', npmCmd, ['run', 'perf:smoke:write'], {
    skip,
    env: {
      RESULT_FILE: process.env.WRITE_RESULT_FILE || 'perf/results/release-write-smoke.json',
      BASE_URL: baseUrl,
      SPD_USERNAME: localUsername,
      SPD_PASSWORD: localPassword,
    },
    resultJson: process.env.WRITE_RESULT_FILE || 'perf/results/release-write-smoke.json',
  });
}
