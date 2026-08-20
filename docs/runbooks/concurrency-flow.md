# Concurrency Flow Runbook

This runbook documents the Concurrency Flow Interface for the SPD product. It explains which
Modules are hit under concurrent use, what each Interface must preserve, and which Gate proves
the behavior before Server Signoff.

## Scope

The target workload is a small hospital server deployment with about 200 daily users and short
concurrent peaks during shift handoff, delivery signing, replenishment, and settlement review.
The goal is not only request throughput. The Pressure Gate must also protect these domain
invariants:

- Inventory Balance never becomes negative.
- Every successful Inventory Movement writes an Inventory Event.
- Document Number generation stays unique while concurrent write flows create business rows.
- Operational Closure read flows remain responsive while write flows are running.

The domain-facing Pressure Gate scenario steps, thresholds, and evidence expectations are declared
in `perf/pressure-gate-scenarios.mjs`. The local Node smoke Adapter and deployment k6 Adapter consume
that module so read/write paths, group names, write cadence, and latency/failure thresholds stay
aligned across both adapters.

## Concurrent Read Flow

The read flow represents workspace users opening the dashboard, inventory pages, purchase pages,
receiving pages, and Operational Closure lists at the same time.

| Step | Interface | Expected invariant | Evidence |
| --- | --- | --- | --- |
| Login | Authentication token issue | Each virtual user obtains a token before protected reads. | `perf/spd-concurrency-smoke.mjs`, `perf/k6-spd-concurrency.js` |
| Workspace reads | module and dashboard reads | Reads return `code = 0` without cross-user state assumptions. | `release-read-smoke.json`, shared `workspace-read-flow` |
| Inventory reads | Inventory Balance and Inventory Event list | Reads stay available while write flows mutate balances and events. | `release-read-smoke.json`, `release-write-smoke.json` |
| Operational Closure reads | Overview and shortage list | Closure overview and list queries remain below configured p95 latency. | Node smoke and k6 duration thresholds |

## Concurrent Write Flow

The current repeatable write flow is shortage generation. It exercises Operational Closure and
Document Number without requiring test data cleanup between every virtual user iteration.

| Step | Interface | Expected invariant | Evidence |
| --- | --- | --- | --- |
| Shortage generation | Operational Closure `generateShortage` | Creates a pending replenishment task in one transaction. | `node-smoke-write` gate |
| Number allocation | Document Number `nextNo` | Concurrent callers receive unique daily task numbers. | `DocumentNumberServiceTest`, write smoke failures must remain `0` |
| Reviewable result | Pressure Gate summary | Failures, p95, p99, and write request counts are written to JSON. | Shared evidence expectations, `perf/results/release-write-smoke.json`, `k6-deployment-summary.json` |

The deployment k6 Adapter uses the same write flow when `DEPLOYMENT_MUTATING_FLOWS=1` is passed
through the release verification Adapter. Server Signoff requires mutating flows so the deployment
Pressure Gate proves Document Number and Operational Closure write concurrency, not only reads.

## Inventory Movement Flow

Inventory Movement is the Deep Module that owns balance mutation. It should be treated as the only
safe Interface for stock-in, stock-out, and stocktaking adjustment.

| Caller flow | Interface | Implementation responsibility | Focused evidence |
| --- | --- | --- | --- |
| Receiving acceptance | `receiveAvailable` | Upsert no-location balance, lock the balance row, write Inventory Event, link `last_event_id`. | `InventoryMovementServiceTest` |
| Delivery sign | `consumeAvailableFifo` | Lock FIFO candidate balances, use guarded non-negative update, write one event per deduction. | `InventoryMovementServiceTest`, `OperationalClosureServiceTest` |
| Stocktaking adjustment | `adjustAvailable` | Lock balance, reject zero change, reject negative result, write Inventory Event. | `InventoryMovementServiceTest`, `InventoryServiceTest` |

The Interface keeps locality: callers do not assemble `FOR UPDATE`, non-negative guards, event
writing, or `last_event_id` updates themselves.

## Document Number Flow

Document Number is concurrency-sensitive because Operational Closure, receiving, purchase,
delivery, stocktaking, settlement, PDA, recall, and charge flows all create business rows.

The Interface uses a database-backed sequence row per prefix and day. The Implementation uses
`INSERT ... ON DUPLICATE KEY UPDATE` with `LAST_INSERT_ID` so concurrent callers serialize through
the database row and receive one sequence value each.

Required invariants:

- Prefix and date form the sequence key.
- Existing business rows can be reconciled into the allocator floor.
- Unsafe table and column names are rejected before reconciliation SQL is built.
- Every write flow must use `SupplyChainSupport.nextNo` rather than ad hoc numbering.

Evidence:

- `DocumentNumberServiceTest` proves padding, floor reconciliation, and safe SQL shape.
- `release-write-smoke.json` proves concurrent shortage generation does not produce API failures.
- `k6-deployment-summary.json` proves the same Interface under the deployment peak profile and must include `metrics.mutating_flow_requests.values.count > 0`.

## Pressure Gate Mapping

| Gate | Adapter | Concurrency proof | Signoff role |
| --- | --- | --- | --- |
| `backend-tests` | Maven/JUnit | Deep Module Interface tests for Inventory Movement, Document Number, and Operational Closure callers. | Required local evidence |
| `node-smoke-read` | `perf/spd-concurrency-smoke.mjs` | Concurrent authenticated reads across the workspace. | Required local evidence |
| `node-smoke-write` | `perf/spd-concurrency-smoke.mjs` with `MUTATING_FLOWS=1` | Concurrent Operational Closure write smoke and Document Number allocation. | Required local evidence |
| `docker-compose-up` + health | `deploy/docker-compose.yml` | The server deployment starts the same runtime topology users will hit. | Required deployment evidence |
| `k6-deployment-pressure` | `perf/k6-spd-concurrency.js` | Longer 200-daily-user peak profile with thresholded failure, latency, and mutating write metrics. | Required deployment evidence |
| `signoff:server` | `scripts/run-server-signoff.mjs` | Runs the full Server Signoff sequence with production defaults and no secret values in reports. | Deployment-host Adapter |
| `audit:signoff` | `scripts/audit-signoff.mjs` | Machine-checks that every required concurrency and deployment evidence item is proven. | Final audit evidence |
| `signoff:bundle` | `scripts/create-signoff-bundle.mjs` | Collects the proof files and this runbook into one review package. | Review evidence |

## Known Gaps Before Server Signoff

Local smoke evidence is useful but not enough to claim Server Signoff. The following remain missing
until executed on a host with Docker and k6:

- Docker Compose startup evidence for MySQL, backend, and frontend.
- Deployment health evidence through `/api/health`.
- k6 deployment pressure evidence written to `perf/results/k6-deployment-summary.json` with `profile.mutatingFlows: true` and `metrics.mutating_flow_requests.values.count > 0`.

Future write scenarios should deepen the Pressure Gate by adding more Operational Closure writes
through the existing Adapter instead of creating unrelated scripts.
