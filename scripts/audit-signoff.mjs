import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import {
  auditModeFromSignoffRun,
  requiredEvidenceFilesForAuditMode,
  requiredEvidenceNames,
} from './server-signoff-evidence.mjs';

const releaseFile = process.env.RELEASE_REPORT_FILE || 'perf/results/release-verification.json';
const manifestFile = process.env.SIGNOFF_MANIFEST_FILE || 'perf/results/server-signoff-manifest.json';
const auditFile = process.env.SIGNOFF_AUDIT_FILE || 'perf/results/server-signoff-audit.json';
const signoffRunFile = process.env.SERVER_SIGNOFF_RUN_FILE || 'perf/results/server-signoff-run.json';
const k6SummaryFile = process.env.K6_SUMMARY_FILE || 'perf/results/k6-deployment-summary.json';
const hostPrereqFile = process.env.DEPLOYMENT_HOST_PREREQ_REPORT_FILE || 'perf/results/deployment-host-prereqs.json';
const allowIncomplete = process.env.ALLOW_INCOMPLETE_SIGNOFF === '1';

const release = await readJson(releaseFile);
const manifest = await readJson(manifestFile);
const signoffRun = await readJson(signoffRunFile);
const k6Summary = await readJson(k6SummaryFile);
const hostPrereq = await readJson(hostPrereqFile);
const auditMode = auditModeFromSignoffRun(signoffRun);
const checks = [];

check('release-report-readable', Boolean(release), releaseFile, 'Release verification report is readable JSON.');
check('signoff-manifest-readable', Boolean(manifest), manifestFile, 'Server Signoff manifest is readable JSON.');
check('signoff-run-readable', Boolean(signoffRun), signoffRunFile, 'Server Signoff runner report is readable JSON.');
check('deployment-host-prereqs-readable', Boolean(hostPrereq), hostPrereqFile, 'Deployment host prereq report is readable JSON.');

if (release) {
  check('release-summary-ok', release.summary?.ok === true, releaseFile, 'Required release gates did not fail.');
  check('local-gates-ok', release.summary?.localGatesOk === true, releaseFile, 'Local backend, frontend, configuration, and smoke Gates passed.');
  check('release-required-deployment-tools',
    release.environment?.requireDeploymentTools === true,
    releaseFile,
    'Release verification required Docker and k6 tooling for Server Signoff.',
  );
  check('release-ran-compose-up',
    release.environment?.runComposeUp === true,
    releaseFile,
    'Release verification attempted Docker Compose startup.',
  );
  check('release-ran-deployment-pressure',
    release.environment?.runDeploymentPressure === true,
    releaseFile,
    'Release verification attempted deployment k6 pressure.',
  );
  check('release-ran-mutating-deployment-pressure',
    String(release.environment?.deploymentPressureMutatingFlows) === '1',
    releaseFile,
    'Release verification configured k6 deployment pressure with mutating flows.',
  );
  check('deployment-readiness-ready', release.deploymentReadiness?.ready === true, releaseFile, 'Docker Compose startup and k6 deployment pressure are both present.');
  check('no-missing-deployment-evidence',
    Array.isArray(release.summary?.missingDeploymentEvidence) && release.summary.missingDeploymentEvidence.length === 0,
    releaseFile,
    'Release report has no missing deployment evidence.',
  );

  const evidenceByName = new Map((release.evidenceChecklist || []).map((item) => [item.name, item]));
  for (const name of requiredEvidenceNames()) {
    const item = evidenceByName.get(name);
    check(`evidence-${name}`, item?.status === 'proven', item?.evidence || releaseFile, item?.proves || 'Required evidence is present.');
  }

  const deploymentConfigGate = (release.gates || []).find((gate) => gate.name === 'deployment-config');
  check('deployment-config-production-mode',
    deploymentConfigGate?.result?.requireProductionConfig === true,
    deploymentConfigGate?.result ? releaseFile : 'release report gate: deployment-config',
    'Deployment Configuration Gate ran with production secret requirements enabled.',
  );
  check('deployment-config-production-ready',
    deploymentConfigGate?.result?.productionReady === true,
    deploymentConfigGate?.result ? releaseFile : 'release report gate: deployment-config',
    'Deployment Configuration Gate proved production secrets are present and safe.',
  );
}

check('k6-summary-readable', Boolean(k6Summary), k6SummaryFile, 'k6 deployment summary is readable JSON.');
if (k6Summary) {
  check('k6-mutating-flows-enabled',
    k6Summary.profile?.mutatingFlows === true,
    k6SummaryFile,
    'Deployment k6 pressure included concurrent write flows.',
  );
  check('k6-mutating-flow-requests',
    metricCount(k6Summary.metrics?.mutating_flow_requests) > 0,
    k6SummaryFile,
    'Deployment k6 pressure executed at least one concurrent write request.',
  );
}

if (hostPrereq) {
  check('deployment-host-prereqs-ok',
    hostPrereq.ok === true,
    hostPrereqFile,
    'Docker CLI, Docker Compose, Docker daemon, k6, production secrets, and deployment ports are ready before Server Signoff.',
  );
  check('deployment-host-prereqs-secret-values-not-recorded',
    Array.isArray(hostPrereq.checks)
      && hostPrereq.checks
        .filter((entry) => String(entry.name || '').startsWith('secret-'))
        .every((entry) => entry.valueRecorded === false),
    hostPrereqFile,
    'Deployment host prereq report does not record secret values.',
  );
}

if (signoffRun) {
  check('signoff-run-not-dry-run',
    signoffRun.dryRun === false,
    signoffRunFile,
    'Server Signoff was executed through the deployment runner, not only dry-run validation.',
  );
  check('signoff-run-ready-to-run',
    signoffRun.readyToRun === true,
    signoffRunFile,
    'Server Signoff runner accepted production inputs without recording secret values.',
  );
  check('signoff-run-secret-values-not-recorded',
    Array.isArray(signoffRun.requiredEnv) && signoffRun.requiredEnv.every((entry) => entry.valueRecorded === false),
    signoffRunFile,
    'Server Signoff runner did not record secret values.',
  );
  if (signoffRun.summary) {
    check('signoff-run-summary-ok',
      signoffRun.summary.ok === true,
      signoffRunFile,
      'Server Signoff runner completed without failed steps.',
    );
  }
  for (const stepName of ['deployment-host-prereqs', 'verify-release', 'signoff-bundle-before-audit']) {
    const step = (signoffRun.steps || []).find((entry) => entry.name === stepName);
    check(`signoff-run-step-${stepName}`,
      step?.exitCode === 0,
      signoffRunFile,
      `Server Signoff runner completed ${stepName}.`,
    );
  }
  if (signoffRun.finalBundleRefresh) {
    check('signoff-run-final-bundle-refresh',
      signoffRun.finalBundleRefresh.exitCode === 0,
      signoffRunFile,
      'Server Signoff runner refreshed the final bundle after writing its final summary.',
    );
  }
}

if (manifest) {
  check('manifest-ready', manifest.ready === true, manifestFile, 'Manifest marks the Server Signoff bundle ready.');
  const filesByPath = new Map((manifest.files || []).map((item) => [item.path, item]));
  for (const path of requiredEvidenceFilesForAuditMode(auditMode)) {
    const item = filesByPath.get(path);
    check(`file-${path}`, item?.present === true, path, 'Required evidence file is present in the signoff bundle.');
  }
}

const missing = checks.filter((entry) => !entry.ok);
const audit = {
  generatedAt: new Date().toISOString(),
  adapter: 'server signoff audit',
  interface: 'Server Signoff',
  releaseFile,
  manifestFile,
  auditFile,
  signoffRunFile,
  k6SummaryFile,
  hostPrereqFile,
  mode: auditMode,
  allowIncomplete,
  ready: missing.length === 0,
  checks,
  missing: missing.map((entry) => ({
    name: entry.name,
    evidence: entry.evidence,
    proves: entry.proves,
  })),
  nextActions: nextActions(release, manifest, hostPrereq),
};

await mkdir(dirname(auditFile), { recursive: true });
await writeFile(auditFile, `${JSON.stringify(audit, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({
  ready: audit.ready,
  missing: audit.missing.map((entry) => entry.name),
  nextActions: audit.nextActions,
  auditFile,
}, null, 2));

if (!audit.ready && !allowIncomplete) {
  process.exitCode = 1;
}

function check(name, ok, evidence, proves) {
  checks.push({
    name,
    status: ok ? 'proven' : 'missing',
    ok,
    evidence,
    proves,
  });
}

function metricCount(metric) {
  return Number(metric?.values?.count || metric?.count || 0);
}

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch {
    return null;
  }
}

function nextActions(releaseReport, manifestReport, hostPrereqReport) {
  const actions = [];
  const missingDeploymentEvidence = releaseReport?.summary?.missingDeploymentEvidence || [];
  if (hostPrereqReport?.ok !== true) {
    actions.push({
      name: 'fix-deployment-host-prereqs',
      command: 'start Docker Desktop/daemon, install Docker and k6 if missing, free deployment ports, set production secrets, then run npm run verify:deployment-host-prereqs',
      proves: 'Docker CLI, Docker Compose, Docker daemon, k6, production secrets, and deployment ports are ready before Server Signoff.',
    });
  }
  if (releaseReport?.environment?.requireDeploymentTools !== true
    || releaseReport?.environment?.runComposeUp !== true
    || releaseReport?.environment?.runDeploymentPressure !== true) {
    actions.push({
      name: 'run-production-release-verification',
      command: 'set production secrets and deployment URLs, then run npm run signoff:server',
      proves: 'The Server Signoff runner executed release verification in full deployment mode.',
    });
  }
  if (missingDeploymentEvidence.includes('docker-compose-startup')) {
    actions.push({
      name: 'run-docker-compose-startup',
      command: 'set production secrets and deployment URLs, then run npm run signoff:server',
      proves: 'MySQL, backend, and frontend start through the deploy Adapter and expose /api/health.',
    });
  }
  if (missingDeploymentEvidence.includes('k6-deployment-pressure')) {
    actions.push({
      name: 'run-k6-deployment-pressure',
      command: 'set production secrets and deployment URLs, then run npm run signoff:server',
      proves: 'The deployment k6 Adapter passed the 200-daily-user peak profile thresholds with concurrent write flows enabled.',
    });
  }
  const missingFiles = manifestReport?.missingFiles || [];
  if (missingFiles.length > 0) {
    actions.push({
      name: 'refresh-signoff-bundle',
      command: 'npm run signoff:bundle',
      proves: 'The Server Signoff bundle collected all available evidence files.',
    });
  }
  if (actions.length === 0) {
    actions.push({
      name: 'audit-ready',
      command: 'npm run audit:signoff',
      proves: 'Server Signoff is machine-audited as ready.',
    });
  }
  return actions;
}
