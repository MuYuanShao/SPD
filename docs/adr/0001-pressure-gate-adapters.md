# ADR 0001: Pressure Gate Adapters

## Status

Accepted

## Context

The SPD product must be deployable as a server product and must support a small hospital
workload of about 200 daily users with short concurrent peaks. The current development
environment does not always have Docker or k6 available, but the product still needs
repeatable evidence before deployment work reaches a server.

## Decision

Use two Pressure Gate Adapters at the same verification Seam:

- Node smoke Adapter: `perf/spd-concurrency-smoke.mjs`
  - Runs with the repository's existing Node runtime.
  - Writes JSON evidence to `perf/results/` through `RESULT_FILE`.
  - Covers authenticated read mix and optional read/write mix.
  - Suitable for local development and CI smoke verification.
- k6 deployment Adapter: `perf/k6-spd-concurrency.js`
  - Runs in the target environment where k6 is installed.
  - Models the longer 200-daily-user concurrent peak profile.
  - Suitable for deployment signoff.

Both Adapters exercise the same domain-facing Interface: login, workspace reads,
Inventory Balance/Event reads, purchase and receiving reads, Operational Closure reads,
and optional shortage task creation.

Use `npm run verify:release` as the release verification Adapter. It runs backend tests,
frontend build, both Node smoke Adapters, and deployment tool checks, then writes a JSON
report to `perf/results/release-verification.json`.

## Consequences

The Node smoke Adapter provides fast feedback and audit files even when Docker and k6 are
missing. It is not a replacement for deployment signoff. Server deployment readiness remains
incomplete until Docker Compose startup and the k6 deployment Adapter have both run in an
environment that has those tools available.

Future pressure scenarios should extend the shared Pressure Gate vocabulary in
`CONTEXT.md` rather than creating unrelated one-off scripts.
