import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { tmpdir } from 'node:os';

const nodeCmd = process.execPath;
const script = 'scripts/verify-deployment-host-prereqs.mjs';
const results = [];

await scenario('strong-prereqs', async (dir) => {
  const reportFile = join(dir, 'strong.json');
  const fakeDocker = await fakeCommand(dir, 'docker', 'Docker version 27.0.0');
  const fakeK6 = await fakeCommand(dir, 'k6', 'k6 v0.50.0');
  const result = await runFixture(reportFile, {
    DOCKER_CMD: fakeDocker,
    K6_CMD: fakeK6,
    DEPLOYMENT_HOST_PREREQ_SKIP_PORTS: '1',
    SPD_DB_PASSWORD: 'StrongDatabasePassword123',
    SPD_DB_ROOT_PASSWORD: 'StrongRootPassword123',
    SPD_JWT_SECRET: 'strong-jwt-secret-value-with-more-than-forty-eight-characters',
  });
  assert(result.exitCode === 0, 'strong prereqs should pass');
  const report = await readJson(reportFile);
  assert(report.ok === true, 'strong prereq report should be ok');
  assertNoSecretValues(report, ['StrongDatabasePassword123', 'StrongRootPassword123', 'strong-jwt-secret-value']);
});

await scenario('missing-tools', async (dir) => {
  const reportFile = join(dir, 'missing-tools.json');
  const result = await runFixture(reportFile, {
    DOCKER_CMD: join(dir, 'missing-docker'),
    K6_CMD: join(dir, 'missing-k6'),
    DEPLOYMENT_HOST_PREREQ_SKIP_PORTS: '1',
    SPD_DB_PASSWORD: 'StrongDatabasePassword123',
    SPD_DB_ROOT_PASSWORD: 'StrongRootPassword123',
    SPD_JWT_SECRET: 'strong-jwt-secret-value-with-more-than-forty-eight-characters',
  });
  assert(result.exitCode !== 0, 'missing tools should fail');
  const report = await readJson(reportFile);
  assert(report.ok === false, 'missing tools report should not be ok');
  assert(hasStatus(report, 'docker-tool', 'missing'), 'docker should be missing');
  assert(hasStatus(report, 'docker-daemon', 'missing'), 'docker daemon should be missing');
  assert(hasStatus(report, 'k6-tool', 'missing'), 'k6 should be missing');
});

await scenario('weak-secrets', async (dir) => {
  const reportFile = join(dir, 'weak-secrets.json');
  const fakeDocker = await fakeCommand(dir, 'docker', 'Docker version 27.0.0');
  const fakeK6 = await fakeCommand(dir, 'k6', 'k6 v0.50.0');
  const result = await runFixture(reportFile, {
    DOCKER_CMD: fakeDocker,
    K6_CMD: fakeK6,
    DEPLOYMENT_HOST_PREREQ_SKIP_PORTS: '1',
    SPD_DB_PASSWORD: 'admin123',
    SPD_DB_ROOT_PASSWORD: 'root123',
    SPD_JWT_SECRET: 'change-me-to-a-strong-64-byte-secret-before-production',
  });
  assert(result.exitCode !== 0, 'weak secrets should fail');
  const report = await readJson(reportFile);
  assert(report.ok === false, 'weak secret report should not be ok');
  assert(report.checks.filter((check) => check.status === 'missing-or-unsafe').length === 3, 'all three secrets should be rejected');
  assertNoSecretValues(report, ['admin123', 'root123', 'change-me-to-a-strong']);
});

const summary = {
  generatedAt: new Date().toISOString(),
  adapter: 'deployment host prereqs fixtures',
  ok: results.every((result) => result.ok),
  results,
};

console.log(JSON.stringify(summary, null, 2));

if (!summary.ok) {
  process.exitCode = 1;
}

async function scenario(name, fn) {
  const dir = await mkdtemp(join(tmpdir(), `spd-host-prereq-${name}-`));
  try {
    await fn(dir);
    results.push({ name, ok: true });
  } catch (error) {
    results.push({ name, ok: false, error: error.message });
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

async function fakeCommand(dir, name, output) {
  const ext = process.platform === 'win32' ? '.cmd' : '.sh';
  const file = join(dir, `${name}${ext}`);
  const content = process.platform === 'win32'
    ? `@echo off\r\necho ${output}\r\nexit /b 0\r\n`
    : `#!/bin/sh\necho "${output}"\nexit 0\n`;
  await writeFile(file, content, 'utf8');
  return file;
}

async function runFixture(reportFile, env) {
  await mkdir(dirname(reportFile), { recursive: true });
  return run(nodeCmd, [script], {
    ...process.env,
    ...env,
    DEPLOYMENT_HOST_PREREQ_REPORT_FILE: reportFile,
  });
}

function run(command, args, env) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { cwd: process.cwd(), env, shell: false });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
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

async function readJson(file) {
  return JSON.parse(await readFile(file, 'utf8'));
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function assertNoSecretValues(report, fragments) {
  const text = JSON.stringify(report);
  for (const fragment of fragments) {
    assert(!text.includes(fragment), `report leaked secret fragment: ${fragment}`);
  }
}

function hasStatus(report, name, status) {
  return report.checks.some((check) => check.name === name && check.status === status);
}
