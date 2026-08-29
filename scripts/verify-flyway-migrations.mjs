import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const manifestPath = 'backend/src/main/resources/db/migration-checksums.sha256';
const migrationRoot = 'backend/src/main/resources/db/migration';
const manifest = await readFile(manifestPath, 'utf8');
const entries = manifest.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
const failures = [];

for (const line of entries) {
  const [expected, fileName] = line.split(/\s+/, 2);
  const bytes = await readFile(`${migrationRoot}/${fileName}`);
  const actual = createHash('sha256').update(bytes).digest('hex');
  if (actual !== expected) failures.push({ fileName, expected, actual });
}

if (failures.length) {
  console.error(JSON.stringify({ ok: false, failures }, null, 2));
  process.exitCode = 1;
} else {
  console.log(JSON.stringify({ ok: true, checked: entries.length }));
}
