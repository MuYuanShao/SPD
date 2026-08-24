# AGENTS.md

## Project Positioning

This repository is an in-hospital SPD supply chain management platform.
The frontend is Vue 3 + Vite + Element Plus. The backend is Spring Boot 3 + Java 17 + MySQL + Flyway.

Before changing behavior, read the relevant local context first:

- `CONTEXT.md`
- `docs/code-standards.md`
- `docs/ARCHITECTURE.md`
- the related module source and tests

## Common Commands

Frontend:

```powershell
npm --prefix frontend run build
npm run dev:frontend
```

Backend:

```powershell
mvn -f backend/pom.xml test
mvn -f backend/pom.xml spring-boot:run
```

Root verification commands:

```powershell
npm run build:frontend
npm run build:backend
npm run verify:release
npm run test:smoke:business-ui
```

One-click startup and offline deployment packaging:

```powershell
npm run start:dev           # 一键启动前后端（检查 MySQL → 后端 → 前端）
npm run package:offline     # 打包后端 jar + 前端构建产物 + JDK17 + 一键脚本到 output/offline-bundle
```

Opt-in real MySQL concurrency verification:

```powershell
$env:SPD_MYSQL_INTEGRATION_TESTS='true'
mvn -f backend/pom.xml "-Dtest=OperationalHighValueMysqlConcurrencyTest" test
```

The MySQL concurrency test mutates and cleans up fixture data. Keep it opt-in; do not enable it implicitly in the normal unit-test suite.

Local defaults:

- Frontend: `http://localhost:1820`
- Backend API: `http://localhost:1818/api`
- Database: MySQL `ISPD`

## Code Map

Backend:

- `backend/src/main/java/com/hospital/spd`
- `backend/src/main/resources/db/migration`
- `backend/src/test/java/com/hospital/spd`

Frontend:

- `frontend/src/views`
- `frontend/src/api`
- `frontend/src/router`
- `frontend/src/config`
- `frontend/src/components`
- `frontend/src/styles`

Database schema changes must be implemented through Flyway migrations. Do not depend on manual database edits.

## Backend Rules

Controllers bind request parameters, call services, and return the standard response shape.

Business validation, status transitions, inventory changes, audit writes, and document number generation belong in services or shared domain services.

Paged list endpoints should return the shared page shape:

- `rows`
- `total`
- `page`
- `size`
- optional `summary`

Prefer existing shared services for cross-cutting behavior:

- `DocumentNumberService`
- `AuditLogService`
- `InventoryEventService`
- `OperatorContextProvider`

Inventory receipt, deduction, adjustment, and transfer behavior must preserve non-negative inventory and write immutable inventory events.

## Frontend Rules

Follow the existing page, router, API client, and style patterns before adding new abstractions.

Common locations:

- Pages: `frontend/src/views/**`
- API clients: `frontend/src/api/**`
- Routes: `frontend/src/router/index.ts`
- Feature catalog: `frontend/src/config/featureCatalog.ts`
- Global styles: `frontend/src/styles/main.css`

Do not commit generated `*.js` or `*.vue.js` files under `frontend/src`.

This is an operational hospital system. Prefer dense, scannable, work-focused UI over marketing-style pages.

Do not add large top overview/stat cards to business pages unless the user explicitly asks for them. Existing product direction is to remove top overview sections.

## Product Boundaries

Inventory Management is a simple inventory balance query. It is not inventory movement history.

Inventory transaction history is a separate feature and must not be mixed into Inventory Management.

Do not add or restore the "inventory event snapshot" business module. Do not show inventory event snapshot features or data in replenishment tasks, department consumption, charge consumable details, cold-chain monitoring, recall/isolation, or other business pages unless the user explicitly reverses this decision.

The old High-Value Consumables Management page should be treated as Charge Consumable Detail Query. It should support searching by patient, product, date, inpatient number, UID/unique code, supplier, manufacturer, registration certificate, and related catalog fields. Results should show hospital catalog consumable information plus charge quantity, unit price, amount, and patient information.

UDI/unique-code traceability must support both high-value consumables and low-value quota-package traceability.

Persist stable UDI relationships with `trace_code_id` when a business table owns a trace relationship. UDI and unique-code text fields may remain as integration payload and display fields, but should not be the primary relational identity.

For high-value consumable traceability, SPD records usage and deducts inventory after it receives billing data from HIS/anesthesia/surgery systems. Department take-out does not mean billing has happened.

Department requisition entry should first show historical department requisition order summaries. Creating a new requisition should show the current department catalog only, not the full hospital catalog.

## Verification

After backend changes, run focused tests when possible:

```powershell
mvn -f backend/pom.xml "-Dtest=TargetTestClass" test
```

After frontend changes, run:

```powershell
npm --prefix frontend run build
```

After changes to critical business-page interactions, run the fixed Playwright smoke flow when the local MySQL environment is available:

```powershell
npm run test:smoke:business-ui
```

The smoke flow covers authentication, real API loading, filtering, pagination, empty state, and horizontal table interaction. On Windows it uses the installed Chrome by default; override with `PLAYWRIGHT_CHROME_PATH` when needed.

For frontend/backend integration changes, verify:

- API paths include `/api`
- authentication state is considered
- backend data is read from or written to the database
- frontend uses real API clients instead of static mock data

## Text And Encoding

Store source, SQL migrations, and docs as UTF-8. Mojibake in PowerShell output is not enough evidence that the file itself is corrupted.

Keep UI copy and API messages as readable Chinese. Do not commit text produced by a misconfigured terminal or editor.

## Worktree Safety

The worktree may contain user changes. Do not reset, revert, or overwrite unrelated files.

Keep edits scoped to the current task. Avoid opportunistic refactors.

Users often verify behavior through `localhost:1820`; after changing running UI or backend behavior, rebuild or restart the relevant service when needed.
