# SQL-IDE — Tổng quan dự án / Project Overview

> **Mục đích / Purpose:** một file duy nhất để lần sau **không cần đọc lại cả dự án**. Chỉ đọc lại file nguồn khi mục "Khi nào phải đọc lại" (cuối file) nói cần.
> **Lập ngày:** 2026-11-05 (đọc code trực tiếp từ `D:\Luong\NB_Personal\NB_Java\SQL-IDE`, mới nhất theo mtime ≈ 2026-11-05 ~20:50).
> **Tuân thủ skill:** `java-desktop-ui-designer` v1.0 (đầu ra nằm trong `ui-specifications/`, nhãn bằng chứng `observed / code_verified / inferred / unknown`). Mọi dòng dưới đây là `code_verified` (đọc từ code / tài liệu dự án) trừ khi ghi khác. Phần "chưa kiểm chứng" ghi rõ ở mục 13.
> Ngôn ngữ: tiếng Việt trước, thuật ngữ kỹ thuật giữ tiếng Anh.

---

## 1. Dự án là gì

- **SQL IDE**: ứng dụng desktop **Java 21 + JavaFX 21.0.5** (kiểu DataGrip / VS Code), là bản chuyển đổi từ **LLTool** (Python + PyWebView, bản `LLTool_v1.3.1`, mã nguồn gốc ở `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`).
- Phạm vi UI đã duyệt: **cửa sổ chính + Settings + các dialog**. Giữ **Monaco**. Title bar kiểu VS Code nhưng hành xử như native. Mục tiêu: **tương đương VS Code**, không sao chép 100% bản Python. Mục legacy bị loại khỏi phạm vi.
- Quy trình: skill chia 4 cổng — **A** (danh sách màn hình) → **B** (đặc tả từng màn hình) → **C** (prototype mock) → **D** (tích hợp logic thật). Cổng D **đã đồng ý 2026-11-05**, đang ở Giai đoạn 8 (tích hợp thật từng khu vực).
- Maven multi-module: gốc `sql-ide` (app JavaFX, package `app.*`) + `core/` (4 module) + `infrastructure/` (10 module). Version `1.0.0-SNAPSHOT`.
- Dependencies chính (`pom.xml`): javafx-controls/fxml/graphics/**web** (WebView cho Monaco), **AtlantaFX 2.0.1** (7 theme), ControlsFX 11.2.1, Ikonli 12.3.1 (feather + materialdesign2), **RichTextFX 0.11.4** (editor cũ, còn lại làm dự phòng), **JNA-platform 5.14.0** (Win32), JUnit 5.10.2. Plugin: `maven-compiler-plugin 3.13.0` (release 21), `javafx-maven-plugin 0.0.8` (mainClass `app.application.AppLauncher`), surefire 3.2.5.

## 2. Cách chạy / build (Windows)

| Lệnh (thư mục gốc) | Việc |
|---|---|
| `run-ui.cmd` | `mvnw -q compile javafx:run`, **backend thật** (mọi kết nối chỉ đọc); log → `target\run-ui-<random>.log` |
| `run-ui-mock.cmd` | như trên nhưng `SQLIDE_BACKEND=mock` (dữ liệu mẫu, không kết nối server) |
| `run-tests.cmd` | `mvnw test` → `target\run-tests.log` (+ `target\surefire-reports`) |
| `apply-phase8-d1.cmd` | script commit mốc `ui-prototype-baseline` rồi chép/commit code Giai đoạn 8 (T6) |
| `mvnw.cmd javafx:run` | **không chắc** có biên dịch lại → dùng `mvn compile javafx:run` hoặc `run-ui.cmd` |

Khác: `.mvn/`, `mvnw*`, `SQL-IDE.code-workspace`, file `memory` (rỗng, 0 byte), `target/` (build, bỏ qua).

## 3. Cờ chạy (`framework/backend/BackendFlags`)

- Mặc định **thật**. `-Dsqlide.backend=mock` hoặc env `SQLIDE_BACKEND=mock` → mock mọi khu vực. Riêng từng khu vực: `-Dsqlide.backend.<connections|metadata|query|plan>=mock|real`. Cờ cũ `-Dsqlide.mockMetadata=true` = mock hết. Test Maven luôn chạy mock (cấu hình trong `pom.xml`).
- `-Dsqlide.readOnly` (mặc định **true**): mọi kết nối chỉ đọc (xem mục 8.3).
- `-Dsqlide.uiDebug=true`: **chưa cài đặt** (mới nằm trong kế hoạch D0.2, mục 12).

## 4. Cấu trúc thư mục (thu gọn)

```
SQL-IDE/
├─ pom.xml · README.md (README cũ, mô tả Phase 1–5) · run-*.cmd · apply-phase8-d1.cmd
├─ core/                      (Maven parent; DDD, không phụ thuộc UI — có ArchUnit)
│   ├─ core-common/    com.lltool.core.common.{error,event,identity,result,time,validation}   (Result<T>, AppError, EventBus…)
│   ├─ core-domain/    com.lltool.core.domain.{connection,project,workspace}                 (ConnectionProfile, Project, Workspace)
│   ├─ core-application/ com.lltool.core.application.{connection,port,project,workspace}     (use case + port)
│   └─ core-api/       com.lltool.core.api.{application,connection,event,project,request,workspace}
├─ infrastructure/            (Ports & Adapters)
│   ├─ configuration  · logging (SensitiveDataMasker) · filesystem (AppDirectoryManager, AtomicFileWriter)
│   ├─ storage-local (repo JSON: connection/project/workspace) · security (SecureCredentialVault, AES-256-GCM)
│   └─ database-api · database-jdbc · database-postgresql · database-sqlite · database-sqlserver
├─ src/main/java/app/         (app JavaFX — mục 5)
├─ src/main/resources/{fxml,css,monaco,static/icons,codestyle}
├─ src/test/java/app/         (JUnit; ~20 test UI/service + framework/ + ui/components/)
├─ docs/                      (phase1…phase6, feature_03…08, LLTool_Java_Migration_3_Files/{AGENTS,SOURCE_ANALYSIS,MIGRATION_TRACKER}.md, java-desktop-ui-designer-skill-v1.0/ = bản skill, ui_plan/, templates/)
└─ ui-specifications/         (đầu ra của skill — mục 10)
```
Mức class của `core/` và `infrastructure/` **không** được liệt kê ở đây (chỉ biết cấp package/module); xem `docs/phase2_core_ARCHITECTURE.md`, `docs/phase3_INFRASTRUCTURE_ARCHITECTURE.md` nếu cần.

## 5. Mã nguồn app (`src/main/java/app`, 118 file .java)

### 5.1 Khởi động & vòng đời
- `Main` (entry, không extends Application) → `application/AppLauncher extends Application`.
  - `init()`: cài `DesktopErrorHandler`, khởi tạo `DesktopBootstrap` (infrastructure + core).
  - `start()`: nạp `/fxml/main-window.fxml` → `MainController`; `Scene` 1280×820; `ThemeService.initializeTheme`; icon logo 16/32/48/128/256; `CustomWindow` (min 960×620) gắn title bar + `WindowController`.
  - `stop()`: `DesktopLifecycleManager.initiateShutdown`, đóng `ConnectionSessionManager`, `TabSessionManager.closeAll`, `DesktopMetadataService.closeAllConnections`; **watchdog 3 s** rồi `Runtime.halt(0)` (T2: JVM không thoát).
- `application/AppContext` (singleton: primaryStage, ThemeService, MainController), `AppInfo` (tên/phiên bản/runtime/platform, **mock** tới D8).

### 5.2 Controllers (`controllers/`, FXML ↔ controller)
| Controller | FXML | Dòng | Vai trò |
|---|---|---|---|
| `MainController` | `main-window.fxml` | 1592 | Điều phối cửa sổ chính: menu, toolbar ngữ cảnh, bố cục card, sash, preset layout, chạy câu lệnh (`handleExecute`), Plan, mật khẩu (`ensurePasswordForRun`), kết nối theo tab (`connectTab`), status bar, workspace, Settings/About/Update |
| `SidebarController` | `sidebar.fxml` | 744 | Object Explorer: cây nhóm→kết nối→DB…, lọc, nhóm (new/rename/delete/move), mini-rail; implements `DatabaseTreeCell.ExplorerActions` |
| `EditorController` | `editor.fxml` | 712 | Tab SQL (primary + split), Monaco host, menu tab, pin, close*, format, split |
| `ResultsController` | `results.fxml` | 1970 | Results (nhiều lưới), Messages, Timeline, Execution Plan, Terminal; menu chuột phải lưới; export/copy/aggregate/visualize |
| `StatusBarController` | `status-bar.fxml` | 384 | Status bar VS Code 22px (`SCR-MAIN-005`) |
| `ConnectionManagerController` | `connection-manager-dialog.fxml` | 859 | DLG-001 Connection Manager |
| `SettingsDialogController` | `settings-dialog.fxml` | 771 | Settings Center (`SCR-SET-001`) |
| `TitleBarController` (trong `ui/titlebar`) | `titlebar.fxml` | 440 | Title bar: menu, nút cửa sổ, badge/Update |

### 5.3 `framework/` (Phase 4–8: lớp desktop nối UI ↔ core/infra)
- `bootstrap/DesktopBootstrap` (singleton: directory manager, configuration service, vault, provider registry, các repository, core platform, project/workspace/connection API).
- `async/AsyncTaskManager`, `error/DesktopErrorHandler` (mask token), `lifecycle/DesktopLifecycleManager`, `navigation/NavigationManager` (perspective), `state/GlobalDesktopState` (workspace/project/connection/catalog/schema/dialect — observable).
- `connection/`: `ConnectionSessionManager` (kết nối **chung toàn app**, test/fetch DB+schema, `openSession`, `applyReadOnly`, `mapDatabaseType`: chỉ PostgreSQL/SQLServer/SQLite, loại khác báo lỗi), `DesktopConnectionTesterAdapter`, `DesktopCredentialVaultAdapter` (→ `SecureCredentialVault`).
- `session/`: **`TabSessionManager`** (mỗi tab editor một kết nối riêng, khóa theo `documentId`; đổi DB: SQL Server `setCatalog`, PostgreSQL mở lại; đọc PID `@@SPID`/`pg_backend_pid()`), `TabSession` (profile, database, state, error, serverPid, readOnly, environment), `CredentialResolver` (thứ tự: vừa nhập → bộ nhớ → vault).
- `metadata/`: `DesktopMetadataService` (async, cache; fetch catalogs/schemas/tables/views/columns/routines/indexes/triggers/mat.views/types/keys/constraints/sequences; 1 kết nối chỉ đọc cho Explorer mỗi profile+DB; lỗi **không** cache rỗng), `DesktopMetadataCache`, `MockMetadataProvider`, `ObjectScriptService` (D2: View Definition / Script … as CREATE/ALTER/DROP đọc từ server), `SqlScriptGenerator` (SELECT/INSERT/UPDATE/DELETE/COUNT/DROP…), `filter/{DatabaseFilter,ObjectFilter,DesktopFilterManager}`.
- `query/`: `DesktopQueryExecutionService` (virtual threads; **hiện một câu lệnh một lúc cho cả app**, Hủy chưa theo tab), `SqlStatementClassifier` (port `classify_statement`/`check_query_safety` của LLTool; có test), `SqlProtectionService` (Fatal Actions Guard: DELETE/UPDATE không WHERE, TRUNCATE…), `DebugExtensionDetector` (`bs.xxx(...)`).
- `plan/DesktopExecutionPlanService` (PostgreSQL EXPLAIN JSON, SQL Server ShowPlan XML, SQLite; có plan mô phỏng; plan thực tế chỉ chạy khi câu lệnh chỉ đọc).
- `workspace/WorkspaceDesktopManager` (khôi phục/lưu workspace, tab, layout).
- `backend/BackendFlags` (mục 3).

### 5.4 `services/` (UI-side)
`ThemeService` (7 theme: Primer Dark/Light, Dracula, Nord Dark/Light, Cupertino Dark/Light; `applyShape` nạp `shape.css` luôn + `shape-flat.css` khi phẳng, cho mọi Scene), `SettingsService` (đọc/ghi `settings.json` trong `AppDirectoryManager.getConfigDir()`, dự phòng `~/.lltool/config/settings.json`; hot-apply qua listener), `LayoutService` (sidebar/results visible-collapsed-maximized-floating, 4 preset: Default 22%/65%, Editor Focused, Explorer Focused 35%, Results Maximized 85%), `ExplorerGroupService` (nhóm = trường `group` của profile, lồng bằng `/`; nhóm rỗng lưu `Preferences`), `ExplorerConnectionState` (icon xám/spinner/đậm/đỏ — chỉ trạng thái UI), `ConnectionColorService` (màu status bar theo connection, `Preferences`, mặc định xanh), `PasswordPolicyService` (mật khẩu lưu mã hóa, hết hạn 30 ngày), `NotificationService` (toast DLG-011), mock: `DatabaseMockService`, `QueryResultMockService`.

### 5.5 `models/`
`IdeSettings` (713 dòng; khớp `settings.json` của LLTool: theme, editor, appearance, grid, messages, sql, addons(bravoTool), session, updates, syntax, history, logging, typography, query; `appearance.panelStyle` = `rounded|flat`; `grid.resultLayout` = list|tab), `DatabaseNode`/`DatabaseNodeType`, `QueryResult/Column/Row`, `ConsoleMessage`, `PhysicalExecutionPlan`, `PlanOperatorNode`.

### 5.6 `ui/`
- `components/`: `AppButton`, `IconButton`, `Badge`, `BreadcrumbBar`, `CardPane`, `RoundedCard` (clip góc + viền vẽ đè — sửa lỗi che viền), `EmptyState`, `LoadingView`, `LoadingOverlay` (DLG-014, hiện sau 150 ms, có Hủy), `SearchField`, `StatusIndicator`, `ToastHost` (toast tự vẽ), `ComponentFactory`, `ConnectionTreeBuilder` (cây nhóm dùng chung Explorer + Connection Manager), `DatabaseTreeCell` (1116 dòng; dòng Explorer + toàn bộ context menu), `LazyDatabaseTreeItem` (724 dòng; tải lười, lỗi hiện dòng "⚠ Không tải được").
- `dialogs/` (khung chung `DialogChrome`: title bar 34px tự vẽ, bo 8px / phẳng theo `panelStyle`, Esc đóng): `ConnectionManagerDialog`(DLG-001), `PasswordPromptDialog`(002), `GroupNameDialog`(003), `ConfirmDialog`(004), `DatabaseFilterDialog`(005), `ObjectFilterDialog`(006), `ActionsGuardDialog`(007a), `ProductionSafetyDialog`(007b), `FormatConfirmDialog`(008), `CodeStyleDialog`(009), `DebugExtensionDialog`(010), toast(011), `AboutDialog`(012), `UpdateDialog`(013, **mock**), `LoadingOverlay`(014); phụ: `MessageDialog` (thay Alert hệ thống), `CloseTabDialog`, `TextPromptDialog`, `SettingsDialog`, `AppDialog` (shim → MessageDialog).
- `editor/`: `MonacoEditorHost` (WebView + bridge Java↔JS, multi-model: create/switch/dispose model, theme, font, readonly, format, gotoLine/Begin/End, cursor/content callbacks), `EmbeddedMonaco` (Monaco nhỏ cho Settings JSON / xem trước code style), `EditorTabStrip` (thanh tab tự vẽ: icon kết nối, chấm trạng thái, pin, tràn, chuột giữa đóng), `SqlEditorTab` (tab + banner "Chưa kết nối"), `SqlCodeArea`/`SqlSyntaxHighlighter` (RichTextFX, **cũ**, không phải editor chính).
- `results/`: `GridFilterState` (bộ lọc riêng từng lưới), `ColumnFilterPopup` (điều kiện + checklist giá trị), `FrozenColumnSupport` (cột # cố định — vẫn lỗi T11 khi nhiều lưới), `GridDataExportUtils` (TSV/CSV/JSON/Markdown/XML/INSERT/hex/aggregates), `DesktopGridExportService` (Excel tạm, Save As).
- `settings/SettingsRowStyler` (biến dòng FXML thành item kiểu VS Code: tiêu đề + mô tả + tên biến, vạch màu nhấn khi đã sửa, lọc).
- `codestyle/`: `SqlStyleMetadata` (15 nhóm / 31 tùy chọn, resource `codestyle/sql-style-metadata.json`), `SqlStyleStore` (`sql-styles.json` cạnh settings), `SimpleSqlFormatter` (formatter **mẫu**, D6 sẽ thay).
- `titlebar/` (`TitleBar`, `TitleBarController`, `WindowControls`, `WindowTitleModel`), `window/` (`CustomWindow`, `WindowController`, `WindowStateManager`, `WindowDragHandler`, `WindowResizeHandler`, `platform/WindowsWindowAdapter` 615 dòng: Win32 frameless qua JNA — hit-test, system menu chuột phải/Alt+Space, snap, viền resize ngoài), `utils/WindowResizeHelper` (dự phòng).

## 6. Giao diện (UI)

### 6.1 Bố cục cửa sổ chính (`main-window.fxml`, BorderPane `mainWindowRoot`)
```
[titleBar  34px  — fx:include titlebar.fxml: logo · menu (File/Edit/Format/View/Tools/…/Help, gộp ☰ khi hẹp) · search · 5 nút layout · theme · settings · nút cửa sổ]
[contextToolBar 32px — card: connectionComboBox › databaseComboBox › schemaComboBox · limitComboBox · Run(executeButton) · Stop · Plan/Format/Refresh · ⋯]
[mainHorizontalSplit]
   ├ sidebar (Explorer, 260px cố định, kéo 180px…½ cửa sổ, rail thu gọn 46px; Ctrl+B / Alt+1)
   └ centerVerticalSplit ─ editorSlot(VBox, chừa khe 4px) → editor.fxml (tab strip + Monaco, split Right/Down)
                          └ results.fxml (Results / Messages / Timeline / Execution Plan / Terminal)
[statusBar 22px — fx:include status-bar.fxml]
```
`main-window.fxml`: 51 MenuItem, 10 Menu, 35 fx:id, 32 action. Khe giữa các card 6px (chế độ bo góc).

### 6.2 Hai kiểu panel — **R-UI-01 (bắt buộc)**: `appearance.panelStyle`
- `rounded` (mặc định): card bo 8px (status bar 6px), khe 6px, nền sau card = màu nền sâu nhất của theme.
- `flat`: không bo bất kỳ phần tử nào (cửa sổ, dialog, card, nút, checkbox, ô nhập, ComboBox, badge, popup), panel liền nhau, đường kẻ 1px, vùng kéo 4px.
- Ô chọn/Spinner dùng chevron down/up (không dùng tam giác mặc định); bo ô nhập/nút 6px, checkbox 4px; popup ComboBox không padding.
- Thực hiện: `ThemeService` nạp `shape.css` (luôn) và `shape-flat.css` (khi phẳng) **sau cùng** cho mọi Scene; đổi ngay khi lưu Settings. Không hard-code bán kính trong file khác nếu không có luật `.flat` tương ứng.

### 6.3 Các FXML (`src/main/resources/fxml`)
| File | Dòng | fx:id | Ghi chú |
|---|---|---|---|
| `main-window.fxml` | 309 | 35 | xem 6.1 |
| `settings-dialog.fxml` | 597 | 83 | các `sectionEditor/Grid/Messages/Appearance/…`, 18 Spinner, 17 CheckBox |
| `results.fxml` | 272 | 49 | 5 tab: Results, Messages, Timeline, Execution Plan, Terminal |
| `connection-manager-dialog.fxml` | 198 | 51 | form 2 cột + danh sách nhóm |
| `sidebar.fxml` | 100 | 9 | header "OBJECT EXPLORER" + 6 nút (Kết nối mới, Nhóm mới, Lọc, Làm mới, Thu gọn tất cả, Thu về rail) |
| `titlebar.fxml` | 73 | 17 | |
| `status-bar.fxml` | 70 | 22 | trái: kết nối, PID, lỗi, READ ONLY/TX OPEN, kết quả lần chạy; phải: Ln/Col, Spaces, UTF-8, LF/CRLF, SQL, zoom, chuông |
| `editor.fxml` | 56 | 8 | TabPane + SplitPane |

### 6.4 CSS (`resources/css`, thứ tự nạp do `ThemeService`)
`base.css` (font/`.root`, định nghĩa looked-up color — sửa T3) · `light-theme.css` / `dark-theme.css` · `layout.css` (563 dòng: card, khe, sash, status bar) · `components.css` (1507 dòng, ~253 luật: Explorer, tab, grid…) · `editor.css` · `dialogs.css` · `connection-manager.css` · `settings.css` · `code-style.css` · `toast.css` · `titlebar/{titlebar,window-controls}.css` · **`shape.css`** (luôn) + **`shape-flat.css`** (chỉ khi phẳng, nạp cuối). Biến màu theo AtlantaFX (`-color-fg-default`, `-color-fg-muted`…). Icon: Ikonli `FontIcon` (feather / MDI2) + PNG logo (`static/icons`: app-logo-16…256, postgresql, sqlite, sqlserver).

### 6.5 Design tokens
`ui-specifications/02-design-system/tokens.{md,json}` (đã duyệt): font UI **Inter** (đổi được trong Settings; dự phòng Segoe UI; **chưa** nhúng file font — kế hoạch nhúng ở Phase 1), lưới kết quả **12px** (đổi được), editor/grid **JetBrains Mono**; cao: title bar 34, toolbar 32, status bar 22, dòng cây Explorer 22; sidebar 260; rail 46.

### 6.6 Monaco
`resources/monaco/{editor.html, editor.css, editor-bridge.js (≈12 KB), loader.js, vs/…}` (Monaco bản vendor, `editor.main.js` ≈ 3.7 MB — **đừng đọc**). Một WebView, nhiều model; bridge JS cung cấp tạo/chuyển/hủy model, theme, font, readonly, format, gotoLine/Begin/End, sự kiện nội dung/con trỏ/Execute.

## 7. Màn hình & dialog (ID theo skill) và trạng thái

| ID | Nội dung | Trạng thái (theo `migration-plan.md`) |
|---|---|---|
| SCR-SHELL-001 | Title bar native-like | `implemented_mock`, Gate C tạm duyệt |
| SCR-MAIN-001 | Bố cục chung (card, khe, rail, Ctrl+B) | chốt GĐ 1; lỗi **T7** |
| SCR-MAIN-002 | Object Explorer | mock + D1/D2 thật; lỗi **T8** |
| SCR-MAIN-003 | Editor + tab strip + toolbar ngữ cảnh | chốt GĐ 3 |
| SCR-MAIN-004 | Bảng kết quả | chốt GĐ 4; lỗi **T11**, T10 (D4) |
| SCR-MAIN-005 | Status bar | chốt GĐ 1 |
| SCR-SET-001 | Settings | chốt GĐ 5 (dialog riêng 900×640) |
| DLG-001…006, 007a/b, 008, 009, 010, 011…014 | xem 5.6 | chốt GĐ 2B/2C, 3, 5, 6; lỗi **T9** (ComboBox viền) |

Bộ đặc tả đủ 7+ file chỉ có cho **SCR-SHELL-001** (đầy đủ), **SCR-MAIN-001/002/005** (đủ file); **SCR-MAIN-003** chỉ có `toolbar-redesign.md`; **SCR-MAIN-004, SCR-SET-001, DLG-001…014 chưa có bộ đặc tả** (ghi ở `phase7-review.md`).

## 8. Logic chính (luồng)

### 8.1 Kết nối & phiên theo tab (D1)
Người dùng chọn kết nối trên toolbar / bấm "Kết nối" ở banner → `MainController.connectTab` → `ensurePasswordForRun` (DLG-002 nếu cần) → `TabSessionManager.connectAsync` (nền) → `ConnectionSessionManager.openSession` (+`applyReadOnly`) → `TabSession` (state, PID). Chấm trên tab: màu kết nối / vàng (đang kết nối) / đỏ (lỗi) / xám (chưa). Đổi kết nối trên toolbar ngắt phiên cũ của tab đó; đóng tab đóng phiên; thoát app đóng hết. Loại DB chưa hỗ trợ (MySQL…) → lỗi rõ.

### 8.2 Chạy câu lệnh
`handleExecute` → `SqlStatementClassifier` (chỉ đọc) → `SqlProtectionService` (Actions Guard DLG-007a) → cảnh báo PROD (DLG-007b) → `DesktopQueryExecutionService.execute` (virtual thread, `Statement` được đóng) → `ResultsController.setQueryResult` (nhiều lưới xếp dọc thu gọn / theo tab) + Messages/Timeline. Plan: `DesktopExecutionPlanService`. **Chưa làm (D3):** Messages/PRINT/NOTICE, chạy song song nhiều tab, Hủy theo tab.

### 8.3 Chỉ đọc 3 lớp
(1) kiểm câu lệnh trước khi gửi (kể cả trước hộp xác nhận PROD); (2) JDBC `setReadOnly(true)`; (3) PostgreSQL `SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY`.

### 8.4 Explorer
`SidebarController.loadData` → `ConnectionTreeBuilder` (nhóm theo `profile.group`) → `LazyDatabaseTreeItem` tải lười qua `DesktopMetadataService` (cache, nền). Context menu trong `DatabaseTreeCell` (theo `context-menu.js` của LLTool); mục ngoài phạm vi (Profiler, Activity Monitor, So sánh Schema, Design Table, Edit Data, Dependencies, Properties, Search Object Content) hiện mờ "(chưa hỗ trợ)". Mật khẩu sai → quên mật khẩu tạm để hỏi lại.

### 8.5 Settings & theme
`SettingsService.loadSettings/saveSettings/applySettings` → listener cập nhật Monaco, lưới, shape, font… ngay (hot-apply). Lưu bằng nút Lưu + "N thay đổi chưa lưu" + hỏi khi đóng. Tab JSON dùng Monaco.

### 8.6 Nơi lưu dữ liệu
`settings.json` (config dir của `AppDirectoryManager`, dự phòng `~/.lltool/config/`), `sql-styles.json` (cạnh settings), profile/project/workspace JSON (`storage-local`), mật khẩu trong vault AES-256-GCM, `java.util.prefs.Preferences` cho màu status bar theo connection và nhóm rỗng. Độ rộng Explorer nằm trong `LayoutService.sidebarWidthPx` (nơi lưu bền chưa kiểm chứng).

## 9. Test (`src/test/java/app`)
~25 file: `AppTest, AtlantaThemesTest, CheckIcons/MdiIcons/StylesTest, ComponentsTest, CustomWindowTitleBarTest, DatabaseExplorer(Phase63)Test, EditorWorkspaceTest, ExecutionPlanTest, LayoutServiceTest, MainWindowFXMLTest, MonacoEditorHostTest, QueryExecutionTest, ResultsGrid/ResultsPanelTest, RichTextFXTest, SettingsAndThemesTest, SqlProtectionServiceTest`, `framework/{ConnectionDesktopIntegrationTest, DesktopFrameworkTest, WorkspaceDesktopIntegrationTest, metadata/ObjectScriptServiceTest, query/SqlStatementClassifierTest}`, `ui/components/ComponentFactoryTest`. Mốc đã ghi: **107 test xanh** ở Giai đoạn 0 (sau đó thêm test D1/D2; chưa có số mới). `ObjectScriptServiceTest` chưa chạy bằng JUnit trên máy.

## 10. Bộ tài liệu `ui-specifications/` (theo skill)
```
00-application/  project-map.md, application-map.json, source-index.md, discovery-report.md  (về app **Python** LLTool, 16 cửa sổ)
                 screen-inventory.{md,json} (21 màn hình), navigation-map.md, screenshots/source/ (12 ảnh app gốc)
01-screens/      SCR-SHELL-001/ (7 file + 12 ảnh), SCR-MAIN-001/, SCR-MAIN-002/, SCR-MAIN-005/ (7 file mỗi nơi), SCR-MAIN-003/ (toolbar-redesign.md + ảnh)
02-design-system/ tokens.md, tokens.json
04-reports/      migration-plan.md (kế hoạch + R-UI-01 + lỗi tồn), phase7-review.md, phase8-plan.md (D0…D9, K1–K10)
(03-traceability/ chưa có)
```
Tài liệu cũ của dự án nằm ở `docs/` (xem mục 4). Skill gốc: `docs/java-desktop-ui-designer-skill-v1.0/` và trong Project "SQL-IDE" (SKILL.md, 01…05).

## 11. Giai đoạn đã làm (tóm tắt)
GĐ 0 nền tảng (T1–T5 xong; T6 commit chờ chạy `apply-phase8-d1.cmd`) → GĐ 1 bố cục + status bar → GĐ 2A/2B/2C Explorer + dialog kết nối + R-UI-01 → GĐ 3 editor/tab/dialog chạy → GĐ 4 bảng kết quả → GĐ 5 Settings + code style → GĐ 6 toast/About/Update(mock)/loading → GĐ 7 review (`PASS_WITH_KNOWN_GAPS`) → **GĐ 8 (Gate D đồng ý)**: D0 + D1 tạm đạt (2 tab = 2 PID khác nhau), D2 đã code chờ kiểm (K6–K10), **D3–D9 chưa làm** (D7 IntelliSense tách khỏi GĐ 8).
Quyết định đã chốt (2026-11-05): thứ tự DB SQL Server → PostgreSQL → SQLite (MySQL chưa cần); server thử `localhost,1433` (SQL Server), `localhost,5432` (PostgreSQL, user `postgres`) — **mật khẩu không ghi vào repo/tài liệu**; mỗi tab một kết nối; **mọi kết nối chỉ đọc**; Format phím `Ctrl+Alt+L`; tên title bar "SQL IDE"; 5 nút layout giữ nguyên.

## 12. Việc tồn đọng / lỗi đã biết
| # | Vấn đề | Trạng thái |
|---|---|---|
| T6 | Commit Git | chờ chạy `apply-phase8-d1.cmd` |
| T7 | Thanh kéo giữa Editor và Results không kéo được (sash 2px không bắt chuột; đã thử `installEditorSash`/`editorSlot`) | **chưa sửa**; nghi `RoundedCard`/divider SplitPane che; hướng: thay SplitPane dọc bằng layout tự vẽ |
| T8 | Icon Explorer lệch giữa các cấp (đã thử ô mũi tên 16–18px) | **chưa sửa**; hướng: tự vẽ mũi tên trong graphic của ô |
| T9 | ComboBox viền lệch ở chế độ bo góc | **chưa sửa**; cần phóng ảnh 2 theme |
| T10 | Tiêu đề "Bảng N" chỉ lấy tên bảng sau FROM đầu | làm ở D4 (cần backend trả thông tin câu lệnh) |
| T11 | Cột # không cố định khi cuộn ngang với nhiều lưới | **chưa sửa**; hướng: cột số dòng riêng ngoài TableView |
| D0.2 | Chế độ `-Dsqlide.uiDebug=true` (log cây node/CSS) **chưa làm** — người dùng bảo chỉ ghi lại; log T7–T11 nằm ở `migration-plan.md` mục 2, `phase7-review.md` mục 2, `phase8-plan.md` D0.2 |
| K1–K10 | Danh sách cần kiểm D1/D2 (`phase8-plan.md` mục 7), tất cả ⏳ chưa kiểm |
| D2 | Chưa có lớp phủ "đang tải" + Hủy cho Explorer; Properties/Dependencies/Search Object Content còn mờ |
| Khác | Font Inter chưa nhúng; bộ format SQL thật (D6) và cập nhật thật (D8) chưa làm; `AppInfo` là hằng số |

## 13. Chưa kiểm chứng / giới hạn của file này
- Không chạy được app/test trong lần lập này; mọi trạng thái "chốt/tạm đạt" lấy từ `migration-plan.md` và `phase8-plan.md`, **không** do người lập tự xác nhận trên máy.
- Không đọc nội dung từng dòng của `MainController` (1592), `ResultsController` (1970), `DatabaseTreeCell` (1116) — chỉ đọc danh sách phương thức/Javadoc. Muốn sửa các file này: đọc đúng đoạn cần.
- Không đọc: `core/*`, `infrastructure/*` (mức class), `docs/*`, `ui-specifications/01-screens/*` (spec chi tiết), `monaco/vs/**`, `target/**`.
- Số liệu số dòng / kích thước lấy ngày 2026-11-05; file đổi sau đó sẽ lệch.

## 14. Khi nào phải đọc lại (để tiết kiệm token)
| Việc | Đọc gì |
|---|---|
| Sửa title bar / cửa sổ native | `ui/window/platform/WindowsWindowAdapter.java`, `ui/titlebar/TitleBarController.java`, `01-screens/SCR-SHELL-001/screen-spec.md` |
| Bố cục / khe / sash / card | `MainController` (`setupCardClipping`, `setupCardGaps`, `installEditorSash`, `applyPanelStyle`), `layout.css`, `RoundedCard`, `shape*.css` |
| Explorer / context menu | `DatabaseTreeCell`, `LazyDatabaseTreeItem`, `SidebarController`, `01-screens/SCR-MAIN-002/screen-spec.md` |
| Chạy SQL / an toàn | `DesktopQueryExecutionService`, `SqlStatementClassifier`, `SqlProtectionService`, `MainController.handleExecute` |
| Phiên / kết nối | `TabSessionManager`, `ConnectionSessionManager`, `CredentialResolver` |
| Lưới kết quả | `ResultsController`, `results.fxml`, `ui/results/*` |
| Settings | `settings-dialog.fxml`, `SettingsDialogController`, `IdeSettings`, `SettingsRowStyler` |
| Đổi kế hoạch / lỗi tồn | `ui-specifications/04-reports/migration-plan.md`, `phase8-plan.md` |
| Cần thêm màn hình mới | skill: `workflows/03-screen-analysis.md` + templates; spec mẫu `SCR-SHELL-001` |

**Cập nhật file này** khi: thêm/xóa class lớn, đổi cấu trúc module/FXML, hoàn thành một mốc D*/GĐ, hoặc sửa xong T7–T11. Chỉ sửa mục liên quan, ghi ngày ở đầu file.
