import { mkdir, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { spawn } from 'node:child_process';

const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const resultFile = process.env.SIGNOFF_LOCAL_REPORT_FILE || 'perf/results/signoff-local-verification.json';

const report = {
  generatedAt: new Date().toISOString(),
  adapter: 'server signoff local verifier',
  interface: 'Server Signoff',
  resultFile,
  steps: [],
};

await runStep('verify-signoff-audit-fixtures', ['run', 'verify:signoff-audit-fixtures']);
await runStep('verify-signoff-runner-fixtures', ['run', 'verify:signoff-runner-fixtures']);
await runStep('verify-signoff-bundle-fixtures', ['run', 'verify:signoff-bundle-fixtures']);
await runStep('verify-signoff-status-fixtures', ['run', 'verify:signoff-status-fixtures']);
await runStep('verify-release-fixtures', ['run', 'verify:release-fixtures']);
await runStep('verify-deployment-host-prereqs-fixtures', ['run', 'verify:deployment-host-prereqs-fixtures']);

const failed = report.steps.filter((step) => step.exitCode !== 0);
report.summary = {
  ok: failed.length === 0,
  failed: failed.map((step) => step.name),
};

await writeReport();
console.log(JSON.stringify(report.summary, null, 2));

if (!report.summary.ok) {
  process.exitCode = 1;
}

async function runStep(name, args) {
  const entry = {
    name,
    command: [npmCmd, ...args].join(' '),
    startedAt: new Date().toISOString(),
  };
  report.steps.push(entry);
  console.log(`\n[signoff-local] ${name}`);
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
    const spawnCommand = process.platform === 'win32' ? 'cmd.exe' : command;
    const spawnArgs = process.platform === 'win32'
      ? ['/d', '/s', '/c', quoteCommand(command, args)]
      : args;
    const child = spawn(spawnCommand, spawnArgs, {
      cwd: process.cwd(),
      env: process.env,
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

async function writeReport() {
  await mkdir(dirname(resultFile), { recursive: true });
  await writeFile(resultFile, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}

function tail(text, max = 4000) {
  return text.length > max ? text.slice(-max) : text;
}
