---
name: portable-web-acceptance
description: Plan and perform evidence-based acceptance of browser-based Web features in this county integration platform, tracing project requirements to Vue pages, permissions, APIs, tests, and browser evidence. Use for management, embedded, or prototype page acceptance, test planning, execution, or authorized fix-and-retest work. Not a release, security, or database certification.
---

# Portable Web Acceptance

Review Web work from the approved requirement through the real user-facing page and the appropriate engineering tests. This skill provides a portable process, not product requirements, design decisions, code, or a required framework. The destination project's active rules, contracts, and the user's current request are authoritative.

For this repository, read [County integration project profile](references/county-integration-project.md) before setting scope or deciding what evidence can support a verdict. It identifies the three separate Web surfaces and the project's formal menu acceptance gates.

When the user needs a starting prompt or the task boundary is still being written, use [Usage examples](references/usage-examples.md) and adapt the example to the named feature. Examples are prompts, not evidence or permission to perform writes.

## Operating rules

1. **Read the destination project's rules first.** Locate its agent instructions, active requirements/specs, architecture decisions, design system, API contracts, permission rules, and test commands. Use the target project's actual stack and conventions. Keep missing or conflicting facts explicit; never fill them with assumptions from another project.
2. **Define the task mode and boundary.** Distinguish analysis, planning, review-only, implementation, and authorized repair. Do not turn a review into code changes, data writes, commits, deployments, or publication unless requested and allowed.
3. **Standardize acceptance practice, not project code.** Projects and product surfaces may have independent components, APIs, tokens, page structures, and test stacks. Do not require cross-project component packages, identical UI, or identical tools. Within the reviewed project/surface, verify its own component contract and actual use.
4. **Lock the acceptance trace.** For each in-scope requirement, identify one or more observable acceptance criteria, the page/flow/state it affects, the test level that can prove it, and the evidence to collect. See [Traceability and test strategy](references/traceability-and-tests.md).
5. **Use the smallest adequate test set, then expand by risk.** Run required preflight and smoke checks before broader suites. Prefer focused tests for the changed behavior, then impacted regression. Do not run every possible role × state × viewport combination mechanically, and do not silently omit required cases.
6. **Use the existing project toolchain.** Read package scripts, CI workflows, test config, and existing fixtures before choosing commands. Do not install a second browser runner, duplicate an existing suite, or add dependencies unless explicitly authorized. A skill or plugin can help execute checks but cannot change acceptance criteria.
7. **Keep evidence honest and bounded.** Separate code/test results, browser observations, API/data checks, and visual review. Passing one layer does not prove another. A page result applies only to the stated build, route, role, state, data, browser, and viewport.
8. **Protect data and the worktree.** Preserve pre-existing modifications. Use approved test environments and data; redact secrets and personal data. Write, delete, or mutate data only when explicitly authorized and safe under project rules. Keep verification artifacts in the project's ignored/default evidence location.

## End-to-end workflow

### 1. Establish facts and scope

Record the task mode, baseline/worktree state when relevant, target project and surface, feature or defect, routes/workflows, roles, permissions, supported browsers/viewports, affected APIs/data, and expected deliverable. Read project-local instructions and identify the authoritative requirement and design/API/test sources. Separate confirmed requirements from implementation facts, assumptions, and unknowns.

For implementation or broad regression work, prepare a compact change-to-verification checklist before changing code: allowed scope, must-preserve behavior, affected areas, stop conditions, and evidence needed. Do not rewrite an approved plan to match an implementation that has already drifted.

### 2. Convert requirements into acceptance criteria

Write observable outcomes rather than vague goals. For each criterion, include where relevant:

- actor/role and permission;
- starting state and preconditions;
- user action or event;
- expected UI response and resulting business state;
- API/data effect or explicit read-only expectation;
- error, empty, loading, denied, retry, cancel, and recovery behavior;
- relevant viewport/surface and accessibility expectations;
- evidence that will prove the outcome.

Trace criteria to real page regions and states. Inventory the full visible page, including below-the-fold content, navigational context, primary and secondary regions, overlays, and page end. Identify conditional regions used by the target workflow. Give focused attention to three page-quality gates: **UI layout quality, standardized component application, and global theme consistency**. Inspect them on the real page, not only in source or screenshots. Read [Page and component review](references/page-and-component-review.md).

### 3. Choose test layers

Map each criterion to the least expensive layer that can credibly prove it, adding layers where risk requires. Typical layers are:

- static checks, lint, formatting, and type checking;
- unit tests for deterministic logic and edge cases;
- component tests for rendered states and local interactions;
- integration/API/database tests for contracts, permissions, persistence, and data transformations;
- end-to-end browser tests for critical user journeys and real UI/API wiring;
- visual, accessibility, responsive, and manual browser checks for properties automation cannot establish reliably;
- smoke/regression checks for deployment readiness and impacted existing behavior.

These are options, not a mandatory stack. Use the project's existing levels and commands. Avoid testing the same assertion redundantly at every layer unless different layers prove distinct risks. Every required acceptance criterion must have an evidence path; if automation is impractical, define an explicit manual check.

### 4. Prepare safe, reproducible execution

Before running commands, inspect project instructions and scripts, test environment, service dependencies, test data, and current state. Confirm the target is not production unless the project expressly defines a safe production check. Do not place credentials in command output, screenshots, test reports, or committed fixtures. Use bounded timeouts for long-running commands as project instructions require. If environment setup fails, stop the dependent checks, record the blocker, and continue only independent review work.

Tests that create or update records must use explicitly approved fixtures or test data and have a safe cleanup/read-back plan. If write authorization, isolated data, or cleanup cannot be confirmed, do not perform the mutation; mark that criterion unverified.

### 5. Execute in risk order

Recommended order, adapted to project guidance:

1. Confirm repository/build/service/environment preconditions.
2. Run a narrow smoke or smallest focused check to validate the path.
3. Run tests directly covering changed requirements and affected APIs/components.
4. Review the real page in the supported browser for visual hierarchy, content, state behavior, responsive layout, keyboard/focus, and business plausibility.
5. Run impacted regression suites and broader required checks.
6. Re-run failed or affected checks after any authorized fix.

Do not treat a command starting successfully as a passed test. Capture exact command, environment/build identifier, exit/result, and relevant output. For browser-based assertions, record route, role, browser, exact CSS viewport, state, and evidence location. Screenshots are supporting visual evidence, not proof of data integrity, authorization, or all accessibility behavior.

### 6. Classify failures and follow up

Distinguish product defect, test defect/flakiness, environment/setup failure, and missing requirement/evidence. Preserve first-failure evidence before repair. If authorized to fix, repair the smallest mapped scope, update or add a regression test at the layer that protects the behavior, then rerun the focused test and impacted neighboring checks. Do not hide an initial failure by replacing evidence or changing the requirement after the fact.

### 7. Report results

Use [Verdicts and report template](references/verdicts-and-report.md). Report requirement/page status and engineering test status separately. State any untested roles, routes, states, viewports, browsers, data paths, or test layers. Do not claim full release readiness from a local page review or a green unit test suite.

## Surface references

- For mobile responsive browser H5, read [Mobile H5](references/mobile-h5.md).
- For desktop administration and operations workbenches, read [Management Web](references/management-web.md).
- For this repository's formal-menu lifecycle, three isolated Web surfaces, and required local evidence sources, read [County integration project profile](references/county-integration-project.md).
- For copyable prompts covering formal menus, synthetic prototypes, and embedded pages, read [Usage examples](references/usage-examples.md).

If the target is a native application, a physical-device certification, or a release/security/compliance audit, use the project's specialized standard and add the required evidence. This browser Web process alone does not certify those scopes.
