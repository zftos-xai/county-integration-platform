# Portable Web Acceptance Skill — county integration platform edition

A project-agnostic acceptance skill that connects requirements, real Web pages, behavior, tests, evidence, and regression. It standardizes the review process while leaving business rules, components, architecture, and test stack to each destination project.

This installation is tailored for the county integration platform. Before using it here, read `references/county-integration-project.md` for the management, embedded, and synthetic prototype boundaries; formal menu delivery stages; and the repository's verification commands and acceptance risks. Repository instructions and standards remain authoritative.

## Install

Copy the `portable-web-acceptance` directory into the destination project:

- Codex: `<project>/.agents/skills/portable-web-acceptance/`
- Claude Code: `<project>/.claude/skills/portable-web-acceptance/`
- Other Agent Skills-compatible tools: use that tool's project skill directory.

Keep `SKILL.md` and the `references/` directory together. The `agents/openai.yaml` file provides Codex UI metadata and can be ignored by other agents. Start a new agent session if skill discovery happens only at startup. Check each tool's current documentation for additional discovery rules.

## Use

Ask the agent to use `portable-web-acceptance` and provide the target feature/route, expected role, supported surface, and whether the task is review-only, test planning, test execution, or authorized repair. The agent should inspect the destination project's own instructions and existing scripts before proposing or running tests.

This package does not install a browser runner, impose coverage percentages, certify production release, or share component code across projects.

## Contents

- `SKILL.md`: end-to-end acceptance process.
- `references/traceability-and-tests.md`: requirement-to-test mapping and test-layer selection.
- `references/page-and-component-review.md`: page, component, and business/data checks.
- `references/mobile-h5.md`: mobile responsive browser Web.
- `references/management-web.md`: administration and operations workbenches.
- `references/verdicts-and-report.md`: bounded result vocabulary and report template.
- `references/county-integration-project.md`: local frontend surfaces, menu evidence gates, toolchain, and project-specific acceptance risks.
- `references/usage-examples.md`: copyable starting prompts for formal management menus, synthetic prototypes, embedded pages, and authorized repair/retest.
