import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';

const auditFile = process.env.SIGNOFF_AUDIT_FILE || 'perf/results/server-signoff-audit.json';
const manifestFile = process.env.SIGNOFF_MANIFEST_FILE || 'perf/results/server-signoff-manifest.json';
const hostPrereqFile = process.env.DEPLOYMENT_HOST_PREREQ_REPORT_FILE || 'perf/results/deployment-host-prereqs.json';
const outputFile = process.env.SIGNOFF_STATUS_FILE || '';

const audit = await readJson(auditFile);
const manifest = await readJson(manifestFile);
const hostPrereq = await readJson(hostPrereqFile);

const missing = unique([
  ...(audit?.missing || []).map((entry) => entry.name),
  ...(manifest?.missingEvidence || []),
  ...(manifest?.missingFiles || []).map((path) => `file-${path}`),
]);
const nextActions = audit?.nextActions || [];
const hostPrereqFailures = (hostPrereq?.checks || [])
  .filter((check) => check.level === 'error' || ['missing', 'in-use', 'invalid', 'missing-or-unsafe'].includes(check.status))
  .map((check) => ({
    name: check.name,
    status: check.status,
    proves: check.proves,
    command: check.command,
    port: check.port,
    detail: check.detail || check.stderrTail,
  }));

const status = {
  generatedAt: new Date().toISOString(),
  adapter: 'server signoff status',
  interface: 'Server Signoff',
  ready: audit?.ready === true && manifest?.ready === true && hostPrereq?.ok === true,
  auditReady: audit?.ready === true,
  manifestReady: manifest?.ready === true,
  hostPrereqsReady: hostPrereq?.ok === true,
  missing,
  firstNextAction: nextActions[0] || null,
  nextActions,
  hostPrereqFailures,
  files: {
    auditFile,
    manifestFile,
    hostPrereqFile,
  },
};

if (outputFile) {
  await mkdir(dirname(outputFile), { recursive: true });
  await writeFile(outputFile, `${JSON.stringify(status, null, 2)}\n`, 'utf8');
}

console.log(JSON.stringify(status, null, 2));

if (!status.ready) {
  process.exitCode = process.env.SIGNOFF_STATUS_ALLOW_NOT_READY === '1' ? 0 : 1;
}

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, 'utf8'));
  } catch {
    return null;
  }
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}
