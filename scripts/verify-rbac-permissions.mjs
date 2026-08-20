import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';

const authorizationSource = await readFile(
  'backend/src/main/java/com/hospital/spd/common/RbacAuthorizationService.java',
  'utf8',
);
const featureCatalogSource = await readFile('frontend/src/config/featureCatalog.ts', 'utf8');
const migrationDirectory = 'backend/src/main/resources/db/migration';
const migrationSource = (
  await Promise.all(
    (await readdir(migrationDirectory))
      .filter((name) => name.endsWith('.sql'))
      .map((name) => readFile(join(migrationDirectory, name), 'utf8')),
  )
).join('\n');

const apiFeatureCodes = collectMatches(authorizationSource, [
  /prefixes\.put\("[^"]+", "([^"]+)"\)/g,
  /featureCode = "([^"]+)"/g,
  /case [^\n]+ -> "([^"]+)"/g,
]);
const explicitAdministrationCodes = collectMatches(authorizationSource, [
  /return "((?:user|role)[^"]+)"/g,
]);
const frontendFeatureCodes = new Set(
  [...featureCatalogSource.matchAll(/\bcode:\s*'([^']+)'/g)].map((match) => match[1]),
);

const missingDatabaseCodes = [...apiFeatureCodes, ...explicitAdministrationCodes]
  .filter((code) => !migrationSource.includes(`'${code}'`));
const apiOnlyFeatures = new Set(['operational-closure']);
const missingFrontendCodes = [...apiFeatureCodes]
  .filter((code) => !apiOnlyFeatures.has(code) && !frontendFeatureCodes.has(code));

if (!migrationSource.includes("CONCAT(perm_code, ':write')")) {
  throw new Error('RBAC migrations no longer generate write permissions for feature permissions');
}
if (missingDatabaseCodes.length > 0) {
  throw new Error(`RBAC API permissions missing from Flyway catalog: ${missingDatabaseCodes.sort().join(', ')}`);
}
if (missingFrontendCodes.length > 0) {
  throw new Error(`RBAC API features missing from frontend catalog: ${missingFrontendCodes.sort().join(', ')}`);
}

console.log(JSON.stringify({
  ok: true,
  apiFeatureCount: apiFeatureCodes.size,
  administrationPermissionCount: explicitAdministrationCodes.size,
  frontendFeatureCount: frontendFeatureCodes.size,
}, null, 2));

function collectMatches(source, patterns) {
  const values = new Set();
  for (const pattern of patterns) {
    for (const match of source.matchAll(pattern)) {
      values.add(match[1]);
    }
  }
  return values;
}
