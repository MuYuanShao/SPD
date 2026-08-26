import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import { resolvePressureWriteFlow } from '../perf/pressure-gate-scenarios.mjs';

const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const tmpRoot = await mkdtemp(join(tmpdir(), 'spd-release-fixtures-'));

try {
  verifyPressureWriteFlowFixture();
  await runCredentialScenario('credential-default', {
    env: {
      USERNAME: 'os-user-should-not-leak',
      PASSWORD: 'os-password-should-not-leak',
    },
    expectedUsername: 'admin',
  });

  await runCredentialScenario('credential-override', {
    env: {
      USERNAME: 'os-user-should-not-leak',
      PASSWORD: 'os-password-should-not-leak',
      DEPLOYMENT_USERNAME: 'deployment-user',
      DEPLOYMENT_PASSWORD: 'deployment-password',
    },
    expectedUsername: 'deployment-user',
  });

  await runK6Scenario('k6-read-only-is-not-ready', {
    profile: {
      mutatingFlows: false,
    },
    metrics: {
      mutating_flow_requests: {
        values: {
          count: 0,
        },
      },
    },
  });

  await runK6Scenario('k6-zero-write-requests-is-not-ready', {
    profile: {
      mutatingFlows: true,
    },
    metrics: {
      mutating_flow_requests: {
        values: {
          count: 0,
        },
      },
    },
  });

  console.log(JSON.stringify({
    ok: true,
    adapter: 'release verification fixture verifier',
    scenarios: [
      'credential-default',
      'credential-override',
      'k6-read-only-is-not-ready',
      'k6-zero-write-requests-is-not-ready',
      'pressure-write-flow-resolves-current-options',
      'pressure-write-flow-rejects-missing-options',
    ],
  }, null, 2));
} finally {
  await rm(tmpRoot, { recursive: true, force: true });
}

function verifyPressureWriteFlowFixture() {
  const flow = resolvePressureWriteFlow({
    departments: [{ deptName: 'Fixture Department' }],
    products: [{ productCode: 'FIXTURE-PRODUCT' }],
  });
  const body = flow.steps[0]?.body;
  if (body?.deptName !== 'Fixture Department' || body?.productCode !== 'FIXTURE-PRODUCT') {
    throw new Error('pressure-write-flow-resolves-current-options did not inject current option data');
  }

  let rejected = false;
  try {
    resolvePressureWriteFlow({ departments: [], products: [] });
  } catch (error) {
    rejected = error instanceof Error && error.message.includes('enabled department and product');
  }
  if (!rejected) {
    throw new Error('pressure-write-flow-rejects-missing-options did not fail fast');
  }
}

async function runCredentialScenario(name, options) {
  const releaseFile = join(tmpRoot, `${name}-release.json`);
  const signoffLocalFile = join(tmpRoot, `${name}-signoff-local.json`);
  const deploymentConfigFile = join(tmpRoot, `${name}-deployment-config.json`);

  const result = await run(npmCmd, ['run', 'verify:release'], {
    ...process.env,
    SPD_USERNAME: '',
    SPD_PASSWORD: '',
    SPD_E2E_USERNAME: '',
    SPD_E2E_PASSWORD: '',
    ...options.env,
    RELEASE_REPORT_FILE: releaseFile,
    SIGNOFF_LOCAL_REPORT_FILE: signoffLocalFile,
    DEPLOYMENT_CONFIG_REPORT_FILE: deploymentConfigFile,
    SKIP_BACKEND_TESTS: '1',
    SKIP_FRONTEND_BUILD: '1',
    SKIP_DEPLOYMENT_CONFIG: '1',
    SKIP_SIGNOFF_LOCAL: '1',
    SKIP_NODE_SMOKE: '1',
    REQUIRE_DEPLOYMENT_TOOLS: '0',
    RUN_DOCKER_COMPOSE_UP: '0',
    RUN_DEPLOYMENT_PRESSURE: '0',
  });

  if (result.exitCode !== 0) {
    throw new Error(`${name} release fixture failed: ${result.stderr || result.stdout}`);
  }

  const report = JSON.parse(await readFile(releaseFile, 'utf8'));
  if (report.environment?.deploymentUsername !== options.expectedUsername) {
    throw new Error(`${name} deploymentUsername expected ${options.expectedUsername}, got ${report.environment?.deploymentUsername}`);
  }
  if (report.environment?.deploymentUsername === options.env.USERNAME) {
    throw new Error(`${name} leaked operating-system USERNAME into deployment pressure credentials`);
  }
}

async function runK6Scenario(name, summary) {
  const releaseFile = join(tmpRoot, `${name}-release.json`);
  const signoffLocalFile = join(tmpRoot, `${name}-signoff-local.json`);
  const deploymentConfigFile = join(tmpRoot, `${name}-deployment-config.json`);
  const k6SummaryFile = join(tmpRoot, `${name}-k6-summary.json`);
  const fakeBin = join(tmpRoot, `${name}-bin`);
  await mkdir(fakeBin, { recursive: true });
  const fakeK6 = await writeFakeK6(fakeBin, summary);

  const result = await run(npmCmd, ['run', 'verify:release'], {
    ...process.env,
    RELEASE_REPORT_FILE: releaseFile,
    SIGNOFF_LOCAL_REPORT_FILE: signoffLocalFile,
    DEPLOYMENT_CONFIG_REPORT_FILE: deploymentConfigFile,
    K6_SUMMARY_FILE: k6SummaryFile,
    K6_CMD: fakeK6,
    REQUIRE_DEPLOYMENT_TOOLS: '0',
    RUN_DOCKER_COMPOSE_UP: '0',
    RUN_DOCKER_COMPOSE_CONFIG: '0',
    RUN_DEPLOYMENT_PRESSURE: '1',
    SKIP_BACKEND_TESTS: '1',
    SKIP_FRONTEND_BUILD: '1',
    SKIP_DEPLOYMENT_CONFIG: '1',
    SKIP_SIGNOFF_LOCAL: '1',
    SKIP_NODE_SMOKE: '1',
  });

  if (result.exitCode !== 0) {
    throw new Error(`${name} release fixture failed: ${result.stderr || result.stdout}`);
  }

  const report = JSON.parse(await readFile(releaseFile, 'utf8'));
  if (report.deploymentReadiness?.k6DeploymentPressure !== 'passed-read-only') {
    throw new Error(`${name} k6DeploymentPressure expected passed-read-only, got ${report.deploymentReadiness?.k6DeploymentPressure}`);
  }
  if (!report.summary?.missingDeploymentEvidence?.includes('k6-deployment-pressure')) {
    throw new Error(`${name} did not keep k6-deployment-pressure missing`);
  }
  const item = (report.evidenceChecklist || []).find((entry) => entry.name === 'k6-deployment-pressure');
  if (item?.status !== 'missing') {
    throw new Error(`${name} evidenceChecklist did not mark k6 deployment pressure missing`);
  }
}

async function writeFakeK6(dir, summary) {
  const summaryLiteral = JSON.stringify(summary);
  const jsScript = join(dir, 'fake-k6.mjs');
  await writeFile(jsScript, [
    'import { writeFileSync } from "node:fs";',
    'if (process.argv[2] === "version") {',
    '  process.exit(0);',
    '}',
    `writeFileSync(process.env.K6_SUMMARY_FILE, JSON.stringify(${summaryLiteral}, null, 2));`,
    '',
  ].join('\n'), 'utf8');

  if (process.platform === 'win32') {
    const script = join(dir, 'fake-k6.cmd');
    await writeFile(script, [
      '@echo off',
      `node "${jsScript}" %*`,
      'exit /b %ERRORLEVEL%',
      '',
    ].join('\r\n'), 'utf8');
    return script;
  }

  const script = join(dir, 'fake-k6.sh');
  await writeFile(script, [
    '#!/usr/bin/env sh',
    `node "${jsScript}" "$@"`,
    '',
  ].join('\n'), { encoding: 'utf8', mode: 0o755 });
  return script;
}

function run(command, args, env) {
  return new Promise((resolve) => {
    const usesCmdShim = process.platform === 'win32' && /\.(cmd|bat)$/i.test(command);
    const spawnCommand = usesCmdShim ? 'cmd.exe' : command;
    const spawnArgs = usesCmdShim
      ? ['/d', '/s', '/c', quoteCommand(command, args)]
      : args;
    const child = spawn(spawnCommand, spawnArgs, {
      cwd: process.cwd(),
      env,
      shell: false,
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    child.on('error', (error) => {
      resolve({ exitCode: 127, stdout, stderr: `${stderr}${error.message}` });
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
