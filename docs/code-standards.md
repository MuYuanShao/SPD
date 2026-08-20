 # SPD Code Standards

This project is moving toward shared UI components and service-layer business rules. Use these rules for new code and for refactoring existing modules.

## Frontend

- Put reusable Vue components under `frontend/src/components/common`.
- Prefer shared components for repeated UI patterns:
  - `PageHeader` for page title/action headers.
  - `SectionTitle` for card or panel section headings with icons.
  - `StatusMessage` for success, warning, and error feedback.
  - `EmptyState` for loading, empty, and error placeholder text.
- Keep business page text in the page component, not inside common components.
- Use lucide icons through component slots when the icon is part of a reusable title or action pattern.
- Do not commit generated frontend build/type-check files under `frontend/src`, including `*.vue.js` and same-name `.ts -> .js` output files.
- Keep `vue-tsc` as a type checker only. `frontend/tsconfig.json` must keep `noEmit: true`.

## Backend

- Controllers bind request parameters, call services, and return `ApiResponse`.
- Business validation, status transitions, inventory changes, audit writes, and document numbers belong in services or common service helpers.
- Paged list endpoints must return the shared `PageResponse` shape: `rows`, `total`, `page`, `size`, and optional `summary`.
- Do not hide fixed `LIMIT 100` or `LIMIT 200` caps inside paged list implementations. Use `PageRequest`; keep fixed caps only on explicit option endpoints.
- Every Controller and Service class should have one concise class-level Javadoc that states its business boundary.
- Do not stack multiple class-level Javadocs. Keep the stable responsibility comment and remove stale or duplicated comments.
- Use common services for shared cross-cutting behavior:
  - `DocumentNumberService` for business numbers.
  - `AuditLogService` for audit records.
  - `InventoryEventService` for immutable stock movement events.
  - `OperatorContextProvider` for authenticated operator id, username, roles, and client IP.
- Add method comments only where the method encodes business rules or non-obvious state transitions.

## Text And Encoding

- Store source, docs, SQL migrations, and CSV templates as UTF-8 without BOM.
- When checking Chinese text in PowerShell, prefer commands that preserve UTF-8 rendering or byte-level validation. Mojibake in terminal output is not evidence of file corruption.
- Keep UI copy and API messages in normal human-readable Chinese. Do not commit text produced by a misconfigured terminal or editor.
- Use comments to explain invariants, state transitions, ownership, and compliance constraints.
- Avoid comments that merely restate the next line of code, such as "query users" before a user query.
- In tests, prefer helper method names that describe the mock scenario; use comments only when the mock encodes a business rule or a driver quirk.

## Verification

- Run `mvn -f backend/pom.xml test` after backend changes.
- Run `npm --prefix frontend run build` after frontend changes.
- After frontend builds, confirm no generated `*.js` or `*.vue.js` files appear under `frontend/src` when a same-name TypeScript or Vue source file is the real source.
