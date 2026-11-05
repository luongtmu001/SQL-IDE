# Workflow 03 — Analyze One Screen
# Quy trình 03 — Phân tích một màn hình

## Goal

Produce a complete, evidence-based specification for exactly one approved screen. Do not analyze every screen in one huge pass.

## Step 1 — Capture visual references

Capture:
- Full screen at a recorded window size and display scale.
- Each major region.
- Menus and context menus in their open state.
- Dialogs and popovers.
- Important selected, expanded, disabled, empty, loading, and error states.

Use descriptive names, e.g. `main-full-1920x1080.png`, `sidebar-connections.png`, `connection-context-menu.png`.

## Step 2 — Decompose the screen

Create a hierarchy:
Application → Screen → Region → Component → Control/action.

Assign stable IDs:
- Screen: `SCR-MAIN-001`
- Region: `REG-MAIN-001`
- Component: `CMP-MAIN-001`
- Button: `BTN-MAIN-001`
- Interaction: `INT-MAIN-001`
- State: `STA-MAIN-001`

IDs must be stable and must not be renumbered casually. Never reuse an ID for a different purpose.

## Step 3 — Inspect each region

For every region record:
- Purpose.
- Parent and neighboring regions.
- Position and layout relationship.
- Observed size, min/preferred/flexible behavior if measurable.
- Padding, gap, alignment.
- Background, border, typography, icon style.
- Child components.
- Visibility and collapse behavior.
- Resize/docking/floating behavior.
- Screenshot evidence.

## Step 4 — Inspect every component and button

For each control:
1. Record exact visible label or accessible name.
2. Record icon and tooltip.
3. Locate its HTML element or creation code.
4. Trace JavaScript event listener or framework callback.
5. Trace Python bridge method and relevant backend method when applicable.
6. Determine preconditions, result, confirmation, error handling, and side effects.
7. Test only safe, non-destructive interactions in a permitted environment.
8. Mark evidence as `observed`, `code_verified`, `inferred`, or `unknown`.

Do not trigger production writes, deletes, database changes, or external side effects merely to discover behavior.

## Step 5 — Inspect interaction states

At minimum consider:
- Normal, hover, pressed, focused, disabled.
- Selected/active.
- Expanded/collapsed.
- Loading/busy.
- Empty/no data.
- Error/validation.
- Context-dependent visibility.
- Keyboard navigation and shortcuts.

Only include states relevant to the control; mark unobserved states explicitly.

## Step 6 — Map to JavaFX

For each region/component propose:
- JavaFX standard control, custom component, or retained embedded technology.
- Reason for the mapping.
- Important behavior that needs custom implementation.
- Performance or accessibility concerns.
- Whether the mapping is approved or only proposed.

## Required files for each screen

```text
ui-specifications/01-screens/<screen-id>/
  screen-spec.md
  layout.json
  components.json
  interactions.json
  states.json
  mapping.json
  evidence.md
  screenshots/
```

Use the templates in `templates/`. Validate JSON syntax before reporting completion.

## Step 7 — Report and stop

Report coverage counts and unresolved items. Ask the user to review the specification and proposed redesign. Do not implement JavaFX until the screen specification and design direction are approved.
