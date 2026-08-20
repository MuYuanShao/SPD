import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import {
  requiredEvidenceNames,
  SERVER_SIGNOFF_AUDIT_EVIDENCE_FILE,
} from './server-signoff-evidence.mjs';

const nodeCmd = process.execPath;
const bundleScript = join(process.cwd(), 'scripts/create-signoff-bundle.mjs');
const tmpRoot = await mkdtemp(join(tmpdir(), 'spd-signoff-bundle-'));
const scenarios = [];

try {
  await scenario('ready-with-host-prereqs', { hostPrereqOk: true, expectedReady: true, mustNotMiss: ['deployment-host-prereqs'] });
  await scenario('host-prereqs-not-ready', { hostPrereqOk: false, expectedReady: false, mustMiss: ['deployment-host-prereqs'] });
  console.log(JSON.stringify({
    ok: scenarios.every((entry) => entry.ok),
    adapter: 'server signoff bundle fixture verifier',
    scenarios: scenarios.map((entry) => entry.name),
    results: scenarios,
  }, null, 2));
  if (scenarios.some((entry) => !entry.ok)) {
    process.exitCode = 1;
  }
} finally {
  await rm(tmpRoot, { recursive: true, force: true });
}

async function scenario(name, options) {
  const dir = join(tmpRoot, name);
  await mkdir(join(dir, 'perf/results'), { recursive: true });
  await mkdir(join(dir, 'docs/runbooks'), { recursive: true });
  await mkdir(join(dir, 'deploy'), { recursive: true });
  await seedEvidence(dir, options.hostPrereqOk);
  const bundleFile = join(dir, 'perf/results/server-signoff-bundle.json');
  const manifestFile = join(dir, 'perf/results/server-signoff-manifest.json');

  const result = await run(nodeCmd, [bundleScript], {
    ...process.env,
    SIGNOFF_BUNDLE_FILE: bundleFile,
    SIGNOFF_MANIFEST_FILE: manifestFile,
  }, dir);
  if (result.exitCode !== 0) {
    scenarios.push({ name, ok: false, error: result.stderr || result.stdout });
    return;
  }

  const manifest = JSON.parse(await readFile(manifestFile, 'utf8'));
  const missing = manifest.missingEvidence || [];
  let ok = manifest.ready === options.expectedReady;
  for (const item of options.mustMiss || []) {
    ok = ok && missing.includes(item);
  }
  for (const item of options.mustNotMiss || []) {
    ok = ok && !missing.includes(item);
  }
  scenarios.push({ name, ok, ready: manifest.ready, missingEvidence: missing });
}

async function seedEvidence(dir, hostPrereqOk) {
  const writeJson = (path, value) => writeFile(join(dir, path), `${JSON.stringify(value, null, 2)}\n`, 'utf8');
  await writeJson('perf/results/release-verification.json', releaseReport());
  await writeJson('perf/results/deployment-config-check.json', { ok: true });
  await writeJson('perf/results/deployment-config-production-check.json', { ok: true, productionReady: true });
  await writeJson('perf/results/deployment-host-prereqs.json', hostPrereq(hostPrereqOk));
  await writeJson('perf/results/release-read-smoke.json', { failures: 0 });
  await writeJson('perf/results/release-write-smoke.json', { failures: 0 });
  await writeJson('perf/results/signoff-local-verification.json', { summary: { ok: true } });
  await writeJson('perf/results/k6-deployment-summary.json', { profile: { mutatingFlows: true } });
  await writeJson('perf/results/server-signoff-run.json', { summary: { ok: true } });
  await writeJson(SERVER_SIGNOFF_AUDIT_EVIDENCE_FILE, { ready: true });
  await writeFile(join(dir, 'docs/runbooks/concurrency-flow.md'), '# fixture\n', 'utf8');
  await writeFile(join(dir, 'docs/runbooks/server-signoff.md'), '# fixture\n', 'utf8');
  await writeFile(join(dir, 'deploy/.env.production.example'), 'SPD_DB_PASSWORD=\n', 'utf8');
}

function releaseReport() {
  const evidenceChecklist = requiredEvidenceNames().map((name) => ({ name, status: 'proven' }));
  return {
    summary: { ok: true, localGatesOk: true, missingDeploymentEvidence: [] },
    deploymentReadiness: { ready: true, dockerComposeStartup: 'passed', k6DeploymentPressure: 'passed' },
    evidenceChecklist,
  };
}

function hostPrereq(ok) {
  return {
    ok,
    checks: [
      { name: 'docker-tool', status: ok ? 'proven' : 'missing', level: ok ? 'info' : 'error' },
      { name: 'secret-spd_db_password', status: 'proven', valueRecorded: false },
    ],
  };
}

function run(command, args, env, cwd) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { cwd, env, shell: false });
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
