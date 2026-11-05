# Quality Gates & Acceptance Criteria
# Cổng chất lượng và tiêu chí nghiệm thu

## Gate 1 — Discovery completeness

- [ ] Main window and auxiliary windows identified.
- [ ] Menus, dialogs, popovers, context menus, and secondary states inventoried.
- [ ] Source files and entry points recorded.
- [ ] Unknown/unreachable screens explicitly listed.
- [ ] No screen assumed from memory without source evidence.

## Gate 2 — Screen specification completeness

- [ ] Full-screen screenshot captured where possible.
- [ ] Every region has a stable ID.
- [ ] Every component and button has a stable ID.
- [ ] Menu hierarchy documented.
- [ ] Interactions and state transitions documented.
- [ ] Resizing, empty, loading, error, and disabled states considered.
- [ ] Evidence and uncertainty recorded.
- [ ] Python-to-JavaFX mapping exists.

## Gate 3 — Prototype quality

- [ ] JavaFX prototype launches.
- [ ] Uses mock/sample data only.
- [ ] No real backend or database operations.
- [ ] Layout behaves correctly when resized.
- [ ] All documented controls are represented.
- [ ] Shared design tokens and components are used.
- [ ] No placeholder controls masquerade as completed functionality.
- [ ] No unrelated files or logic modified.

## Gate 4 — Visual and interaction review

Compare source and target side by side. Review:
- Information architecture and region coverage.
- Relative sizing and hierarchy.
- Typography, spacing, icons, and color roles.
- Button/menu coverage.
- Keyboard and mouse interactions.
- Focus, hover, disabled, selected, loading, empty, and error states.
- Minimum window size and resizing.
- Readability at the user's display scaling.

## Completion status

Use only:
- `PASS`
- `PASS_WITH_KNOWN_GAPS`
- `NEEDS_REVISION`
- `BLOCKED`

A screen cannot be marked `PASS` if undocumented controls or unverified critical interactions remain.
