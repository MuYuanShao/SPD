import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';

const nodeCmd = process.execPath;
const statusScript = join(process.cwd(), 'scripts/print-signoff-status.mjs');
const tmpRoot = await mkdtemp(join(tmpdir(), 'spd-signoff-status-'));
const results = [];

try {
  await scenario('host-prereqs-missing', { ready: false, hostPrereqOk: false, expectedFirstAction: 'fix-deployment-host-prereqs' });
  await scenario('ready', { ready: true, hostPrereqOk: true, expectedFirstAction: 'audit-ready' });
  const summary = {
    ok: results.every((result) => result.ok),
    adapter: 'server signoff status fixture verifier',
    scenarios: results.map((result) => result.name),
    results,
  };
  console.log(JSON.stringify(summary, null, 2));
  if (!summary.ok) {
    process.exitCode = 1;
  }
} finally {
  await rm(tmpRoot, { recursive: true, force: true });
}

async function scenario(name, options) {
  const dir = join(tmpRoot, name);
  await mkdir(dir, { recursive: true });
  const paths = {
    audit: join(dir, 'audit.json'),
    manifest: join(dir, 'manifest.json'),
    hostPrereq: join(dir, 'host-prereq.json'),
    status: join(dir, 'status.json'),
  };
  await writeJson(paths.audit, auditReport(options.ready, options.expectedFirstAction));
  await writeJson(paths.manifest, manifestReport(options.ready));
  await writeJson(paths.hostPrereq, hostPrereqReport(options.hostPrereqOk));
  const result = await run(nodeCmd, [statusScript], {
    ...process.env,
    SIGNOFF_AUDIT_FILE: paths.audit,
    SIGNOFF_MANIFEST_FILE: paths.manifest,
    DEPLOYMENT_HOST_PREREQ_REPORT_FILE: paths.hostPrereq,
    SIGNOFF_STATUS_FILE: paths.status,
    SIGNOFF_STATUS_ALLOW_NOT_READY: '1',
  });
  if (result.exitCode !== 0) {
    results.push({ name, ok: false, error: result.stderr || result.stdout });
    return;
  }
  const status = JSON.parse(await readFile(paths.status, 'utf8'));
  const ok = status.ready === options.ready
    && status.firstNextAction?.name === options.expectedFirstAction
    && (options.hostPrereqOk || status.hostPrereqFailures.length > 0);
  results.push({
    name,
    ok,
    ready: status.ready,
    firstNextAction: status.firstNextAction?.name,
    hostPrereqFailures: status.hostPrereqFailures.map((entry) => entry.name),
  });
}

function auditReport(ready, actionName) {
  return {
    ready,
    missing: ready ? [] : [{ name: 'deployment-host-prereqs-ok' }],
    nextActions: [
      {
        name: actionName,
        command: ready ? 'npm run audit:signoff' : 'npm run verify:deployment-host-prereqs',
        proves: ready ? 'Server Signoff is machine-audited as ready.' : 'Host prereqs are ready.',
      },
    ],
  };
}

function manifestReport(ready) {
  return {
    ready,
    missingFiles: ready ? [] : ['perf/results/k6-deployment-summary.json'],
    missingEvidence: ready ? [] : ['deployment-host-prereqs'],
  };
}

function hostPrereqReport(ok) {
  return {
    ok,
    checks: ok
      ? [{ name: 'docker-tool', level: 'info', status: 'proven' }]
      : [
          { name: 'docker-tool', level: 'error', status: 'missing', command: 'docker --version', stderrTail: 'missing' },
          { name: 'frontend-port-available', level: 'error', status: 'in-use', port: 1820, detail: 'tcp-connect-succeeded' },
        ],
  };
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
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
      resolve({ exitCode: 127, stdout, stderr: `${stderr}${error.message}` });
    });
    child.on('close', (exitCode) => {
      resolve({ exitCode: exitCode ?? 1, stdout, stderr });
    });
  });
}
