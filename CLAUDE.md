# CLAUDE.md

This file defines Claude-specific working guidance for the in-hospital SPD repository. `AGENTS.md` is the authoritative cross-agent project instruction file. Read and follow it in full before changing code. If this file and `AGENTS.md` differ, follow `AGENTS.md` and update this file to remove the drift.

## Required Context

Before changing behavior, read:

- `AGENTS.md`
- `CONTEXT.md`
- `docs/code-standards.md`
- `docs/ARCHITECTURE.md`
- related source, Flyway migrations, and tests

The product is an operational hospital SPD supply-chain platform:

- Frontend: Vue 3, Vite, Element Plus, TypeScript
- Backend: Spring Boot 3, Java 17, JDBC, MySQL
- Schema management: Flyway only
- Local frontend: `http://localhost:1820`
- Local API: `http://localhost:1818/api`
- Local database: MySQL `ISPD`

## Repository Map

- Backend code: `backend/src/main/java/com/hospital/spd`
- Backend tests: `backend/src/test/java/com/hospital/spd`
- Migrations: `backend/src/main/resources/db/migration`
- Frontend pages: `frontend/src/views`
- Frontend API clients: `frontend/src/api`
- Router: `frontend/src/router/index.ts`
- Feature catalog: `frontend/src/config/featureCatalog.ts`
- Shared components: `frontend/src/components/common`
- Global styles: `frontend/src/styles/main.css`
- Browser smoke tests: `tests/e2e`

## Domain Invariants

- Inventory Balance is the current warehouse/location/product/batch quantity and must never become negative.
- Inventory Movement owns receipt, FIFO deduction, adjustment, transfer, isolation, locking, and immutable Inventory Event creation.
- Controllers bind input and return `ApiResponse`; validation, transitions, inventory writes, audit writes, and document numbers belong in services.
- Use `DocumentNumberService`, `AuditLogService`, `InventoryEventService`, and `OperatorContextProvider` for shared behavior.
- Paged endpoints return `rows`, `total`, `page`, `size`, and optional `summary`.
- Persist schema changes through a new Flyway migration; never rely on manual database changes.

## Non-Negotiable Product Boundaries

- Inventory Management is an inventory balance query, not inventory transaction history.
- Inventory transaction history is a separate feature.
- Do not add or restore an inventory-event-snapshot business module or surface it on replenishment, consumption, charge detail, cold-chain, recall, or other business pages.
- The former High-Value Consumables page is Charge Consumable Detail Query. It searches patient, product, date, inpatient number, UDI/unique code, supplier, manufacturer, registration certificate, and catalog fields, and displays catalog, quantity, unit price, amount, and patient data.
- Department take-out is not high-value billing. Deduct high-value inventory only after HIS, anesthesia, or surgery billing data is received and validated.
- UDI traceability covers high-value consumables and low-value quota packages.
- Use `trace_code_id` as the stable relational identity for persisted UDI relationships. Treat UDI and unique-code strings as payload/search/display values.
- Department requisition opens on historical order summaries. New requisitions use only the current department/warehouse catalog, never the entire hospital catalog.
- Business pages are dense and operational. Do not add large top overview/stat cards unless explicitly requested.

## Commands

```powershell
# Frontend
npm run dev:frontend
npm --prefix frontend run build

# Backend
mvn -f backend/pom.xml test
mvn -f backend/pom.xml spring-boot:run

# Managed local backend
npm run backend:start
npm run backend:restart
npm run backend:status
npm run backend:stop

# Release and browser verification
npm run verify:release
npm run test:smoke:business-ui
```

Focused backend tests:

```powershell
mvn -f backend/pom.xml "-Dtest=TargetTestClass" test
```

The real MySQL billing-concurrency test is intentionally opt-in because it creates and cleans fixture data:

```powershell
$env:SPD_MYSQL_INTEGRATION_TESTS='true'
mvn -f backend/pom.xml "-Dtest=OperationalHighValueMysqlConcurrencyTest" test
```

Do not silently enable real-database mutation in the normal unit-test suite.

## Verification Expectations

- Backend change: run focused tests, then the full backend suite when practical.
- Frontend change: run the production build.
- Critical business-page interaction change: run `npm run test:smoke:business-ui` with frontend, backend, and MySQL available.
- Integration change: verify `/api` paths, authentication, real database persistence, and real frontend API clients.
- Inventory or document-number change: add concurrency-sensitive evidence proportional to risk.
- After a frontend build, ensure no generated `*.js` or `*.vue.js` files appear under `frontend/src` beside their TypeScript/Vue sources.

## Editing and Safety

- Preserve unrelated user changes in a dirty worktree.
- Keep changes scoped; do not perform opportunistic refactors.
- Never reset or overwrite unrelated files.
- Store source, SQL, and documentation as UTF-8. PowerShell mojibake alone is not proof of file corruption.
- Keep UI and API text readable Chinese.
- After changing running behavior, rebuild or restart the relevant service because users commonly verify through `localhost:1820`.

## Keeping Instructions Aligned

When project commands, directory layout, domain boundaries, or verification gates change:

1. Update `AGENTS.md` first.
2. Update only the affected summary in this file.
3. Prefer references to `AGENTS.md` over duplicating volatile detail.
4. Check that both files describe the same product boundaries before finishing.
