# LLTool Java Migration — Agent Rules

> **Core principle: Preserve Functionality, Modernize the Experience, Replace the Technology.**

## 1. Mission

Migrate the existing Python LLTool SQL IDE to a Java desktop application **one existing feature at a time**.

The Python project is the functional reference. Its established workflows, business rules, data meanings, and user expectations must be understood before they are reimplemented.

The Java application must not become a new product with an unrelated workflow. At the same time, the Java UI does not need to reproduce every pixel of the old web UI. It should feel like a polished, modern, native desktop application.

### Priority order

1. Preserve existing user-visible functionality and important behavior.
2. Preserve data meaning, SQL behavior, and workflow expectations.
3. Build a modern, native-feeling desktop experience.
4. Improve implementation quality, maintainability, accessibility, and performance.
5. Add genuinely new features only after the migration baseline is stable.

## 2. Technology baseline

Use the current project decisions unless the user explicitly changes them:

- Java 25
- JavaFX 25
- Maven
- JavaFX controls and JavaFX CSS for the desktop interface
- Ikonli or another already-approved icon library
- Monaco Editor hosted in JavaFX WebView where Monaco is required
- JDBC for Java database connectivity

Do not introduce another UI framework, editor, grid, or major dependency without explaining the trade-offs and getting approval.

## 3. Work incrementally

Migrate in small, verifiable slices. Do not attempt a large rewrite of the entire application in one task.

For each feature, follow this sequence:

1. **Inspect** the relevant Python source, templates, CSS, JavaScript, API routes, database calls, configuration, and related tests.
2. **Describe current behavior** in plain language, including user actions, state changes, validation, errors, and data effects.
3. **Identify dependencies** on other features or shared services.
4. **Plan the Java implementation** and define what is in scope and out of scope.
5. **Implement the smallest complete vertical slice** needed for the feature.
6. **Verify** behavior and visual quality.
7. **Update migration records** and report deviations or remaining gaps.

Do not begin implementation based only on a feature name or a screenshot when source behavior can be inspected.

## 4. Treat the Python project as the source of truth — with judgment

Before changing or migrating an existing feature:

- Find its actual implementation and entry points in the Python project.
- Trace the UI event through JavaScript/API/backend/database where relevant.
- Record defaults, edge cases, validation, error handling, and side effects.
- Distinguish confirmed behavior from assumptions.
- Reuse existing terminology and data semantics unless there is a clear reason to change them.

Do not blindly copy an obvious defect. Record suspected defects separately. Do not silently change meaningful behavior: explain the issue and obtain approval before changing the expected workflow or business rule.

## 5. UI direction: reference the old product, build a native-feeling desktop app

The existing LLTool UI is a **reference for information architecture and workflows**, not a pixel-perfect specification.

### Preserve

- The purpose and hierarchy of existing screens and panels.
- Important actions, commands, shortcuts, context menus, and status feedback.
- Existing user workflows and expected outcomes.
- Useful labels and domain terminology.
- The visibility and relationship of important information.

### Modernize

The Java UI should feel like a contemporary desktop application rather than a web page placed inside a window.

Prefer:

- Native JavaFX controls where they provide a good experience.
- Clear window chrome, menus, toolbars, panels, tabs, splitters, dialogs, and status areas.
- Consistent spacing, typography, icon sizing, focus states, hover states, and keyboard navigation.
- A restrained design system with reusable color, spacing, typography, border, and radius tokens.
- Responsive resizing and sensible minimum sizes.
- Light and dark themes if the existing project scope includes them.
- Native-feeling context menus, dialogs, selection, and keyboard interaction.

Avoid:

- Copying Bootstrap/web styling mechanically into JavaFX.
- Reproducing web-only layout quirks that do not make sense on desktop.
- Decorative effects that reduce readability or performance.
- Replacing a familiar workflow merely to make the screen look different.

**Rule:** modernize presentation; preserve the user's mental model and feature behavior.

## 6. UI implementation rules

- Keep view code, view models/controllers, services, and data access separated.
- Do not place the entire application in one Java class.
- Use JavaFX controls for normal desktop UI.
- Use WebView only for components that genuinely require web technology, especially Monaco.
- Keep one primary Monaco WebView/editor workspace unless a demonstrated requirement justifies more.
- Do not embed large HTML, CSS, or JavaScript strings inside Java classes.
- Keep web assets in separate files and define a clear Java↔JavaScript bridge.
- Do not couple the rest of the application directly to Monaco's internal JavaScript objects.
- Make panels resizable and support sensible collapse/expand behavior where applicable.
- Keep the result grid behind a replaceable component boundary so its implementation can be upgraded later.

## 7. Feature migration rules

Migrate existing features first. Do not mix unrelated feature additions into a migration task.

For each feature, define:

- Existing Python behavior.
- Java UI scope.
- Java application/service scope.
- Database or external integration scope.
- Dependencies.
- Acceptance criteria.
- Known limitations.
- Verification evidence.

A feature is not complete merely because its screen exists. It must perform the relevant existing workflow, or be explicitly marked as a UI-only milestone.

Use mock data only when the task is explicitly a UI prototype. Keep mock-data boundaries obvious and do not present mock behavior as completed functionality.

## 8. Additions and scope control

New features are out of scope during baseline migration unless the user explicitly requests them.

When an idea for a new feature appears:

1. Record it in a future-enhancements list.
2. Do not implement it as part of the current migration slice.
3. Do not let it delay parity for existing functionality.

Small technical changes required to support the migration are allowed when they do not introduce new user-facing scope.

## 9. Data and compatibility

- Do not overwrite or discard existing user data.
- Identify configuration files, connection definitions, saved groups, snippets, history, and other persisted data before changing storage.
- Preserve field meanings and compatibility where practical.
- Provide an explicit migration or import strategy before changing formats.
- Never place credentials or secrets in source control.
- Do not connect to or modify a real database during a UI-only task.

## 10. Database behavior

The existing product supports database workflows including SQL Server and PostgreSQL. Preserve the behavior of each supported provider when migrating a feature.

- Keep database-specific behavior explicit; do not assume SQL dialects are interchangeable.
- Use JDBC and isolate provider-specific code.
- Preserve transaction boundaries, parameter handling, result metadata, error reporting, and cancellation behavior where present.
- Do not execute destructive or modifying SQL during tests unless the user has explicitly approved a safe test environment.

## 11. Performance and reliability

- Avoid blocking the JavaFX Application Thread with database, file, or network work.
- Use background tasks/services for long-running operations.
- Keep large result rendering responsive.
- Avoid unnecessary WebView instances and duplicated editor models.
- Handle exceptions at meaningful boundaries and show useful messages to users.
- Do not silently swallow errors.

## 12. Verification requirements

For every migrated feature, verify as applicable:

- Normal workflow.
- Empty, missing, and invalid input.
- Error and cancellation paths.
- Keyboard and mouse interaction.
- Resizing and layout behavior.
- Light/dark theme behavior if supported.
- Data correctness and side effects.
- Regression against the Python reference.

Use screenshots for visual review where useful. Compare hierarchy, density, alignment, and workflow—not pixel identity.

## 13. Migration status vocabulary

Use these statuses consistently:

- `NOT_ANALYZED` — not inspected yet.
- `ANALYZED` — Python behavior and dependencies documented.
- `PLANNED` — Java scope and acceptance criteria agreed.
- `UI_IN_PROGRESS` — Java UI is being built.
- `UI_VERIFIED` — visual and interaction shell verified; backend may still be absent.
- `IMPLEMENTING` — functional Java implementation underway.
- `FUNCTIONALLY_VERIFIED` — behavior and data effects verified.
- `COMPLETE` — acceptance criteria met and regression checks passed.
- `BLOCKED` — waiting on a dependency or decision.
- `DEFERRED` — intentionally postponed.
- `APPROVED_DEVIATION` — behavior or design intentionally differs with approval.

Never mark a feature `COMPLETE` just because it compiles or displays mock data.

## 14. Required task report

At the end of each task, report:

1. What Python source was inspected.
2. What existing behavior was identified.
3. What was implemented in Java.
4. What was intentionally not implemented.
5. How it was verified.
6. Remaining gaps, risks, and assumptions.
7. Which migration tracker entries were updated.

## 15. Definition of done

A migration slice is done only when:

- Its scope matches the agreed plan.
- Existing behavior is understood and preserved, or an approved deviation is recorded.
- The Java implementation follows the project boundaries.
- The UI feels consistent with the modern native desktop direction.
- Relevant normal and edge cases are verified.
- No unrelated feature additions were introduced.
- Documentation and migration status are updated.
