import { mkdir, readFile, writeFile, stat } from 'node:fs/promises';
import { dirname } from 'node:path';
import { SERVER_SIGNOFF_REQUIRED_FILES } from './server-signoff-evidence.mjs';

const bundleFile = process.env.SIGNOFF_BUNDLE_FILE || 'perf/results/server-signoff-bundle.json';
const manifestFile = process.env.SIGNOFF_MANIFEST_FILE || 'perf/results/server-signoff-manifest.json';

const bundle = {
  generatedAt: new Date().toISOString(),
  adapter: 'server signoff bundle',
  files: [],
  summary: {
    ready: false,
    missingFiles: [],
    missingEvidence: [],
  },
};

for (const path of SERVER_SIGNOFF_REQUIRED_FILES) {
  const entry = await readEvidence(path);
  bundle.files.push(entry);
}

const release = getJson('perf/results/release-verification.json');
const hostPrereq = getJson('perf/results/deployment-host-prereqs.json');
if (release) {
  bundle.releaseSummary = release.summary;
  bundle.deploymentReadiness = release.deploymentReadiness;
  bundle.evidenceChecklist = release.evidenceChecklist;
  bundle.hostPrereqs = hostPrereq
    ? {
        ok: hostPrereq.ok === true,
        checks: (hostPrereq.checks || []).map((check) => ({
          name: check.name,
          status: check.status,
          level: check.level,
          valueRecorded: check.valueRecorded,
        })),
      }
    : null;
  bundle.summary.ready = release.deploymentReadiness?.ready === true
    && Array.isArray(release.summary?.missingDeploymentEvidence)
    && release.summary.missingDeploymentEvidence.length === 0
    && Array.isArray(release.evidenceChecklist)
    && release.evidenceChecklist.every((item) => item.status === 'proven')
    && hostPrereq?.ok === true;
  bundle.summary.missingEvidence = (release.evidenceChecklist || [])
    .filter((item) => item.status !== 'proven')
    .map((item) => item.name);
  if (hostPrereq?.ok !== true) {
    bundle.summary.missingEvidence.push('deployment-host-prereqs');
  }
}

bundle.summary.missingFiles = bundle.files
  .filter((entry) => !entry.present)
  .map((entry) => entry.path);

await mkdir(dirname(bundleFile), { recursive: true });
await writeFile(bundleFile, `${JSON.stringify(bundle, null, 2)}\n`, 'utf8');

const manifest = {
  generatedAt: bundle.generatedAt,
  bundleFile,
  ready: bundle.summary.ready,
  files: bundle.files.map((entry) => ({
    path: entry.path,
    present: entry.present,
    bytes: entry.bytes,
    json: entry.json,
  })),
  missingFiles: bundle.summary.missingFiles,
  missingEvidence: bundle.summary.missingEvidence,
};
await writeFile(manifestFile, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

console.log(JSON.stringify(manifest, null, 2));

function getJson(path) {
  return bundle.files.find((entry) => entry.path === path)?.parsed;
}

async function readEvidence(path) {
  try {
    const [metadata, text] = await Promise.all([
      stat(path),
      readFile(path, 'utf8'),
    ]);
    const entry = {
      path,
      present: true,
      bytes: metadata.size,
      updatedAt: metadata.mtime.toISOString(),
      json: path.endsWith('.json'),
    };
    if (entry.json) {
      try {
        entry.parsed = JSON.parse(text);
      } catch (error) {
        entry.parseError = error instanceof Error ? error.message : String(error);
      }
    } else {
      entry.textPreview = text.slice(0, 2000);
    }
    return entry;
  } catch (error) {
    return {
      path,
      present: false,
      error: error instanceof Error ? error.message : String(error),
    };
  }
}
