# SPD Deployment and Pressure Test

This folder contains the first deployable server shape for the SPD product:

- `docker-compose.yml`: MySQL 8.4, Spring Boot backend, Nginx-hosted frontend.
- `backend.Dockerfile`: builds and runs the backend jar.
- `frontend.Dockerfile`: builds the Vue app and serves it through Nginx.
- `nginx.conf`: serves the SPA and proxies `/api` to the backend.

## Start

```powershell
docker compose -f deploy/docker-compose.yml up --build -d
```

The frontend is available at `http://localhost:1820`.

Useful overrides:

```powershell
$env:SPD_HTTP_PORT="1820"
$env:SPD_DB_PASSWORD="replace-me"
$env:SPD_DB_ROOT_PASSWORD="replace-root"
$env:SPD_JWT_SECRET="replace-with-a-long-random-production-secret"
docker compose -f deploy/docker-compose.yml up --build -d
```

## Health Checks

```powershell
docker compose -f deploy/docker-compose.yml ps
curl http://localhost:1820/api/health
```

## 200-Daily-User Pressure Test

### Server Signoff

Run the Server Signoff Adapter on a deployment host with Docker and k6 installed. It applies
the production Server Signoff defaults, runs release verification, starts the Docker Compose
deployment, runs the k6 deployment Pressure Gate with mutating flows enabled, collects the
evidence bundle, runs the machine audit, and refreshes the final bundle.

```powershell
$env:SPD_DB_PASSWORD="<strong database password>"
$env:SPD_DB_ROOT_PASSWORD="<strong root password>"
$env:SPD_JWT_SECRET="<at least 48 random characters>"
$env:DEPLOYMENT_BASE_URL="http://localhost:1820/api"
$env:DEPLOYMENT_HEALTH_URL="http://localhost:1820/api/health"
$env:DEPLOYMENT_USERNAME="admin"
$env:DEPLOYMENT_PASSWORD="<输入强密码>"
npm run signoff:server
```

The runner writes `perf/results/server-signoff-run.json` without recording secret values.
It also refreshes `perf/results/release-verification.json`,
`perf/results/server-signoff-audit.json`, `perf/results/server-signoff-bundle.json`, and
`perf/results/server-signoff-manifest.json`.
Deployment pressure login uses `DEPLOYMENT_USERNAME` and `DEPLOYMENT_PASSWORD`, not the
operating-system `USERNAME`.

Use `docs/runbooks/server-signoff.md` as the evidence checklist for deployment readiness.

To validate the required production inputs without starting Docker or k6:

```powershell
$env:SPD_DB_PASSWORD="<strong database password>"
$env:SPD_DB_ROOT_PASSWORD="<strong root password>"
$env:SPD_JWT_SECRET="<at least 48 random characters>"
$env:SIGNOFF_DRY_RUN="1"
npm run signoff:server
```

`deploy/.env.production.example` lists the variables expected by production signoff.

### Release Verification

Run the release verification Adapter during local development or CI before server signoff.
It runs backend tests, frontend build, read/read-write Node smoke pressure checks, and
deployment configuration checks.

```powershell
npm run verify:release
```

The JSON report is written to `perf/results/release-verification.json`. If Docker and k6
are not installed, the report keeps server deployment readiness incomplete while still
recording local evidence. Local release verification is not a Server Signoff substitute.

### Local or CI Smoke Test

Use this when Docker or k6 is not installed. It uses Node's built-in `fetch`
and runs the same authenticated read mix against a running backend.

```powershell
$env:BASE_URL="http://localhost:1818/api"
$env:SPD_USERNAME="admin"
$env:SPD_PASSWORD="<输入测试账号密码>"
$env:RESULT_FILE="perf/results/local-read-smoke.json"
npm run perf:smoke:read
```

For a short read/write smoke test against test data:

```powershell
$env:BASE_URL="http://localhost:1818/api"
$env:RESULT_FILE="perf/results/local-write-smoke.json"
npm run perf:smoke:write
```

The smoke gate fails when failure rate is above `1%` or p95 is above `800ms`.
Tune with `MAX_FAILURE_RATE` and `MAX_P95_MS` when testing slower hardware.

### k6 Deployment Test

The default k6 profile models a small hospital deployment with about 200 daily users
and a concurrent peak, rather than a flat 24-hour average. It ramps to 20 VUs,
holds for 8 minutes, and checks login, workspace reads, inventory, receiving,
purchase, and operational closure endpoints.

```powershell
$env:K6_SUMMARY_FILE="perf/results/k6-deployment-summary.json"
k6 run perf/k6-spd-concurrency.js
```

Tune the peak and duration:

```powershell
$env:BASE_URL="http://localhost:1820/api"
$env:PEAK_VUS="30"
$env:HOLD="15m"
k6 run perf/k6-spd-concurrency.js
```

Enable mutating flows only against test data. This adds concurrent shortage task
generation and exercises document number generation plus transactional writes:

```powershell
$env:MUTATING_FLOWS="1"
k6 run perf/k6-spd-concurrency.js
```
