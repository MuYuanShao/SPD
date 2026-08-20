import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import net from 'node:net';

const isWin = process.platform === 'win32';
const dockerCmd = process.env.DOCKER_CMD || (isWin ? 'docker.exe' : 'docker');
const k6Cmd = process.env.K6_CMD || (isWin ? 'k6.exe' : 'k6');
const resultFile = process.env.DEPLOYMENT_HOST_PREREQ_REPORT_FILE || 'perf/results/deployment-host-prereqs.json';
const skipPorts = process.env.DEPLOYMENT_HOST_PREREQ_SKIP_PORTS === '1';
const host = process.env.DEPLOYMENT_HOST || '127.0.0.1';
const frontendPort = Number(process.env.SPD_HTTP_PORT || '1820');
const backendPort = Number(process.env.SPD_BACKEND_PORT || '1818');

const requiredSecrets = [
  { name: 'SPD_DB_PASSWORD', minLength: 16, unsafeValues: ['admin123', 'root123'] },
  { name: 'SPD_DB_ROOT_PASSWORD', minLength: 16, unsafeValues: ['root123', 'admin123'] },
  {
    name: 'SPD_JWT_SECRET',
    minLength: 48,
    unsafeValues: ['change-me-to-a-strong-64-byte-secret-before-production'],
  },
];

const checks = [
  await commandCheck('docker-tool', dockerCmd, ['--version'], 'Docker CLI is available for Compose startup.'),
  await commandCheck('docker-compose-tool', dockerCmd, ['compose', 'version'], 'Docker Compose plugin is available.'),
  await commandCheck('docker-daemon', dockerCmd, ['info', '--format', '{{json .ServerVersion}}'], 'Docker daemon is running and can execute Compose startup.'),
  await commandCheck('k6-tool', k6Cmd, ['version'], 'k6 CLI is available for deployment pressure.'),
  ...requiredSecrets.map(secretCheck),
];

if (skipPorts) {
  checks.push(skippedPortCheck('frontend-port-available', frontendPort));
  checks.push(skippedPortCheck('backend-port-available', backendPort));
} else {
  checks.push(await portAvailableCheck('frontend-port-available', host, frontendPort, 'Frontend HTTP port is free before Compose startup.'));
  checks.push(await portAvailableCheck('backend-port-available', host, backendPort, 'Backend HTTP port is free before Compose startup.'));
}

const errors = checks.filter((check) => check.level === 'error');
const report = {
  generatedAt: new Date().toISOString(),
  adapter: 'deployment host prereqs',
  interface: 'Server Signoff host prerequisites',
  commands: { docker: dockerCmd, k6: k6Cmd },
  ports: { host, frontendPort, backendPort, skipped: skipPorts },
  ok: errors.length === 0,
  checks,
};

await mkdir(dirname(resultFile), { recursive: true });
await writeFile(resultFile, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(report, null, 2));

if (!report.ok) {
  process.exitCode = 1;
}

async function commandCheck(name, command, args, proves) {
  const result = await run(command, args);
  return {
    name,
    level: result.exitCode === 0 ? 'info' : 'error',
    status: result.exitCode === 0 ? 'proven' : 'missing',
    proves,
    command: [command, ...args].join(' '),
    exitCode: result.exitCode,
    stdoutTail: tail(result.stdout),
    stderrTail: tail(result.stderr),
  };
}

function secretCheck(secret) {
  const value = process.env[secret.name] || '';
  const present = value.length > 0;
  const longEnough = value.length >= secret.minLength;
  const safeValue = !secret.unsafeValues.includes(value);
  const ok = present && longEnough && safeValue;
  return {
    name: `secret-${secret.name.toLowerCase()}`,
    envName: secret.name,
    level: ok ? 'info' : 'error',
    status: ok ? 'proven' : 'missing-or-unsafe',
    proves: `${secret.name} is present and safe for production signoff.`,
    present,
    minLength: secret.minLength,
    longEnough,
    safeValue,
    valueRecorded: false,
  };
}

function skippedPortCheck(name, port) {
  return {
    name,
    level: 'warning',
    status: 'skipped',
    proves: `Port ${port} availability was not checked.`,
    port,
  };
}

async function portAvailableCheck(name, host, port, proves) {
  if (!Number.isInteger(port) || port <= 0 || port > 65535) {
    return { name, level: 'error', status: 'invalid', proves, host, port };
  }
  const result = await canConnect(host, port);
  return {
    name,
    level: result.open ? 'error' : 'info',
    status: result.open ? 'in-use' : 'available',
    proves,
    host,
    port,
    detail: result.detail,
  };
}

function canConnect(host, port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port });
    socket.setTimeout(1200);
    socket.once('connect', () => {
      socket.destroy();
      resolve({ open: true, detail: 'tcp-connect-succeeded' });
    });
    socket.once('timeout', () => {
      socket.destroy();
      resolve({ open: false, detail: 'tcp-connect-timeout' });
    });
    socket.once('error', (error) => {
      resolve({ open: false, detail: error.code || error.message });
    });
  });
}

function run(command, args) {
  return new Promise((resolve) => {
    const usesCmdShim = process.platform === 'win32' && /\.(cmd|bat)$/i.test(command);
    const child = spawn(
      usesCmdShim ? 'cmd.exe' : command,
      usesCmdShim ? ['/d', '/s', '/c', quoteCommand(command, args)] : args,
      { cwd: process.cwd(), env: process.env, shell: false },
    );
    let stdout = '';
    let stderr = '';
    child.stdout?.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr?.on('data', (chunk) => {
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

function quoteCommand(command, args) {
  return [command, ...args].map((part) => {
    const value = String(part);
    return /[\s&()^|<>"]/.test(value) ? `"${value.replace(/"/g, '\\"')}"` : value;
  }).join(' ');
}

function tail(text, max = 800) {
  return text.length > max ? text.slice(-max) : text;
}
