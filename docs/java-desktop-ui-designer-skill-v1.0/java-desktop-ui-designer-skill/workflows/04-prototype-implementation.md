# Workflow 04 — JavaFX UI Prototype
# Quy trình 04 — Dựng prototype giao diện JavaFX

## Preconditions

- Screen inventory approved.
- The specific screen specification approved.
- Shared design tokens approved or explicitly accepted as provisional.
- Existing application architecture and project constraints understood.

## Rules

1. Implement only the approved screen and its required shared shell dependencies.
2. Use JavaFX and the project's approved Java architecture.
3. Keep UI classes, stylesheets, assets, and mock data separated.
4. Do not put the entire screen in one giant class.
5. Use mock data and local in-memory state only.
6. Do not connect to SQL Server, PostgreSQL, production services, or Python backend.
7. Do not implement business rules under the guise of UI behavior.
8. Preserve documented control IDs in comments, metadata, or a traceability registry.
9. Use shared components and design tokens; do not create one-off styling without reason.
10. Keep editor technology decisions unchanged unless the user approves a change.

## Suggested structure

Adapt to the existing Java project rather than forcing this exact structure:

```text
src/main/java/
  app/
  shell/
  screens/
    main/
    settings/
  components/
  design/
  mock/

src/main/resources/
  styles/
  icons/
  fonts/
```

## Prototype behavior

Controls should demonstrate safe visual interactions:
- Open/close panels.
- Switch tabs.
- Expand/collapse tree nodes.
- Open menus and dialogs.
- Change local mock selection.
- Show sample grid data.
- Show validation or empty states using mock data.

Actions that would normally mutate data must show a clearly labeled mock confirmation/result and must not call a real backend.

## Deliverables

- Runnable JavaFX prototype.
- List of created/modified files.
- Mapping update.
- Screenshot of target screen.
- Known gaps and deferred behaviors.

Stop after prototype and review. Do not integrate real logic until explicit user approval.
