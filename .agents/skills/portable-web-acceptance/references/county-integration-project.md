# County integration platform profile

Read this reference whenever an acceptance task targets this repository. The repository's `AGENTS.md`, confirmed requirements, API contracts, and standards remain authoritative; this file routes to project-specific constraints and evidence.

## Web surfaces and trust boundaries

The repository has three distinct browser surfaces:

- `web-admin/` is the formal Vue 3 and TypeScript management application. Review the registered route, navigation and permission metadata, API client and types, service-backed states, and applicable menu record. Direct URL access must be considered separately from menu visibility. Frontend guards improve navigation but do not establish server authorization or organization scope.
- `web-embed/` is an embedded Vue surface for an HIS or基层 workstation context. Confirm the contracted entry, host context, supported browser, and minimum information/actions for that integration. Do not assume it shares management navigation or authentication state.
- `/prototype` routes in `web-admin/` are synthetic demonstrations. Their mock data and prototype authentication must remain isolated from formal routes, server sessions, and real API evidence. A prototype can support interaction/design review only; it cannot prove production behavior, permissions, persistence, or integration.

Never combine evidence between these surfaces. A login, role, data set, or successful action on one surface does not prove the corresponding behavior on another.

## Authoritative project sources

Read the relevant parts of these files before defining acceptance criteria:

- `AGENTS.md` for repository-wide verification, SQL Server 2012 SP4 compatibility, change boundaries, security, and evidence rules.
- `docs/standards/frontend-coding-guidelines.md` for browser behavior, session/permission boundaries, prototype isolation, sensitive data, supported verification, and accessibility expectations.
- `docs/standards/菜单功能完成检查.md` for formal menu stage definitions and the evidence required to advance them.
- `docs/standards/业务功能完成与验收标准.md` for business lifecycle, duplicate/unknown outcomes, and business-owner acceptance.
- Relevant OpenAPI/interface specs, design decisions, test cases, and current verification records for the target feature. Existing verification documents may describe an older implementation; confirm they still apply before citing them.

Do not infer a new business field, status, role, permission, organization rule, endpoint, HIS behavior, or acceptance owner from an old screenshot, mock, verification note, or a nearby feature.

## Formal management menu evidence

For an item registered in `web-admin/menu-delivery-status.json`, check its current stage and evidence fields against the actual route, page, API, editor/form, tests, audit source/actions, and any linked evidence. Use `node tools/verify-menu-delivery.mjs` to check the repository's registration contract when the task changes a formal menu or its recorded stage. A successful script is a completeness check, not proof that browser behavior, real writes, read-back, audit, or business acceptance occurred.

Respect the ordered stages in `docs/standards/菜单功能完成检查.md`:

- `仅有计划` and `后端已实现` are not finished user-facing features and must not appear as available formal menu entries.
- `前端已接入` needs a working formal route, real API wiring, relevant error and permission states, and automated coverage; it does not prove real write/read-back.
- `已验证写入和回读` requires a controlled environment, synthetic reversible data, write, refresh/read-back, audit evidence, and cleanup/recovery evidence.
- `业务已验收` additionally requires the listed role, cross-organization, conflict, unavailable-backend cases and business-owner confirmation. Do not promote a stage based on UI presence, an HTTP success, or a green test suite.

Read `web-admin/menu-delivery-status.json` and any cited evidence; do not edit a stage unless the task explicitly includes stage/evidence maintenance and the required evidence exists. Never run acceptance seed/cleanup scripts or write test records against a live/shared database without explicit scope, a confirmed non-production target, and a safe cleanup/read-back plan.

## Existing frontend checks

The root `package.json` defines the current frontend commands. Re-read it before execution because scripts can change. At this snapshot, focused options include:

```bash
npm run test:frontend
npm run typecheck
npm run build
npm run verify:frontend
node tools/verify-menu-delivery.mjs
node tools/verify-frontend-comments.mjs
```

Choose only commands required by the task and project standards. `verify:frontend` runs comment checks, menu registration checks, both workspaces' TypeScript checks and builds, and the Node frontend tests. Browser acceptance is still required for user-facing flows; the repository's current test suite is Node-based and is not a substitute for observing the declared route in a browser. Do not install another browser runner or claim a browser check unless it was actually performed.

For frontend implementation changes, follow all checks required by `AGENTS.md` and `docs/standards/frontend-coding-guidelines.md`, including affected workspace typecheck/build, frontend tests, and comment verification. Before a commit, the repository requires `./tools/verify.sh`.

## Project-specific acceptance risks

For relevant features, include these dimensions in the trace:

- Functional permission and explicit organization scope are separate. Verify client visibility/navigation and server-side denial independently; do not infer scope from a role, parent organization, selected filter, or request parameter.
- Session expiry, 401, 403, 409/rowversion conflicts, network failure, duplicate requests, and unknown write outcomes have distinct user-facing and recovery behavior where applicable. Retained form values and read-back matter after uncertain outcomes.
- HIS and other external calls must be checked against approved interface contracts and the exact institution/environment configuration. A mock or prototype result is not upstream evidence.
- Audit evidence must be correlated to the actual action and sanitized. Do not capture passwords, tokens, cookies, credentials, patient content, full identity details, or secrets in screenshots, commands, logs, fixtures, or reports.
- Where SQL-backed behavior is in scope, this Web skill cannot certify database compatibility. Project-required database checks must use Microsoft SQL Server 2012 SP4, compatibility level 110, and the applicable migration rules; never substitute a SQL Server Linux container or a different engine.

Keep unsupported roles, institutions, browser hosts, external systems, and failure states explicit as gaps. If the required environment or safe synthetic data is unavailable, leave the criterion unverified instead of substituting mock evidence.
