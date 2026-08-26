import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';

const resultFile = process.env.DEPLOYMENT_CONFIG_REPORT_FILE || 'perf/results/deployment-config-check.json';
const requireProductionConfig = process.env.REQUIRE_PRODUCTION_CONFIG === '1';

const files = {
  compose: 'deploy/docker-compose.yml',
  backendDockerfile: 'deploy/backend.Dockerfile',
  frontendDockerfile: 'deploy/frontend.Dockerfile',
  nginx: 'deploy/nginx.conf',
  application: 'backend/src/main/resources/application.yml',
};

const content = Object.fromEntries(await Promise.all(
  Object.entries(files).map(async ([key, path]) => [key, await readFile(path, 'utf8')]),
));

const findings = [];

requireText('compose-has-mysql', content.compose, 'mysql:', 'Docker Compose includes MySQL service.');
requireText('compose-has-backend', content.compose, 'backend:', 'Docker Compose includes backend service.');
requireText('compose-has-frontend', content.compose, 'frontend:', 'Docker Compose includes frontend service.');
requireText('compose-backend-waits-for-mysql-health', content.compose, 'condition: service_healthy', 'Backend waits for healthy MySQL before startup.');
requireText('compose-backend-health-url', content.compose, 'http://127.0.0.1:1818/api/health', 'Backend healthcheck uses the real /api/health Interface.');
requireText('compose-frontend-port', content.compose, '${SPD_HTTP_PORT:-1820}:80', 'Frontend port can be overridden and defaults to 1820.');
requireText('backend-exposes-1818', content.backendDockerfile, 'EXPOSE 1818', 'Backend image exposes Spring Boot port.');
requireText(
  'backend-copies-stable-artifact',
  content.backendDockerfile,
  'COPY --from=build /workspace/backend/target/app.jar app.jar',
  'Backend image copies the stable Maven finalName without coupling the Dockerfile to one release version.',
);
requireText('frontend-exposes-80', content.frontendDockerfile, 'EXPOSE 80', 'Frontend image exposes Nginx port.');
requireText('nginx-proxies-api', content.nginx, 'proxy_pass http://backend:1818/api/;', 'Nginx proxies /api/ to backend service.');
requireText('nginx-spa-fallback', content.nginx, 'try_files $uri $uri/ /index.html;', 'Nginx serves SPA fallback.');
requireText('application-api-context', content.application, 'context-path: /api', 'Backend application uses /api context path.');
requireText('application-flyway-enabled', content.application, 'enabled: true', 'Flyway migration is enabled.');

warnDefault('compose-default-db-password', content.compose, 'SPD_DB_PASSWORD:-admin123', 'Compose contains default DB password for local use.');
warnDefault('compose-default-root-password', content.compose, 'SPD_DB_ROOT_PASSWORD:-root123', 'Compose contains default MySQL root password for local use.');
warnDefault('compose-default-jwt-secret', content.compose, 'change-me-to-a-strong-64-byte-secret-before-production', 'Compose contains placeholder JWT secret.');
warnDefault('application-default-db-password', content.application, 'SPD_DB_PASSWORD:admin123', 'Application contains default DB password for local use.');

if (requireProductionConfig) {
  requireSecret('production-db-password', 'SPD_DB_PASSWORD', ['admin123', 'root123'], 16, 'Production DB password is explicitly provided and not the local default.');
  requireSecret('production-root-password', 'SPD_DB_ROOT_PASSWORD', ['root123', 'admin123'], 16, 'Production MySQL root password is explicitly provided and not the local default.');
  requireSecret('production-jwt-secret', 'SPD_JWT_SECRET', ['change-me-to-a-strong-64-byte-secret-before-production'], 48, 'Production JWT secret is explicitly provided and long enough for signing.');
}

const errors = findings.filter((finding) => finding.level === 'error');
const warnings = findings.filter((finding) => finding.level === 'warning');
const report = {
  generatedAt: new Date().toISOString(),
  adapter: 'deployment configuration',
  requireProductionConfig,
  ok: errors.length === 0,
  productionReady: requireProductionConfig ? errors.length === 0 : errors.length === 0 && warnings.length === 0,
  findings,
};

await mkdir(dirname(resultFile), { recursive: true });
await writeFile(resultFile, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(report, null, 2));

if (!report.ok) {
  process.exitCode = 1;
}

function requireText(name, text, expected, proves) {
  const ok = text.includes(expected);
  findings.push({
    name,
    level: ok ? 'info' : 'error',
    status: ok ? 'proven' : 'missing',
    proves,
    expected,
  });
}

function warnDefault(name, text, expected, proves) {
  const present = text.includes(expected);
  if (!present) {
    findings.push({
      name,
      level: 'info',
      status: 'not-present',
      proves,
      expected,
    });
    return;
  }
  findings.push({
    name,
    level: 'warning',
    status: 'local-default',
    proves,
    expected,
  });
}

function requireSecret(name, envName, unsafeValues, minLength, proves) {
  const value = process.env[envName] || '';
  const ok = value.length >= minLength && !unsafeValues.includes(value);
  findings.push({
    name,
    envName,
    level: ok ? 'info' : 'error',
    status: ok ? 'proven' : 'missing-or-unsafe',
    proves,
    minLength,
  });
}
