export const SERVER_SIGNOFF_AUDIT_EVIDENCE_FILE = 'perf/results/server-signoff-audit.json';

export const SERVER_SIGNOFF_EVIDENCE_NAMES = Object.freeze([
  'release-verification-report',
  'backend-tests',
  'frontend-build',
  'deployment-configuration',
  'local-signoff-verifier',
  'local-read-pressure-smoke',
  'local-read-write-pressure-smoke',
  'docker-compose-startup',
  'k6-deployment-pressure',
]);

export const SERVER_SIGNOFF_REQUIRED_FILES = Object.freeze([
  'perf/results/release-verification.json',
  'perf/results/deployment-config-check.json',
  'perf/results/deployment-config-production-check.json',
  'perf/results/deployment-host-prereqs.json',
  'perf/results/release-read-smoke.json',
  'perf/results/release-write-smoke.json',
  'perf/results/signoff-local-verification.json',
  'perf/results/k6-deployment-summary.json',
  'perf/results/server-signoff-run.json',
  SERVER_SIGNOFF_AUDIT_EVIDENCE_FILE,
  'docs/runbooks/concurrency-flow.md',
  'docs/runbooks/server-signoff.md',
  'deploy/.env.production.example',
]);

const RELEASE_EVIDENCE_DEFINITIONS = Object.freeze([
  {
    name: 'release-verification-report',
    evidence: ({ resultFile }) => resultFile,
    proves: 'Top-level Pressure Gate Adapter wrote a reviewable report.',
    proven: () => true,
  },
  {
    name: 'backend-tests',
    evidence: () => 'release report gate: backend-tests',
    proves: 'Deep Module Interfaces, including Inventory Movement, pass focused tests.',
    proven: ({ gateOk }) => gateOk('backend-tests'),
  },
  {
    name: 'frontend-build',
    evidence: () => 'release report gate: frontend-build',
    proves: 'The deployable frontend bundle can be built.',
    proven: ({ gateOk }) => gateOk('frontend-build'),
  },
  {
    name: 'deployment-configuration',
    evidence: ({ deploymentConfigReportFile }) => deploymentConfigReportFile,
    proves: 'Deployment Configuration Interface is internally consistent before server startup.',
    proven: ({ gateOk }) => gateOk('deployment-config'),
  },
  {
    name: 'local-signoff-verifier',
    evidence: ({ signoffLocalReportFile }) => signoffLocalReportFile,
    proves: 'Server Signoff audit and runner fixture Interfaces pass local verification.',
    proven: ({ gateOk }) => gateOk('signoff-local'),
  },
  {
    name: 'local-read-pressure-smoke',
    evidence: ({ readResultFile }) => readResultFile,
    proves: 'Authenticated read mix is stable under local concurrent smoke load.',
    proven: ({ gateOk }) => gateOk('node-smoke-read'),
  },
  {
    name: 'local-read-write-pressure-smoke',
    evidence: ({ writeResultFile }) => writeResultFile,
    proves: 'Operational Closure writes and Document Number generation survive concurrent smoke load.',
    proven: ({ gateOk }) => gateOk('node-smoke-write'),
  },
  {
    name: 'docker-compose-startup',
    evidence: () => 'release report gates: docker-compose-up, docker-compose-health, docker-compose-ps',
    proves: 'MySQL, backend, and frontend start through the deploy Adapter and expose /api/health.',
    proven: ({ deploymentReadiness }) => deploymentReadiness?.dockerComposeStartup === 'passed',
  },
  {
    name: 'k6-deployment-pressure',
    evidence: ({ k6SummaryFile }) => k6SummaryFile,
    proves: 'The deployment k6 Adapter passed the 200-daily-user peak profile thresholds with concurrent write flows enabled.',
    proven: ({ deploymentReadiness }) => deploymentReadiness?.k6DeploymentPressure === 'passed',
  },
]);

export function buildReleaseEvidenceChecklist(options) {
  return RELEASE_EVIDENCE_DEFINITIONS.map((definition) => ({
    name: definition.name,
    status: definition.proven(options) ? 'proven' : 'missing',
    evidence: definition.evidence(options),
    proves: definition.proves,
  }));
}

export function requiredEvidenceNames() {
  return [...SERVER_SIGNOFF_EVIDENCE_NAMES];
}

export function requiredEvidenceFilesForAuditMode(mode) {
  const files = [...SERVER_SIGNOFF_REQUIRED_FILES];
  if (mode === 'pre-audit') {
    return files.filter((path) => path !== SERVER_SIGNOFF_AUDIT_EVIDENCE_FILE);
  }
  return files;
}

export function auditModeFromSignoffRun(signoffRun) {
  return signoffRun?.summary ? 'final-audit' : 'pre-audit';
}
