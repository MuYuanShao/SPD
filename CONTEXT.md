# SPD Domain Context

This file defines the domain vocabulary used for architecture work in this repository.
Use these terms when naming Modules, Interfaces, tests, pressure scenarios, and runbooks.

## Product Shape

The system is an in-hospital SPD supply chain product. It is expected to run as a server
deployment for a small hospital workload, with about 200 daily users and short concurrent
peaks around operational handoff times.

## Core Domain Terms

### Inventory Balance

The current quantity snapshot for one warehouse, location, product, and batch. A balance
must never become negative. The database enforces uniqueness with normalized location
identity so that "no location" has the same identity everywhere.

### Inventory Movement

A domain operation that changes Inventory Balance and records the matching immutable
Inventory Event. This is a Deep Module: callers ask for receipt, FIFO consumption, or
adjustment; the Implementation owns locking, non-negative quantity checks, event writing,
and last-event linkage.

The Inventory Movement Interface includes these invariants:

- Receipt adds positive available quantity to the no-location balance for a batch.
- FIFO consumption locks candidate balances before deducting.
- Deduction must use a non-negative update guard.
- Stocktaking adjustment must reject zero changes and negative resulting balances.
- Every successful movement records an Inventory Event.

### Inventory Event

The immutable audit row for an Inventory Movement. It records source business type, source
business id, warehouse, product, batch, quantity change, quantity after, and remark.

### Document Number

A business document identifier generated through a database-backed sequence. Document Number
generation is concurrency-sensitive and is part of the pressure-test surface whenever flows
create shortage, receiving, purchase, stocktaking, or delivery records.

### Operational Closure

The set of downstream flows that close operational work: shortage reminders, replenishment,
requisition, delivery, consumption, settlement, PDA upload, cold-chain exceptions, recalls,
and high-value charges.

### Concurrency Flow

The operational path exercised by many users at the same time. The Concurrency Flow includes
authenticated workspace reads, Inventory Balance/Event reads, Operational Closure reads, and
controlled write flows that create business rows through Document Number. The runbook at
`docs/runbooks/concurrency-flow.md` maps these flows to the Modules and Gates that prove them.

### Pressure Gate

A repeatable verification of expected server workload. The local smoke Adapter records JSON
evidence for read and read/write concurrent flows. The deployment gate uses Docker Compose
and k6 when those tools are available in the target environment.

The release verification Adapter is the top-level Pressure Gate Interface for a release
candidate. It runs the backend test gate, frontend build gate, local Node pressure smoke
Adapters, local Server Signoff verifier Adapter, and deployment tool checks, then writes a
JSON report under `perf/results/`.

The deployment configuration Adapter checks the static deployment Interface: Docker Compose,
Dockerfiles, Nginx routing, Spring context path, health checks, and production-only secret
requirements. It improves locality by keeping deployment configuration checks in one report.

### Server Signoff

The deployment-readiness decision for the SPD product. Server Signoff is only true when the
release verification Adapter proves local gates, Docker Compose startup, and k6 deployment
pressure. The required evidence is listed in `docs/runbooks/server-signoff.md`.

The Server Signoff bundle Adapter collects the release report, deployment configuration
report, pressure results, and runbook into `perf/results/server-signoff-bundle.json` with
a smaller manifest at `perf/results/server-signoff-manifest.json`.

The Server Signoff runner Adapter is the preferred operator Interface for production
readiness. It validates required production secrets without recording their values, applies
the deployment-only release verification defaults, runs release verification, refreshes the
bundle before the first machine audit, writes a final runner summary, refreshes the bundle,
runs a final audit against the completed runner report, captures the final bundle, and records
the sequence in `perf/results/server-signoff-run.json`.

The Server Signoff local verifier Adapter proves the local release verification, audit, and
runner fixture Interfaces without requiring Docker or k6, and records the result in
`perf/results/signoff-local-verification.json`.

The deployment host prereqs Adapter checks the Server Signoff host before the full runner starts:
Docker, Docker Compose, k6, production secret shape, and deployment port availability. Its
fixture verifier proves the host prereq Interface locally without recording secret values.

## Verification Expectations

Architecture changes that touch Inventory Movement, Document Number, Operational Closure,
Concurrency Flow, or deployment must name the affected Interface and provide evidence through at least one of:

- focused unit tests against the Deep Module Interface;
- local Node pressure smoke results under `perf/results/`;
- release verification report under `perf/results/`;
- deployment configuration report under `perf/results/`;
- Server Signoff runner and audit reports under `perf/results/`;
- Server Signoff fixture verifiers for audit and runner Interfaces;
- deployment host prereq reports and fixture verifier results;
- deployment k6 results;
- Docker Compose health and startup evidence.

Do not mark server deployment readiness complete unless container startup and a deployment
pressure run have executed in an environment with Docker and k6 available.
