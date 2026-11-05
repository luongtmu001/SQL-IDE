# SQL IDE - Modern Desktop Application

A modern desktop SQL IDE built with JavaFX, following the JetBrains DataGrip and modern IDE design principles.

## Tech Stack
- **Language**: Java 21+ (Tested on Java 25)
- **UI Framework**: JavaFX 21
- **Themes**: AtlantaFX (Primer Dark / Light, Dracula, Nord Dark / Light, Cupertino Dark / Light)
- **Icons**: Ikonli (Feather pack & MaterialDesign2 pack)
- **Controls**: ControlsFX
- **Editor PoC**: RichTextFX
- **Build Tool**: Apache Maven (Wrapper included)

## Requirements
- JDK 21 or higher installed (`JAVA_HOME` configured).

## How to Build and Run

### Run Unit Tests
```cmd
.\mvnw.cmd test
```

### Run Application
```cmd
.\mvnw.cmd javafx:run
```

## Phase 1 Progress
- [x] **Phase 1.1 – Project Foundation**: Maven project initialization, dependency integration (JavaFX, AtlantaFX, ControlsFX, Ikonli, RichTextFX), application lifecycle architecture (`Main`, `AppLauncher`, `AppContext`, `ThemeService`), base Main Window FXML and CSS stylesheets.
- [x] **Phase 1.2 – Main Window**:
  - Modern Custom Window Title Bar (App icon, title, minimize/maximize/close, window dragging, double-click maximize/restore, 8-direction mouse resizer).
  - Comprehensive Menu Bar (File, Edit, View, Navigate, Tools, Window, Help) with standard accelerators (`Ctrl+N`, `Ctrl+O`, `Ctrl+S`, `Ctrl+Enter`, `Alt+F4`, etc.).
  - Action Toolbar (Execute & Stop buttons, Refresh, Connection selector, Schema selector, New/Open/Save, Quick search, Layout & Theme toggles).
  - Modular FXML layout architecture (`main-window.fxml`, `sidebar.fxml`, `editor.fxml`, `results.fxml`, `status-bar.fxml`).
  - Detailed Status Bar with live connection status, dialect, document stats, cursor coordinates, encoding, line endings, theme mode, and notifications.
  - Multi-theme switching via AtlantaFX (Primer, Dracula, Nord, Cupertino).
- [x] **Phase 1.3 – Database Explorer**:
  - Hierarchical TreeView with mock schema data for multiple connections (PostgreSQL, MySQL, SQLite).
  - Object hierarchy: Connections -> Databases -> Schemas -> Categories (Tables, Views, Functions, Procedures) -> Columns/Parameters.
  - Custom TreeCell renderer with distinct icons, primary key (`PK`) and foreign key (`FK`) badges, data type and row count metadata.
  - Interactive right-click Context Menu customized per object type (Select First 100 Rows, Count Rows, Copy Name, Generate DDL, Drop Table, New Console).
  - Real-time search/filter bar with match counter badge, empty state feedback, and instant clear button.
  - Quick action toolbar: Add Connection, Refresh Schemas, Collapse All, Expand All.
  - Drag-and-drop support: drag table or column name directly to SQL query editors.
- [x] **Phase 1.4 – Editor Workspace**:
  - Multiple tabs management (`SqlEditorTab`) with close confirmation for unsaved/dirty tabs.
  - Dirty State Indicator: dynamic `•` badge when content is modified, cleared on save.
  - Tab pinning (`fth-bookmark`): pinned tabs cannot be closed accidentally and remain docked.
  - Tab context menu: Close, Close Others, Close All, Close Unmodified, Pin/Unpin, Split Right, Split Down, Rename.
  - RichTextFX SQL Editor (`SqlCodeArea`) with line number gutter (`LineNumberFactory`).
  - Comprehensive SQL syntax highlighting (`SqlSyntaxHighlighter`): keywords, data types, functions, strings, comments, numbers.
  - Split Editor PoC: Split Horizontally (side-by-side) and Vertically (top-and-bottom) with dual TabPanes.
  - Real-time caret position tracking updating the Status Bar (`Ln X, Col Y`).
  - Drag-and-drop target: drag tables/columns from Explorer directly into the query editor.
- [x] **Phase 1.5 – Results Panel**:
  - Results Grid (`TableView<QueryResultRow>`) with pinned Row Number `#` column, column type headers (`name [type]`), cell selection enabled.
  - Multi-format Export options: CSV, JSON, Markdown Table (copied to clipboard or exported).
  - Context Menu on grid: Copy Cell Value, Copy Row as CSV, Copy Row as JSON.
  - Real-time client-side filter across all columns with dynamic row counter.
  - Loading State overlay with progress indicator during simulated execution.
  - Messages Console tab (`TableView<ConsoleMessage>`): timestamps, colored level badges (SUCCESS, INFO, WARN, ERROR), message duration, clear & copy actions.
  - Interactive Mock Terminal (`postgres=# `): command line input, history navigation (Up/Down arrows), simulated CLI commands (`\l`, `\dt`, `help`, `clear`).
- [x] **Phase 1.6 – Theme & Components**:
  - **Explorer Object Icons per Type**:
    - Complete DataGrip/DBeaver style icon mapping across all database objects using MaterialDesign2 and Feather icon packs.
    - Tables: `mdi2t-table` (accent blue `#3584e4`) & Category Tables: `mdi2t-table-multiple`.
    - Views: `mdi2t-table-eye` (purple `#9141ac`) & Category Views: `mdi2t-table-headers-eye`.
    - Functions: `mdi2f-function-variant` (emerald green `#2ec27e`) & Category Functions: `mdi2f-function`.
    - Procedures: `mdi2c-cog-outline` (vibrant amber `#ff7800`) & Category Procedures: `mdi2c-cogs`.
    - Columns by Data Type:
      - Primary Key: `mdi2k-key-variant` (gold `#f5c211`) with `[PK]` badge.
      - Foreign Key: `mdi2k-key-link` (blue `#3584e4`) with `[FK]` badge.
      - Text / Varchar / String: `mdi2f-format-letter-case` (`#99c1f1`).
      - Numeric / Integer / Float: `mdi2n-numeric` (`#8ff0a4`).
      - Date / Timestamp / Time: `mdi2c-calendar-clock` (`#dc8add`).
      - Generic / Other: `mdi2t-table-column`.
    - Automatic icon & CSS classification in `DatabaseNode` and `DatabaseTreeCell`.
  - **Shared UI Components (`app.ui.components`)**:
    - `AppButton`: Unified button component supporting variants (`PRIMARY`, `SUCCESS`, `DANGER`, `WARNING`, `FLAT`, `OUTLINE`) and sizes (`SMALL`, `MEDIUM`, `LARGE`).
    - `IconButton`: Compact icon-only button with automatic tooltip and flat styling.
    - `SearchField`: Custom reusable search box with prefix search icon, live text input, and auto-toggling clear button (`×`).
    - `StatusIndicator`: Live dot indicator (`ONLINE`, `BUSY`, `OFFLINE`, `IDLE`) with tooltips.
    - `EmptyState`: Reusable empty-state placeholder with customizable icon, title, description, and optional action button.
    - `LoadingView`: Spinner progress indicator with message text.
  - **Notification Service (`NotificationService`)**:
    - Non-intrusive toast popups via ControlsFX (`showSuccess`, `showInfo`, `showWarning`, `showError`).
    - Integrated with Theme Switching, SQL Formatting, Query Execution, and Results Export.
  - **Theme System & CSS Polish**:
    - Full dark and light theme styles with unified design tokens, panel separation, and high contrast.
    - Support for 7 themes: Primer Dark/Light, Dracula, Nord Dark/Light, Cupertino Dark/Light.
- [x] **Phase 1.7 – Layout Management**:
  - **Layout Service (`LayoutService`)**: Centralized layout state management coordinating panels, dividers, and presets.
  - **Panel Management & Resizing**:
    - Show / Hide toggle for Database Explorer (`Alt+1`) and Results Panel (`Alt+8`).
    - Sidebar Collapse button (`fth-chevron-left`) on explorer panel header.
    - Full divider memory: remembers user divider positions across hides, collapses, and undocks.
  - **Layout Presets**:
    - `Default Layout`: 22% Database Explorer, 65% Editor on top, 35% Results on bottom.
    - `Editor Focused (Distraction-Free)`: Sidebar and results hidden, editor takes 100% of workspace (`Shift+F12`).
    - `Database Explorer Focused`: 35% wider explorer for deep schema and table inspection.
    - `Results Maximized`: 85% expanded results area for deep dataset inspection (`Ctrl+Alt+R`).
    - `Restore Default Layout`: Quick factory reset button on toolbar and menu (`Ctrl+Shift+F10`).
  - **Docking & Floating Window PoC (Multi-Monitor Mode)**:
    - Results Panel can be undocked into a floating, independent OS window (`Ctrl+Shift+U` or pop-out icon `fth-external-link`).
    - Floating window includes a "Dock Back to Workspace" action button.
    - Seamless re-docking restores the Results Panel back into the main window's `centerVerticalSplit` at the exact divider position.
  - **Preferences Persistence**: Automatically saves and restores divider positions and panel visibility states across application sessions using Java Preferences.
- [x] **Phase 1.8 – UI Polish & Mini Sidebar**:
  - **Dual Sidebar Collapsing Modes**:
    - **Collapse to Mini Strip (`<` button on Explorer header)**: Shrinks explorer into a slim 46px icon bar (`mini-sidebar-strip`).
    - **Connection Icons with Rich Tooltips**: Displays icons for all configured connections (PostgreSQL, MySQL, SQLite) with color accents and status indicators.
    - **Hover Inspection**: Hovering over any connection icon shows a rich tooltip detailing connection name, host/port, online status, and database info.
    - **Instant Expand & Navigate**: Clicking any connection icon or the expand button (`>`) instantly re-expands the Explorer to full width and selects/expands that connection in the TreeView.
    - **Complete Hide (`Alt+1` or Toolbar Layout Toggle)**: Completely removes the sidebar (both full and mini) from the split pane, giving 100% width to the editor workspace.
  - **Interactive States & Polish**:
    - Smooth hover effects on buttons, table rows, tree cells, and tabs.
    - Clear focus rings and active states across inputs and tree views.
    - Clean responsive resizing across all split pane dividers.
    - Comprehensive unit test suite with 30 passing tests.
- [x] **Phase 1.9 – Custom Title Bar**:
  - **Decoupled Architecture**: Window management completely decoupled from `MainController` into dedicated packages:
    - `app.ui.window`: `CustomWindow`, `WindowController`, `WindowStateManager`, `WindowDragHandler`, `WindowResizeHandler`.
    - `app.ui.titlebar`: `TitleBar`, `TitleBarController`, `WindowControls`, `WindowTitleModel`.
  - **Custom Undecorated Window**: `StageStyle.UNDECORATED` with full Windows-standard interactivity.
  - **Window State Management (`WindowStateManager`)**:
    - Clean state transitions: Normal <-> Maximized <-> Iconified (Minimized) -> Close.
    - Robust un-maximized bounds memory (`restoreX`, `restoreY`, `restoreWidth`, `restoreHeight`) so restoring always returns to the exact prior coordinates.
    - Reactive `maximizedProperty` triggering dynamic icon toggle (`fth-square` for Maximize, `fth-copy` for Restore Down).
  - **Intelligent Drag Handler (`WindowDragHandler`)**:
    - Smooth title bar window dragging.
    - Ignores clicks on interactive components (buttons, search inputs, menu triggers).
    - Double-click on vacant title bar area toggles Maximize / Restore.
  - **8-Direction Mouse Resizer (`WindowResizeHandler`)**:
    - Full 8-direction border & corner resizing: N, S, E, W, NE, NW, SE, SW.
    - Dynamic cursor updates (`E_RESIZE`, `S_RESIZE`, `NE_RESIZE`, etc.).
    - Enforces minimum window dimensions (800x600).
    - Automatically disabled when the window is maximized.
  - **Modern Title Bar UI (`titlebar.fxml` & `TitleBarController`)**:
    - App icon and reactive multi-part title (`WindowTitleModel`: app name, active document/tab, theme badge).
    - Extensible custom area currently hosting the global search field.
    - Windows 11 style control buttons (`WindowControls`):
      - Minimize (`fth-minus`).
      - Maximize / Restore (`fth-square` / `fth-copy`).
      - Close button (`fth-x`) with Windows-standard red hover style (`#e81123`).
  - **Theme Synchronization & CSS Tokens**:
    - `titlebar.css` and `window-controls.css` integrated seamlessly with `ThemeService`.
    - Strict zero-hardcoded-colors policy in Java code; all colors defined via CSS design tokens matching AtlantaFX dark/light themes.
  - **Testing**: 34 unit tests covering window model, state management, FXML loading, and custom window instantiation.
  - **Native Windows Integration (`app.ui.window.platform.WindowsWindowAdapter`, JNA)**:
    - Sub-classes the Win32 window procedure (`WM_NCCALCSIZE`, `WM_NCHITTEST`, `WM_GETMINMAXINFO`) – same technique as VS Code / Electron.
    - Aero Snap (drag to edges/corners with preview), Win+Arrow snapping, Win11 Snap Layouts on the maximize button, native double-click maximize, system menu (Alt+Space).
    - Maximize is limited to the monitor work area, so the Windows taskbar stays visible.
    - Falls back to the pure JavaFX drag/resize handlers on other platforms.
  - **VS Code style title bar**: menu bar embedded on the left, centered `document - app` title, layout toggles on the right.
  - **Title Bar & Workspace Refinements**:
    - **Maximized Viewport Fix**: Custom `WM_NCCALCSIZE` and `WM_GETMINMAXINFO` adjustments eliminate 8px border clipping on full screen, ensuring title bar, sidebar, and status bar are 100% visible with zero cut-off.
    - **Responsive Title Bar Menu**: Automatic collapse into a compact hamburger MenuButton (`≡`) when window width < 950px, restoring full inline MenuBar when enlarged.
    - **Compact Toolbar & Header Heights**: Normalized main toolbar, database explorer header, tab headers, and results mini-toolbar to 28px matching the sleek filter input.
    - **Card-Based UI Architecture**: Database Explorer, Editor Workspace, and Results Panel designed as floating cards with 8px rounded corners, 4px margins, and subtle borders.
    - **Explorer Icon Proportions**: Scaled explorer object icons to 11px (tables, views, functions) and 10px (columns, keys, data types), matching the 11.5px tree font.
    - **Unified Submenu & Context Menu UI**: Styled `.context-menu`, `.menu-item`, icons (11.5px), labels (11.5px), and accelerator shortcuts (10.5px) with 6px rounded borders.
    - **Card Corner Scene-Graph Clipping**: Applied JavaFX `setClip(Rectangle)` and CSS radii across cards (`sidebar`, `editor`, `results`) to ensure child elements (headers, scrollbars, code area, tables) never obscure or bleed over the 8px rounded corners.

## Phase 2 Progress – Core Architecture
- [x] **Phase 2.1 – Core Module Foundation**: Maven multi-module architecture under `core/` (`core-parent`, `core-common`, `core-domain`, `core-application`, `core-api`) with strict dependency rules.
- [x] **Phase 2.2 – Core Common**: Sealed `Result<T>` functional monad, sealed `AppError` taxonomy, strongly typed `EntityId`, fluent `Validator`, and deterministic `TimeProvider`.
- [x] **Phase 2.3 – Core Domain**: Pure `Project` and `Workspace` Aggregate Roots, validated Value Objects (`ProjectName`, `ProjectId`, `ProjectPath`, `WorkspaceId`), Domain Events, and Repository contracts.
- [x] **Phase 2.4 – Core Application**: Use Cases (`CreateProjectUseCase`, `RenameProjectUseCase`, `DeleteProjectUseCase`, `GetProjectDetailUseCase`, `GetProjectListUseCase`), Commands, Queries, pure DTOs, Mappers, and thread-safe `InMemoryProjectRepository`.
- [x] **Phase 2.5 – Core API**: Public client-facing contract `ProjectApi`, `EventApi`, Request/Response DTOs, and `CorePlatform` Composition Root using Constructor Injection without external DI frameworks.
- [x] **Phase 2.6 – Event System**: Synchronous thread-safe `EventBus` with subscriber error isolation and `AutoCloseable` subscription handles.
- [x] **Phase 2.7 – Testing & Architecture Validation**: 21 passing automated tests including ArchUnit rules verifying zero coupling to JavaFX, Spring, or Desktop UI.
- [x] **Phase 2.8 – Documentation & Handover**: Detailed implementation report [`docs/phase2_implementation_report.md`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/phase2_implementation_report.md) with guidelines for Phase 03.

## Phase 3 Progress – Infrastructure Architecture
- [x] **Phase 3.1 – Infrastructure Module Foundation**: 11-module Maven parent under `infrastructure/` with clean Ports & Adapters architecture.
- [x] **Phase 3.2 – FileSystem**: `AppDirectoryManager` resolving standard cross-platform paths (`~/.lltool/config`, `data`, `logs`, `cache`) and `AtomicFileWriter` for crash-safe, atomic file replacement via temporary files.
- [x] **Phase 3.3 – Configuration**: Strongly typed immutable records (`AppConfig`, `UiConfig`, `RootConfig`), JSON persistence via Jackson, and `ConfigurationService` with atomic persistence and change notifications.
- [x] **Phase 3.4 – Logging & Security**: `SensitiveDataMasker` ensuring database passwords, bearer tokens, and credentials are never exposed in log outputs; Logback configuration with daily file rotation.
- [x] **Phase 3.5 – Secure Vault**: `SecureCredentialVault` using AES-256-GCM encryption with PBKDF2/SHA-256 key derivation, assigning opaque reference URIs (`cred://<uuid>`) to safeguard credentials at rest.
- [x] **Phase 3.6 – Storage Local**: `LocalProjectRepository` implementing the `ProjectRepository` port from `core-domain` backed by local JSON storage and atomic write operations.
- [x] **Phase 3.7 – Database API & JDBC Foundation**: Unified SPI defining `DatabaseType`, `DatabaseConnectionConfig`, `DatabaseConnection`, `DatabaseProvider`, and `DatabaseProviderRegistry` alongside `DbExceptionTranslator`.
- [x] **Phase 3.8 – Database Providers**: Full support for PostgreSQL, SQLite (with in-memory `:memory:` verification tests), and SQL Server.

## Phase 4 Progress – Desktop Framework Implementation
- [x] **Phase 4.1 – Desktop Platform Bootstrap**: `DesktopBootstrap` singleton wiring `CorePlatform`, `LocalProjectRepository`, `ConfigurationService`, `SecureCredentialVault`, and registering all 3 database providers.
- [x] **Phase 4.2 – Async Task Manager**: `AsyncTaskManager` managing background operations and executor services with safe UI thread synchronization via `Platform.runLater`.
- [x] **Phase 4.3 – Global Desktop State**: `GlobalDesktopState` reactive state container holding JavaFX properties for active project, connection, schema, dialect, busy flag, and status message.
- [x] **Phase 4.4 – Navigation & Perspective Manager**: `NavigationManager` supporting switching perspectives (`EXPLORER`, `QUERY_CONSOLE`, `DATABASE_OBJECT_VIEWER`, `SETTINGS`).
- [x] **Phase 4.5 – Lifecycle Management**: `DesktopLifecycleManager` handling application startup and coordinated shutdown tasks (connection pools, background threads, preference flushing).
- [x] **Phase 4.6 – Global Error Handling**: `DesktopErrorHandler` catching uncaught thread exceptions with automatic sensitive data masking.

## Phase 5 Progress – UI Component System
- [x] **Phase 5.1 – Design Tokens & Themes**: AtlantaFX theme tokens (Primer, Dracula, Nord, Cupertino) with strict zero-hardcoded-colors rule.
- [x] **Phase 5.2 – Component Factory**: `ComponentFactory` centralized one-stop factory for instantiating design-system compliant controls.
- [x] **Phase 5.3 – Reusable Basic Controls**:
  - `AppButton`: Variant styling (`PRIMARY`, `SUCCESS`, `DANGER`, `WARNING`, `FLAT`, `OUTLINE`) and sizes (`SMALL`, `MEDIUM`, `LARGE`).
  - `IconButton`: Flat icon-only buttons with built-in tooltips.
  - `SearchField`: Input field with search prefix icon and clear button (`×`).
  - `StatusIndicator`: Live connection and operational status dots (`ONLINE`, `BUSY`, `OFFLINE`, `IDLE`).
- [x] **Phase 5.4 – Layout & Card Clipping**:
  - `CardPane`: Card container with scene-graph clipping via `setClip(Rectangle)` ensuring internal child elements never bleed over 8px rounded corners.
- [x] **Phase 5.5 – Navigation Components**:
  - `BreadcrumbBar`: Hierarchical navigation breadcrumb bar with push/pop/peek operations, separator chevrons, and interactive item links.
- [x] **Phase 5.6 – Feedback & State Components**:
  - `AppDialog`: Modal dialogs with confirm, alert, and info builders.
  - `EmptyState`: Clean visual placeholder for empty datasets or query lists.
  - `LoadingView`: Progress indicator with dynamic message display.
  - `Badge`: Semantic badges (`INFO`, `SUCCESS`, `WARNING`, `DANGER`, `PRIMARY`, `NEUTRAL`).
- [x] **Phase 5.7 – Automated Verification**: Full test suite with 47 desktop tests + 21 core tests + 5 infrastructure tests passing with 100% success rate.
