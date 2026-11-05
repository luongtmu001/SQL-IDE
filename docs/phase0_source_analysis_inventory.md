# Phase 0: Source Analysis & Migration Preparation Report

## 1. Xác nhận Kho mã nguồn & Phiên bản Python (Source Confirmation)
- **Repository gốc:** `https://github.com/luongtmu001/IDE_SQL_Lite_Pywebview.git`
- **Thư mục làm việc thực tế hiện tại:** `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`
  - **Phiên bản:** `v1.2.3` (Cập nhật ngày 03/11/2026)
  - **Git Branch:** `main` (Up to date with origin/main)
  - **Ghi chú:** Bản phát hành v1.2.3 hoàn thiện bộ phân tích cú pháp PostgreSQL (UPDATE temp table, CASE/WHEN, system columns ctid, ::type cast), tối ưu hóa bộ nhớ đệm Bravo Layout Cache (Full XML Search), Report Layout Gen v2 (Excel reader), và căn giữa cửa sổ thích ứng.
- **Thư mục sao lưu/cũ:** `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb` (v1.0.5).

---

## 2. Kiểm tra Dự án Java Hiện tại & Bảo toàn Thành quả (Java Baseline Inspection)
- **Kiến trúc:** Clean Architecture / Hexagonal Ports & Adapters (Tách biệt hoàn toàn Core Domain, Infrastructure, Framework và UI).
- **Các phân hệ đã triển khai:**
  1. **Core Platform:** `core-api`, `core-common`, `core-domain`, `core-application` (Quản lý Project, Workspace, Connection Domain, EventBus).
  2. **Infrastructure:** `logging` (Sensitive Data Masker), `configuration` (Strongly-typed config), `filesystem` (AppDirectoryManager, AtomicFileWriter chống crash), `security` (SecureCredentialVault AES-256-GCM), `storage-local` (LocalProjectRepository, LocalWorkspaceRepository), `database-api`, `database-jdbc`, `database-postgresql`, `database-sqlite`, `database-sqlserver`.
  3. **Desktop Framework:** `DesktopBootstrap`, `AsyncTaskManager` (Virtual Threads), `GlobalDesktopState`, `NavigationManager`, `DesktopLifecycleManager`, `DesktopErrorHandler`.
  4. **UI Components & Windows:** `ComponentFactory`, `Badge`, `CardPane` (với bo góc clipping chống tràn), `BreadcrumbBar`, `SearchField`, `StatusIndicator`, `DatabaseTreeCell`, `LazyDatabaseTreeItem`, `CustomWindow` với Win32 Native Aero Snap (`WindowsWindowAdapter`), `TitleBarController`, `MainController`, `SidebarController`, `EditorController`, `ResultsController`, `StatusBarController`, `ConnectionManagerController`, `LayoutService`.
- **Kết quả kiểm thử tự động:** **71/71 tests PASS (100% BUILD SUCCESS)** thông qua `mvn test`.

---

## 3. Bản đồ Kho mã nguồn Python (Source Inventory)

### 3.1 Giao diện & Templates
| Thành phần | Tập tin Python / Template | Mô tả chức năng |
| :--- | :--- | :--- |
| Main Window Shell | `templates/index.html` (~180KB), `templates/splash.html` | Cửa sổ chính, bố cục top toolbar, sidebar trái, editor tabstrip, results panel, statusbar |
| Top Toolbar | `templates/partials/toolbar.html`, `static/js/app.js` | Các nút New Query, Open, Save, Run (F5), Cancel, Theme selector, Settings launcher |
| Connection Modal | `templates/partials/connection_modal.html`, `password_modal.html` | Hộp thoại cấu hình kết nối SQL Server & PostgreSQL, test connection, lưu mật khẩu |
| Object Explorer | `templates/partials/sidebar.html`, `static/js/explorer.js` | Cây đối tượng Server > DB > Schema > Tables/Views/Routines, search bar, filter popup |
| Query Editor | `templates/partials/editor.html`, `static/js/editor.js`, `monaco-init.js` | Tabstrip truy vấn, Monaco editor nhúng, toolbar ngữ cảnh (Connection, Database, Schema) |
| Results Panel | `templates/partials/results.html`, `static/js/grid-result.js` | Kết quả dạng bảng (AG-Grid), Messages tab, Execution Plan tab |
| Table Designer & Editor | `templates/partials/table_design_modal.html`, `static/js/table-designer.js`, `table-data-editor.js` | Soạn thảo cấu trúc bảng (cột, khóa PK/FK, index) và chỉnh sửa dữ liệu trực tiếp |
| Advanced Workspaces | `bravo_form.html`, `report_gen_window.html`, `schema_compare_window.html`, `profiler_window.html` | Công cụ so sánh form Bravo XML, phát sinh báo cáo Excel, so sánh Schema, SQL Profiler |

### 3.2 Lớp API & Backend Services
| Phân hệ API | File Mixin (`app/api/`) | Service tương ứng (`app/services/`) | Chức năng chính |
| :--- | :--- | :--- | :--- |
| Connections | `connections_api.py` | `connection_manager.py` | CRUD cấu hình kết nối, lưu/giải mã password, RAM credential cache |
| Metadata | `metadata_api.py` | `metadata_service.py` | Lấy danh sách databases, schemas, tables, columns, routines, DDL scripts |
| Query & Execution | `query_api.py` | `query_service.py` | Thực thi SQL, hủy truy vấn (cancel), phân trang kết quả, định dạng dữ liệu |
| History | `history_api.py` | `history/history_service.py` | Ghi log lịch sử truy vấn vào SQLite (`history.db`) |
| Settings | `settings_api.py` | `settings_store.py` | Đọc/ghi cấu hình font, theme, sql protection, max rows vào `settings.json` |
| Database Adapters | `app/database/factory.py` | `sqlserver.py`, `postgresql.py` | Kết nối ODBC (SQL Server) và psycopg2 (PostgreSQL) |

### 3.3 Tệp lưu trữ dữ liệu (Persistence Layer)
- `data/.secret.key`: Khóa mã hóa đối xứng Fernet (fallback).
- `data/connections.json`: Danh sách cấu hình kết nối (mật khẩu mã hóa dạng `ENC:` hoặc `ENC2:` qua DPAPI).
- `data/settings.json`: Cấu hình ứng dụng (editor, appearance, sql options, logging).
- `data/history.db`: SQLite database lưu trữ toàn bộ lịch sử query.
- `data/snippets.json`: Danh sách mã snippet SQL lưu sẵn.
- `data/code_styles.json`: Quy tắc định dạng code SQL.
- `data/session.json`: Lưu trạng thái phiên làm việc (các tab đang mở, kết nối active).

---

## 4. Các luồng công việc chính (Major Workflows)

1. **Quản lý Kết nối (Connection Lifecycle):**
   - Mở hộp thoại `Connection Manager` $\rightarrow$ Chọn loại CSDL (SQL Server / PostgreSQL).
   - Nhập thông số (Host, Port, DB, Schema, User, Password, Windows Auth, Group, Environment).
   - Test Connection $\rightarrow$ Xác thực thông tin.
   - Lưu kết nối $\rightarrow$ Mã hóa mật khẩu an toàn, ghi `connections.json`, cập nhật danh sách vào Explorer.
   - Kết nối $\rightarrow$ Khởi tạo phiên làm việc, nạp metadata cơ sở.

2. **Duyệt Cấu trúc Cơ sở dữ liệu (Object Explorer):**
   - Mở rộng Server $\rightarrow$ Load danh sách Database $\rightarrow$ Schema $\rightarrow$ Thư mục đối tượng.
   - Lazy load chi tiết (Cột, Khóa chính, Khóa ngoại, Kiểu dữ liệu) khi bung nút cây.
   - Tìm kiếm nhanh (Filter) theo tên đối tượng với debounce.
   - Menu chuột phải: *New Query*, *Select Top 1000*, *Script as CREATE/DROP/ALTER*, *View Data*, *Design Table*.

3. **Soạn thảo & Thực thi Truy vấn (Query Editing & Execution):**
   - Mở tab New Query với Database Context tự động chọn theo đối tượng đang focus.
   - Monaco Editor hỗ trợ cú pháp SQL, gợi ý IntelliSense (Metadata Cache), Hover info.
   - Bấm `F5` hoặc `Run` $\rightarrow$ Chạy truy vấn nền, hiển thị loading spinner.
   - Trả về Result Grid: Hiển thị bảng dữ liệu, cho phép sort, copy cell/row/range (TSV/CSV/JSON).
   - Tab Messages: Hiển thị thời gian thực thi, số dòng ảnh hưởng hoặc thông báo lỗi SQL chi tiết.
   - Tự động lưu vào `history.db`.

---

## 5. Danh sách Nợ kỹ thuật & Lỗi đã ghi nhận từ Python (Defects & Technical Debt)
1. **Lỗi Invalidate Cache sau khi F5 DDL Routine (Bug 1 trong docs/bugs):**
   - Khi chỉnh sửa Procedure/Function bằng ALTER script rồi F5 thành công, metadata cache cũ chưa tự động bị hủy, dẫn đến thao tác lấy DDL tiếp theo vẫn ra nội dung cũ.
   - *Giải pháp trên Java:* Khi thực thi thành công câu lệnh DDL, phát ra `DatabaseMetadataChangedEvent` để tự động làm mới node tương ứng trên cây Object Explorer.
2. **Lỗi Monaco Find Widget bắt phím Enter (Bug 2 trong docs/bugs):**
   - Khi bấm `Ctrl + F`, nhấn `Enter` trong ô tìm kiếm có lúc bị lọt phím xuống thân editor tạo dòng mới.
   - *Giải pháp trên Java:* Java-JS Bridge cần kiểm soát event routing chặt chẽ khi Monaco Find widget active.
3. **Hiện tượng Dropdown Z-index bị che khuất:**
   - Trên webview cũ, dropdown tỷ lệ zoom ở status bar từng bị kết quả AG-Grid đè lên. Trên JavaFX native controls, hiện tượng này được giải quyết hoàn toàn bằng layout stack tự nhiên.
4. **Sự khác biệt trong mã hóa mật khẩu giữa Python và Java:**
   - Python dùng Windows DPAPI (`ENC2:`) hoặc Fernet (`ENC:`, `ENC2:F:`).
   - Java sử dụng AES-256-GCM trong `SecureCredentialVault`.
   - *Kế hoạch tương thích:* Cần có cơ chế import/migration tương thích để đọc được các cấu hình từ `connections.json` cũ của người dùng mà không bắt người dùng nhập lại từ đầu.

---

## 6. Thống nhất Lát cắt Chuyển đổi Đầu tiên (Initial Migration Slice)
- **Tính năng được chọn:** **Connection Management (Quản lý Kết nối)** — *Slice 1 của Phase 2*.
- **Lý do lựa chọn:**
  - Kết nối là điều kiện tiên quyết (Prerequisite) cho toàn bộ các tính năng khác (Object Explorer, Query Execution, Autocomplete).
  - Phân hệ Java đã có nền tảng vững chắc: `core-api/ConnectionApi`, `infrastructure-security/SecureCredentialVault`, `database-api/DatabaseProviderRegistry`, `ConnectionManagerDialog` và FXML.
  - Phạm vi rõ ràng, độc lập, có thể xác minh hoàn chỉnh từ UI đến lưu trữ an toàn mà không bị phụ thuộc vào WebView hay Monaco.

