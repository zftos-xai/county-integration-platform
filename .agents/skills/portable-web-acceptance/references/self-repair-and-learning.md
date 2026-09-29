# Self-repair and learning loop

Use this with the repository's [project acceptance learning record](project-learning.json). Learning entries are actionable regression memory; they do not replace live project requirements or expand the user's authorization.

## Before the pass

1. Read every `active` entry and check whether its scope matches this task. Convert applicable entries into explicit route/region/state checks in the acceptance trace. Record a reason when an entry does not apply.
2. Reopen applicable defects and user-corrected observations from prior reports. A previous pass is a lead, not proof that the issue is fixed.
3. Reuse evidence only when commit/build, route, identity, data, browser, viewport, and state still match. If one changed, rerun the affected assertion only; avoid repeating unrelated checks.

## When a miss or contradiction occurs

1. Stop expanding the broad pass. Preserve first-failure evidence and the original report; mark contradicted outcomes failed or partial as supported by evidence.
2. Classify the cause as product defect, acceptance omission, test defect/flakiness, environment failure, or missing requirement/evidence. Keep unknown causes unknown.
3. Turn the miss into a reproducible check and add it to the current trace. For annotated screenshots, treat each marked region as a separate assertion and verify it on the actual page.
4. If implementation repair is authorized, fix the narrowest real owner and add/update a regression check. If repair is not authorized, leave an explicit gap and recovery condition.
5. Run the focused regression and affected neighbor checks. Close a finding only on new passing evidence; do not erase the initial failure.

## At close: update project learning

Add or update an entry only for a confirmed user correction, missed finding, repeated unproductive check, or durable improvement. Each entry must state:

- `id`, `status` (`active` or `retired`), `source`, and `scope`;
- the observed `trigger` and the resulting `required_action`;
- a concrete `regression_check` and evidence needed to verify it;
- `last_verified` or `null` until the regression check has actually passed.

Do not record credentials, tokens, cookies, patient data, guesses, or one-off visual preferences as universal project requirements. User scope in the current task overrides remembered scope. Retire stale entries only after identifying the changed requirement or source. State in the report which entries were used and whether each regression check passed; state “no new learning” when appropriate.
