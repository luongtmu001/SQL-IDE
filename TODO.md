# BẢNG THEO DÕI CÔNG VIỆC DỰ ÁN SQL-IDE

> **Cập nhật:** 2026-11-08  
> **Nhánh:** `master` | **Cam kết gần nhất:** `503a408` (Merge Feature 09)  
> **Tài liệu tham chiếu chuẩn:** [HANDOFF.md](file:///docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/HANDOFF.md) | [SKILL.md](file:///docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/SKILL.md)  
> **Quy ước:** `[x]` Đã hoàn thành · `[ ]` Chưa làm · `🔒` Cần người dùng duyệt/quyết định  

---

## 📌 1. Các việc kiểm tra ngay sau merge (Immediate Actions)

- [x] **A1. Cách ly dữ liệu test:** Đã cấu hình `pom.xml` trỏ vào `target/test-appdata` và cài đặt `InMemoryPreferencesFactory` ngăn ghi registry Windows.
- [x] **A1.2. Dọn dẹp profile rác:** Đã sao lưu 30 connection và xóa 14 file `EDIT_TEST_*` trong AppData.
- [x] **A2.1. Cài đặt lại module hạ tầng:** Đã cài đặt thành công 11/11 submodules của `infrastructure` vào `~/.m2` (`BUILD SUCCESS` 5.0s).
- [ ] **A2.2. Chạy kiểm thử an toàn:**
  - [x] Chạy bộ Test JS IntelliSense: `.\run-intellisense-tests.cmd` (435/435 PASS)
  - [x] Chạy bộ Test Java Syntax & Editor: `OfflineSqlSyntaxCheckerTest` (8 PASS), `PostgresSyntaxCheckerTest` (4 PASS), `SqlServerSyntaxCheckerTest` (5 PASS), `MonacoEditorHostTest` (2 PASS)
  - [ ] Chạy bộ Unit Test Java toàn diện: `.\run-tests.cmd` (ghi log `target\run-tests.log`)
- [ ] **A2.3. Khởi chạy app xác minh giao diện sau merge:**
  - [ ] Lệnh: `.\run-ui.cmd`
  - [ ] Kiểm tra Splash screen (`SCR-SPLASH-001`) chạy đủ 6 bước thật, mượt mà.
  - [ ] Mở tab Editor, kiểm tra Monaco Editor nạp thành công và nhận phím gõ mượt mà (không còn khựng).
  - [ ] Thử phím tắt reload WebView `Ctrl+Shift+R`.

---

## 💡 2. Feature 09: IntelliSense & Check Syntax Parity (Nhóm C)

*Kế hoạch chi tiết:* [feature_09_intellisense_syntax_parity_plan.md](file:///docs/feature_09_intellisense_syntax_parity_plan.md)  
*Số đo hiện trạng:* [feature_09_baseline_measurements.md](file:///docs/feature_09_baseline_measurements.md)  
*Quy tắc kiến trúc:* [INTELLISENSE_ARCHITECTURE_RULES.md](file:///docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/rules/intellisense/INTELLISENSE_ARCHITECTURE_RULES.md) | [PHASE_00_INTELLISENSE_AUDIT.md](file:///docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/rules/intellisense/PHASE_00_INTELLISENSE_AUDIT.md)

### 🚀 Đã khắc phục triệt để lỗi gõ khựng / Lag phím (FE-001, FE-002, FE-003)
- [x] **FE-001 (MRU IO bottleneck):** Đã cache `_cachedHistory` trong RAM tại `completion-ranker.js`, triệt tiêu 4.300 lần đọc `localStorage.getItem` & `JSON.parse` trên mỗi lần gợi ý.
- [x] **FE-002 (Bridge Crossing bottleneck):** Đã debounce `model.onDidChangeContent` (300ms) và `onDidChangeCursorPosition` (40ms) trong `editor-bridge.js`. Bổ sung cơ chế `flushContentChanged` tức thì khi bấm F5, Ctrl+Enter, Ctrl+E, chuyển tab hoặc đóng tab, triệt tiêu việc gửi chuỗi toàn văn qua JNI WebEngine mỗi phím gõ.
- [x] **FE-003 (IntelliSense Candidate & DOM bottleneck):**
  - Trong `candidate-filter.js`: Cache `_subWords` trực tiếp trên candidate, loại bỏ việc chạy regex Unicode phân tách từ lặp đi lặp lại trên 4.300 object.
  - Trong `completion-ranker.js`: Thay thế `localeCompare` bằng phép so sánh nhanh `<` / `>` trong thuật toán sắp xếp (giảm 90% chi phí sắp xếp hàng nghìn item).
  - Trong `intellisense.js`: Nâng debounce gõ phím ký tự từ 20ms lên 120ms (tránh tranh chấp đơn luồng WebKit khi gõ nhanh); tối ưu `selectIndex` cập nhật DOM O(1) thay vì duyệt `querySelectorAll` trên toàn bộ danh sách.

### 🚀 Đã khắc phục triệt để lỗi font chữ bị răng cưa trong Text Editor (FE-004)
- [x] **FE-004 (DirectWrite Subpixel ClearType & Font Anti-aliasing):**
  - **Opaque PageFill:** Chuyển `webView.setPageFill(...)` từ `TRANSPARENT` sang màu nền opaque theo theme (`updateWebViewPageFill`), kích hoạt DirectWrite ClearType hardware rasterizer (bởi DirectWrite luôn vô hiệu hóa subpixel antialiasing trên nền trong suốt).
  - **CSS Font Smoothing:** Bổ sung `-webkit-font-smoothing: antialiased`, `-moz-osx-font-smoothing: grayscale`, `text-rendering: optimizeLegibility`, `font-variant-ligatures: none` vào `editor.css`.
  - **Quoted Font Tokens & Monospace Grid Alignment:** Bọc `'...'` quanh tên font có khoảng trắng (`Cascadia Code`, `JetBrains Mono`, `Iosevka`, `Monaspace`) khi truyền sang Monaco CSS; chuẩn hóa `letterSpacing` số lẻ về số nguyên tránh lệch tọa độ pixel vật lý.
  - **Prism DirectWrite Engine:** Bổ sung flag `-Dprism.lcdtext=true` và `-Dprism.text=native` trong `AppLauncher` và `pom.xml`.

### 🚀 Đã khắc phục và hoàn thiện Check Syntax (SQL Server + PostgreSQL + Offline)
- [x] **PostgreSQL Syntax Checker:** Tạo mới `PostgresSyntaxChecker.java` sử dụng `EXPLAIN (VERBOSE) <sql>` qua kết nối chuyên biệt, bóc tách chính xác vị trí lỗi từ `SQLException` PostgreSQL (`Position: <x>`, token `near "..."`).
- [x] **Offline Structural Syntax Checker:** Tạo mới `OfflineSqlSyntaxChecker.java` kiểm tra nhanh cục bộ (<1ms): nháy đơn chưa đóng (`'`), comment khối chưa đóng (`/*`), ngoặc đơn `(...)`, ngoặc vuông `[...]`, lỗi dấu phẩy kép hoặc dấu phẩy trước mệnh đề. Hoạt động ngay cả khi tab chưa kết nối DB.
- [x] **SyntaxCheckService Pipeline:** Kết nối định tuyến theo dialect (SQL Server -> `SqlServerSyntaxChecker`, PostgreSQL -> `PostgresSyntaxChecker`), fallback mượt mà sang `OfflineSqlSyntaxChecker`.
- [x] **Giao diện Cài đặt (Settings):** Đã gỡ bỏ nhãn "Chưa hỗ trợ" tại `SettingsRowStyler.java` đối với `editor.syntaxCheck`.

### 🔒 Quyết định thiết kế đã chốt
- [x] **D1–D8:** Giữ nguyên các thay đổi hiện tại của Java (Eager snapshot store, TTL=0, `sql-completion-rules.js`), chỉ sửa các lỗi khựng/lag và cải thiện hiệu năng.
- [x] **Q4 (SQL Server):** Sử dụng `SET PARSEONLY ON` qua kết nối JDBC.
- [x] **Q6 (PostgreSQL):** Sử dụng `EXPLAIN (VERBOSE)` qua kết nối JDBC (chỉ đọc).

### 🛠️ Triển khai mã nguồn
- [x] **Giai đoạn 1: Khắc phục triệt để lỗi gõ khựng & hiệu chỉnh engine JS:**
  - FE-001 (RAM cache MRU trong `completion-ranker.js`), FE-002 (Bridge debounce 300ms + flush on run trong `editor-bridge.js`), FE-003 (subWords cache, O(1) DOM active, so sánh nhanh string).
  - Khởi tạo Check Syntax: `OfflineSqlSyntaxChecker.java` (<1ms structural check), `PostgresSyntaxChecker.java` (`EXPLAIN (VERBOSE)`), gỡ nhãn "Chưa hỗ trợ" trên Settings.
  - Người dùng đã test và nghiệm thu thực tế mượt mà.
- [x] **Giai đoạn 2: Sửa IntelliSense & Kết nối đồng bộ cache metadata giữa JavaFX và Monaco JS:**
  - **D2 Parity:** Khôi phục đầy đủ object types SQL Server (`TR`, `TA`, `X`, `AF`, `SO`, `SN`), trigger cấp database (`sys.triggers WHERE parent_id = 0`), bảng tạm toàn cục (`tempdb.sys.tables WHERE name LIKE '##%'`), synonym mọi schema đưa vào default schema `dbo`.
  - **PostgreSQL Parity:** Bổ sung trigger (`pg_trigger`), map `relkind='f'` sang `foreign_table`.
  - **N5 Performance Bottleneck:** Cache RAM `ConcurrentHashMap` trong `DefaultSchemaService` triệt tiêu việc đọc Windows Registry trên mỗi sự kiện caret/tab switch.
  - **Ctrl+Shift+R Parity:** Nối `onReloadRequestListener` từ Monaco Editor -> `EditorController` -> `MainController.handleRefreshObjects` làm mới cả snapshot IntelliSense và cây Object Explorer.
  - **DDL Execution Invalidation:** Nối `EditorController.reloadIntellisenseAfterRun` gọi `monacoHost.notifyDdlExecution` (kích hoạt `AppMetadataCache.handleDdlExecution` xóa ngay object trong RAM JS) và tự động tải lại snapshot nền.
  - **Kiểm thử:** 435/435 JS tests PASS (`run-intellisense-tests.cmd`), 19/19 Java tests liên quan PASS.
- [x] **Giai đoạn 3: Đối chiếu kiến trúc & Audit Check Syntax (Python vs Java):**
  - Tài liệu đối chiếu: [feature_09_syntax_diff.md](file:///docs/feature_09_syntax_diff.md) chi tiết 11 tầng S1–S11.
  - Đồng bộ 6 bộ corpus kiểm thử chuẩn sang `src/test/resources/corpus/syntax/`: `batches_go.json`, `invalid.json`, `positions_l7.json`, `postgres_offline.json`, `temp_tables.json`, `valid.json`.
- [x] **Giai đoạn 4: Hoàn thiện Check Syntax đa tầng chuẩn parity (S1 – S11):**
  - **S1 (Diagnostic Model & Normalizer):** `SyntaxDiagnostic.java` hỗ trợ đầy đủ `Severity`, `code`, `source`, `token`, `line`, `col`, `endLine`, `endCol`. `DiagnosticNormalizer.java` làm sạch driver prefix (`[Microsoft]...`, `psycopg2...`, `Msg \d+...`), chuẩn hóa tọa độ token.
  - **S2 (Coordinator & Caching & Deduplication):** `SyntaxCoordinator.java` cache SHA-256 statement (TTL 5.0s, max 512 entries), lọc trùng theo `(line, col, token)`.
  - **S3 & S4 (SQL Server Adapter & Batches):** `SqlServerSyntaxAdapter.java` phân tách batch `GO` chính xác, chạy `SET PARSEONLY ON`, bóc tách dòng lỗi `Line <num>` và vị trí token. Đạt 100% test case `batches_go.json`.
  - **S5 (Structural Checks):** `StructuralSqlChecks.java` kiểm tra tức thì (<1ms) ngoặc đơn `()`, ngoặc vuông `[]`, nháy đơn `'`, comment khối `/* */`, dấu phẩy thừa trước clause, `SELECT` rỗng không có cột, và dollar quote `$tag$...$tag$` PostgreSQL. Đạt 100% test case `postgres_offline.json`.
  - **S6 (Proc Body Detector):** `ProcBodyDetector.java` bóc tách routine start, tham số `@param`, biến cục bộ `@var`.
  - **S7 (PostgreSQL Adapter):** `PostgresSyntaxAdapter.java` kết hợp `EXPLAIN (VERBOSE)` và fallback phân tích procedural blocks offline.
  - **S8 (Semantic Analyzer):** `SemanticAnalyzer.java` kiểm tra semantic offline: theo dõi `#temp` (CREATE, ALTER ADD/DROP, SELECT INTO, EXEC open states), `@table`, CTEs, derived subqueries `(SELECT ...) alias`, và cột bảng thật qua metadata cache.
  - **S9 (Best Practice Rules):** `BestPracticeRules.java` kiểm tra cảnh báo chất lượng mã: BP001 (`SELECT *`), BP002 (`WITH (NOLOCK)`), BP004 (`TOP N` không có `ORDER BY`), BP005 (`ISNULL`), BP006 (`CONVERT`), có che chắn chuỗi ký tự và chú thích.
  - **S10 & S11 (Cache Invalidation & Status Bar Integration):** `SyntaxCheckService.java` tích hợp `SyntaxCoordinator`, `EditorController.java` báo số lượng errors/warnings lên Status Bar và hủy cache cú pháp khi thực thi DDL. `MonacoEditorHost.java` chuyển tiếp chính xác severity và source sang Monaco markers.
  - **Kiểm thử:** 38/38 Java syntax tests PASS 100% (bao gồm cả 6 test suites mới và 3 suites cũ), 435/435 JS IntelliSense tests PASS.
- [ ] **Giai đoạn 5: Nghiệm thu toàn diện với bộ corpus kiểm thử và server thật.**

---

## 🎨 3. Sửa lỗi giao diện tồn đọng (Nhóm B)

*Nguyên tắc bắt buộc:* Tuân thủ [04-ui-change.md](file:///docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/workflows/04-ui-change.md) — Phải có ảnh chụp snapshot/số đo thực tế trước và sau khi sửa, không sửa dựa trên giả thuyết.

- [ ] **T7. Thanh kéo Editor & Results:** Sửa thanh sash phân chia giao diện (thay `SplitPane` dọc bằng layout tự vẽ).
- [ ] **T8. Icon mũi tên Explorer:** Căn chỉnh icon mũi tên mở/đóng không bị lệch thụt lùi giữa các cấp node cây.
- [ ] **T9. Viền ComboBox bo góc:** Sửa viền hiển thị ComboBox không bị lệch khi ở chế độ bo góc (`rounded`).
- [x] **T10. Nguồn bảng kết quả:** Backend trả metadata tên bảng nguồn cho từng result set (trích xuất `meta.getTableName(i)` từ JDBC metadata, hiển thị trên tab và header của stacked result grid).
- [ ] **T11. Cố định cột `#` khi cuộn ngang:** Khắc phục lỗi cột số thứ tự không neo cố định khi bảng kết quả cuộn ngang (chạy lại và sửa `GridPerfIT`).

---

## ⚡ 4. Hoàn thiện các mốc Giai đoạn 8 — Nối Logic Thật (Nhóm D)

*Kế hoạch mốc:* [phase8-plan.md](file:///ui-specifications/04-reports/phase8-plan.md)  

### 🔒 Danh sách kiểm chứng thực tế trên giao diện (K1 – K10)
- [ ] **K1:** Kiểm tra chặn câu lệnh ghi `UPDATE` / `DELETE` trên PostgreSQL chỉ đọc.
- [ ] **K2:** Kiểm tra SQL Server mở 2 tab truy vấn sinh đúng 2 `@@SPID` riêng biệt.
- [ ] **K3:** Kiểm tra xử lý khi nhập sai mật khẩu (hiện hộp thoại hỏi lại).
- [ ] **K4:** Đổi kết nối ở một tab không làm ảnh hưởng đến các tab khác.
- [ ] **K5:** Đóng tab hoặc tắt ứng dụng giải phóng phiên kết nối đúng cách.
- [ ] **K6:** Hiển thị cây Object Explorer đầy đủ cho cả SQL Server và PostgreSQL.
- [ ] **K7:** Chức năng View Definition và Script CREATE / ALTER / DROP với server thật.
- [ ] **K8:** Chức năng Script Table đầy đủ các cột và kiểu dữ liệu.
- [ ] **K9:** Ô chọn Database / Schema trên thanh công cụ chuyển đúng theo tab active.
- [ ] **K10:** Thông báo lỗi tải metadata hiển thị rõ ràng bên dưới node cây.

### 🚀 Hoàn thành các tính năng mốc D còn lại
- [x] **D0.2:** Bổ sung cờ khởi động `-Dsqlide.uiDebug` vào hệ thống (`BackendFlags.isUiDebug()`).
- [ ] **D2:** Bổ sung lớp phủ `LoadingOverlay` và nút Hủy cho Object Explorer khi tải metadata.
- [x] **D4:** Bổ sung xuất file Excel định dạng thật `.xlsx` (OpenXML streaming thuần qua `FastXlsxWriter`, không phụ thuộc POI; hỗ trợ >100.000 dòng, format header `#E6E6E6`, viền `#D4D4D4`, NULL format `#FFFFE1`, freeze panes `A2`, auto-filter, tự căn độ rộng cột).
- [x] **D5:** Kiểm chứng Execution Plan thật; xử lý nhãn thông báo rõ ràng khi fallback sang Plan mô phỏng (`plan.setSimulated(true)`, hiển thị nhãn `"(MÔ PHỎNG)"` trên `planTypeBadge`).
- [x] **D6:** Tích hợp bộ Format SQL thật: port trọn vẹn `sql-formatter.js` (641 dòng) từ Python LLTool vào Monaco Editor; đăng ký `registerDocumentFormattingEditProvider` & `registerDocumentRangeFormattingEditProvider`; hỗ trợ đầy đủ 31 tùy chọn code style từ `SqlStyleStore`, phím tắt `Shift+Alt+F` / `Ctrl+K Ctrl+D`, đồng bộ style realtime khi lưu cài đặt và fallback an toàn sang `SimpleSqlFormatter`.
- [x] **D8:** Nối logic kiểm tra cập nhật tự động thật và hoàn thiện Dialog About: đọc phiên bản build động từ Maven package `AppInfo.java`, kiểm tra `AboutDialog` và `UpdateDialog`.
- [ ] **D9:** Hoàn thiện báo cáo đánh giá và nghiệm thu toàn diện Giai đoạn 8 (`phase8-review.md`).

---

## 🧹 5. Nợ kỹ thuật & Kiểm thử (Nhóm E)

- [ ] **Sửa 2 Unit Test đang lỗi:**
  - [ ] `ResultsGridTest.testResultsControllerIntegration` (lỗi timeout đồng bộ FX).
  - [ ] `SettingsAndThemesTest.testApplyAndCancelBehavior` (lệch giá trị theme name vs id).
- [ ] **Nhúng font Inter cục bộ:** Nhúng file font vào tài nguyên app để không phụ thuộc font hệ thống.
- [ ] **G1. So sánh ảnh đối chiếu:** Chụp ảnh đối chiếu giao diện cạnh nhau giữa LLTool (Python) và SQL-IDE (cùng cỡ 1456×816).
- [ ] **G2. Nghiệm thu Gate B:** Rà soát và hoàn thiện 17 gói đặc tả màn hình viết hồi tố.

---

## 🏗️ 6. Feature 10: Table Designer (New / Edit / Copy Table — SCR-TD-001)

*Kế hoạch chi tiết:* [feature_10_table_designer_migration_plan.md](file:///docs/feature_10_table_designer_migration_plan.md)  
*Hồ sơ đặc tả:* [ui-specifications/01-screens/SCR-TD-001/](file:///ui-specifications/01-screens/SCR-TD-001/)

- [x] **TD0 (Gate 1 — Mốc TD0):** Lập trọn bộ hồ sơ đặc tả màn hình `SCR-TD-001` (`screen-spec.md`, `layout.json`, `components.json`, `interactions.json`, `states.json`, `mapping.json`, `evidence.md`) và đăng ký vào `screen-inventory.json` / `screen-inventory.md`.
- [x] **TD1 (Core Domain, Differ & Dialects):**
  - Đã tạo domain models: `TableDesign`, `ColumnDesign` (với `ColumnId` bất biến và `originalName`), `IndexDesign`, `ForeignKeyDesign`, `UniqueDesign`, `CheckDesign`, `TriggerDesign`.
  - Đã tạo `TableDesignValidator` (kiểm tra tên rỗng, tên trùng B13, kiểu dữ liệu, scale, precision, identity).
  - Đã tạo `TableDesignDiffer` (tính toán `MigrationPlan`, 20 bước thứ tự DDL chuẩn, phát hiện destructive changes).
  - Đã tạo `SqlServerDdlDialect` & `PostgresDdlDialect`.
  - Đã port test suite Python sang JUnit: 35/35 tests PASS 100% bao gồm cả test hồi quy cho B1, B3, B4, B5, B6, B7, B8, B9, B13, B16. Toàn bộ `core` module đã build và install vào `~/.m2`.
- [ ] **TD2 (Metadata Reader & Services):**
  - Tạo interface `TableDesignReader` và cài đặt cho SQL Server và PostgreSQL (truy vấn scale, identity seed/increment, collation, computed, comment, default).
  - Tích hợp `TableDesignerService` qua `BackendFlags` (`-Dsqlide.backend=mock`).
- [ ] **TD3 (UI New Table & Column Grid):**
  - `TableDesignerTab`, `ColumnGrid` (`TableView` ảo hóa với `fixedCellSize` theo `grid.fontSize`), `ColumnInspector`, Monaco Live DDL Preview.
  - Hoàn thiện 15 thao tác dòng chuẩn TD-ROW-01…15 (Undo/Redo, chèn, nhân bản, kéo thả, TSV clipboard).
- [ ] **TD4 (UI Edit Table & Save Migration Dialog):**
  - Đánh dấu trạng thái dòng (`+`, `~`, `↻`, `−`), checkbox lọc cột thay đổi.
  - `SaveDesignDialog` bọc `DialogChrome`, hiển thị danh sách statements, xác nhận destructive changes, thực thi trong single transaction.
  - Chế độ chỉ đọc: khóa nút Lưu, hỗ trợ "Mở trong tab SQL". Đồng bộ làm mới Object Explorer.
- [ ] **TD5 (Copy Table Dialog):**
  - `CopyTableDialog` bọc `DialogChrome`, kiểm tra tên bảng đích với DB, kiểm tra giới hạn 128 / 63 ký tự, lọc thành phần sao chép.
- [ ] **TD6 (Sub-tabs nâng cao):**
  - Hoàn thiện các sub-tab: Indexes (phân biệt dialect enum), Foreign Keys, Unique & Check Constraints, Triggers (`CREATE OR ALTER`).
- [ ] **TD7 (Nghiệm thu & Benchmark hiệu năng):**
  - Gate 4 & Gate 5: Thử nghiệm bảng lớn 300 cột, gõ phím không trễ, diff ≤ 50ms trên luồng nền.

---

## 🛠️ Cheatsheet Lệnh Terminal Thường Dùng

| Mục đích | Lệnh PowerShell |
|---|---|
| Chạy app (backend thật, chỉ đọc) | `.\run-ui.cmd` |
| Chạy app (dữ liệu mock an toàn) | `.\run-ui-mock.cmd` |
| Chạy bộ Unit Test Java | `.\run-tests.cmd` |
| Chạy bộ Test JS IntelliSense | `.\run-intellisense-tests.cmd` |
| Chạy Integration Test DB thật | `.\run-db-tests.cmd` |
| Build lại module core | `.\mvnw.cmd -f core\pom.xml install -DskipTests` |
| Build lại module infrastructure | `.\mvnw.cmd -f infrastructure\pom.xml install -DskipTests` |
| Xem trạng thái git ngắn gọn | `git status -s` |

