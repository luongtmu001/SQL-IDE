# Workflow 05 — UI Review & Approval
# Quy trình 05 — Kiểm tra và phê duyệt UI

## Review dimensions

1. **Coverage:** every source region, component, button, and menu action has a target or an approved exception.
2. **Structure:** information architecture and major layout relationships are preserved or intentionally improved.
3. **Visual quality:** hierarchy, typography, spacing, iconography, color, borders, and states are consistent.
4. **Desktop behavior:** resizing, minimum dimensions, focus, keyboard navigation, docking/splitting where required.
5. **Prototype safety:** mock data only; no production operations.
6. **Maintainability:** reusable components, separated styles, modular screen code.
7. **Traceability:** source IDs map to JavaFX IDs and statuses are current.

## Comparison procedure

- Run the source application and JavaFX prototype at comparable window dimensions and display scaling.
- Compare full-screen and region screenshots.
- Inspect every menu and context menu, not only the initial screen.
- Exercise documented safe interactions.
- Record deviations with severity, evidence, and proposed correction.

## Traceability statuses

- `not_started`
- `analyzed`
- `designed`
- `implemented_mock`
- `reviewed`
- `approved`
- `deferred`
- `not_applicable_approved`

Never mark `approved` without user approval.

## Approval request

Present:
- Source screenshot and JavaFX prototype screenshot.
- Summary of preserved and changed layout decisions.
- Coverage counts.
- Known gaps and unresolved behavior.
- Confirmation that no real backend/business logic was integrated.

Wait for explicit user approval before proceeding to backend integration or the next screen if the user requires sequential approval.
