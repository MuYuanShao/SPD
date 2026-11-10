import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { readdir, readFile, stat } from 'node:fs/promises';
import { basename, isAbsolute, join, relative, resolve } from 'node:path';

const releaseRoot = resolve('output/offline-bundle');
const releaseName = /^spd-server-offline-windows-x64-(\d+\.\d+\.\d+)-([0-9a-f]{12})((?:-dirty)?(?:-reused)?)-(\d{8}T\d{9}Z)$/;
const releases = process.env.SPD_OFFLINE_BUNDLE_DIR ? [] :
  (await readdir(releaseRoot, { withFileTypes: true }).catch(error => {
    if (error.code === 'ENOENT') return [];
    throw error;
  })).filter(entry => entry.isDirectory() && releaseName.test(entry.name))
    .map(entry => entry.name).sort((a, b) => b.slice(-19).localeCompare(a.slice(-19)));
const bundleRoot = resolve(process.env.SPD_OFFLINE_BUNDLE_DIR || join(releaseRoot, releases[0] || 'spd-server'));
const required = [
  'app/app.jar',
  'app/web/index.html',
  'runtime/jdk-17/bin/java.exe',
  'service/HospitalSPD.exe',
  'service/HospitalSPD.xml',
  'installer/install.ps1',
  'installer/common.ps1',
  'installer/mysql.ps1',
  'installer/vendor.json',
  'installer/manage.ps1',
  'installer/legacy-repair.ps1',
  'installer/invoke-utf8-script.ps1',
  '安装SPD.bat',
  '启动SPD.bat',
  '停止SPD.bat',
  '重启SPD.bat',
  '打开SPD系统.bat',
  'SPD诊断.bat',
  '升级SPD.bat',
  '卸载SPD.bat',
  '数据库兼容升级.bat',
  'licenses/WinSW-LICENSE.txt',
  'licenses/MySQL-LICENSE.txt',
  'licenses/第三方组件说明.txt',
  'licenses/mysql-8.4.11.tar.gz',
  'vendor/mysql-8.4.11-winx64.zip',
  'prerequisites/vc_redist.x64.exe',
  'MIGRATION_VERSION.txt',
  'VERSION.txt',
  'SHA256SUMS.txt',
  '部署说明.txt',
];

const findings = [];
const release = basename(bundleRoot).match(releaseName);
if (release) {
  const version = await readFile(join(bundleRoot, 'VERSION.txt'), 'utf8').catch(() => '');
  const revision = await readFile(join(bundleRoot, 'BUILD_REVISION.txt'), 'utf8').catch(() => '');
  if (version.trim() !== release[1]) findings.push('release-version-mismatch');
  const expectedRevision = new RegExp(`^${release[2]}(?:[0-9a-f]{28}|[0-9a-f]{52})${release[3] || ''}$`);
  if (!expectedRevision.test(revision.trim())) findings.push('build-revision-mismatch');
}
const forbiddenSecrets = [
  'admin' + '123',
  'root' + '123',
  'change-me-to-a-strong',
  'hnzlth' + '@' + '20260620',
].map((value) => value.toLowerCase());
for (const path of required) {
  try {
    if (!(await stat(join(bundleRoot, path))).isFile()) findings.push(`not-file:${path}`);
  } catch {
    findings.push(`missing:${path}`);
  }
}

const files = await walk(bundleRoot).catch(() => []);
try {
  const artifacts = JSON.parse(await readFile(join(bundleRoot, 'installer/vendor.json'), 'utf8'));
  for (const artifact of artifacts) {
    if (isAbsolute(artifact.path) || artifact.path.split(/[\\/]/).includes('..') || !/^[a-f0-9]{64}$/i.test(artifact.sha256)) {
      findings.push('unsafe-vendor-manifest');
      continue;
    }
    if (await hashFileContent(join(bundleRoot, artifact.path)) !== artifact.sha256.toLowerCase()) findings.push(`vendor-hash-mismatch:${artifact.path}`);
  }
} catch {
  findings.push('invalid-or-missing-vendor-artifact');
}
for (const file of files) {
  const relativePath = relative(bundleRoot, file).replaceAll('\\', '/');
  if (/\.(sql|java|class)$/i.test(relativePath)) findings.push(`forbidden:${relativePath}`);
  if (/(^|\/)config\.(env|properties|ya?ml)$/i.test(relativePath)) findings.push(`secret-config:${relativePath}`);
  if (/\.(bat|cmd|ps1|xml|txt|properties|ya?ml|json)$/i.test(relativePath)) {
    const text = await readFile(file, 'utf8').catch(() => '');
    if (forbiddenSecrets.some((secret) => text.toLowerCase().includes(secret))) {
      findings.push(`fixed-secret:${relativePath}`);
    }
  }
}

const batchFiles = files.filter((file) => file.toLowerCase().endsWith('.bat'));
for (const file of batchFiles) {
  const relativePath = relative(bundleRoot, file).replaceAll('\\', '/');
  const text = await readFile(file, 'utf8').catch(() => '');
  if (text.includes('-ScriptArguments')) findings.push(`legacy-bootstrap-arguments:${relativePath}`);
  if (!text.includes('invoke-utf8-script.ps1')) findings.push(`missing-utf8-bootstrap:${relativePath}`);
}

const hashFile = join(bundleRoot, 'SHA256SUMS.txt');
if (!findings.some((item) => item === 'missing:SHA256SUMS.txt')) {
  const lines = (await readFile(hashFile, 'utf8')).split(/\r?\n/).filter(Boolean);
  const covered = new Set();
  for (const line of lines) {
    const match = line.match(/^([a-f0-9]{64})  (.+)$/i);
    if (!match) {
      findings.push('invalid-hash-manifest');
      continue;
    }
    const manifestPath = match[2].replaceAll('\\', '/');
    if (isAbsolute(manifestPath) || manifestPath.split('/').includes('..') || covered.has(manifestPath)) {
      findings.push(`unsafe-hash-path:${manifestPath}`);
      continue;
    }
    covered.add(manifestPath);
    const target = join(bundleRoot, manifestPath);
    const digest = await hashFileContent(target).catch(() => null);
    if (!digest || digest !== match[1].toLowerCase()) {
      findings.push(`hash-mismatch:${match[2]}`);
    }
  }
  for (const file of files) {
    const relativePath = relative(bundleRoot, file).replaceAll('\\', '/');
    if (relativePath !== 'SHA256SUMS.txt' && !covered.has(relativePath)) findings.push(`hash-missing:${relativePath}`);
  }
}

const result = { ok: findings.length === 0, bundleRoot, fileCount: files.length, findings };
console.log(JSON.stringify(result, null, 2));
if (!result.ok) process.exitCode = 1;

async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const nested = await Promise.all(entries.map((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  }));
  return nested.flat();
}

async function hashFileContent(path) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(path)) hash.update(chunk);
  return hash.digest('hex');
}
