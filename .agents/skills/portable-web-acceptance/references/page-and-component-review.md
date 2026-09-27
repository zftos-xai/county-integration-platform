# Page and component review

Review the real rendered page against the target project's approved UI and business sources. This guide supplies dimensions, not universal visual rules or component implementations. The three page-quality gates below are priority checks for every in-scope page; a green test suite cannot substitute for them.

## Priority page-quality gates

### A. UI layout quality

- Inventory the complete page and relevant overlays, including below-the-fold regions; record the actual viewport and state.
- Check hierarchy, alignment, spacing, density, region relationships, width/height behavior, and whether primary actions and important information remain easy to find.
- Exercise representative short, typical, long, empty, and no-space content where relevant. Find clipping, overlap, unexpected wrapping, unstable layout shifts, hidden actions, horizontal overflow, and unclear scroll ownership.
- Check responsive changes only against the target project's supported profiles and breakpoints. Describe visible impact when no explicit numeric threshold exists; do not invent one.
- Capture evidence across the page, not just a first-screen crop. For materially different regions or states, record each relevant one.

**Gate:** pass only when all required regions in the declared scope have been reviewed at the contracted viewport/state and no unresolved issue violates the project's layout or usability requirements. Missing regions, unsupported content states, or unreviewed required viewports keep the result partial.

### B. Standardized component application

- Identify the project's approved component catalogue or page-level conventions and map relevant controls/regions to their intended component contract.
- Trace actual consumers from page source/call site to rendered DOM and behavior. Check state, keyboard/focus, error, loading, disabled, and responsive behavior against the component contract.
- Look for ad hoc lookalikes, duplicated controls, bypassed wrappers, and page-local CSS that changes component internals or causes inconsistent behavior. Record an approved exception when one exists.
- Confirm reusable components have clear responsibility and local ownership/source where the project defines it. A package export or Storybook is not proof of actual use.

**Gate:** pass only when all in-scope standardized components have traceable real use or a documented project-approved exception. Do not require a shared code package across projects.

### C. Global theme consistency

- Trace page styling to the target project's authoritative theme/tokens and global styles. Review semantic colors, typography, spacing, borders/radii, elevation, focus, and status meaning where those tokens exist.
- Compare repeated controls and equivalent states across relevant pages or shared shells when in scope; identify local hard-coded values or overrides that visibly drift from the global theme.
- Check theme variants only when the project supports them. Check readable contrast and visible focus against the project's adopted accessibility standard.
- Separate a real theme contract violation from a deliberate, documented product/surface exception.

**Gate:** pass only when the page consistently consumes the project's global theme for the reviewed scope, with documented exceptions. If the theme source or required token rules cannot be identified, mark this gate partial instead of guessing.

## Page inventory

List all regions within the review scope: application shell/navigation, page title and object context, filters/search, primary content, secondary panels, actions, dialogs/drawers/popovers, footer/page end, and conditional regions reached by the flow. Include content below the first viewport. Record the role, data state, viewport, and route for each observation.

For each region, inspect:

- purpose and information hierarchy;
- geometry and relationship to neighboring regions;
- typography, theme/tokens, icons, copy, and content density;
- overflow, truncation, clipping, sticky/fixed behavior, and scroll ownership;
- interactions, loading/empty/error/disabled/success states, and recovery;
- keyboard order, focus visibility, accessible names, and field-error association;
- responsive behavior only at project-supported or explicitly scoped viewports.

Use project-defined breakpoints, spacing, touch targets, and accessibility rules. If the project has no stated threshold, report the observable effect and user impact; do not present a personal preference as an approved requirement.

## Component standardization

Within the target project/end, examine each relevant standard component for:

1. Clear responsibility and an owner/source entry point.
2. An explicit contract for properties, events, control model, states, errors, loading, disabled behavior, keyboard/focus, and accessibility.
3. Use of approved project tokens, business rules, permissions, and validation sources.
4. Documented surface-specific adaptation without breaking the local contract.
5. Actual page consumption proven through source/call site and rendered behavior; a package export or showcase page alone is insufficient.

Do not require other projects to use the same source package, component name, API, or design. A page passing does not certify every consumer or freeze the component library.

## Data and business behavior

Where the user flow depends on API or persisted data, distinguish:

- actual empty data from no permission, missing configuration, and request failure;
- real server results from mock/fixture/static content and optimistic UI;
- saved from active/effective, submitted from approved, and queued from completed;
- client-side control visibility from server-side authorization.

Check request/result consistency and read-back only when the environment and authorization permit. Visual evidence alone cannot establish data integrity, correct access control, or durable persistence.
