# Project Map — LLTool / luoBTool IDE (Python + PyWebView)
# Bản đồ dự án — ứng dụng nguồn

| Field | Value |
|---|---|
| Source root | `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude` |
| Product name (window title) | `luoBTool IDE` (release name in `version.json`: `LLTool IDE v1.3.1`) |
| Version | 1.3.1 (release date 2026-11-04) |
| Target (JavaFX) project | `D:\Luong\NB_Personal\NB_Java\SQL-IDE` |
| Workflow step | 01 — Project Discovery |
| Evidence basis | Source code only (`code_verified`). The app was **not** run; nothing is `observed`. |

---

## 1. Entry point & startup flow / Điểm khởi chạy và trình tự khởi động

**Entry:** `run.py` → `main.main()` (`main.py`). PyInstaller spec: `run.spec`.

Startup sequence (code_verified, `main.py:167–385`):

1. Create `DATA_DIR` (resolved by `settings_store.ensure_data_dir_migrated(APP_DIR)`, supports portable mode via `portable.flag`) and `DATA_DIR/.webview_cache`.
2. Load `settings.json`, configure logging (`app/utils/logging_setup.py`).
3. Pick a startup background colour from the saved theme (`win-nt` → `#C0C0C0`, `win-xp` → `#ece9d8`, light → `#f7f8fa`, otherwise `#1e1f22`).
4. Compute centred geometry (`calculate_centered_geometry`): main window 1400×900 (min 900×600, ≤96%×94% of screen); splash 800×500.
5. Create **splash window** (`templates/splash.html`, frameless, easy-drag, non-resizable, on-top, bg `#071324`).
6. Create **main window** (`templates/index.html`, `js_api=MainApi`, **hidden**, `text_select=True`).
7. Transition rule: show main window and destroy splash when **both** (a) the 2.0 s minimum splash timer has fired and (b) the frontend has called `MainApi.notify_app_ready()`. A 10 s fallback timer forces the transition.
8. After showing: apply DWM title-bar colour (repeated at 0.1/0.35/0.7 s), force Monaco `layout()` several times (0.05–0.9 s), start background update check (3.5 s delay) → `window.onAppUpdateAvailable(payload)`.
9. `window.events.loaded` → `ThemeManager.applyTheme(theme, true)`.
10. `window.events.closed` → `connection_manager.close_all_sessions()`.
11. `webview.start(gui="edgechromium", storage_path=.webview_cache, private_mode=False, debug=False)`.

---

## 2. UI technology stack / Công nghệ giao diện

| Layer | Technology | Evidence |
|---|---|---|
| Native shell | pywebview ≥ 6.2 on **Edge WebView2** (`gui="edgechromium"`), pythonnet | `requirements.txt`, `main.py:385` |
| Title bar | **Native Windows title bar** (windows are not frameless except splash), recoloured via DWM (`DwmSetWindowAttribute`) | `app/utils/dwm.py:233 apply_dwm_titlebar_theme` |
| Page framework | Vanilla JS + Bootstrap 5 (CSS + bundle JS) | `templates/index.html:79, 2040` |
| SQL editor | **Monaco Editor** (`static/vendor/monaco`), custom SQL language `monaco-sql-lang.js`; Monaco diff editor for compare | `index.html:103, 2050–2052` |
| Result grid | **AG Grid Community** (`ag-theme-quartz`) | `index.html:105–106, 2106` |
| Graphs | D3 (dependency graph, SQL query-flow visualiser) | `index.html:2047`, `static/js/visualization/*` |
| Secondary editor | CodeMirror 5 + darcula (loaded in main page) | `index.html:100–101, 2043–2045` |
| Icons | Font Awesome (`static/vendor/fontawesome/all.min.css`), DB logos `static/icons/*.png|svg` | `index.html:82` |
| Fonts | JetBrains Mono (local), Inter & Roboto (Google Fonts, online) + system font enumeration `app/utils/system_fonts.py` | `index.html:72–86` |
| Other vendor | Tabulator (present in `static/vendor`, not loaded by `index.html` — usage `unknown`) | directory listing |
| Secondary channel | Flask blueprints `app/routes/*` (optional, fallback/web mode only) | `app/__init__.py`, README |

---

## 3. Window inventory / Danh sách cửa sổ

All windows are created with `webview.create_window(...)`. Sub-windows are created by `app/desktop/windows.py` and are exposed to JS through API mixins. Details: `application-map.json`.

| ID | Window | Template | Bridge class | Instancing | Default size (min) | How it opens |
|---|---|---|---|---|---|---|
| WIN-SPLASH | Splash | `splash.html` | — | single | 800×500 | startup |
| WIN-MAIN | Main IDE | `index.html` | `MainApi` | single | 1400×900 (900×600) | startup |
| WIN-SETTINGS | Cài đặt (Settings) | `settings_window.html` | `SettingsSubwindowApi` | singleton (focus if open) | 1050×720 (800×520) | `menu-settings`, `ide-settings-btn` → `open_settings_window`; fallback in-page `#settingsModal` |
| WIN-ABOUT | Thông tin phần mềm | `about_window.html` | `AboutSubwindowApi` | singleton | 580×460 (480×380) | Help ▸ About → `open_about_window`; fallback `#ide-about-modal` |
| WIN-UPDATE | Kiểm tra Cập nhật | `update_window.html` | `UpdateSubwindowApi` | singleton | 620×500 (500×420) | Help ▸ Check updates, `ide-update-btn` → `open_update_window`; fallback `#ide-update-modal` |
| WIN-PROFILER | SQL Trace Profiler | `profiler_window.html` | `ProfilerSubwindowApi` | multiple | 1320×840 (920×560) | Explorer context menu `open-profiler` |
| WIN-EVENT-LISTENER | PostgreSQL Event Listener | `event_listener_window.html` | `EventListenerSubwindowApi` | multiple | 1340×860 (920×580) | **No UI trigger found** (bridge method exists) |
| WIN-QUERY-HISTORY | Query History | `query_history_window.html` | `QueryHistorySubwindowApi` | singleton | 1280×820 (900×560) | View ▸ Query History, action bar History |
| WIN-BRAVO | BRAVO Tool — Layout XML Editor | `bravo_form.html` | `BravoApi` (new instance per window) | multiple | 1340×860 (960×600) | Navbar `ide-bravo-launcher` (visible only if add-on `bravo_tool.enabled`) |
| WIN-ACTIVITY-MONITOR | Activity Monitor | `activity_monitor_window.html` | `ActivityMonitorSubwindowApi` | multiple | 1320×840 (920×560) | "Pop out" from the Activity Monitor **tab** |
| WIN-SCHEMA-COMPARE | Schema Compare | `schema_compare_window.html` | `SchemaCompareSubwindowApi` | multiple | 1340×860 (960×600) | View ▸ Schema Compare (context menu opens a tab instead) |
| WIN-RESULT-COMPARATOR | Result Comparator | `result_comparator_window.html` | `ResultComparatorSubwindowApi` | multiple | 1380×880 (960×600) | Results toolbar / View ▸ Compare results → in-page modal → window |
| WIN-GSHEETS-MANAGER | Google Sheets Workspace | `google_sheets_window.html` | `GoogleSheetsSubwindowApi` | singleton | 1320×840 (960×600) | Navbar `ide-google-sheets-btn`, View ▸ Google Sheets |
| WIN-GSHEET-COMPANION | 📊 {sheet} — Google Sheets | external Google URL | — | one per sheet | unknown | from Google Sheets manager (`gs_open_sheet_window`) |
| WIN-SYNTAX-REFERENCE | Tra cứu & Đối chiếu cú pháp | `syntax_reference_window.html` | `SyntaxReferenceSubwindowApi` | singleton | 1260×800 (920×560) | action bar, tab-strip `?`, overflow menu, editor |
| WIN-REPORT-GEN | Sinh Layout Báo cáo Bravo từ Excel | `report_gen_window.html` | `ReportGenSubwindowApi` | singleton | 1280×800 (960×600) | View ▸ Report Gen |

All sub-windows get the IDE title-bar colour applied through DWM and receive settings via `broadcast_settings`.

---

## 4. Main window structure (high level) / Cấu trúc cửa sổ chính

`templates/index.html` (3003 lines) is a single self-contained page. The files in `templates/partials/` are **not included** anywhere (no Jinja include, no reference) — legacy candidates.

```
WIN-MAIN
├─ Top navbar (auto-hide capable, hover sensor #ide-top-hover-sensor)
│  ├─ Menus: File · Edit · Format · View · Settings · Help
│  ├─ Buttons: Snippets · Google Sheets · BRAVO (add-on)
│  └─ Right: Update (hidden until available) · Theme toggle · Settings · Env badge
├─ #ide-main-area
│  ├─ #ide-sidebar (Explorer, default 260 px, resizable, collapsible to rail)
│  │  ├─ toolbar: Connect · New group · Filter DB · Refresh · Collapse
│  │  ├─ search box  └─ tree #ide-tree-root  └─ collapsed rail
│  ├─ #ide-sidebar-resizer · #ide-sidebar-reopen
│  └─ Workspace
│     ├─ #ide-editor-pane
│     │  ├─ Action bar: Run · Debug · Stop · Transaction group · Parse · Format · Plan
│     │  │  · Import · Export · Compare · Syntax ref · History · File history · Object search · overflow
│     │  ├─ Tab strip (#ide-tabs-bar, overflow, syntax-ref pin button)
│     │  ├─ Connection-lost banner
│     │  ├─ Editor area: Monaco (pane 1/2 split), diff editor, file-history panel, IntelliSense popup, empty-tabs placeholder
│     │  ├─ Tab views (one visible at a time): designer · data-editor · dependency-graph · object-search
│     │  │  · sql-visualizer (query-designer) · activity-monitor · schema-compare · user-permissions
│     │  └─ Editor status bar (issues, safety badges)
│     └─ #ide-result-panel
│        ├─ Tabs: Result · Messages · Errors · Logs · Plan  (+ Compare results, Copy messages)
│        ├─ Views: AG Grid + structured-data side panel · messages · errors · logs · execution plan
│        └─ Execution stats footer (sparkline, copy stats)
└─ Modals/overlays: connection · reconnect password · group input · group confirm · settings (fallback)
   · table-design confirm · settings popup · fatal-actions guard · debug-extension warning
   · format confirm · SQL code style manager · about (fallback) · update (fallback) · app loading
```

---

## 5. Python ↔ JavaScript bridge / Cầu nối Python ↔ JS

- JS calls `window.pywebview.api.<method>(...)` (Promise). Readiness via `pywebviewready`; `static/js/api.js` wraps calls (`waitForPywebview`).
- `MainApi` (main window) = multiple inheritance of 16 mixins: Query, History, Session, MainMetadata, Activity, SchemaCompare, GoogleSheets, DatabaseUsers, SyntaxCatalog, ReportGen + `BravoApi` (Settings, TableEditor, Designer, Metadata, Connections, Profiler, Bravo).
- Each sub-window has its own thin `*SubwindowApi` delegating to `MainApi`.
- Python → JS: `window.evaluate_js(...)` (theme apply, Monaco relayout, `onAppUpdateAvailable`, settings broadcast).
- Snapshot of the public surface: `tests/snapshots/api_surface.txt` (MainApi 234, BravoApi 111 methods). The snapshot **does not list** the newer sub-window APIs (Activity, GoogleSheets, SchemaCompare, ResultComparator, SyntaxReference, ReportGen) — it is stale.

---

## 6. Shared styles, icons, fonts, themes / Tài nguyên dùng chung

- **CSS:** 28 files in `static/css/` (+ `google-sheets/` subfolder); design variables in `theme-variables.css` (124 KB) and `themes.css`; per-feature CSS (explorer, editor, settings, table-designer, …).
- **Themes:** 38 entries in `static/js/theme-registry.js` (dark, light, win-nt, win-xp, monokai, nord, tokyo-night ×3, GitHub ×9, synthwave, palenight ×4, shades-of-purple ×2, cobalt2, winter-is-coming ×4, …). VS Code `.vsix` themes can be imported (`app/desktop/themes.py process_import_vsix_theme`). Current saved theme: `light-jetbrains-color-theme`.
- **Typography** (from `data/settings.json`): editor JetBrains Mono 12, UI Inter 14, grid JetBrains Mono 15, messages Source Code Pro 13; applied through CSS variables by `static/js/typography.js` / `settings.js`.
- **Icons:** Font Awesome classes (e.g. `fa-drafting-compass`, `fa-diagram-project`, `fa-heart-pulse`, `fa-bolt`).

---

## 7. Dynamic rendering mechanisms / Cơ chế dựng giao diện động

- Explorer tree built and lazily expanded in JS (`explorer.js`, 202 KB); context menus generated per node type in `context-menu.js` (connection, group, database, schema, table, view, materialized view, function, procedure, trigger, column, user type, linked server, users/roles).
- Tabs are JS state (`tabs.js`, `tabsData` map, `tabType`); 11 tab types switch which workspace container is visible.
- Settings UI is generated from a schema in `settings.js` (categories: editor, grid, messages, appearance, sql, addons, history, logging, updates).
- AG Grid renders results virtually; grid edit mode (`static/js/grid/*`) with CRUD preview dialog.
- IntelliSense, syntax check, hover, signature help are custom JS engines on top of Monaco.
- Context menus also exist in: grid, editor, tabs, explorer, table designer, data editor, schema compare, query history, syntax reference, report-gen sheet grid, BRAVO program list.

---

## 8. Known runtime limitations / Giới hạn khi khảo sát

- Windows-only runtime (WebView2 + DWM + pythonnet); could not be launched from the analysis environment → no screenshots yet.
- Google Fonts load from the internet; offline rendering may differ.
- Several features need a live SQL Server/PostgreSQL connection to reach their UI states.
