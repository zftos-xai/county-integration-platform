# Traceability and test strategy

The goal is a reviewable path from an approved requirement to a result:

`requirement → acceptance criterion → page/API behavior → test case → evidence → verdict`

## Requirement-to-test mapping

For each in-scope requirement, record:

| Field | What to capture |
|---|---|
| Requirement | Source and stable identifier or section; mark informal decisions as such |
| Acceptance criterion | Observable expected result, including role, starting state, and outcome |
| Surface | Route/region/workflow, API or data boundary, viewport if relevant |
| Risk | User impact, likelihood, reversibility, permission/data/security impact |
| Test layer | Existing project layer best suited to prove this criterion |
| Scenario | Positive case and relevant boundary, error, denial, or recovery case |
| Evidence | Test id/command/output, browser observation, sanitized request/result, screenshot or trace |
| Result | pass, fail, blocked, not-run, or not-applicable with reason |

For page acceptance, explicitly include separate criteria for **layout quality**, **standardized component application**, and **global theme consistency**. Map each to the page regions/states to inspect and any supporting static, component, or visual regression test. Automated assertions support these checks, but direct review of the actual page remains required for perceptual or contract-level judgments.

An acceptance criterion may need multiple test layers. Conversely, one test may cover several criteria, but preserve explicit links so a green suite does not obscure an uncovered requirement.

## Select the right layer

- **Static/lint/type:** syntax, formatting, type constraints, prohibited patterns. Does not prove runtime behavior.
- **Unit:** deterministic rules, transformations, validation, permission decisions, boundary conditions. Keep external systems stubbed only where project conventions allow.
- **Component:** rendered states, local keyboard/pointer behavior, labels, error display, and component contract. Does not prove the production page actually uses the component or that the API works.
- **Integration/API:** request/response contracts, authentication/authorization, persistence, transactions, and service boundaries using the project's intended environment. A mock response does not prove real persistence.
- **End-to-end/browser:** critical user flows through the real application boundary available to the test, including UI/API integration and role behavior. Avoid asserting unstable implementation details when observable behavior is the contract.
- **Visual/responsive/accessibility/manual:** appearance, layout, content hierarchy, keyboard/focus flow, assistive semantics, viewport behavior, and business plausibility. Use automation as evidence where reliable, with direct browser review for perceptual judgments.
- **Smoke/regression:** essential routes and critical existing workflows after change or deployment. A smoke pass is not exhaustive regression.

Prefer one strong test at the lowest credible layer, plus a higher-level test for cross-boundary risks. Add coverage where failure cost, history, permission sensitivity, or data impact justifies it. Do not impose a universal coverage percentage.

## Scenario design

For each behavior, consider only relevant dimensions:

- expected success;
- empty, missing, boundary, and long-content data;
- validation failure and recovery;
- request timeout/failure and retry/cancel;
- unauthenticated, unauthorized, and role-specific behavior;
- duplicate or repeated actions and idempotency where applicable;
- narrow/short viewport and keyboard/focus behavior for web forms;
- persisted result and read-back when the operation changes data.

Choose representatives by risk rather than generating every combination. Document excluded combinations and why. Keep test data deterministic, isolated, and safe to clean up.

## Test quality checks

Review whether tests are discoverable in the project's normal runner, deterministic, independent, meaningful, and aligned to externally observable behavior. Check that negative assertions are meaningful, waits synchronize on state rather than arbitrary sleep when project tools support it, and failure output helps locate the cause. Flaky tests are not reliable acceptance evidence until stabilized or explicitly qualified.

Do not add an alternate runner, test library, selector convention, or quality gate simply because it is popular elsewhere. First use the destination project's current tools and contracts.
