import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';

const nodeCmd = process.execPath;
const tmpRoot = await mkdtemp(join(tmpdir(), 'spd-signoff-runner-'));

try {
  await runStrongDryRun();
  await runWeakDryRun();
  await runHostPrereqFailFast();
  console.log(JSON.stringify({
    ok: true,
    adapter: 'server signoff runner fixture verifier',
    scenarios: ['strong-dry-run', 'weak-dry-run', 'host-prereq-fail-fast'],
  }, null, 2));
} finally {
  await rm(tmpRoot, { recursive: true, force: true });
}

async function runHostPrereqFailFast() {
  const reportFile = join(tmpRoot, 'host-prereq-fail-fast.json');
  const callsFile = join(tmpRoot, 'fake-npm-calls.txt');
  const fakeNpm = await fakeNpmCommand(callsFile);
  const secrets = {
    SPD_DB_PASSWORD: 'StrongDatabasePassword123!',
    SPD_DB_ROOT_PASSWORD: 'StrongRootPassword123!',
    SPD_JWT_SECRET: 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789',
  };

  const result = await run(reportFile, secrets, {
    SIGNOFF_DRY_RUN: '0',
    NPM_CMD: fakeNpm,
  });
  if (result.exitCode === 0) {
    throw new Error('host prereq fail-fast unexpectedly passed');
  }

  const report = JSON.parse(await readFile(reportFile, 'utf8'));
  assert(report.dryRun === false, 'host prereq fail-fast did not run non-dry mode');
  assert(report.readyToRun === true, 'host prereq fail-fast did not accept strong inputs');
  assert(report.summary?.ok === false, 'host prereq fail-fast summary was not failed');
  assert(report.summary?.stoppedAfter === 'deployment-host-prereqs', 'host prereq fail-fast did not stop after prereqs');
  assert(report.steps.length === 1, 'host prereq fail-fast executed more than the prereq step');
  assert(report.steps[0].name === 'deployment-host-prereqs', 'host prereq fail-fast first step was not prereqs');
  assert(report.steps[0].exitCode !== 0, 'host prereq fail-fast prereq step did not fail');

  const calls = await readFile(callsFile, 'utf8');
  assert(calls.includes('verify:deployment-host-prereqs'), 'fake npm did not run host prereq step');
  assert(!calls.includes('verify:release'), 'runner did not stop before verify:release');
  assert(!calls.includes('signoff:bundle'), 'runner did not stop before bundle refresh');
}

async function runStrongDryRun() {
  const reportFile = join(tmpRoot, 'strong-run.json');
  const secrets = {
    SPD_DB_PASSWORD: 'StrongDatabasePassword123!',
    SPD_DB_ROOT_PASSWORD: 'StrongRootPassword123!',
    SPD_JWT_SECRET: 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789',
  };

  const result = await run(reportFile, secrets);
  if (result.exitCode !== 0) {
    throw new Error(`strong dry-run failed: ${result.stderr || result.stdout}`);
  }

  const text = await readFile(reportFile, 'utf8');
  for (const value of Object.values(secrets)) {
    if (text.includes(value)) {
      throw new Error('strong dry-run report recorded a secret value');
    }
  }

  const report = JSON.parse(text);
  assert(report.dryRun === true, 'strong dry-run did not record dryRun true');
  assert(report.readyToRun === true, 'strong dry-run did not become readyToRun');
  assert(report.summary?.ok === true, 'strong dry-run summary was not ok');
  assert(Array.isArray(report.steps) && report.steps.length === 0, 'strong dry-run executed runner steps');
  assert(report.requiredEnv.every((entry) => entry.valueRecorded === false), 'strong dry-run recorded a secret marker incorrectly');
  assert(report.appliedDefaults.REQUIRE_DEPLOYMENT_TOOLS === '1', 'strong dry-run missed deployment tool default');
  assert(report.appliedDefaults.REQUIRE_PRODUCTION_CONFIG === '1', 'strong dry-run missed production config default');
  assert(report.appliedDefaults.RUN_DOCKER_COMPOSE_UP === '1', 'strong dry-run missed compose default');
  assert(report.appliedDefaults.RUN_DEPLOYMENT_PRESSURE === '1', 'strong dry-run missed k6 default');
  assert(report.appliedDefaults.DEPLOYMENT_MUTATING_FLOWS === '1', 'strong dry-run missed mutating flow default');
}

async function runWeakDryRun() {
  const reportFile = join(tmpRoot, 'weak-run.json');
  const result = await run(reportFile, {
    SPD_DB_PASSWORD: 'admin123',
    SPD_DB_ROOT_PASSWORD: 'root123',
    SPD_JWT_SECRET: 'change-me-to-a-strong-64-byte-secret-before-production',
  });
  if (result.exitCode === 0) {
    throw new Error('weak dry-run unexpectedly passed');
  }

  const report = JSON.parse(await readFile(reportFile, 'utf8'));
  assert(report.readyToRun === false, 'weak dry-run did not reject unsafe inputs');
  assert(Array.isArray(report.steps) && report.steps.length === 0, 'weak dry-run executed runner steps');
  assert(report.requiredEnv.every((entry) => entry.valueRecorded === false), 'weak dry-run recorded a secret marker incorrectly');
  const failedSecrets = report.requiredEnv.filter((entry) => !entry.ok).map((entry) => entry.name).sort();
  assert(failedSecrets.join(',') === 'SPD_DB_PASSWORD,SPD_DB_ROOT_PASSWORD,SPD_JWT_SECRET', 'weak dry-run did not reject every unsafe secret');
}

function run(reportFile, envOverrides, extraEnv = {}) {
  return new Promise((resolve) => {
    const child = spawn(nodeCmd, ['scripts/run-server-signoff.mjs'], {
      cwd: process.cwd(),
      env: {
        ...process.env,
        ...envOverrides,
        ...extraEnv,
        SERVER_SIGNOFF_RUN_FILE: reportFile,
        SIGNOFF_DRY_RUN: extraEnv.SIGNOFF_DRY_RUN ?? '1',
      },
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

async function fakeNpmCommand(callsFile) {
  const ext = process.platform === 'win32' ? '.cmd' : '.sh';
  const file = join(tmpRoot, `fake-npm${ext}`);
  const content = process.platform === 'win32'
    ? `@echo off\r\necho %*>>"${callsFile}"\r\necho %* | findstr /C:"verify:deployment-host-prereqs" >nul && exit /b 2\r\nexit /b 0\r\n`
    : `#!/bin/sh\necho "$*" >> "${callsFile}"\ncase "$*" in *verify:deployment-host-prereqs*) exit 2;; esac\nexit 0\n`;
  await writeFile(file, content, 'utf8');
  return file;
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}
