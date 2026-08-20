import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { delimiter, dirname } from 'node:path';

const npmCmd = process.env.NPM_CMD || (process.platform === 'win32' ? 'npm.cmd' : 'npm');
const dryRun = process.env.SIGNOFF_DRY_RUN === '1';
const resultFile = process.env.SERVER_SIGNOFF_RUN_FILE || 'perf/results/server-signoff-run.json';

const requiredSecrets = [
  {
    name: 'SPD_DB_PASSWORD',
    minLength: 16,
    unsafeValues: ['admin123', 'root123'],
  },
  {
    name: 'SPD_DB_ROOT_PASSWORD',
    minLength: 16,
    unsafeValues: ['root123', 'admin123'],
  },
  {
    name: 'SPD_JWT_SECRET',
    minLength: 48,
    unsafeValues: ['change-me-to-a-strong-64-byte-secret-before-production'],
  },
];

const signoffDefaults = {
  REQUIRE_DEPLOYMENT_TOOLS: '1',
  REQUIRE_PRODUCTION_CONFIG: '1',
  RUN_DOCKER_COMPOSE_UP: '1',
  RUN_DEPLOYMENT_PRESSURE: '1',
  DEPLOYMENT_MUTATING_FLOWS: '1',
  DEPLOYMENT_BASE_URL: 'http://localhost:1820/api',
  DEPLOYMENT_HEALTH_URL: 'http://localhost:1820/api/health',
  K6_SUMMARY_FILE: 'perf/results/k6-deployment-summary.json',
};

const env = normalizeEnv({ ...process.env, ...missingDefaults(process.env, signoffDefaults) });
const report = {
  generatedAt: new Date().toISOString(),
  adapter: 'server signoff runner',
  interface: 'Server Signoff',
  dryRun,
  resultFile,
  requiredEnv: requiredSecrets.map(secretCheck),
  appliedDefaults: Object.fromEntries(Object.keys(signoffDefaults).map((name) => [name, env[name]])),
  steps: [],
};

const invalidSecrets = report.requiredEnv.filter((entry) => !entry.ok);
if (invalidSecrets.length > 0) {
  report.readyToRun = false;
  report.error = `Missing or unsafe production environment variables: ${invalidSecrets.map((entry) => entry.name).join(', ')}`;
  await writeReport();
  console.error(report.error);
  process.exitCode = 1;
} else if (dryRun) {
  report.readyToRun = true;
  report.summary = {
    ok: true,
    message: 'Dry run only. Required production variables are present and Server Signoff defaults are resolved.',
  };
  await writeReport();
  console.log(JSON.stringify(report.summary, null, 2));
} else {
  report.readyToRun = true;
  const hostPrereqStep = await runStep('deployment-host-prereqs', ['run', 'verify:deployment-host-prereqs']);
  if (hostPrereqStep.exitCode !== 0) {
    report.summary = {
      ok: false,
      failed: ['deployment-host-prereqs'],
      stoppedAfter: 'deployment-host-prereqs',
      message: 'Deployment host prerequisites failed. Server Signoff stopped before long-running release verification.',
    };
    await writeReport();
    console.log(JSON.stringify(report.summary, null, 2));
    process.exitCode = 1;
  } else {
    await runStep('verify-release', ['run', 'verify:release']);
    await runStep('signoff-bundle-before-audit', ['run', 'signoff:bundle']);
    await runStep('audit-signoff', ['run', 'audit:signoff']);
    await runStep('signoff-bundle-after-audit', ['run', 'signoff:bundle']);
    const failed = report.steps.filter((step) => step.exitCode !== 0);
    report.summary = {
      ok: failed.length === 0,
      failed: failed.map((step) => step.name),
    };
    await writeReport();
    console.log(JSON.stringify(report.summary, null, 2));
    const finalization = await finalizeSignoffEvidence();
    if (failed.length > 0) {
      process.exitCode = 1;
    } else if (finalization.exitCode !== 0) {
      process.exitCode = 1;
    }
  }
}

async function runStep(name, args) {
  const entry = {
    name,
    command: [npmCmd, ...args].join(' '),
    startedAt: new Date().toISOString(),
  };
  report.steps.push(entry);
  console.log(`\n[server-signoff] ${name}`);
  const started = Date.now();
  const result = await run(npmCmd, args);
  entry.finishedAt = new Date().toISOString();
  entry.durationMs = Date.now() - started;
  entry.exitCode = result.exitCode;
  entry.stdoutTail = tail(result.stdout);
  entry.stderrTail = tail(result.stderr);
  await writeReport();
  return entry;
}

function run(command, args) {
  return new Promise((resolve) => {
    const usesCmdShim = process.platform === 'win32' && /\.(cmd|bat)$/i.test(command);
    const child = spawn(usesCmdShim ? 'cmd.exe' : command, usesCmdShim ? ['/d', '/s', '/c', quoteCommand(command, args)] : args, {
      cwd: process.cwd(),
      env,
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

async function finalizeSignoffEvidence() {
  const refresh = await runFinalReportStep(
    'finalBundleRefresh',
    'refresh-final-bundle',
    ['run', 'signoff:bundle'],
    'Final Server Signoff bundle refresh failed after runner summary was written.',
  );
  const audit = await runFinalReportStep(
    'finalAudit',
    'audit-final-signoff',
    ['run', 'audit:signoff'],
    'Final Server Signoff audit failed after runner summary was written.',
  );
  const capture = await runFinalReportStep(
    'finalBundleCapture',
    'capture-final-signoff-bundle',
    ['run', 'signoff:bundle'],
    'Final Server Signoff bundle capture failed after final audit was written.',
  );
  return {
    exitCode: [refresh, audit, capture].every((entry) => entry.exitCode === 0) ? 0 : 1,
  };
}

async function runFinalReportStep(propertyName, name, args, failureMessage) {
  console.log(`\n[server-signoff] ${name}`);
  const entry = {
    name,
    command: [npmCmd, ...args].join(' '),
    startedAt: new Date().toISOString(),
  };
  report[propertyName] = entry;
  await writeReport();
  const started = Date.now();
  const result = await run(npmCmd, args);
  entry.finishedAt = new Date().toISOString();
  entry.durationMs = Date.now() - started;
  entry.exitCode = result.exitCode;
  entry.stdoutTail = tail(result.stdout);
  entry.stderrTail = tail(result.stderr);
  await writeReport();
  if (result.exitCode !== 0) {
    console.error(failureMessage);
  }
  return entry;
}

async function writeReport() {
  await mkdir(dirname(resultFile), { recursive: true });
  await writeFile(resultFile, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}

function missingDefaults(sourceEnv, defaults) {
  return Object.fromEntries(Object.entries(defaults).filter(([name]) => !sourceEnv[name]));
}

function secretCheck(secret) {
  const value = env[secret.name] || '';
  const present = value.length > 0;
  const longEnough = value.length >= secret.minLength;
  const safeValue = !secret.unsafeValues.includes(value);
  return {
    name: secret.name,
    present,
    minLength: secret.minLength,
    longEnough,
    safeValue,
    ok: present && longEnough && safeValue,
    valueRecorded: false,
  };
}

function normalizeEnv(sourceEnv) {
  if (process.platform !== 'win32') {
    return sourceEnv;
  }
  const normalized = {};
  const seen = new Map();
  for (const [key, value] of Object.entries(sourceEnv)) {
    const lower = key.toLowerCase();
    if (seen.has(lower)) {
      delete normalized[seen.get(lower)];
    }
    const canonicalKey = lower === 'path' ? 'Path' : key;
    normalized[canonicalKey] = value;
    seen.set(lower, canonicalKey);
  }
  prependPathDirs(normalized, [dirname(normalized.DOCKER_CMD || ''), dirname(normalized.K6_CMD || '')]);
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

function tail(text, max = 4000) {
  return text.length > max ? text.slice(-max) : text;
}
