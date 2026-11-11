# LLTool Java Migration — Feature Tracker

## 1. Migration strategy

Migrate the existing LLTool product **one existing feature at a time**.

The Python application is the behavior reference. The Java application will use a modern native-feeling desktop UI, while preserving established workflows and functionality.

### Scope rule

- First: migrate existing features.
- Later: implement new features and enhancements.
- Do not bundle unrelated additions into a migration task.
- A screen built with mock data is a UI milestone, not a completed feature.

## 2. Status definitions

| Status | Meaning |
|---|---|
| `NOT_ANALYZED` | Python implementation has not been inspected. |
| `ANALYZED` | Existing behavior and dependencies documented. |
| `PLANNED` | Java scope and acceptance criteria agreed. |
| `UI_IN_PROGRESS` | Java UI implementation underway. |
| `UI_VERIFIED` | Layout and interactions reviewed; functionality may still be mocked. |
| `IMPLEMENTING` | Functional Java implementation underway. |
| `FUNCTIONALLY_VERIFIED` | Existing behavior and data effects tested. |
| `COMPLETE` | Acceptance criteria and regression checks passed. |
| `BLOCKED` | Waiting on a dependency or decision. |
| `DEFERRED` | Intentionally postponed. |
| `APPROVED_DEVIATION` | A behavior or design difference was explicitly approved. |

## 3. Migration phases

### Phase 0 — Source understanding and migration preparation
- [x] Confirm the Python repository and current branch/version. (Verified: `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude` v1.2.3, branch `main`)
- [x] Inspect the existing Java project and preserve any work already completed. (Verified: Clean Architecture, Core Platform, Infrastructure, Desktop Framework, UI Components, 71/71 tests passing)
- [x] Build a source inventory of screens, frontend handlers, API routes, backend services, database access, and persisted data. (Completed in Phase 0 Inventory Report)
- [x] Record the current product's major workflows. (Connection lifecycle, Object Explorer, Query Editor/Monaco, Results Grid, Settings)
- [x] Capture reference screenshots for major screens and states. (Located in `docs/archive/_concept_/`, `docs/templates/`)
- [x] Create a list of known defects, technical debt, and unanswered questions. (Documented: Cache invalidation, Monaco find keyboard leak, encryption format transition)
- [x] Agree on the initial migration slice. (Slice 1: Connection Management)

**Exit condition:** the team can identify the real source implementation for the first feature and explain its expected behavior. -> **MET**

### Phase 1 — Main desktop shell and visual foundation
- [x] Reproduce the main application regions using JavaFX.
- [x] Establish a modern native desktop visual system.
- [x] Implement menus, toolbar, workspace regions, status area, and window behavior as applicable.
- [x] Implement basic panel resizing and collapse/expand behavior.
- [x] Verify layout at common window sizes.
- [x] Use mock data only where needed to validate the UI.

**Exit condition:** the main shell feels like a coherent desktop application and provides the structural foundation for feature migration. -> **MET**

### Phase 2 — Core feature migration
Migrate each existing feature as a separate vertical slice. A suggested order is below; adjust it after source analysis and dependency mapping.

| Order | Feature | Python analysis | Java UI | Java behavior | Verification | Notes |
|---:|---|---|---|---|---|---|
| 1 | Connection management | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | Saved connections, add/edit/delete, duplicate, connect/disconnect, secure vault |
| 2 | Object Explorer | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | Tree, refresh, filtering, context actions, drag & drop, lazy load |
| 3 | Query tabs and editor lifecycle | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | Multi-tab strip, split view, dirty state, close/pin, empty state, per-tab context |
| 4 | Monaco editor integration | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | Single WebView host, Multi TextModel, Java<->JS bridge, Dark/Light theme, offline vs/ (UI-03) |
| 5 | Query execution | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | Run, stop, parameters, errors, selection execution, auto-show/persist results panel |
| 6 | Results and Messages | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | Comprehensive Context Menu (15+ items), NULL styling, Aggregates, Script INSERT, Excel Open/Save, Messages |
| 7 | Execution Plan | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | PostgreSQL (JSON), SQL Server (XML ShowPlan), SQLite, Visual Tree with Cost %, Inspector, Raw Text |
| 8 | Settings and themes | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | 10 Categories Visual Form + Raw JSON Editor, Real-time Syntax Badge, 0ms Hot-Apply (Monaco, Grid, Messages, Typography, Toolbar), SQL Protection Guard (with Temp Table & Table Variable exemption), 100% JSON Schema fidelity |
| 9 | Snippets and saved SQL | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | 78 standard snippets, Red Gate `$var$` to Monaco `${1:var}` auto-selection/focus, CRUD, pinning, DB target filtering, atomic persistence |
| 10 | Query history | `ANALYZED` | `UI_VERIFIED` | `FUNCTIONALLY_VERIFIED` | `COMPLETE` | SQLite WAL storage, SHA-256 deduplication, zlib compression, RunCount coalescing, tagging, favorites, instant filtering, EmbeddedMonaco preview, non-blocking background logger |

### Phase 3 — Existing advanced workspaces
Only include a workspace after confirming it exists and is functional in the Python source.

| Feature / workspace | Python analysis | Java UI | Java behavior | Verification | Notes |
|---|---|---|---|---|---|
| Schema Compare | `COMPLETE` | `COMPLETE` | `IN_PROGRESS` | `IN_PROGRESS` | Forward Sync, Master-Detail categorized tree, side-by-side Monaco DDL viewers, transactional migration script, selective deploy (47/47 tests pass) |
| Bravo Layout Compare | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | |
| Table Designer | `COMPLETE` | `COMPLETE` | `COMPLETE` | `COMPLETE` | Hoàn thiện grids ràng buộc, SaveDesignDialog, migration DDL (19/19 tests pass) |
| Table Data Editor | `COMPLETE` | `COMPLETE` | `COMPLETE` | `COMPLETE` | Hoàn thành inline editor, optimistic concurrency, PreviewDataChangesDialog (30/30 tests pass) |
| Dependency Graph | `COMPLETE` | `COMPLETE` | `IN_PROGRESS` | `IN_PROGRESS` | Đồ thị phân cấp Sugiyama, canvas kéo thả/zoom/pan/export PNG, Inspector 3 tab (Properties, Monaco DDL, Suggested Steps), tích hợp context menu Table/View/Proc/Col (54/54 tests pass) |
| Object Content Search | `COMPLETE` | `COMPLETE` | `COMPLETE` | `COMPLETE` | Phân nhóm loại object kèm tổng số, resizable/collapsible bottom occurrences panel, Monaco decorations, click-to-jump (41/41 tests pass) |
| SQL Visualizer | `COMPLETE` | `COMPLETE` | `IN_PROGRESS` | `IN_PROGRESS` | Trực quan hóa luồng dữ liệu 6 giai đoạn, Canvas pipeline Bezier kéo thả/zoom/pan/export PNG, Inspector AST, đồng bộ hai chiều SourceRange với mã SQL (42/42 tests pass) |
| Activity Monitor | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | |
| User Permissions | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | |
| Google Sheets integration | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | `NOT_ANALYZED` | |

### Phase 4 — Compatibility, regression, and release readiness
- [ ] Verify supported database providers and their feature differences.
- [ ] Verify user data import/compatibility.
- [ ] Run regression checks across migrated workflows.
- [ ] Review error handling and cancellation.
- [ ] Review performance with realistic result sizes.
- [ ] Review keyboard navigation and accessibility basics.
- [ ] Review packaging, startup, configuration, and logging.
- [ ] Document any approved deviations from Python behavior.

**Exit condition:** migrated functionality is stable, user data is protected, and known gaps are documented.

### Phase 5 — Future enhancements (after migration baseline)
New features belong here, not inside existing feature migration tasks.

| Enhancement | Priority | Status | Notes |
|---|---|---|---|
| To be defined after baseline migration | — | `DEFERRED` | Add only when requested and scoped |

## 4. Per-feature work record

### Feature 1: Connection Management

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 1

#### Python reference
- Files: `app/api/connections_api.py`, `app/services/connection_manager.py`, `app/utils/crypto.py`, `templates/partials/connection_modal.html`, `static/js/connections.js`
- Functions / classes: `ConnectionsApiMixin`, `ConnectionManager`, `encrypt_password`, `decrypt_password`
- API routes: `get_saved_connections`, `save_connection_profile`, `delete_connection_profile`, `test_connection`, `get_connection_password`
- Database / persistence: `data/connections.json`, `.secret.key`
- Reference screenshots: `docs/archive/_concept_/connect.png`, `docs/templates/connection_edit.png`

#### Existing behavior
- Main workflow: Người dùng mở modal kết nối, xem danh sách kết nối đã lưu (phân nhóm), tạo mới/sửa/nhân bản/xóa kết nối cho PostgreSQL hoặc SQL Server, cấu hình thông số máy chủ/port/database/schema/auth, kiểm tra kết nối (Test Connection) bất đồng bộ, lưu cấu hình đã mã hóa mật khẩu, và kết nối ngay (Connect Now) để mở phiên làm việc.
- Validation: Bắt buộc có Name, Host, Port hợp lệ, Database/FilePath hợp lệ, kiểm tra thông tin đăng nhập khi không dùng Windows Auth.
- Error handling: Hiển thị thông báo lỗi chi tiết khi test thất bại hoặc khi thông số không hợp lệ; không làm crash ứng dụng.
- State and side effects: Cập nhật danh sách kết nối vào file cấu hình; khi kết nối thành công, cập nhật Active Connection trong toàn hệ thống và đồng bộ lên Object Explorer & Toolbar.
- Related features: Object Explorer, Query Tabs, Query Execution.

#### Java implementation
- UI: `ConnectionManagerDialog.java`, `ConnectionManagerController.java`, `connection-manager-dialog.fxml`, `connection-manager.css` (Master-detail layout, search/filter, provider icons, badges, toggle switches).
- Services: `ConnectionApi`, `DefaultConnectionApi`, `ConnectionSessionManager`, `DesktopConnectionTesterAdapter`, `DesktopCredentialVaultAdapter`.
- Data access: `LocalConnectionRepository` (lưu trữ nguyên tử JSON trong `~/.lltool/data/connections/{id}.json`), `SecureCredentialVault` (mã hóa AES-256-GCM với tiền tố `cred://<uuid>`).
- External dependencies: PostgreSQL JDBC driver, SQL Server JDBC driver, SQLite JDBC driver.
- Mock data (if any): Không dùng mock trong môi trường chạy thật; chỉ dùng in-memory SQLite/temp profiles trong unit/integration tests.

#### Acceptance criteria
- [x] Existing main workflow works: Thêm, sửa, nhân bản, xóa, kiểm tra và lưu kết nối hoạt động hoàn chỉnh.
- [x] Important edge cases are covered: Port ngoài khoảng, thiếu host, password rỗng, Windows Auth vs SQL Auth, SQLite file path.
- [x] Errors are handled and visible: Lỗi kết nối hiển thị rõ ràng trên UI qua `statusLabel` mà không làm đơ giao diện.
- [x] Existing data semantics are preserved: Giữ nguyên các trường `group`, `environment`, `readOnly`, `databaseType`, `host`, `port`, `databaseName`, `timeoutSeconds`, `trustServerCertificate`.
- [x] UI follows the modern native desktop design system: Áp dụng giao diện desktop AtlantaFX với Master-Detail, Ikonli icons, SearchField, badges.
- [x] No unrelated new feature was added: Tập trung đúng phạm vi quản lý hồ sơ kết nối và phiên kết nối.

#### Verification
- Test cases: `ConnectionDesktopIntegrationTest` (6 test cases bao gồm bootstrap, vault, tester, session manager, FXML, edit loading, context menu - 100% PASS).
- Manual checks: Tải FXML không lỗi, kéo thả hộp thoại mượt mà, lưu cấu hình nguyên tử.
- Known gaps: Cần viết thêm module tiện ích nhập tự động (import) từ file `connections.json` cũ của Python cho người dùng có sẵn dữ liệu.

#### Deviations and decisions
- Thay thế lưu trữ nguyên mảng `connections.json` của Python bằng cấu trúc thư mục `~/.lltool/data/connections/{id}.json` kết hợp `AtomicFileWriter` để chống hỏng file khi crash/mất điện đột ngột.
- Thay thế mã hóa DPAPI/Fernet bằng chuẩn mã hóa quốc tế `AES-256-GCM` trong `SecureCredentialVault`.

### Feature 2: Object Explorer

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 2

#### Python reference
- Files: `templates/partials/sidebar.html`, `static/js/explorer.js`, `static/css/explorer.css`, `app/api/metadata_api.py`, `app/services/metadata_service.py`
- Functions / classes: `MetadataApiMixin`, `MainMetadataApiMixin`, `MetadataService`, `renderExplorerTree`
- API routes: `get_databases`, `get_schemas`, `get_tables`, `get_columns`, `get_routines`, `get_ddl`
- Database / persistence: Truy vấn catalog hệ thống (PostgreSQL: `pg_catalog`, `information_schema`; SQL Server: `sys.tables`, `sys.columns`, `sys.schemas`)
- Reference screenshots: `docs/archive/_concept_/Object_Menu/talbe_menu.png`, `view_menu.png`, `procedure_function_menu.png`

#### Existing behavior
- Main workflow: Người dùng duyệt cấu trúc Server > Database > Schema > Tables, Views, Procedures, Functions, Synonyms. Hỗ trợ lazy load khi mở rộng node, tìm kiếm lọc nhanh (search filter) có đếm số lượng khớp, các menu ngữ cảnh tạo script DDL (SELECT, INSERT, UPDATE, DELETE), mở xem dữ liệu bảng (top 100 rows), xem cấu trúc DDL, lọc đối tượng (Filter Dialog), và thu gọn thanh bar thành mini strip icon.
- Validation: Tìm kiếm không phân biệt hoa thường, hỗ trợ debounce chống giật lag, tự động reset trạng thái rỗng khi không có kết quả.
- Error handling: Hiển thị lỗi kết nối nếu không nạp được metadata mà không làm sập giao diện.
- State and side effects: Cho phép kéo thả tên bảng hoặc câu lệnh `SELECT` trực tiếp vào Editor.
- Related features: Connection Management, Query Editor, Table Data Editor, Table Designer.

#### Java implementation
- UI: `sidebar.fxml`, `SidebarController.java`, `DatabaseTreeCell.java`, `LazyDatabaseTreeItem.java`, `layout.css`, `components.css`.
- Services: `DesktopMetadataService`, `DesktopFilterManager`, `SqlScriptGenerator`.
- Data access: `DatabaseProvider` SPI qua JDBC (`PostgreSqlProvider`, `SqlServerProvider`, `SqliteProvider`).
- Dialogs: `DatabaseFilterDialog.java`, `ObjectFilterDialog.java`.
- Drag and drop: Hỗ trợ kéo thả `Table` sinh `SELECT * FROM ...`, kéo node sinh tên đối tượng.

#### Acceptance criteria
- [x] Existing main workflow works: Hiển thị cây đối tượng phân cấp mượt mà, hỗ trợ lazy loading nền chống đơ UI.
- [x] Important edge cases are covered: Database/Schema rỗng, lọc tìm kiếm không có kết quả hiển thị EmptyState, thu gọn/mở rộng All.
- [x] Errors are handled and visible: Lỗi metadata được log an toàn và hiển thị trạng thái thích hợp.
- [x] Existing data semantics are preserved: Giữ nguyên các danh mục Table, View, Materialized View, Procedure, Function, Index, Trigger, Sequence, Column PK/FK.
- [x] UI follows the modern native desktop design system: Áp dụng AtlantaFX Dark/Light theme, Ikonli icons, badges PK/FK, mini strip collapse/expand.
- [x] No unrelated new feature was added: Giữ đúng phạm vi duyệt cấu trúc dữ liệu và menu ngữ cảnh thao tác DDL.

#### Verification
- Test cases: `DatabaseExplorerPhase63Test` (11 tests pass) và `DatabaseExplorerTest` (3 tests pass).
- Manual checks: Lazy load mở rộng node mượt mà, lọc text search hiển thị badge số lượng kết quả, menu ngữ cảnh đầy đủ thao tác DDL.
- Known gaps: Tích hợp với Monaco Editor khi kéo thả thay cho RichTextFX.

#### Deviations and decisions
- Bổ sung thanh Mini Sidebar Strip dạng icon (JetBrains style) khi người dùng thu gọn Object Explorer để chuyển đổi kết nối nhanh.

### Feature 3: Query Tabs and Editor Lifecycle

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 3

#### Python reference
- Files: `static/js/tabs.js`, `static/js/editor.js`, `templates/partials/editor.html`, `tests/test_tab_manager.py`, `docs/archive/bugs/tab_manager.md`, `data/session.json`
- Functions / classes: `createTab`, `switchTab`, `closeTab`, `closeOtherTabs`, `closeAllTabs`, `pinTab`, `unpinTab`, `calculateTabLayout`
- API routes: Quản lý frontend state, lưu session vào `data/session.json`
- Database / persistence: `data/session.json` (lưu tab title, path, active tab)
- Reference screenshots: `docs/archive/_concept_/intellisense/intellisense_text_editor.png`

#### Existing behavior
- Main workflow: Tạo tab mới (`+` hoặc `Ctrl+N`), đóng tab (`Ctrl+W`), đóng tất cả (`Ctrl+Shift+W`), đóng tab khác, đóng tab chưa sửa đổi (Close Unmodified). Ghim tab (Pin Tab) cố định bên trái chống vô tình đóng. Theo dõi trạng thái chưa lưu (dirty state `•` / `*`) và nhắc người dùng trước khi đóng (Save, Don't Save, Cancel). Chia màn hình soạn thảo (Split Right, Split Down). Hiển thị Empty State khi đóng toàn bộ tab. Gắn ngữ cảnh Connection/Database/Schema theo từng tab.
- Validation: Không cho phép đóng tab ghim khi thực hiện Close All / Close Others; nhắc nhở khi đóng tab dirty; tự động kích hoạt Empty State khi không còn tab nào.
- Error handling: Hộp thoại xác nhận lưu thay đổi không làm crash hoặc mất trạng thái; khôi phục an toàn.
- State and side effects: Đồng bộ context Connection / Database / Schema từ tab được chọn lên thanh Toolbar và StatusBar.
- Related features: Monaco Editor (Feature 4), Object Explorer (Feature 2), Query Execution (Feature 5).

#### Java implementation
- UI: `editor.fxml`, `EditorController.java`, `SqlEditorTab.java`, `layout.css`, `editor.css`.
- Tabstrip & Split View: Hỗ trợ `primaryTabPane` và `secondaryTabPane` trong `SplitPane`, cho phép chia màn hình ngang/dọc, nút đóng split (`closeSplitButton`), nút `+` New Tab ghim cuối thanh tab.
- Lifecycle & Action handlers: Close, Close Others, Close All, Close Unmodified, Split Right, Split Down, Rename Tab, Pin/Unpin Tab.
- Tab Context: Bổ sung `connectionName`, `databaseName`, `schemaName` vào `SqlEditorTab`, tự động đồng bộ lên `connectionComboBox` và `schemaComboBox` trên thanh Toolbar qua `MainController.syncConnectionToToolbar()` và `syncSchemaToToolbar()`.
- Keyboard Shortcuts: `Ctrl + N` (New Tab), `Ctrl + W` (Close Tab), `Ctrl + Shift + W` (Close All Tabs).

#### Acceptance criteria
- [x] Existing main workflow works: Mở, đóng, chuyển đổi tab, chia màn hình soạn thảo, ghim tab hoạt động chuẩn xác.
- [x] Important edge cases are covered: Đóng tab khi dirty hỏi lưu, tab ghim không bị đóng bởi Close Others/Close All, đóng hết tab hiển thị Empty State.
- [x] Errors are handled and visible: Không có lỗi runtime khi đóng hoặc chia tab.
- [x] Existing data semantics are preserved: Lưu giữ nguyên vẹn ngữ cảnh kết nối, database, schema và đường dẫn file cho từng tab.
- [x] UI follows the modern native desktop design system: Tab header hiện đại, icon pin, icon dirty indicator, phím tắt desktop chuẩn.
- [x] No unrelated new feature was added: Giữ đúng phạm vi quản lý vòng đời tab soạn thảo.

#### Verification
- Test cases: `EditorWorkspaceTest` (6 unit tests pass 100% bao gồm syntax highlighting, dirty tracking, empty state transitions, tab context lifecycle, tab pinning close protection, split editor context preservation).
- Manual checks: FXML tải mượt mà, menu File hỗ trợ `Ctrl+W` và `Ctrl+Shift+W`, context từ Object Explorer được chuyển giao đầy đủ vào tab mới.
- Known gaps: Tích hợp với Monaco Editor engine (Feature 4 - UI-03) thay thế `SqlCodeArea` thử nghiệm.

#### Deviations and decisions
- Trong JavaFX, `TabPane` hỗ trợ phân trang tab tự nhiên, kết hợp Empty State Box khi số lượng tab = 0.
- Điều chỉnh phím tắt `Save Workspace` thành `Ctrl+Alt+W` để dành `Ctrl+Shift+W` cho thao tác `Close All Tabs` chuẩn convention của desktop IDEs.

### Feature 4: Monaco Editor Integration

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 4 (UI-03)

#### Python reference
- Files: `static/vendor/monaco/vs/*`, `static/js/editor.js`, `templates/partials/editor.html`, `static/css/editor.css`
- Functions / classes: `initMonaco`, `createModel`, `switchModel`, `disposeModel`, `monaco.editor.create`, `onDidChangeModelContent`, `onDidChangeCursorPosition`
- API routes: Không có API HTTP riêng; toàn bộ diễn ra tại client runtime
- Database / persistence: `settings.json` (font size, theme, editor options)
- Reference screenshots: `docs/archive/_concept_/intellisense/intellisense_text_editor.png`

#### Existing behavior
- Main workflow: Soạn thảo SQL phong phú với highlight từ khóa, số, chuỗi, comment, tự động đóng ngoặc; mỗi tab giữ một `TextModel` riêng biệt với lịch sử Undo/Redo, vị trí con trỏ và scroll position; chuyển tab mượt mà (<5ms) mà không tải lại trang web; cập nhật dòng/cột lên status bar; cập nhật dirty state khi sửa đổi; hỗ trợ Dark/Light theme đồng bộ; phím tắt định dạng SQL (`Ctrl+Shift+F` / format).
- Validation: Kiểm tra an toàn `tabId` khi switch hoặc dispose model; tạo model mới tự động nếu chưa có.
- Error handling: Graceful fallback sang `SqlCodeArea` (RichTextFX) nếu WebView không khả dụng (ví dụ trong môi trường headless/testing); hàng đợi `readyCallbacks` hoãn lệnh thực thi an toàn cho đến khi Monaco WebView hoàn tất nạp tài nguyên.
- State and side effects: Cập nhật dirty flag trên `SqlEditorTab`; đồng bộ vị trí con trỏ dòng/cột lên `StatusBarController`.
- Related features: Query Tabs (Feature 3), Query Execution (Feature 5), Results and Messages (Feature 6).

#### Java implementation
- UI & Bridge: `MonacoEditorHost.java` (kế thừa `StackPane`, chứa duy nhất 1 `WebView`), `BridgeCallback` (giao tiếp JavaFX $\leftrightarrow$ JavaScript qua `JSObject`), `editor.html`, `editor.css`, `editor-bridge.js`.
- Resources: Toàn bộ thư viện độc lập `src/main/resources/monaco/vs/` (~11MB) hoạt động hoàn toàn offline không cần internet.
- Controller integration: `EditorController.java` quản lý vòng đời host, hoán đổi `TextModel` khi tab thay đổi (`selectionModel`), hủy model khi tab đóng, hỗ trợ `getActiveText()` và `handleFormatSql()`.
- Theme sync: Định nghĩa sẵn theme `ide-dark` và `ide-light`, tự động cập nhật khi ứng dụng thay đổi theme.

#### Acceptance criteria
- [x] Existing main workflow works: Monaco nạp mượt mà, soạn thảo SQL, highlight từ khóa, chuyển đổi giữa nhiều tab không mất trạng thái con trỏ và undo/redo.
- [x] Important edge cases are covered: Thao tác trước khi WebView nạp xong được đưa vào hàng đợi `runWhenReady`; giải phóng `model.dispose()` khi đóng tab chống rò rỉ RAM; tắt web worker để chạy mượt trong JavaFX `file://` scheme.
- [x] Errors are handled and visible: Tự động fallback về `SqlCodeArea` trong môi trường headless unit test.
- [x] Existing data semantics are preserved: Giữ nguyên cấu trúc TextModel và format text của editor cũ.
- [x] UI follows the modern native desktop design system: Quy tắc Single WebView - Multi TextModel (Rule 6 của `AGENTS.md`) được tuân thủ nghiêm ngặt, đảm bảo hiệu năng cao và tiêu thụ bộ nhớ tối ưu.
- [x] No unrelated new feature was added: Giữ đúng phạm vi tích hợp trình soạn thảo Monaco cho SQL.

#### Verification
- Test cases: `MonacoEditorHostTest` (2 tests pass: `testMonacoEditorHostCreationAndModelManagement`, `testMonacoEditorHostListeners`), `EditorWorkspaceTest` (6 tests pass), `MainWindowFXMLTest` (1 test pass). Tổng bộ test suite: 76/76 tests pass 100%.
- Manual checks: Monaco khởi động và gắn `javaBridge` thành công, log ghi nhận `Attached javaBridge to Monaco WebView window` và `Monaco Editor is fully initialized and ready`.

#### Deviations and decisions
- Tuân thủ Rule 6 trong `AGENTS.md`: Chỉ tạo duy nhất 1 instance `WebView` cho toàn bộ ứng dụng, thay vì tạo mỗi tab 1 WebView (vốn gây tốn bộ nhớ và chậm lag).
- Cấu hình Monaco fallback main thread worker để tương thích hoàn toàn với cơ chế bảo mật local `file://` của JavaFX WebKit.
- Tối ưu hóa khởi động và triệt tiêu hiện tượng chớp đen (Visual Continuity & Zero-Flicker): Trì hoãn khởi tạo WebView sang `Platform.runLater()` giúp thời gian nạp FXML từ >5s giảm xuống còn ~0.8s; ban đầu hiển thị tức thì qua `SqlCodeArea` (0ms, có syntax highlight), đặt nền trong suốt `transparent` cho WebView và chuyển giao sang Monaco bằng `FadeTransition` (120ms) ngay khi `onEditorReady()` kích hoạt.

### Feature 5: Query Execution

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 5

#### Python reference
- Files: `lltool/database/executor.py`, `app/services/query_service.py`, `app/api/query_api.py`, `static/js/editor.js`, `static/js/results.js`, `templates/partials/results.html`
- Functions / classes: `QueryExecutor`, `execute_query`, `cancel_query`, `runQuery`, `handleExecute`, `getSelectedText`
- API routes: `/api/query/execute`, `/api/query/cancel`
- Database / persistence: `query_history.json`
- Reference screenshots: `docs/archive/_concept_/intellisense/intellisense_text_editor.png`

#### Existing behavior
- Main workflow: Thực thi câu lệnh SQL qua nút Run hoặc phím tắt `F5` / `Ctrl+Enter`. Nếu người dùng bôi đen đoạn mã SQL, chỉ thực thi đoạn được bôi đen (`selected text`); nếu không bôi đen thì thực thi toàn bộ script trong tab hiện tại. Hỗ trợ nút Cancel/Stop để hủy query đang chạy (`Statement.cancel()`). Hiển thị thời gian chạy, số hàng trả về hoặc số dòng bị ảnh hưởng (DML/DDL update count). Tự động điều hướng kết quả sang bảng Results hoặc tab Messages.
- Validation: Kiểm tra kết nối active trước khi chạy, kiểm tra câu lệnh SQL rỗng.
- Error handling: Bắt lỗi cú pháp SQL, lỗi timeout, lỗi mất kết nối và hiển thị chi tiết trong tab Messages mà không làm đơ hoặc crash UI.
- State and side effects: Trạng thái nút Run/Stop (Run đổi thành Running, Stop bật sáng); tự động mở bảng Results khi có kết quả; giữ nguyên vị trí panel khi ẩn/hiện.
- Related features: Connection Management (Feature 1), Query Tabs (Feature 3), Monaco Editor (Feature 4), Results & Messages (Feature 6).

#### Java implementation
- Service: `DesktopQueryExecutionService.java` chạy trên Java 21 Virtual Threads (`Thread.ofVirtual()`), thực hiện truy vấn bất đồng bộ không chặn JavaFX Application Thread, hỗ trợ hủy `Statement.cancel()`, đếm update count cho DML/DDL và mapping tự động `ResultSetMetaData` (tên cột, kiểu dữ liệu) sang danh sách `Map<String, Object>`.
- Editor & Shortcuts: `MonacoEditorHost.java` và `editor-bridge.js` tích hợp lệnh `getSelectedText()` và bind `F5` + `Ctrl+Enter` trực tiếp vào Monaco editor engine thông qua `window.javaBridge.onExecuteRequested()`.
- Layout & Results Panel Persistence:
  - Mặc định khi mở tab mới (`openNewTab` / `handleNewTab`), panel Results được ẩn đi (`resultsVisible = false`).
  - Khi chạy query (`handleExecute()`, `F5`, `Ctrl+Enter`) hoặc khi người dùng bấm `Ctrl+R` (`menuViewResults`), panel Results tự động hiển thị.
  - Lưu nhớ vị trí SplitPane divider (`savedResultsDividerPosition`) trước khi ẩn và khôi phục chính xác vị trí này khi hiển thị lại qua `applyResultsVisibility()`.
- Results & Messages Integration: `ResultsController.java` (`selectResultsTab()`, `selectMessagesTab()`, hiển thị bảng dữ liệu động và thông điệp thực thi).

#### Acceptance criteria
- [x] Existing main workflow works: Chạy query bằng Run, F5, Ctrl+Enter; chạy câu lệnh bôi đen hoặc toàn bộ nội dung; hủy query đang chạy với Stop.
- [x] Important edge cases are covered: Query lỗi không crash ứng dụng mà hiển thị lỗi chi tiết trong Messages tab; query rỗng bị bỏ qua; truy vấn không có kết nối chủ động báo lỗi rõ ràng.
- [x] Custom user requirement met: Tab mới mặc định ẩn Results panel; tự động mở Results panel khi chạy query hoặc nhấn `Ctrl+R`; ghi nhớ chính xác vị trí divider sau các lần ẩn/hiện.
- [x] Errors are handled and visible: Lỗi hiển thị trong tab Messages với icon cảnh báo và thời gian thực thi.
- [x] Existing data semantics are preserved: Hỗ trợ cả SELECT (trả về hàng dữ liệu) lẫn INSERT/UPDATE/DELETE/CREATE (trả về update count).
- [x] UI follows the modern native desktop design system: Tích hợp với toolbar, accelerators, progress bar mượt mà.
- [x] No unrelated new feature was added: Giữ đúng phạm vi thực thi truy vấn.

#### Verification
- Test cases: `QueryExecutionTest` (4 unit/integration tests bao gồm: `testSelectQueryExecutionWithMetadata`, `testDdlUpdateCountExecution`, `testSyntaxErrorExecutionHandledGracefully`, `testResultsPanelDefaultHiddenOnNewTabAndDividerPreserved`). Tổng test suite 80/80 tests PASS (100%).
- Manual checks: F5/Ctrl+Enter kích hoạt thực thi; bôi đen text chỉ chạy đoạn bôi đen; SplitPane divider nhớ đúng vị trí tỉ lệ.

#### Deviations and decisions
- Sử dụng Java 21 Virtual Threads (`Thread.ofVirtual().name("query-executor-...")`) để xử lý tác vụ I/O JDBC bất đồng bộ thay vì kiến trúc Twisted/AsyncIO cũ của Python, mang lại hiệu năng cao và độ trễ tối thiểu.
- Kết quả bảng được binding động vào JavaFX `TableView` với `PropertyValueFactory` / `ReadOnlyObjectWrapper` theo metadata động của JDBC.

### Feature 6: Results and Messages

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 6

#### Python reference
- Files: `static/js/grid-result.js` (3,760 lines), `static/js/structured-data-viewer.js`, `static/js/query.js`, `app/desktop/exports.py`, `templates/partials/results.html`, `.SKILL/result_sql.md`
- Functions / classes: `GridResult`, `showContextMenu`, `handleAction`, `copyAllHeaders`, `copySelectedHeaders`, `copyCurrentCell`, `copySqlValuesList`, `scriptAsInsert`, `copySelectionAsXmlSpreadsheet`, `showGridAggregates`, `visualizeAs`, `findDataInGrid`, `findColumnInGrid`, `grid_open_in_excel`, `grid_save_results_as`
- API routes: Client runtime DOM/AG-Grid manipulation; `/api/grid/open-excel`, `/api/grid/save-results`
- Database / persistence: Tạm thời theo phiên truy vấn; file tạm tại `%TEMP%/SqlGridExports/`
- Reference screenshots: `docs/archive/_concept_/Result_ContextMenu.png`, `docs/archive/_concept_/intellisense/intellisense_text_editor.png`

#### Existing behavior
- Main workflow: Hiển thị kết quả truy vấn SELECT dưới dạng bảng dữ liệu động với cột chỉ số `#` (Row index). Phân biệt trực quan giá trị `NULL` (chữ in nghiêng, màu nhạt) với chuỗi rỗng `""` và khoảng trắng. Mô hình chọn theo ô (Cell-Based Selection Model) cho phép chọn ô đơn, chọn dải ô (Range BoundingBox), hoặc chọn toàn bộ bảng (`Ctrl+A`). Hệ thống Context Menu phong phú với 15+ chức năng (Freeze cột, Copy All/Selected Headers, Copy Cell 1:1, Copy as SQL Values List, Structured Data Quick View JSON/XML, Find Data `Ctrl+F`, Find Column, Show Aggregates Count/Sum/Avg/Min/Max, Script as INSERT với `#tabletmp`, XML Spreadsheet cho Excel, Visualize Text/Hex/JSON/XML, Open in Excel chế độ Read-Only, Save Results As CSV/TSV/JSON/XLSX, Print). Tab Messages ghi nhận chi tiết số dòng bị ảnh hưởng, thời gian chạy, mã lỗi SQLState và hỗ trợ nút "Copy All Messages".
- Validation: Phân biệt tuyệt đối giữa `NULL` (không nháy), chuỗi rỗng `''`, và khoảng trắng `'   '` (cấm `.trim()` tự động - tuân thủ TC02, TC03, TC04 của `.SKILL/result_sql.md`). Escape chuẩn RFC 4180 cho TSV/CSV. CSV bắt buộc có UTF-8 BOM (`\uFEFF`) để Excel trên Windows hiển thị đúng tiếng Việt không bị lỗi font.
- Error handling: Lỗi cú pháp SQL, timeout, mất kết nối được định tuyến tự động sang tab Messages với mã lỗi chi tiết mà không làm đơ bảng dữ liệu.
- State and side effects: Tự động chuyển tab sang Results khi có dữ liệu SELECT, sang Messages khi chạy lệnh DDL/DML. Cập nhật nhãn thống kê dòng và thời gian thực thi.
- Related features: Query Execution (Feature 5), Monaco Editor (Feature 4), Execution Plan (Feature 7).

#### Java implementation
- UI & TableView: `results.fxml`, `ResultsController.java`, `components.css`. Sử dụng JavaFX `TableView` với `VirtualFlow` hiệu năng cao. Chiều cao header cột nhỏ gọn (~26px), kẻ đường viền ô rõ nét cả chiều ngang và dọc (`-fx-border-width: 0 1 1 0`). Định dạng màu sắc ô: `.null-cell-value` (italic muted), `.boolean-cell-false` (cam đất `#d97706`), `.boolean-cell-true` (xanh lá `#16a34a`), số (căn phải, màu xanh `#2563eb`).
- Selection & Column Filter: Click cột `#` chọn toàn bộ hàng, click góc `#` chọn toàn bộ bảng, click tiêu đề cột chọn cả cột, kéo chuột/Shift+click chọn dải ô chữ nhật. Bộ lọc trên từng cột (`col-header-filter-btn`) có popup tìm kiếm, nút Clear, Sort A-Z/Z-A, và viền cam `.column-header-filtered` đánh dấu cột đang lọc (tham khảo ảnh người dùng cung cấp).
- Multi-grid Support: Hỗ trợ hiển thị nhiều kết quả truy vấn SELECT xếp chồng qua `SplitPane` dọc (`Bảng #1`, `Bảng #2`) với thanh công cụ điều khiển riêng biệt.
- Tách biệt tab Messages và Timeline: Tab Messages hiển thị console text cho `PRINT`, `RAISERROR`, đếm dòng; Tab Timeline hiển thị bảng lịch sử thực thi truy vấn.
- Footer Execution Stats Bar: Thanh thống kê ở chân trang hiển thị số dòng (e.g. `2,506 dòng`), badge vàng đồng hồ thời gian (e.g. `1649 ms`), dung lượng dữ liệu (`1.20 MB`), thông tin kết nối và chỉ số trend.
- Context Menu: Xây dựng menu động với đầy đủ 4 nhóm chức năng tương thích 100% với Python:
  - Cố định cột [colName] (Freeze) / Bỏ cố định cột.
  - Copy all Headers / Copy selected Headers / Copy current cell 1:1 / Copy as SQL values List.
  - Xem JSON/XML (Quick View) và Mở JSON/XML trong tab mới (tự động nhận diện chuỗi JSON/XML candidate).
  - Find data in Results Grid (`Ctrl+F`) / Find column in Results Grid / Show grid aggregates (dialog thống kê Count, Num Count, Sum, Avg, Min, Max) / Script grid data / Copy selection as Xml Spreadsheet.
  - Visualize as (Submenu: Text, Hex dump, JSON, XML).
  - Copy (`Ctrl+C`, chuẩn TSV) / Copy with Headers (`Ctrl+Shift+C`) / Select All (`Ctrl+A`) / Script as INSERT / Open in Excel / Save Results As... (`Ctrl+Shift+S`) / Print... (`Ctrl+P`).
- Core Engine & Utilities:
  - `GridDataExportUtils.java`: Xử lý toàn bộ thuật toán trích xuất TSV (RFC 4180), CSV, SQL Values List, sinh script DDL `CREATE TABLE #tabletmp` + batch `INSERT INTO`, XML Spreadsheet 2003, JSON, Markdown Table, Aggregates, Hex Dump, và Structured Data Candidate Detector.
  - `DesktopGridExportService.java`: Quản lý việc tạo file tạm với UTF-8 BOM, thiết lập thuộc tính `ReadOnly` cho file tạm trước khi mở ứng dụng Excel qua `Desktop.getDesktop().open()`, và hộp thoại lưu file hệ thống `FileChooser`.
- Keyboard Shortcuts: Gắn `addEventFilter(KeyEvent.KEY_PRESSED)` trực tiếp vào TableView hỗ trợ `Ctrl+C`, `Ctrl+Shift+C`, `Ctrl+A`, `Ctrl+Shift+S`, `Ctrl+F`.

#### Acceptance criteria
- [x] Existing main workflow works: Hiển thị bảng kết quả SELECT, cột `#`, phân trang ảo VirtualFlow, cell selection model mượt mà.
- [x] Important edge cases are covered: Giá trị `NULL` hiển thị chữ italic xám nhạt; chuỗi rỗng `""` xuất ra `''`; khoảng trắng `"   "` được giữ nguyên vẹn không bị trim; ký tự đặc biệt được escape theo chuẩn RFC 4180.
- [x] Context menu is comprehensive: Toàn bộ 15+ chức năng từ file `grid-result.js` và tài liệu kỹ thuật `.SKILL/result_sql.md` được triển khai đầy đủ và hoạt động hoàn hảo.
- [x] Excel integration verified: "Open in Excel" tạo file tạm Read-Only; "Save Results As" hỗ trợ CSV (UTF-8 BOM), TSV, JSON, XML Spreadsheet.
- [x] Tab Messages and Statistics: Hiển thị thông báo, bắt lỗi SQLState, sao chép toàn bộ log ("Copy All Messages").
- [x] UI follows the modern native desktop design system: Đồng bộ theme Dark/Light AtlantaFX, Ikonli icons, hộp thoại Dialog native đẹp mắt.
- [x] No unrelated new feature was added: Tập trung đúng phạm vi hiển thị kết quả, thông điệp và context menu.

#### Verification
- Test cases: `ResultsGridTest` (7 unit tests bao gồm: `testTsvFormatting`, `testSqlLiteralsAndScriptAsInsert`, `testSqlValuesListFormatting`, `testAggregatesCalculation`, `testXmlSpreadsheetFormatting`, `testHexDumpAndStructuredData`, `testResultsControllerIntegration`).
- Regression suite: Toàn bộ 87 tests trong hệ thống (bao gồm `ResultsPanelTest`, `QueryExecutionTest`, `MainWindowFXMLTest`) chạy tự động đều đạt 100% PASS (`mvn test` BUILD SUCCESS).
- Manual checks: Bôi đen vùng cell $\rightarrow$ chuột phải hiển thị đầy đủ context menu; copy dán vào Excel giữ nguyên định dạng; Script INSERT tạo bảng tạm `#tabletmp`.

#### Deviations and decisions
- Thay vì dùng AG Grid / HTML Table trong WebKit (vi phạm Rule 6 Single WebView của `AGENTS.md`), sử dụng native JavaFX `TableView` kết hợp với styling AtlantaFX giúp tốc độ mở bảng dữ liệu tức thì 0ms và mức tiêu thụ RAM cực thấp.
- Sử dụng cơ chế file tạm Read-Only kết hợp lệnh native OS `Desktop.getDesktop().open()` cho chức năng "Open in Excel", giúp Excel bắt buộc người dùng chọn Save As khi muốn lưu chỉnh sửa.

### Feature 7: Execution Plan

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 7

#### Python reference
- Files: `app/services/query_plan_service.py` (668 dòng), `app/api/query_api.py`, `static/js/query.js`, `templates/partials/results.html`, `templates/index.html`, `.SKILL/query_plan_visualization.md`, `tests/test_phase3_bug2_explain_query.py`
- Functions / classes: `PlanOperatorNode`, `PhysicalExecutionPlan`, `QueryPlanService.get_execution_plan`, `parse_sqlserver_xml_plan`, `parse_postgres_json_plan`, `parse_sqlite_plan`, `requestPlan`, `explain_query`
- API routes: `/api/query/explain`, `/api/metadata/query-plan`
- Database commands:
  - PostgreSQL: `EXPLAIN (FORMAT JSON, COSTS, VERBOSE)` (Estimated), `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` (Actual)
  - SQL Server: `SET SHOWPLAN_XML ON / OFF` (Estimated), `SET STATISTICS XML ON / OFF` (Actual)
  - SQLite: `EXPLAIN QUERY PLAN`
- Reference screenshots: `docs/templates/index.html` (`#ide-btn-plan`, `#ide-rtab-plan`, `#ide-results-plan-view`)

#### Existing behavior
- Main workflow: Người dùng yêu cầu xem kế hoạch thực thi câu lệnh SQL từ thanh công cụ (nút `Plan`) hoặc menu (`Ctrl+L` cho Estimated Plan, `Ctrl+M` cho Actual Plan). Hệ thống gửi truy vấn tương ứng tới database engine (an toàn không chạy query đối với Estimated; thực thi đo runtime đối với Actual), chuẩn hóa cây toán tử (Operator Tree), phân tích chi phí phần trăm (`% Cost`) và mức độ nghiêm trọng (High $\ge 30\%$, Medium $\ge 10\%$, Low $< 10\%$), và hiển thị trực quan dưới dạng cây phân cấp kèm bảng thuộc tính chi tiết (Operator Inspector).
- Validation: Kiểm tra câu lệnh SQL không rỗng; hiển thị cảnh báo khi lấy Actual Plan với câu lệnh DML.
- Error handling: Bảo đảm các cờ `SET SHOWPLAN_XML OFF` và `SET STATISTICS XML OFF` của SQL Server luôn được tắt trong khối `finally` ngay cả khi xảy ra SQLException (tuân thủ Phase 3 Bug 2). Nếu không có kết nối DB hoặc ở chế độ offline, tự động chuyển sang bộ sinh kế hoạch mô phỏng thông minh (Simulated Fallback) dựa trên cấu trúc SQL.
- State and side effects: Tự động mở tab `Execution Plan` trong Results Panel, hiển thị badge Dialect, Type, Total Cost, Operator Count, và thời gian thực thi (nếu là Actual).
- Related features: Query Editor (Feature 4), Query Execution (Feature 5), Results and Messages (Feature 6).

#### Java implementation
- Model:
  - `PlanOperatorNode.java`: Quản lý các thuộc tính toán tử (`operatorType`, `physicalOp`, `logicalOp`, `relationName`, `schema`, `database`, `estimatedRows`, `actualRows`, `subtreeCost`, `operatorCost`, `costPercent`, `costSeverity`, `actualTimeMs`, `actualLoops`, `warnings`, `outputColumns`, `children`).
  - `PhysicalExecutionPlan.java`: Quản lý dialect, planType, totalCost, rootNode, danh sách allNodes, rawPlanText, thời gian executionDurationMs, và hàm sinh cây văn bản `toTextTree()`.
- Services:
  - `DesktopExecutionPlanService.java`: Chạy bất đồng bộ trên Java 21 Virtual Threads (`Thread.ofVirtual()`), tự động nhận diện dialect từ phiên kết nối active (`ConnectionSessionManager`), thực thi và phân tích cú pháp cho PostgreSQL (Jackson JSON), SQL Server (XML DOM Parser) và SQLite, kèm bộ sinh kế hoạch giả lập thông minh khi chạy offline hoặc test.
- UI & FXML:
  - `results.fxml`: Bổ sung thanh công cụ Plan Toolbar (`Est Plan`, `Act Plan`, `Raw Text` toggle, `Copy Plan`), các badge tóm tắt, `SplitPane` trực quan gồm `TreeView<PlanOperatorNode>` bên trái (với custom TreeCell hiển thị icon toán tử, tên, bảng và badge % cost có màu cảnh báo) và `Operator Inspector` bên phải (`TableView` hiển thị 14+ thuộc tính chi tiết của toán tử đang chọn).
  - `main-window.fxml` & `MainController.java`: Tích hợp nút `Plan` trên toolbar chính, menu `Tools -> Display Estimated Execution Plan (Ctrl+L)` và `Tools -> Include Actual Execution Plan (Ctrl+M)`.
  - `components.css`: Định dạng badge chi phí (`.plan-cost-badge-high`, `.plan-cost-badge-medium`, `.plan-cost-badge-low`), tree view, và textarea raw text font monospace.

#### Acceptance criteria
- [x] Existing main workflow works: Lấy Estimated Plan (`Ctrl+L`), Actual Plan (`Ctrl+M`), hiển thị cây toán tử phân cấp, và xem chi tiết toán tử trong Inspector.
- [x] Provider-specific behavior preserved: Hỗ trợ chuẩn xác PostgreSQL EXPLAIN JSON, SQL Server ShowPlanXML, SQLite EXPLAIN QUERY PLAN.
- [x] Safe cleanup guaranteed: `SET SHOWPLAN_XML OFF` và `SET STATISTICS XML OFF` luôn được tắt trong khối `finally`.
- [x] Fallback for offline/testing: Sinh kế hoạch mô phỏng chuẩn xác dựa trên SQL syntax khi không có kết nối DB.
- [x] View toggle and copy: Hỗ trợ chuyển đổi nhanh giữa Visual Tree và Raw Text, hỗ trợ nút sao chép kế hoạch vào Clipboard.
- [x] UI follows the modern native desktop design system: Áp dụng AtlantaFX theme, Ikonli icons, SplitPane co giãn mượt mà.
- [x] No unrelated new feature was added: Giữ đúng phạm vi phân tích và hiển thị kế hoạch thực thi.

#### Verification
- Test cases: `ExecutionPlanTest.java` (5 unit tests bao gồm: `testPostgresJsonPlanParsing`, `testSqlServerXmlPlanParsing`, `testSqlitePlanParsing`, `testSimulatedPlanGeneration`, `testResultsControllerExecutionPlanUI`).
- Regression suite: Toàn bộ 92 tests trong hệ thống chạy tự động đều đạt 100% PASS (`mvn test` BUILD SUCCESS).
- Manual checks: Bấm nút `Est Plan` / `Ctrl+L` hoặc `Act Plan` / `Ctrl+M` hiển thị ngay cây toán tử với phân màu chi phí; bấm từng toán tử cập nhật tức thì thuộc tính trong Inspector.

#### Deviations and decisions
- Sử dụng native JavaFX `TreeView` và `SplitPane` kết hợp `TableView` thuộc tính thay vì nhúng WebKit D3 graph vào Results panel, giúp tốc độ phản hồi tức thì 0ms, không tốn RAM và hoàn toàn tuân thủ Rule 6 (Single WebView for Monaco only) của `AGENTS.md`.

### Feature 8: Settings and Themes

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 8

#### Python reference
- Files: `.SKILL/setting_form.md`, `app/services/settings_store.py`, `settings/settings.json`, `app/desktop/themes.py`, `.SKILL/s1_themes.md`, `com.lltool.infrastructure.configuration.service.ConfigurationService`.
- Functions / classes: `load_settings()`, `save_settings()`, `apply_theme()`, `AppSettings`, `SettingDialog`.
- API routes: Client desktop settings dialog & local persistence.
- Database / persistence: `~/.lltool/config/settings.json`.

#### Existing behavior
- Main workflow: Người dùng mở bảng điều khiển Settings từ nút biểu tượng bánh răng (Settings gear icon) trên thanh tác vụ/tiêu đề hoặc qua menu `File -> Preferences: Settings...` (`Ctrl+,`). Giao diện tích hợp cả 2 chế độ trực quan:
  1. Tab "Visual Form": Trình đơn Master-Detail gồm 10 danh mục chuẩn xác theo cấu hình Python: General / Appearance, SQL Editor, Data Grid, Messages Tab, SQL & Protection, Session & AutoSave, Typography & Fonts, Addons & Tools, Updates & Logging, Syntax & Advanced.
  2. Tab "Raw JSON": Trình soạn thảo JSON trực tiếp với công cụ phân tích cú pháp thời gian thực (Live Syntax Validation Badge), nút Sao chép JSON (Copy to Clipboard), và đồng bộ 2 chiều tự động với Visual Form.
- Hot-Apply Zero-Reload: Khi nhấn "Lưu & Áp dụng" (`Ctrl+S` hoặc nút bấm), cấu hình được lưu vào `~/.lltool/config/settings.json`, đồng bộ với `ConfigurationService`, và Hot-Apply tức thì:
  - Monaco Editor: fontFamily, fontSize, lineHeight, letterSpacing, tabSize, insertSpaces, wordWrap, minimap, lineNumbers.
  - Data Grid: fontFamily, fontSize, ẩn/hiện cột số thứ tự dòng `#`, hiển thị giá trị NULL (`NULL` nghiêng xám mờ).
  - Messages Tab: fontFamily, fontSize cho nhật ký timeline và console tin nhắn.
  - Typography UI: Áp dụng phông chữ và kích thước toàn hệ thống giao diện.
  - Thanh công cụ (Toolbar): Đồng bộ Limit ComboBox và hiển thị/ẩn nút Bravo Tool theo cấu hình `addons.bravo_tool.enabled` (không ẩn thanh công cụ theo yêu cầu người dùng).
- SQL Protection Guard (Fatal Actions Guard): Cảnh báo khi người dùng vô tình thực thi lệnh `DELETE` hoặc `UPDATE` không có mệnh đề `WHERE`, hoặc lệnh `TRUNCATE TABLE`.
  - **Miễn trừ an toàn đặc biệt (Exemption)**: Tự động nhận diện và hoàn toàn bỏ qua cảnh báo đối với bảng tạm cục bộ/toàn cục (`#temp`, `##temp`, `[#temp]`) và biến bảng (`@tableVar`, `[@tableVar]`), nâng cao tối đa hiệu suất thao tác script tạm thời.

#### Java implementation
- Models & Services:
  - `IdeSettings.java`: Model cấu trúc dữ liệu JSON đầy đủ với 10 POJOs lồng nhau (`EditorSettings`, `AppearanceSettings`, `GridSettings`, `MessagesSettings`, `SqlSettings`, `AddonsSettings`, `SessionSettings`, `UpdatesSettings`, `SyntaxSettings`, `HistorySettings`, `LoggingSettings`, `TypographySettings`, `QuerySettings`), tương thích 100% với schema file cấu hình Python.
  - `SettingsService.java`: Singleton service quản lý đọc/ghi file `~/.lltool/config/settings.json` thông qua Jackson ObjectMapper, đồng bộ với `ConfigurationService` của backend hạ tầng, và phát thông báo Hot-Apply tới các listeners qua `Platform.runLater()`.
  - `SqlProtectionService.java`: Bộ phân tích ngữ pháp SQL (Lexer/Tokenizer) bảo vệ thao tác nguy hiểm, phân tách comment/string literals, kiểm tra WHERE clause, và miễn trừ triệt để bảng tạm/biến bảng.
- UI & FXML:
  - `settings-dialog.fxml`: Thiết kế Master-Detail 2 cột hiện đại với 10 nút danh mục, thanh tìm kiếm thông minh, tab Visual Form và tab Raw JSON với status badge.
  - `SettingsDialogController.java`: Điều khiển toàn bộ logic form, ràng buộc dữ liệu 2 chiều, logic lọc tìm kiếm, kiểm tra cú pháp JSON, copy to clipboard, và xử lý đóng/lưu.
  - `SettingsDialog.java`: Lớp khởi chạy hộp thoại modal dạng undecorated stage với shadow drop hiện đại.
  - `settings.css`: Bộ stylesheet chuẩn hệ thống thiết kế AtlantaFX cho Settings Center.
- TitleBar & Hot-Apply Integration:
  - `main-window.fxml`: Tích hợp `settingsButton`, `limitComboBox`, `btnBravoTool`.
  - `MainController.java`: Mở hộp thoại Settings, kích hoạt Hot-Apply cho giao diện, đồng bộ thanh công cụ, và kiểm tra SQL Protection Guard trước khi chạy truy vấn.
  - `ResultsController.java`: Đồng bộ Hot-Apply cho bảng dữ liệu Data Grid và khung tin nhắn Messages.
  - `MonacoEditorHost.java` & `editor-bridge.js`: Cập nhật trực tiếp runtime options của Monaco (fontFamily, fontSize, lineHeight, letterSpacing, tabSize, insertSpaces, wordWrap, minimap, lineNumbers) mà không tải lại WebView.

#### Acceptance criteria
- [x] Existing main workflow works: Mở hộp thoại Settings từ titlebar gear icon hoặc `Ctrl+,`, tinh chỉnh cấu hình, chuyển tab Visual <-> JSON, lưu và áp dụng thành công.
- [x] Hot-Apply functionality: Thay đổi theme hoặc tùy chọn editor (font size, word wrap, line numbers), grid (# column, font, NULL format), messages font, toolbar có hiệu lực ngay lập tức tại runtime.
- [x] SQL Protection Guard: Chặn các lệnh DELETE/UPDATE thiếu WHERE và TRUNCATE trên bảng thật, đồng thời miễn trừ hoàn toàn cho bảng tạm (`#`, `##`) và biến bảng (`@`).
- [x] Master-Detail & Search & Raw JSON: Cột trái chuyển 10 danh mục mượt mà, ô tìm kiếm lọc chính xác, tab JSON kiểm tra lỗi cú pháp trực quan và cho phép sao chép JSON.
- [x] Persistence & Compatibility: Lưu an toàn vào `~/.lltool/config/settings.json` với schema chuẩn 100% khớp file JSON của dự án Python.
- [x] UI follows the modern native desktop design system: Tuân thủ quy cách thiết kế AtlantaFX themes, Ikonli Feather icons.
- [x] No unrelated new feature was added: Giữ nguyên thanh công cụ hiển thị (không làm ẩn theo yêu cầu người dùng).

#### Verification
- Test cases: `SettingsAndThemesTest.java` (7 unit tests bao gồm: roundtrip JSON schema đầy đủ, sao chép đối tượng, tương thích ngược, FXML, tìm kiếm, lưu trữ, chuyển đổi tab).
- Test cases: `SqlProtectionServiceTest.java` (8 unit tests bao gồm: phát hiện DELETE/UPDATE không có WHERE, TRUNCATE trên bảng thường, câu lệnh an toàn có WHERE, miễn trừ bảng tạm `#temp` và `##temp`, miễn trừ biến bảng `@tableVar`, bỏ qua chú thích SQL).
- Full test suite: 107/107 unit tests đạt 100% PASS (`mvn test` BUILD SUCCESS).

#### Deviations and decisions
- Bố trí nút bánh răng cài đặt trên thanh taskbar/titlebar ngay cạnh nút chuyển Dark/Light theo đúng đặc tả của `.SKILL/setting_form.md`.
- Sử dụng JavaFX native controls cho toàn bộ hộp thoại cài đặt để đảm bảo tốc độ phản hồi 0ms và tính thẩm mỹ cao nhất, duy trì nguyên tắc Single WebView của Rule 6.

### Feature 9: Snippets and Saved SQL

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 9

#### Python reference
- Files: `static/js/snippets-manager.js`, `data/snippets.json` (78 snippets), `static/css/snippets-manager.css`.
- Functions / classes: `SnippetsManager`, Red Gate `$placeholder$` expansion, prefix matching.
- Database / persistence: `data/snippets.json`, `~/.lltool/data/snippets.json`.

#### Existing behavior
- Main workflow: Người dùng gõ tiền tố snippet (ví dụ `ssf`, `sfb`, `ii`, `ct`) trong trình soạn thảo Monaco, hệ thống hiển thị gợi ý IntelliSense và tự động mở rộng mẫu câu lệnh SQL tương ứng khi nhấn `Tab` hoặc `Enter`.
- Snippets Manager Dialog (`780×520px`): Cho phép tìm kiếm snippet, lọc theo hệ cơ sở dữ liệu (`ALL`, `MSSQL`, `POSTGRESQL`), tạo mới, chỉnh sửa, ghim lên đầu (Pin/Star), xóa, và khôi phục về 78 mẫu mặc định.
- Yêu cầu đặc thù cải tiến (User enhancement): Khi gõ tắt `ssf` (tương đương `SELECT * FROM $table$`), con trỏ mặc định tự động bôi đen (focus/highlight) biến `table` để người dùng có thể gõ đè tên bảng ngay lập tức mà không cần xóa hay di chuyển chuột.

#### Java implementation
- Models & Services:
  - `SqlSnippet.java`: Model dữ liệu snippet đầy đủ với chuyển đổi ngữ pháp Red Gate `$var$` sang định dạng Monaco Snippet `${1:var}` và gán chỉ số tabstop giống nhau cho các biến trùng tên.
  - `SnippetService.java`: Singleton service quản lý đọc/ghi nguyên tử vào `~/.lltool/data/snippets.json`, nạp 78 mẫu gốc từ resources, xuất định dạng Monaco JSON, và phát sự kiện thay đổi.
- Monaco Bridge & IntelliSense:
  - `editor-bridge.js` & `host.js`: Hàm `convertRedGateToMonacoSnippet(body)` và `tryExpandSnippet` xử lý tự động chèn tabstop của Monaco Editor (`snippetController2.insert(...)`) giúp tự động bôi đen biến khai báo đầu tiên ngay khi nhấn `ssf`.
  - `MonacoEditorHost.java`: Tự động đồng bộ toàn bộ danh sách snippets từ `SnippetService` sang runtime Monaco Editor qua IPC bridge `loadSnippets()`.
- UI & Dialog:
  - `SnippetsManagerDialog.java`: Hộp thoại thiết kế chuẩn hệ thống `DialogChrome` (780×520px), hỗ trợ tìm kiếm qua `SearchField`, chip lọc hệ CSDL, danh sách ListView với huy hiệu và gắn sao, cùng hộp thoại xác nhận `ConfirmDialog`.
  - `snippets-manager.css`: Áp dụng 100% biến thiết kế AtlantaFX, tuân thủ nghiêm ngặt R-UI-01 (`rounded` vs `flat`) và `03-design-tokens.md`.
- Main Window Integration:
  - `main-window.fxml`: Nút `fth-code` trên Toolbar, menu `Tools -> SQL Snippets Manager...` (`Ctrl+Alt+S`), menu `File -> Open SQL Script...` (`Ctrl+O`), `File -> Save` (`Ctrl+S`), `File -> Save As...` (`Ctrl+Shift+S`).
  - `MainController.java`: Điều khiển mở hộp thoại Snippets Manager, mở file SQL và lưu file an toàn.

#### Acceptance criteria
- [x] Existing main workflow works: Gõ `ssf` hoặc chọn từ IntelliSense chèn ngay mẫu SQL và tự động bôi đen biến `$table$` để người dùng gõ ngay.
- [x] Snippets Manager UI: Quản lý CRUD, tìm kiếm, ghim lên đầu, lọc database target, lưu trữ nguyên tử.
- [x] Design token compliance: Không dùng hardcoded hex color, tuân thủ R-UI-01.

#### Verification
- Test cases: `SnippetServiceTest.java` (8 unit tests bao gồm: chuyển đổi $var$ đơn/đa biến/biến trùng, nạp mặc định SSF, CRUD, ghim, xuất Monaco payload - 100% PASS).

---

### Feature 10: Query History

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 2 - Core Feature 10

#### Python reference
- Files: `app/services/history/storage.py`, `app/services/history/service.py`, `app/api/history_api.py`, `templates/query_history_window.html`, `static/js/query-history.js`, `static/css/query-history.css`.
- Functions / classes: `HistoryStore`, `HistoryService`, `HistoryApiMixin`.
- Database / persistence: `data/history.db` (SQLite WAL mode, zlib compression, SHA-256 deduplication, RunCount coalescing).

#### Existing behavior
- Main workflow: Mỗi khi người dùng bấm F5 hoặc lưu file SQL, câu lệnh được ghi nhận tự động và bất đồng bộ vào cơ sở dữ liệu `history.db` mà không gây đơ giao diện (<0.5ms). Cập nhật thời gian thực thi, mã lỗi hoặc trạng thái hoàn thành khi truy vấn kết thúc.
- Deduplication & Coalescing: Nội dung SQL được băm SHA-256 và lưu duy nhất 1 lần trong bảng `Content` dưới dạng nén zlib (~70-90% tiết kiệm dung lượng). Các lần chạy F5 liên tiếp giống hệt nhau trên cùng server/database được tự động gộp và tăng `RunCount`.
- Query History Window (`1050×680px`):
  - Cột trái: Cây tài liệu gần đây (Recent Queries) với số lần chạy, gắn sao yêu thích; danh sách các lần thực thi của tài liệu đó với bộ lọc nhanh (Tất cả, Thành công, Lỗi, Selection, Yêu thích).
  - Cột phải: Xem trước mã nguồn bằng Monaco Editor (chỉ đọc, tô màu cú pháp, đồng bộ theme), thanh gắn nhãn (Tag) và ghi chú (Note), banner cảnh báo lỗi chi tiết, thanh thông tin máy chủ/database/thời gian/dung lượng, cùng các thao tác: "Sao chép SQL", "Mở vào Tab mới" (`Enter`).
  - Thanh công cụ & Ngăn bộ lọc (Filter Drawer): Tìm kiếm toàn văn (tìm cả trong nội dung SQL đã giải nén via `decompress_sql`), lọc theo Server, Database, Khoảng thời gian, Nhãn; Xuất / Nhập lịch sử dạng JSON.
  - Chính sách dọn dẹp tự động (Retention Policy): Giữ tối đa 200 lần chạy/tài liệu, thời hạn tối đa 90 ngày, bảo vệ tuyệt đối các tài liệu gắn sao và lần chạy yêu thích.

#### Java implementation
- Models & Storage:
  - `HistoryDocument.java`, `HistoryEntry.java`, `HistoryTag.java`: Models tương thích 100% với schema SQLite của Python.
  - `SqliteHistoryStore.java`: Tầng truy xuất SQLite cục bộ với WAL mode, `PRAGMA auto_vacuum = INCREMENTAL`, hàm SQLite nhúng `decompress_sql` cho tìm kiếm nội dung, nén `Deflater`/`Inflater` zlib chuẩn RFC 1950, dedup SHA-256, gộp RunCount, và dọn dẹp retention.
  - **Tách biệt cơ sở dữ liệu hoàn toàn (Zero Shared Database)**: Java lưu trữ độc lập tại file `history_java.db` bên trong thư mục dữ liệu riêng của Java (`%APPDATA%\LLTool-Desktop\data\history_java.db`), hoàn toàn không chia sẻ, không khóa và không can thiệp vào file `history.db` của Python (`%APPDATA%\LLTool\history.db`).
- Services & Background Worker:
  - `HistoryLoggingService.java`: Singleton service điều phối luồng background worker ngầm, lên lịch dọn dẹp sau 30s khởi động và định kỳ mỗi 6 giờ, tích hợp cấu hình từ `SettingsService`, và phát thông báo cập nhật giao diện thời gian thực qua `Platform.runLater()`.
- UI & Dialog:
  - `QueryHistoryDialog.java`: Hộp thoại modal chuẩn `DialogChrome` (1050×680px, resizable), bố cục `SplitPane` 2 cột, danh sách `ListView` với tùy biến Cell hiển thị biểu tượng trạng thái, thời gian chạy, huy hiệu `xN`, nhãn tag; xem trước bằng `EmbeddedMonaco` (Rule 6 compliant).
  - `query-history.css`: Định dạng toàn diện tuân thủ quy tắc R-UI-01 và hệ thống thẻ màu ngữ nghĩa AtlantaFX (không dùng mã màu hex cố định).
- Main Window Integration:
  - `main-window.fxml`: Nút `fth-clock` trên Toolbar, menu `Tools -> Query History...` (`Ctrl+Alt+H`), menu `View -> Query History...`, và menu mở rộng overflow.
  - `MainController.java`: Tích hợp hook ghi nhận thực thi trong `startRun()`, cập nhật kết quả trong `onSuccess()`, `onError()`, `onCancelled()`, và ghi snapshot khi lưu file trong `saveTabFile()`.

#### Acceptance criteria
- [x] Existing main workflow works: Ghi nhận lịch sử F5 không làm đơ giao diện; mở hộp thoại Query History tìm kiếm, lọc, xem trước SQL và mở vào tab mới mượt mà.
- [x] Storage & Retention parity: SQLite WAL, nén zlib, băm SHA-256, gộp RunCount, đa nhãn tag, yêu thích, dọn dẹp entry quá hạn mà không xóa mục yêu thích/gắn sao.
- [x] UI follows desktop design system: Áp dụng `DialogChrome`, `SearchField`, `EmbeddedMonaco` (tuân thủ Rule 6 Single WebView), không có mã hex hardcode trong CSS/Java.

#### Verification
- Test cases: `SqliteHistoryStoreTest.java` (8 unit tests bao gồm: dedup, bỏ qua query rỗng/comment, cập nhật kết quả, gộp tài liệu khi lưu file, yêu thích, nhãn tag, xóa entry, dọn dẹp retention, xuất/nhập JSON - 100% PASS).
- Test cases: `HistoryLoggingServiceTest.java` (3 unit tests bao gồm: luồng ghi log bất đồng bộ, snapshot khi lưu file, quản lý ghi chú và nhãn tag - 100% PASS).
- Test cases: `MainWindowFXMLTest.java` (1 test: tải FXML tích hợp menu/toolbar Query History - 100% PASS).
- Full Phase 2 test suite: 20/20 unit tests đạt 100% PASS (`mvn test` BUILD SUCCESS).

---

### Feature 12 (Phase 3): Object Content Search

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 3 - Slice 3

#### Python reference
- Files: `app/services/object_search_service.py`, `static/js/object-search/*.js`, `static/css/object-search.css`
- Core classes & functions: `ObjectSearchService` (`search`, `get_ddl`, `_compile_pattern`, `_search_sqlserver_raw`, `_search_postgres_raw`), `DdlViewer`, `MatchPanel`.

#### Java implementation
- Core domain: `SearchMatchLocation.java`, `ObjectSearchQuery.java`, `ObjectSearchHit.java`.
- Core application: `ObjectSearchService.java` (catalog DDL query generator for SQL Server & PostgreSQL, regex / whole-word pattern compilation, line/column coordinate extraction, sorting by VIEW -> PROCEDURE -> FUNCTION -> TRIGGER).
- Monaco Bridge: `editor-bridge.js`, `editor.css`, `MonacoEditorHost.java`, `EmbeddedMonaco.java` (`setSearchDecorations`, `setActiveSearchMatch`, `clearSearchDecorations`).
- Desktop UI: `ObjectContentSearchTab.java` (Master-Detail layout, search input, type filter chips, database/schema selection, match stepper navigation, quick filter in RAM, DDL viewer, open in SQL tab, copy DDL).
- Integration: `EditorController.openObjectContentSearchTab(...)`, `MainController.openObjectContentSearch(...)`, Tools menu (`Ctrl+Shift+F`), Database/Schema context menus in `DatabaseTreeCell.java`.
- CSS Tokens: `object-content-search.css` adheres to `-ide-*` and `-color-*` tokens and R-UI-01 shapes.

#### Verification
- Unit tests: `ObjectSearchServiceTest.java` (7 tests - 100% PASS).
- Component tests: `ObjectContentSearchTabTest.java` (1 test - 100% PASS).
- Cập nhật theo phản hồi Mockup: Nhóm theo loại object ở panel trái kèm số lượng tổng, panel bottom hiển thị các vị trí trùng trong file có thể ẩn và kéo resize kèm click-to-jump vào Monaco DDL.
- Phase 3 full regression suite: 41/41 unit tests đạt 100% PASS (`mvn test` BUILD SUCCESS).

---

### Feature 13 (Phase 3): Schema Compare & Sync

- Status: `COMPLETE`
- Owner: Migration Team
- Target milestone: Phase 3 - Slice 4

#### Python reference
- Files: `app/services/schema_compare_service.py`, `app/api/schema_compare_api.py`, `static/js/schema-compare.js`, `static/css/schema-compare.css`, `templates/schema_compare_window.html`
- Core classes & functions: `SchemaCompareService` (`snapshot`, `diff`, `_normalize_constraints_for_diff`, `_diff_code_objects`, `generate_migration_script`), `SchemaCompareApiMixin`.

#### Java implementation
- Core domain: `DiffStatus.java`, `DiffAction.java`, `SchemaObjectType.java`, `ColumnDiff.java`, `SchemaObjectDiff.java`, `SchemaSnapshot.java`, `SchemaCompareSummary.java`, `SchemaCompareResult.java`, `MigrationScriptResult.java`.
- Core application: `SchemaCompareService.java` (Forward Sync comparison for tables using `TableDesignDiffer` & DDL Dialects, views, procedures, functions, triggers; whitespace-cleaned DDL diff; topological transaction-wrapped migration script generation; mock snapshots generator).
- Desktop UI: `SchemaCompareTab.java` (Source/Target dual endpoints with Swap button, filter & metrics bar with status chips, categorized Master Diff Tree with selection checkboxes and diff count badges, Side-by-side Monaco DDL Viewers for Source and Target, resizable & collapsible Script Drawer with Deploy / Copy / Open in SQL Tab actions, DialogChrome for Options).
- Integration: `EditorController.openSchemaCompareTab(...)`, `MainController.openSchemaCompare(...)`, Tools menu (`Ctrl+Shift+C`), Database & Schema context menu in `DatabaseTreeCell.java`.
- CSS Tokens: `schema-compare.css` adheres to design tokens (`-ide-*`, `-color-*`) and R-UI-01 styling.

#### Verification
- Unit tests: `SchemaCompareServiceTest.java` (5 tests - 100% PASS).
- Component tests: `SchemaCompareTabTest.java` (1 test - 100% PASS).
- Phase 3 full regression suite: 47/47 unit tests đạt 100% PASS (`mvn test` BUILD SUCCESS).

---

## 5. Rules for declaring completion

A feature must not be marked `COMPLETE` if:
- It only has a visual shell.
- It uses mock data for a workflow that should be functional.
- Important actions are missing.
- Its Python behavior has not been inspected.
- Data effects or error paths have not been verified where relevant.
- Known differences have not been disclosed.

Use `UI_VERIFIED` for a completed visual prototype and keep functional status separate.

## 6. Migration decision log

| Date | Feature | Decision | Reason | Approved by |
|---|---|---|---|---|
| | | | | |

## 7. Known issues / questions

| Item | Type | Impact | Next action | Status |
|---|---|---|---|---|
| | | | | |

## 8. Final guiding principle

**Migrate the product that users already know, not an imagined replacement.**

Keep the workflows and capabilities that made LLTool useful. Rebuild them with Java technologies and present them through a cleaner, more native, modern desktop experience. Complete the existing baseline before expanding the product.
