---
name: project-engineering-guardrails
description: "Audit, adopt, enforce, or roll out executable repository engineering standards and CI guardrails. Use when a user asks to improve coding standards, inspect project conformance, establish team governance, migrate standards to another project, or align Java/MyBatis, Vue/TypeScript, and SQL Server delivery rules. Do not use for ordinary feature work unless standards compliance or delivery gates are an explicit outcome."
---

# Project Engineering Guardrails

Turn written standards into repository-local rules, deterministic checks, and adoption evidence. Keep universal governance separate from stack-specific and project-specific constraints.

## Workflow

1. Read the target repository's `AGENTS.md` and applicable standards completely. Repository instructions override this skill.
2. Run `scripts/audit_repository.py <repo>` for a deterministic baseline. Treat its result as an inventory, not proof that code is correct.
3. Classify every proposed rule:
   - team baseline: security, review, traceability, verification, definition of done;
   - stack profile: only when the detected stack applies;
   - project override: package names, ports, compatibility versions, domain vocabulary, and regulatory constraints.
4. Build a traceability map from each MUST rule to an automated gate or an explicit review checkpoint.
5. Fix safe governance gaps in bounded batches. Do not mass-format or redesign unrelated code merely to make the audit green.
6. Run the project's own verification entry point. Report commands and actual results; never infer success from file presence.
7. For rollout, pilot in one repository and one real change before enabling required checks across the team.

## Rule Design

- Use MUST, SHOULD, and MAY consistently; define the enforcement consequence for MUST.
- A rule must state scope, rationale, verification method, and exception path.
- Prefer compilers, linters, tests, migration validators, and UI acceptance evidence over prose-only review.
- Preserve upstream artifacts and repository-specific requirements. Never fabricate API contracts, environment assumptions, or verification evidence.
- Keep generated or AI-assisted code subject to the same requirements as handwritten code.
- Make exceptions explicit, owned, time-limited, and paired with compensating controls.

## Stack Routing

Read [stack-checklists.md](references/stack-checklists.md) only for detected or requested stacks:

- Vue or TypeScript frontend;
- Java, Spring, or MyBatis backend;
- SQL Server schema and migration work;
- browser UI acceptance.

Read [adoption-and-rollout.md](references/adoption-and-rollout.md) when creating a team baseline, moving rules to another project, designing exceptions, or versioning a standards package.

## Deliverables

For an audit, provide:

- applicable rule layers and detected stack;
- findings grouped by severity, with file evidence;
- rule-to-gate gaps;
- fixes completed and remaining exceptions;
- exact validation commands and outcomes.

For implementation, prefer these durable repository artifacts:

- `AGENTS.md` for agent-visible project constraints;
- Markdown standards for human-readable policy;
- `.editorconfig` and stack configuration for deterministic defaults;
- one local verification entry point reused by CI;
- a versioned plugin or skill only when cross-project reuse is required.

Do not claim that installing this skill enforces policy. Enforcement comes from repository configuration, protected branches, CI, review ownership, and real validation.
