import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawn } from 'node:child_process';
import {
  requiredEvidenceFilesForAuditMode,
  requiredEvidenceNames,
  SERVER_SIGNOFF_AUDIT_EVIDENCE_FILE,
} from './server-signoff-evidence.mjs';

const nodeCmd = process.execPath;
const tmpRoot = await mkdtemp(join(tmpdir(), 'spd-signoff-audit-'));

try {
  await runScenario('pre-audit', {
    finalMode: false,
    includeAuditInManifest: false,
    expectedReady: true,
    mustNotMiss: [`file-${SERVER_SIGNOFF_AUDIT_EVIDENCE_FILE}`],
  });

  await runScenario('final-audit-ready', {
    finalMode: true,
    includeAuditInManifest: true,
    expectedReady: true,
    mustHaveChecks: ['signoff-run-summary-ok', 'signoff-run-final-bundle-refresh'],
  });

  await runScenario('final-audit-read-only-k6', {
    finalMode: true,
    includeAuditInManifest: true,
    expectedReady: false,
    k6Summary: {
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
    },
    mustMiss: ['k6-mutating-flows-enabled', 'k6-mutating-flow-requests'],
  });

  await runScenario('final-audit-host-prereq-failed', {
    finalMode: true,
    includeAuditInManifest: true,
    expectedReady: false,
    hostPrereq: {
      ok: false,
      checks: [
        { name: 'docker-tool', status: 'missing' },
        { name: 'secret-spd_db_password', valueRecorded: false },
      ],
    },
    mustMiss: ['deployment-host-prereqs-ok'],
    mustHaveNextActions: ['fix-deployment-host-prereqs'],
  });

  console.log(JSON.stringify({
    ok: true,
    adapter: 'server signoff audit fixture verifier',
    scenarios: ['pre-audit', 'final-audit-ready', 'final-audit-read-only-k6', 'final-audit-host-prereq-failed'],
  }, null, 2));
} finally {
  await rm(tmpRoot, { recursive: true, force: true });
}

async function runScenario(name, options) {
  const dir = join(tmpRoot, name);
  await mkdir(dir, { recursive: true });
  const paths = {
    release: join(dir, 'release.json'),
    manifest: join(dir, 'manifest.json'),
    audit: join(dir, 'audit.json'),
    signoffRun: join(dir, 'run.json'),
    k6Summary: join(dir, 'k6.json'),
    hostPrereq: join(dir, 'host-prereq.json'),
  };

  await writeJson(paths.release, releaseReport());
  await writeJson(paths.signoffRun, signoffRunReport(options.finalMode));
  await writeJson(paths.k6Summary, options.k6Summary || k6Summary());
  await writeJson(paths.hostPrereq, options.hostPrereq || hostPrereq());
  await writeJson(paths.manifest, manifestReport(options.includeAuditInManifest));

  const result = await run(nodeCmd, ['scripts/audit-signoff.mjs'], {
    ...process.env,
    RELEASE_REPORT_FILE: paths.release,
    SIGNOFF_MANIFEST_FILE: paths.manifest,
    SIGNOFF_AUDIT_FILE: paths.audit,
    SERVER_SIGNOFF_RUN_FILE: paths.signoffRun,
    K6_SUMMARY_FILE: paths.k6Summary,
    DEPLOYMENT_HOST_PREREQ_REPORT_FILE: paths.hostPrereq,
    ALLOW_INCOMPLETE_SIGNOFF: options.expectedReady ? '0' : '1',
  });

  if (result.exitCode !== 0) {
    throw new Error(`${name} audit process failed: ${result.stderr || result.stdout}`);
  }

  const audit = JSON.parse(await readFile(paths.audit, 'utf8'));
  if (audit.ready !== options.expectedReady) {
    throw new Error(`${name} ready expected ${options.expectedReady}, got ${audit.ready}`);
  }

  for (const missingName of options.mustNotMiss || []) {
    if (audit.missing.some((entry) => entry.name === missingName)) {
      throw new Error(`${name} unexpectedly missed ${missingName}`);
    }
  }

  for (const missingName of options.mustMiss || []) {
    if (!audit.missing.some((entry) => entry.name === missingName)) {
      throw new Error(`${name} did not miss ${missingName}`);
    }
  }

  const checks = new Map(audit.checks.map((entry) => [entry.name, entry]));
  for (const checkName of options.mustHaveChecks || []) {
    if (checks.get(checkName)?.status !== 'proven') {
      throw new Error(`${name} did not prove ${checkName}`);
    }
  }

  const nextActionNames = new Set((audit.nextActions || []).map((entry) => entry.name));
  for (const actionName of options.mustHaveNextActions || []) {
    if (!nextActionNames.has(actionName)) {
      throw new Error(`${name} did not suggest next action ${actionName}`);
    }
  }
}

function releaseReport() {
  const evidenceChecklist = [
    ...requiredEvidenceNames(),
  ].map((name) => ({
    name,
    status: 'proven',
    evidence: `fixture:${name}`,
    proves: `fixture proves ${name}`,
  }));

  return {
    summary: {
      ok: true,
      localGatesOk: true,
      missingDeploymentEvidence: [],
    },
    environment: {
      requireDeploymentTools: true,
      runComposeUp: true,
      runDeploymentPressure: true,
      deploymentPressureMutatingFlows: '1',
    },
    deploymentReadiness: {
      ready: true,
      dockerComposeStartup: 'passed',
      k6DeploymentPressure: 'passed',
    },
    evidenceChecklist,
    gates: [
      {
        name: 'deployment-config',
        result: {
          requireProductionConfig: true,
          productionReady: true,
        },
      },
    ],
  };
}

function signoffRunReport(finalMode) {
  const report = {
    dryRun: false,
    readyToRun: true,
    requiredEnv: [
      { name: 'SPD_DB_PASSWORD', valueRecorded: false },
      { name: 'SPD_DB_ROOT_PASSWORD', valueRecorded: false },
      { name: 'SPD_JWT_SECRET', valueRecorded: false },
    ],
    steps: [
      { name: 'verify-release', exitCode: 0 },
      { name: 'deployment-host-prereqs', exitCode: 0 },
      { name: 'signoff-bundle-before-audit', exitCode: 0 },
    ],
  };
  if (finalMode) {
    report.summary = { ok: true, failed: [] };
    report.finalBundleRefresh = { exitCode: 0 };
  }
  return report;
}

function k6Summary() {
  return {
    profile: {
      mutatingFlows: true,
    },
    metrics: {
      mutating_flow_requests: {
        values: {
          count: 3,
        },
      },
    },
  };
}

function hostPrereq() {
  return {
    ok: true,
    checks: [
      { name: 'docker-tool', status: 'proven' },
      { name: 'docker-compose-tool', status: 'proven' },
      { name: 'k6-tool', status: 'proven' },
      { name: 'secret-spd_db_password', valueRecorded: false },
      { name: 'secret-spd_db_root_password', valueRecorded: false },
      { name: 'secret-spd_jwt_secret', valueRecorded: false },
      { name: 'frontend-port-available', status: 'available' },
      { name: 'backend-port-available', status: 'available' },
    ],
  };
}

function manifestReport(includeAudit) {
  const mode = includeAudit ? 'final-audit' : 'pre-audit';
  const files = requiredEvidenceFilesForAuditMode(mode);

  return {
    ready: true,
    files: files.map((path) => ({
      path,
      present: true,
    })),
    missingFiles: [],
    missingEvidence: [],
  };
}

async function writeJson(path, value) {
  await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function run(command, args, env) {
  return new Promise((resolve) => {
    const child = spawn(command, args, {
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
