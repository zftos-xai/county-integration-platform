# Verdicts and report template

## Keep result layers separate

- **Requirement/page acceptance:** whether the observed page and flow satisfy the scoped acceptance criteria.
- **Engineering tests:** which commands and test cases passed, failed, or did not run.
- **Environment/deployment:** what build and environment were actually exercised.
- **Release readiness:** only report if explicitly in scope and all project release criteria were checked.

Use `passed`, `failed`, `partial`, `blocked`, `not-run`, or `not-applicable`. Use the target project's established vocabulary if it has one and explain any mapping. Missing evidence is not a pass. `blocked` describes a dependency that prevents a check; specify the dependency and recovery condition.

## Copyable report

```markdown
# Web acceptance report

- Project/surface:
- Task mode:
- Requirement source/version:
- Route and workflow:
- Role/permissions:
- Build/commit/environment:
- Browser and version:
- CSS viewport(s):
- Data fixture/source (sanitized):
- Project instructions, UI/API sources, and test guidance reviewed:
- Worktree baseline or preserved existing changes (if implementation was authorized):

## Acceptance trace

| Requirement | Acceptance criterion | Route/state/API | Test layer and case | Evidence | Result |
|---|---|---|---|---|---|

## Page review

- Regions/states reviewed:
- UI layout quality gate:
- Standardized component application gate:
- Global theme consistency gate:
- Component consumers verified:
- Responsive, keyboard/accessibility observations:
- Data/business-flow observations:

## Test execution

| Command or test ID | Purpose | Environment | Result/exit | Evidence or failure detail |
|---|---|---|---|---|

## Findings and gaps

| ID | Severity | Requirement/region | Impact and reproduction | Evidence | Status |
|---|---|---|---|---|---|

- Untested roles/routes/states/viewports/test layers and reason:
- Blockers and recovery conditions:

## Verdict

- Page/flow acceptance:
- Engineering tests:
- Environment scope:
- Release readiness (only if in scope):
- Focused follow-up and regression checks:

## Learning loop

- Project learning entries applied (or why not applicable):
- Prior evidence reused and baseline match:
- Entries added/updated/retired and their regression result, or `no new learning`:
- Known misses or learned checks still open:
```
