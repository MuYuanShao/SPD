# Server Signoff Runbook

This runbook defines the Server Signoff Interface for the SPD product. It turns deployment
readiness into evidence that can be reviewed from files, command output, and runtime checks.

## Scope

Server Signoff covers a small hospital deployment profile: about 200 daily users, with short
concurrent peaks during operational handoff. The signoff must prove that the release can
start as a server deployment and survive the expected concurrent Pressure Gate.

## Required Evidence

| Evidence | File or command | Proves |
| --- | --- | --- |
| Release verification report | `perf/results/release-verification.json` | The top-level Pressure Gate Adapter ran and recorded all gate statuses. |
| Backend tests | `backend-tests` gate in the release report | Deep Module Interfaces, including Inventory Movement, still pass their focused tests. |
| Frontend build | `frontend-build` gate in the release report | The deployed UI bundle can be built. |
| Deployment configuration | `perf/results/deployment-config-check.json` | Compose, Dockerfile, Nginx, and Spring deployment settings are internally consistent before startup. |
| Local signoff verifier | `signoff-local` gate in the release report and `perf/results/signoff-local-verification.json` | Server Signoff audit and runner fixture Interfaces are proven locally. |
| Local read pressure smoke | `perf/results/release-read-smoke.json` | Authenticated read mix is stable under local concurrent smoke load. |
| Local read/write pressure smoke | `perf/results/release-write-smoke.json` | Operational Closure writes and Document Number generation survive concurrent smoke load. |
| Concurrency Flow runbook | `docs/runbooks/concurrency-flow.md` | The read/write concurrency paths, Deep Modules, invariants, and Gate mapping are reviewable. |
| Docker Compose startup | `docker-compose-up`, `docker-compose-health`, and `docker-compose-ps` gates | MySQL, backend, and frontend can start through the deploy Adapter and expose `/api/health`. |
| k6 deployment pressure | `perf/results/k6-deployment-summary.json` | The deployment k6 Adapter passed the 200-daily-user peak profile thresholds. |
| Deployment host prereqs | `perf/results/deployment-host-prereqs.json` | Docker CLI, Docker Compose, Docker daemon, k6, production secrets, and deployment ports are ready before the full runner starts. |
| Server Signoff runner | `perf/results/server-signoff-run.json` | The full signoff sequence ran through the unified Adapter without recording secret values. |
| Server Signoff audit | `perf/results/server-signoff-audit.json` | A machine-readable audit confirms all required evidence is present and proven. |
| Signoff bundle | `perf/results/server-signoff-bundle.json` and `perf/results/server-signoff-manifest.json` | The evidence files were collected into one reviewable Server Signoff package. |

## Signoff Command

Run this on a deployment host with Docker Desktop/daemon running and k6 installed:

```powershell
$env:SPD_DB_PASSWORD="<strong database password>"
$env:SPD_DB_ROOT_PASSWORD="<strong root password>"
$env:SPD_JWT_SECRET="<at least 48 random characters>"
$env:DEPLOYMENT_BASE_URL="http://localhost:1820/api"
$env:DEPLOYMENT_HEALTH_URL="http://localhost:1820/api/health"
$env:DEPLOYMENT_USERNAME="admin"
$env:DEPLOYMENT_PASSWORD="admin123"
npm run signoff:server
```

Before the full runner, use the host prereq Adapter for a fast fail check:

```powershell
npm run verify:deployment-host-prereqs
```

It writes `perf/results/deployment-host-prereqs.json`. The report must have `ok: true` before
starting Server Signoff. It records command names, statuses, and secret validation shape, but not
secret values.
On Windows, `docker-daemon` must be `proven`; if it is `missing`, open Docker Desktop and complete
any WSL2/Virtual Machine Platform/admin prompts before running the prereq Adapter again.
When host prereqs are not ready, `npm run audit:signoff` reports the `fix-deployment-host-prereqs`
next action before the longer deployment signoff actions.

The release is signed off only when `deploymentReadiness.ready` is `true` and
`summary.missingDeploymentEvidence` is empty in `perf/results/release-verification.json`.
The `signoff:server` Adapter sets the required Server Signoff defaults, runs the deployment host
prereq Adapter first, then runs release verification, collects the bundle, runs the audit, and collects the final bundle. It writes
`perf/results/server-signoff-run.json` without recording secret values.
If deployment host prereqs fail, the runner stops before the longer release verification and records
`summary.stoppedAfter: "deployment-host-prereqs"`.
The first audit runs before the post-audit bundle refresh, so it checks the completed
pre-audit runner steps and the deployment evidence without requiring the current audit report
to already be inside the manifest. After the runner writes its final summary, it refreshes
the bundle, runs a final audit against the completed runner report, then captures the final
bundle again. The final audit requires the audit report to be present in the manifest. This
keeps the machine audit, runner report, and manifest in the same evidence chain without making
the audit depend on future runner steps.

## Evidence Review

Review these fields in `perf/results/release-verification.json`:

- `summary.ok` must be `true`.
- `summary.localGatesOk` must be `true`.
- `evidenceChecklist.local-signoff-verifier` must be `proven`.
- `environment.requireDeploymentTools` must be `true`.
- `environment.runComposeUp` must be `true`.
- `environment.runDeploymentPressure` must be `true`.
- `environment.deploymentPressureMutatingFlows` must be `"1"`.
- `summary.missingDeploymentEvidence` must be empty.
- `deploymentReadiness.dockerComposeStartup` must be `passed`.
- `deploymentReadiness.k6DeploymentPressure` must be `passed`.
- `deploymentReadiness.ready` must be `true`.
- `evidenceChecklist` must contain only `proven` items.
- `perf/results/deployment-host-prereqs.json` must have `ok: true`, `docker-daemon` must be `proven`, and secret checks must have `valueRecorded: false`.
- `perf/results/server-signoff-run.json` must have `dryRun: false`, `readyToRun: true`, no secret values recorded, `summary.ok: true`, and `finalBundleRefresh.exitCode: 0`.
- `perf/results/server-signoff-audit.json` must have `ready: true`.
- `perf/results/server-signoff-manifest.json` must have `ready: true`.

For production signoff, `npm run signoff:server` sets `REQUIRE_PRODUCTION_CONFIG=1` for the
release verification Adapter. The deployment configuration Gate then requires
`SPD_DB_PASSWORD`, `SPD_DB_ROOT_PASSWORD`, and `SPD_JWT_SECRET` to be provided through the
deployment environment and to differ from local defaults. `deploy/.env.production.example`
lists the required variables without real secrets. The `deployment-config` Gate embedded inside
`perf/results/release-verification.json` must show `requireProductionConfig: true` and
`productionReady: true`; a separate older production check file does not prove the current
release was signed with production settings.

Deployment pressure login uses `DEPLOYMENT_USERNAME` and `DEPLOYMENT_PASSWORD`, falling back to
`SPD_USERNAME` and `SPD_PASSWORD`, then local `admin` / `admin123` defaults. It intentionally
does not read generic operating-system variables such as `USERNAME`.

Review `perf/results/k6-deployment-summary.json`:

- `profile.peakVus` should match the target concurrent peak.
- `profile.mutatingFlows` must be `true` so the deployment Pressure Gate includes write concurrency.
- `metrics.mutating_flow_requests.values.count` must be greater than `0`.
- `metrics.http_req_failed` should satisfy the configured threshold.
- `metrics.http_req_duration` should satisfy `p(95)<800` and `p(99)<1500`.
- `metrics.business_errors` should satisfy the configured threshold.

Review `docs/runbooks/concurrency-flow.md` when expanding the Pressure Gate. New write scenarios
should reuse the same Pressure Gate Seam and document the affected Interface, invariant, and
evidence file.

For local or CI environments without Docker and k6, this non-signoff audit command records the
current missing evidence without failing the run:

```powershell
$env:ALLOW_INCOMPLETE_SIGNOFF="1"
npm run audit:signoff
npm run signoff:bundle
$env:SIGNOFF_STATUS_ALLOW_NOT_READY="1"
npm run signoff:status
```

`signoff:status` is a read-only Server Signoff status Adapter. It summarizes audit readiness,
bundle readiness, host prereq readiness, missing evidence, host prereq failures, and the first
next action to take.

For audit fixture runs, the audit Adapter accepts `RELEASE_REPORT_FILE`,
`SIGNOFF_MANIFEST_FILE`, `SIGNOFF_AUDIT_FILE`, `SERVER_SIGNOFF_RUN_FILE`, and
`K6_SUMMARY_FILE` so tests can point at isolated evidence files.

Run the audit fixture verifier after changing Server Signoff audit logic:

```powershell
npm run verify:signoff-audit-fixtures
```

Run the runner fixture verifier after changing Server Signoff runner inputs, secret handling,
or dry-run behaviour:

```powershell
npm run verify:signoff-runner-fixtures
```

Run the bundle fixture verifier after changing bundle readiness or manifest logic:

```powershell
npm run verify:signoff-bundle-fixtures
```

Run the status fixture verifier after changing the read-only signoff status summary:

```powershell
npm run verify:signoff-status-fixtures
```

Run the local signoff verifier to execute both fixture verifier Adapters and write a combined
local evidence report:

```powershell
npm run verify:signoff-local
```

The local signoff verifier also runs release verification fixtures, including the deployment
pressure credential fallback rule that prevents generic operating-system variables such as
`USERNAME` from becoming k6 login credentials.
It also runs the bundle fixture verifier, proving that the Server Signoff bundle reports
`deployment-host-prereqs` as missing when the host prereq report is not ok.
It also runs the status fixture verifier, proving that the read-only status Adapter reports
host prereq failures and the first next action.
It also runs the deployment host prereq fixture verifier, proving that missing Docker/k6 tools,
missing Docker daemon, and unsafe production secrets are rejected without leaking secret values.
The audit fixtures include a read-only k6 summary case; Server Signoff must reject it even
when the release report claims deployment pressure passed.
The release verification fixtures also reject k6 summaries that are read-only or report zero
mutating flow requests, so the top-level Pressure Gate cannot mark deployment pressure as
passed without actual write concurrency.

To check the Server Signoff runner inputs without starting Docker or k6, provide the production
variables and run:

```powershell
$env:SIGNOFF_DRY_RUN="1"
npm run signoff:server
```

The dry run validates the same production secret Interface as the deployment configuration Gate:
database passwords must be at least 16 characters, the JWT secret must be at least 48 characters,
and local defaults such as `admin123`, `root123`, or the placeholder JWT secret are rejected.

## Non-Signoff States

Local evidence is useful but not sufficient for Server Signoff. If Docker or k6 are missing,
the release verification Adapter should still record local evidence, but readiness must remain
incomplete until the deployment host runs Docker Compose startup and k6 deployment pressure.
If k6 runs with `profile.mutatingFlows: false`, the run is treated as read-only evidence and does
not satisfy Server Signoff.
