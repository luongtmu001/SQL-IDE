# Workflow 02 — Screen Inventory
# Quy trình 02 — Kiểm kê màn hình

## Goal

Create a complete list of screens and UI surfaces before analyzing them individually.

## What counts as a screen/surface?

- Main application window.
- Separate top-level windows.
- Settings/preferences pages.
- Modal and non-modal dialogs.
- Side panels and tool windows.
- Menus and context menus with meaningful action sets.
- Popovers, dropdown panels, wizards, and multi-step flows.
- Important empty/loading/error/selection states that materially change layout.

## Steps

1. Inspect navigation entries and window creation calls.
2. Search HTML templates and route/view registration.
3. Search event handlers that open dialogs, menus, panels, or auxiliary windows.
4. Compare source-derived list with what can be reached in the running app.
5. Assign stable screen IDs: `SCR-<AREA>-<NNN>`.
6. Record parent screen, trigger, importance, implementation dependencies, and evidence.
7. Identify recommended migration order based on shared shell dependencies and user priority; do not decide priority silently.

## Required outputs

- `ui-specifications/00-application/screen-inventory.md`
- `ui-specifications/00-application/screen-inventory.json`
- `ui-specifications/00-application/navigation-map.md`

## Inventory status values

- `discovered`
- `partially_observed`
- `observed`
- `blocked`
- `deprecated_candidate` (requires user confirmation; never remove automatically)

## Stop condition

Present the inventory and proposed screen-by-screen order. Wait for the user to select or approve the first screen.
