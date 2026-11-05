# Workflow 01 — Project Discovery
# Quy trình 01 — Khảo sát dự án

## Goal / Mục tiêu

Understand the actual Python/PyWebView application structure before documenting screens. Do not redesign or modify code in this phase.

## Steps

1. Locate project root, entry point, startup sequence, and window creation code.
2. Identify Python modules responsible for windows, dialogs, menus, and backend bridge methods.
3. Identify HTML templates, CSS, JavaScript entry points, routes/views, and dynamically loaded UI.
4. Identify navigation registration and window-opening mechanisms.
5. Trace bridge methods and event handlers only to understand UI behavior and outcomes.
6. Run the application if safe and permitted. Do not execute destructive actions or connect to production data.
7. Capture application screenshots at a known window size and display scale.
8. Record inaccessible screens and environmental blockers.

## Required output

Create:
- `ui-specifications/00-application/project-map.md`
- `ui-specifications/00-application/application-map.json`
- `ui-specifications/00-application/source-index.md`
- `ui-specifications/00-application/discovery-report.md`

## Project map contents

- Application entry point and startup flow.
- Window inventory and how each window is opened.
- UI technology stack and asset locations.
- Python ↔ JavaScript bridge overview.
- Shared styles, icons, fonts, and themes.
- Important dynamic rendering mechanisms.
- Known runtime limitations.

## Stop condition

Do not proceed to detailed screen redesign until the user has reviewed the discovery summary and agreed on the screen inventory scope.
