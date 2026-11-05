# Source Index — LLTool / luoBTool IDE
# Chỉ mục mã nguồn

Root: `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`  
Sizes in KB from directory listing on 2026-11-04. `Read` = content inspected during discovery (✔) vs. only listed (–).

Excluded from index: `.git/`, `venv/`, `dist/`, `build/`, `__pycache__/`, `.pytest_cache/`, `.data_backup_phase0/`, `data_backup_baseline/`, `scratch/`, `static/vendor/**` (listed by folder only), `tests/**` (count only).

## 1. Entry & build

| File | KB | Read | Role |
|---|---:|:-:|---|
| `run.py` | 0.7 | ✔ | Launcher wrapper (PyInstaller-aware) |
| `main.py` | 14.3 | ✔ | Entry: MainApi/BravoApi, splash→main transition, webview.start |
| `run.spec` | 7.1 | ✔ | PyInstaller build spec |
| `config.py` | 0.2 | ✔ | Flask config (secondary channel) |
| `requirements.txt` | 0.3 | ✔ | Runtime deps |
| `version.json` | 2.3 | ✔ | Release metadata (v1.3.1) |
| `README.md` | 4.5 | ✔ |  |

## 2. Desktop shell (windows, title bar, OS integration)

| File | KB | Read | Role |
|---|---:|:-:|---|
| `app/desktop/__init__.py` | 0.1 | ✔ |  |
| `app/desktop/clipboard.py` | 6.7 | ✔ | Native clipboard |
| `app/desktop/exports.py` | 10.5 | ✔ | Excel/CSV export dialogs |
| `app/desktop/themes.py` | 18.1 | ✔ | VSIX theme import → CSS |
| `app/desktop/windows.py` | 99.5 | ✔ | All sub-window factories + *SubwindowApi classes, geometry, settings broadcast, title-bar theme fan-out |
| `app/utils/dwm.py` | 13.6 | ✔ | Native title-bar colour via DWM |
| `app/utils/paths.py` | 5.2 | ✔ | App/bundle dir, portable mode |
| `app/utils/system_fonts.py` | 5.7 | ✔ | System font enumeration |
| `app/services/settings_store.py` | 10.8 | ✔ | DATA_DIR + settings.json IO |
| `app/services/google_sheets/webview_host.py` | 9.4 | ✔ | Google Sheets companion WebView2 windows |

## 3. Bridge API mixins (JS → Python)

| File | KB | Read | Role |
|---|---:|:-:|---|
| `app/api/__init__.py` | 1.3 | ✔ | Mixin exports |
| `app/api/activity_api.py` | 3.4 | ✔ |  |
| `app/api/connections_api.py` | 19.9 | ✔ |  |
| `app/api/database_users_api.py` | 3.9 | ✔ |  |
| `app/api/designer_api.py` | 4.2 | ✔ |  |
| `app/api/google_sheets_api.py` | 12.2 | ✔ |  |
| `app/api/history_api.py` | 16.0 | ✔ |  |
| `app/api/metadata_api.py` | 22.8 | ✔ |  |
| `app/api/profiler_api.py` | 4.2 | ✔ |  |
| `app/api/query_api.py` | 19.3 | ✔ |  |
| `app/api/report_gen_api.py` | 14.6 | ✔ |  |
| `app/api/schema_compare_api.py` | 28.7 | ✔ |  |
| `app/api/session_api.py` | 0.9 | ✔ |  |
| `app/api/settings_api.py` | 13.2 | ✔ |  |
| `app/api/syntax_catalog_api.py` | 5.3 | ✔ |  |
| `app/api/table_editor_api.py` | 11.9 | ✔ |  |
| `app/bravo/api.py` | 30.6 | ✔ |  |

## 4. HTML templates (one per window)

| File | KB | Read | Role |
|---|---:|:-:|---|
| `templates/about_window.html` | 11.9 | ✔ | About window |
| `templates/activity_monitor_window.html` | 4.2 | ✔ | Activity Monitor (pop-out) |
| `templates/bravo_form.html` | 27.7 | ✔ | BRAVO Layout XML Editor |
| `templates/event_listener_window.html` | 31.8 | ✔ | PostgreSQL Event Listener (no UI trigger) |
| `templates/google_sheets_window.html` | 20.5 | ✔ | Google Sheets Workspace |
| `templates/index.html` | 194.0 | ✔ | Main window (single page, 3003 lines) |
| `templates/profiler_window.html` | 21.4 | ✔ | SQL Trace Profiler |
| `templates/query_history_window.html` | 25.1 | ✔ | Query History |
| `templates/report_gen_window.html` | 35.7 | ✔ | Report Layout Generator |
| `templates/result_comparator_window.html` | 21.6 | ✔ | Result Comparator |
| `templates/schema_compare_window.html` | 8.0 | ✔ | Schema Compare |
| `templates/settings_window.html` | 9.7 | ✔ | Settings window |
| `templates/splash.html` | 5.6 | ✔ | Splash |
| `templates/syntax_reference_window.html` | 18.1 | ✔ | Syntax Reference |
| `templates/update_window.html` | 31.0 | ✔ | Update window |

## 5. Unused partials (deprecated candidates)
Not referenced from `index.html` or any code (verified by search).

| File | KB | Read | Role |
|---|---:|:-:|---|
| `templates/partials/connection_modal.html` | 13.6 | ✔ |  |
| `templates/partials/editor.html` | 37.5 | ✔ |  |
| `templates/partials/password_modal.html` | 4.2 | ✔ |  |
| `templates/partials/results.html` | 7.3 | ✔ |  |
| `templates/partials/sidebar.html` | 3.0 | ✔ |  |
| `templates/partials/statusbar.html` | 0.6 | ✔ |  |
| `templates/partials/table_design_modal.html` | 5.7 | ✔ |  |
| `templates/partials/toolbar.html` | 2.3 | ✔ |  |

## 6. Frontend JavaScript (`static/js/`)

### 6.1 Bootstrap / shell

| File | KB | Read |
|---|---:|:-:|
| `utils.js` | 1.7 | ✔ |
| `api.js` | 65.8 | ✔ |
| `clipboard-bridge.js` | 17.7 | ✔ |
| `storage.js` | 15.0 | ✔ |
| `settings.js` | 118.0 | ✔ |
| `session.js` | 14.3 | ✔ |
| `loader.js` | 3.8 | ✔ |
| `app.js` | 30.4 | ✔ |
| `app-logger.js` | 6.9 | ✔ |
| `autohide-navbar.js` | 7.7 | ✔ |
| `state.js` | 0.2 | ✔ |
| `theme-registry.js` | 11.4 | ✔ |
| `theme.js` | 7.9 | ✔ |
| `typography.js` | 7.6 | ✔ |

### 6.2 Explorer & connections

| File | KB | Read |
|---|---:|:-:|
| `explorer.js` | 197.5 | ✔ |
| `context-menu.js` | 76.5 | ✔ |
| `connections.js` | 48.6 | ✔ |

### 6.3 Editor, tabs & Monaco

| File | KB | Read |
|---|---:|:-:|
| `monaco-init.js` | 28.2 | ✔ |
| `monaco-sql-lang.js` | 15.0 | ✔ |
| `editor-adapter.js` | 6.0 | ✔ |
| `editor.js` | 134.8 | ✔ |
| `tabs.js` | 166.5 | ✔ |
| `find-replace.js` | 1.5 | ✔ |
| `file-history-sidebar.js` | 27.5 | ✔ |
| `snippets-manager.js` | 68.4 | ✔ |
| `sql-formatter.js` | 28.8 | ✔ |
| `sql-style-editor.js` | 40.3 | ✔ |
| `sql-style-metadata.js` | 24.3 | ✔ |
| `sql-refactor.js` | 11.8 | ✔ |
| `sql-block-analyzer.js` | 15.0 | ✔ |
| `query-params.js` | 26.8 | ✔ |
| `sql-protection.js` | 38.3 | ✔ |
| `debug-extension-registry.js` | 17.9 | ✔ |
| `debug-session.js` | 4.3 | ✔ |

### 6.4 IntelliSense & syntax check engines (non-visual logic)

| File | KB | Read |
|---|---:|:-:|
| `sql-dialects.js` | 36.2 | ✔ |
| `sql-context-engine.js` | 36.0 | ✔ |
| `sql-tokenizer.js` | 19.3 | ✔ |
| `scope-resolver.js` | 122.8 | ✔ |
| `context-analyzer.js` | 176.7 | ✔ |
| `metadata-provider.js` | 21.2 | ✔ |
| `metadata-cache.js` | 38.7 | ✔ |
| `sql-function-signatures.js` | 20.7 | ✔ |
| `candidate-filter.js` | 15.2 | ✔ |
| `completion-ranker.js` | 36.4 | ✔ |
| `candidate-generator.js` | 144.8 | ✔ |
| `signature-help-resolver.js` | 31.1 | ✔ |
| `hover-resolver.js` | 30.5 | ✔ |
| `completion-engine.js` | 87.8 | ✔ |
| `trigger-controller.js` | 36.2 | ✔ |
| `intellisense.js` | 118.6 | ✔ |

### 6.5 Query execution & results

| File | KB | Read |
|---|---:|:-:|
| `query.js` | 198.5 | ✔ |
| `grid-result.js` | 172.0 | ✔ |
| `execution-plan-tab.js` | 23.5 | ✔ |
| `structured-data-viewer.js` | 31.9 | ✔ |
| `result-comparator.js` | 75.7 | ✔ |

### 6.6 Feature views & sub-window pages

| File | KB | Read |
|---|---:|:-:|
| `table-designer.js` | 180.7 | ✔ |
| `table-data-editor.js` | 271.0 | ✔ |
| `activity-monitor.js` | 42.0 | ✔ |
| `schema-compare.js` | 201.4 | ✔ |
| `user-permissions.js` | 25.8 | ✔ |
| `syntax-catalog.js` | 9.8 | ✔ |
| `syntax-reference-window.js` | 74.7 | ✔ |
| `query-history.js` | 108.6 | ✔ |
| `profiler.js` | 37.2 | ✔ |
| `event-listener.js` | 46.1 | ✔ |

### 6.7 Sub-folders

| File | KB | Read |
|---|---:|:-:|
| `bravo/bravo-connection-bar.js` | 39.9 | ✔ |
| `bravo/bravo-feature-registry.js` | 1.8 | ✔ |
| `bravo/bravo-manager.js` | 4.8 | ✔ |
| `bravo/bravo-window.js` | 39.0 | ✔ |
| `bravo/features/bravo-xml-monaco.js` | 44.3 | ✔ |
| `bravo/features/layout-editor.js` | 147.4 | ✔ |
| `bravo/features/program-list.js` | 87.9 | ✔ |
| `dialogs/crud_preview_dialog.js` | 15.5 | ✔ |
| `google-sheets/manager.js` | 11.1 | ✔ |
| `google-sheets/sheet-tabs.js` | 9.1 | ✔ |
| `google-sheets/tabs.js` | 2.7 | ✔ |
| `google-sheets/view.js` | 50.6 | ✔ |
| `grid/grid_edit_manager.js` | 29.0 | ✔ |
| `grid/grid_edit_renderer.js` | 8.7 | ✔ |
| `grid/grid_edit_state.js` | 8.7 | ✔ |
| `grid/grid_edit_toolbar.js` | 6.5 | ✔ |
| `grid/grid_edit_validation.js` | 5.3 | ✔ |
| `grid/grid_row_operations.js` | 13.2 | ✔ |
| `object-search/ddl-viewer.js` | 13.4 | ✔ |
| `object-search/match-panel.js` | 5.0 | ✔ |
| `object-search/object-search-api.js` | 3.0 | ✔ |
| `object-search/object-search-controller.js` | 85.1 | ✔ |
| `object-search/object-search-store.js` | 7.7 | ✔ |
| `postgresql/dependency/dependency-api.js` | 2.7 | ✔ |
| `postgresql/dependency/dependency-controller.js` | 45.5 | ✔ |
| `postgresql/dependency/dependency-filter.js` | 16.3 | ✔ |
| `postgresql/dependency/dependency-graph.js` | 20.6 | ✔ |
| `postgresql/dependency/dependency-list.js` | 26.1 | ✔ |
| `postgresql/dependency/dependency-panel.js` | 16.3 | ✔ |
| `report-gen/sheet-grid.js` | 43.5 | ✔ |
| `report-gen/wizard.js` | 141.3 | ✔ |
| `syntax-check/diagnostic-model.js` | 2.5 | – |
| `syntax-check/error-list.js` | 17.7 | ✔ |
| `syntax-check/position-mapper.js` | 5.1 | – |
| `syntax-check/quick-fix-provider.js` | 6.5 | – |
| `syntax-check/structured-syntax-checker.js` | 8.8 | – |
| `syntax-check/syntax-check-service.js` | 27.2 | ✔ |
| `visualization/d3-renderer.js` | 105.0 | – |
| `visualization/query-flow-analyzer.js` | 39.3 | – |
| `visualization/query-flow-graph.js` | 15.3 | – |
| `visualization/query-model.js` | 21.0 | – |
| `visualization/sql-generator.js` | 10.2 | – |
| `visualization/sql-parser.js` | 30.4 | – |
| `visualization/sql-visualizer.js` | 104.3 | ✔ |

## 7. Stylesheets (`static/css/`)

| File | KB | Read | Role |
|---|---:|:-:|---|
| `static/css/activity-monitor.css` | 19.6 | – |  |
| `static/css/app.css` | 16.4 | ✔ |  |
| `static/css/bravo.css` | 75.4 | – |  |
| `static/css/context-menu.css` | 3.7 | ✔ |  |
| `static/css/dependency-graph.css` | 17.4 | – |  |
| `static/css/editor.css` | 96.5 | ✔ |  |
| `static/css/event-listener.css` | 30.7 | – |  |
| `static/css/explorer.css` | 22.6 | ✔ |  |
| `static/css/file-history-sidebar.css` | 7.2 | – |  |
| `static/css/grid-edit.css` | 4.9 | – |  |
| `static/css/object-search.css` | 18.6 | – |  |
| `static/css/profiler.css` | 20.2 | – |  |
| `static/css/query-history.css` | 29.5 | – |  |
| `static/css/report-gen.css` | 37.8 | – |  |
| `static/css/result-comparator.css` | 6.8 | – |  |
| `static/css/schema-compare.css` | 45.4 | – |  |
| `static/css/settings.css` | 31.7 | ✔ |  |
| `static/css/sql-protection.css` | 6.1 | – |  |
| `static/css/sql-style.css` | 15.2 | – |  |
| `static/css/sql-visualization.css` | 24.1 | – |  |
| `static/css/structured-data-viewer.css` | 5.2 | – |  |
| `static/css/style.css` | 212.3 | ✔ |  |
| `static/css/syntax-catalog.css` | 45.5 | – |  |
| `static/css/table-data-editor.css` | 44.0 | – |  |
| `static/css/table-designer.css` | 17.7 | – |  |
| `static/css/theme-variables.css` | 121.5 | ✔ |  |
| `static/css/themes.css` | 10.6 | ✔ |  |
| `static/css/user-permissions.css` | 6.1 | – |  |

## 8. Vendor libraries (`static/vendor/`)

| Folder | Loaded by index.html |
|---|:-:|
| `ag-grid` | yes |
| `bootstrap` | yes |
| `codemirror` | yes |
| `d3` | yes |
| `fontawesome` | yes |
| `fonts (JetBrains Mono local)` | yes |
| `monaco` | yes |
| `tabulator` | no — usage unknown |

## 9. Static assets

| Path | Role |
|---|---|
| `static/icons/postgresql.png`, `sqlserver.png`, `sqlite.svg` | DB type logos |
| `static/image/logo.png`, `logo-200x200.png`, `banner*.png` | Branding, splash/about |
| `logo.ico` | Window/exe icon |
| `static/data/snippets.json` | Default snippets |

## 10. Backend (context only — not to be migrated in UI phase)

`app/services/*`, `app/database/*`, `app/data_edit/*`, `app/bravo/*`, `app/models/*`, `app/utils/*` (except UI-related files above). Read only to trace UI outcomes in workflow 03.

## 11. Supporting documentation in source repo

| Path | Use |
|---|---|
| `.SKILL/INDEX.md` + 80+ spec files | Historical feature specs (themes, menubar, grid, intellisense, settings form, …) — secondary evidence |
| `docs/refactor/*` | Refactor reports, baseline tests |
| `tests/snapshots/api_surface.txt` | Bridge API snapshot (stale for newer sub-windows) |
| `tests/` | ~670 entries; UI-behaviour tests usable as code evidence (e.g. `test_ui_actionbar_and_debug_headless.py`, `test_tab_manager.py`) |
