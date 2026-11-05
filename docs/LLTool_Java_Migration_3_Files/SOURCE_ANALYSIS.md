# LLTool Python Source Analysis — Reference Guide

## 1. Purpose

This document guides the analysis of the existing Python LLTool project before and during migration to Java.

The purpose is to discover **what the current product actually does** so the Java implementation can preserve its established workflows while presenting them in a modern native desktop UI.

This is an evidence-based inventory, not a speculative redesign document.

## 2. Source project

- Repository: `luongtmu001/IDE_SQL_Lite_Pywebview`
- Project folder: `IDE-SQL-Lite_New_Pyweb`
- Existing product: LLTool
- Existing technology: Python backend, pywebview/WebView2, HTML, CSS, JavaScript, Bootstrap, Monaco Editor.

Inspect the current repository state before relying on this document. File names and implementation details may have changed.

## 3. Analysis principles

- Inspect source code; do not infer behavior from filenames alone.
- Trace each important UI action from event handler to backend and persistence layer.
- Separate confirmed facts, reasonable inferences, and unanswered questions.
- Record existing behavior before proposing changes.
- Identify defects and technical debt separately from intended behavior.
- Do not treat every existing visual detail as mandatory. Preserve information hierarchy and workflows, then adapt presentation to native desktop conventions.
- Do not assume a library is active merely because its CSS or script is imported.

## 4. Initial UI map to verify

The following is an initial map from earlier source inspection. Re-verify it against the current repository before implementation.

```text
LLTool Main Window
├── Top application area
│   ├── Menus / navigation
│   ├── Toolbar and common actions
│   ├── Theme / settings / environment controls
│   └── Feature launchers
├── Main workspace
│   ├── Object Explorer
│   │   ├── Connections and groups
│   │   ├── Database / schema / object tree
│   │   ├── Search and filtering
│   │   └── Resize / collapse behavior
│   └── Editor area
│       ├── Query action bar
│       ├── Editor tabs
│       ├── Monaco editor
│       ├── Editor status information
│       └── Results area
│           ├── Results
│           ├── Messages
│           ├── Execution plan
│           └── Other structured viewers
└── Application status area
```

Other screens or workspaces previously observed in markup include Table Designer, Table Data Editor, Dependency Graph, Object Content Search, SQL Visualizer, Activity Monitor, Schema Compare, User Permissions, and connection management. Confirm whether each is reachable and functional before adding it to the migration inventory.

## 5. Source inspection checklist

For each screen or feature, inspect the relevant items where they exist:

| Area | Inspect |
|---|---|
| Entry point | Window creation, route/screen initialization, startup sequence |
| Markup | Main template, partials, dialogs, hidden panels |
| Styling | CSS files, theme tokens, responsive rules, state classes |
| Events | Click, keyboard, context menu, drag, resize, selection handlers |
| Frontend state | Active tab, selected connection, current database, panel state |
| Backend | API routes, Python handlers, service functions |
| Data | Models, SQL statements, database adapters, file formats |
| Persistence | Settings, connection data, snippets, history, local storage |
| Errors | Validation, exceptions, user-facing messages, retries |
| Tests | Unit tests, integration tests, manual test notes |

### Previously identified files to re-check

These are starting points, not a complete or guaranteed-current list:

- `templates/index.html`
- `templates/partials/sidebar.html`
- `templates/partials/editor.html`
- `templates/partials/results.html`
- `static/css/app.css`
- `static/css/editor.css`
- `static/css/theme-variables.css`
- `static/css/themes.css`
- `static/js/app.js`
- `static/js/tabs.js`
- `static/js/monaco-init.js`
- `static/js/editor.js`

The main `index.html` was previously observed to contain substantial inline markup. Determine the actual source of truth for each component before migrating it.

## 6. UI behavior inventory

For every major UI region, document:

- Purpose.
- Default visibility and size.
- Parent/child relationships.
- User actions.
- Keyboard shortcuts.
- Selection and focus behavior.
- Context menu actions.
- Resizing, collapsing, and expanding.
- State persistence.
- Empty, loading, success, and error states.
- Connections to other features.

Do not record only a static screenshot. A screenshot cannot explain tab lifecycle, keyboard shortcuts, context menus, data flow, or state transitions.

## 7. Initial behavior areas to trace

### Main window and navigation
- Startup sequence and initial screen.
- Menu and toolbar commands.
- Theme changes.
- Settings and auxiliary windows.
- Status feedback.

### Object Explorer
- Connection lifecycle.
- Connection groups and saved connections.
- Tree expansion and lazy loading.
- Database/schema/object navigation.
- Search and filtering.
- Context menus and generated actions.
- Refresh behavior and error states.

### Query editor
- Editor initialization and language configuration.
- Tab creation, switching, closing, pinning, and dirty state.
- Connection/database/schema context per tab.
- Open/save behavior if supported.
- Run, stop, formatting, parsing, and execution-plan actions.
- Editor status and keyboard shortcuts.

### Query execution and results
- Request/response lifecycle.
- Multiple result sets.
- Column metadata and data types.
- Sorting, filtering, copying, and exporting if supported.
- Messages and execution timing.
- Cancellation and errors.
- Large-result behavior.

### Settings and persisted data
Identify the actual read/write locations and formats for:
- `.secret.key`
- `connections.json`
- `programgroups.json`
- `programlist.json`
- `settings.json`
- `snippets.json`
- Query history and any other user-owned files.

Verify these names and locations in the current source before treating them as contractual. Preserve existing user data and document any format conversion.

## 8. Java UI translation principles

Use the Python UI to understand the product's structure and interaction patterns. Translate those patterns into a native-feeling JavaFX interface.

| Existing concept | Java desktop direction |
|---|---|
| Main page / application shell | JavaFX `Stage` and `Scene` |
| Main layout regions | `BorderPane`, `VBox`, `HBox`, `SplitPane` |
| Navigation and commands | `MenuBar`, `ToolBar`, JavaFX buttons |
| Object tree | `TreeView` with custom cells where needed |
| Editor tabs | JavaFX tab/header system or a custom tab strip |
| SQL editor | Monaco hosted in WebView |
| Results table | JavaFX `TableView` initially, behind a replaceable result-grid boundary |
| Dialogs | JavaFX dialogs or custom JavaFX stages |
| Context menus | JavaFX `ContextMenu` |
| Resizable panels | `SplitPane` and controlled panel visibility |

This mapping is a guide, not a requirement to force every old web component into a one-to-one JavaFX control.

## 9. Visual direction

The target is a **modern native desktop SQL IDE**.

Use the existing LLTool UI as a reference for:
- Information architecture.
- Important actions and their placement.
- Workflow familiarity.
- Domain terminology.
- Useful density and panel relationships.

Improve the presentation with:
- Native-feeling window and menu behavior.
- Consistent spacing and typography.
- Clear visual hierarchy.
- Well-designed toolbars, tabs, splitters, and status areas.
- Consistent iconography and states.
- Strong keyboard and focus behavior.
- Sensible light/dark theme tokens.
- Smooth resizing without web-page-like layout artifacts.

Do not turn the migration into a broad redesign project. Any visual change should support clarity, desktop usability, or consistency.

## 10. Required analysis output per feature

Use this template:

```markdown
## Feature: [Name]

### Source evidence
- Files:
- Functions / classes / handlers:
- API routes:
- Database tables / queries:
- Persistence:

### Existing user workflow
1.
2.
3.

### Current behavior
- Normal path:
- Validation:
- Error path:
- State changes:
- Side effects:

### UI structure
- Main components:
- Actions:
- Shortcuts:
- Context menus:
- Resizing / visibility:
- Empty / loading / error states:

### Dependencies
-

### Unknowns / assumptions
-

### Suspected defects
-

### Java migration notes
- Native JavaFX components:
- Monaco/WebView requirement:
- Java services:
- JDBC/provider concerns:

### Acceptance criteria
- [ ] 
```

## 11. Analysis completion rule

A feature is ready for implementation only when:
- Its main user workflow is documented.
- Relevant source files and event handlers are identified.
- Backend and persistence dependencies are known or explicitly marked unknown.
- Its UI behavior is understood well enough to reproduce.
- Acceptance criteria can be tested.

If evidence is incomplete, inspect further or record the uncertainty. Do not fill gaps with invented behavior.
