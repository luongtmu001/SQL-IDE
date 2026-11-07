# Giai đoạn 8 — Kế hoạch tích hợp logic thật (Gate D)
# Phase 8 — Backend integration plan

- Ngày lập: 2026-11-05. **Gate D đã đồng ý 2026-11-05.** Trạng thái: D0 + D1 tạm chấp nhận (còn mục cần kiểm lại, xem mục 7), D2 đã code (chờ kiểm), D3–D9 chưa làm.
- Nguyên tắc: tích hợp **từng khu vực một**, mỗi khu vực có công tắc bật / tắt riêng và có mốc duyệt riêng (D1…D9). Khu vực chưa duyệt vẫn chạy dữ liệu mẫu như hiện nay.
- Môi trường thử: chỉ dùng DB thử (SQLite file, SQL Server / PostgreSQL chạy local hoặc Docker). Không thử trên server PROD.

## 1. Hiện trạng (khảo sát code 2026-11-05)

| Khu vực | Hiện tại | Chỗ trong code |
|---|---|---|
| Danh sách kết nối, nhóm, settings, code style | **Đã thật** (đọc / ghi file local) | `ConnectionApi`, `ExplorerGroupService`, `SettingsService`, `SqlStyleStore` |
| Kiểm tra kết nối, ↻ database / schema | Mock | `ConnectionManagerController` (617, 668) |
| Trạng thái kết nối trong Explorer | Mock (chỉ đổi trạng thái, không mở kết nối) | `ExplorerConnectionState`, `SidebarController` (562) |
| Metadata Explorer (12 loại đối tượng) | Mock, đường thật đã có sẵn phía sau công tắc | `DesktopMetadataService`, `MockMetadataProvider` |
| Chạy câu lệnh | Chặn ở GĐ 7: chỉ SQLite trong bộ nhớ | `DesktopQueryExecutionService.resolveJdbcConnection` |
| Execution plan | Chặn ở GĐ 7: chỉ plan mô phỏng | `DesktopExecutionPlanService` |
| Lưới kết quả ban đầu, cây khi chưa có profile | Dữ liệu mẫu | `QueryResultMockService`, `DatabaseMockService` |
| Ô chọn database / schema trên toolbar | Danh sách mẫu | `MainController` (683) |
| Format SQL | Bộ format mẫu `SimpleSqlFormatter` | `CodeStyleDialog`, `EditorController` |
| Kiểm tra cập nhật, phiên bản About | Mock / hằng số | `UpdateDialog`, `AppInfo` |
| Cảnh báo câu lệnh nguy hiểm | **Đã thật** (logic) | `SqlProtectionService` |

Các điểm kiến trúc cần xử lý trước khi nối thật:

1. **Một kết nối dùng chung cho cả app.** `ConnectionSessionManager.connect()` ngắt kết nối cũ trước khi mở kết nối mới, trong khi giao diện cho mỗi tab một kết nối / database / schema riêng. Cần mô hình **phiên theo tab** (hoặc pool theo profile + database).
2. **Chỉ có 3 provider:** SQLite, SQL Server, PostgreSQL. `mapDatabaseType` đưa mọi loại khác (kể cả MySQL) về PostgreSQL. Cần chặn rõ loại chưa hỗ trợ, hoặc thêm provider.
3. **Trạng thái "đã kết nối"** trong Explorer hiện chỉ là cờ giao diện. Khi nối thật, trạng thái phải lấy từ phiên thật (đang kết nối / lỗi / mất kết nối).
4. **Mật khẩu:** đã có `DesktopCredentialVaultAdapter`. Cần nối luồng DLG-002 (hỏi mật khẩu, lưu mã hóa 30 ngày) với vault.

## 2. Việc chuẩn bị (D0)

| # | Việc | Ghi chú |
|---|---|---|
| D0.1 | **T6: commit Git** toàn bộ prototype làm mốc trước khi nối thật | Cần bạn đồng ý |
| D0.2 | Sửa T7, T8, T9, T11 với **bằng chứng chạy thật**: thêm chế độ gỡ lỗi giao diện `-Dsqlide.uiDebug=true` (ghi ra log cây node, kích thước, lớp CSS của vùng lỗi), hoặc cho phép tôi điều khiển máy để chụp | Sửa theo đọc code không đủ (xem log lỗi trong `migration-plan.md`) |
| D0.3 | Công tắc theo khu vực, thay cho một cờ `sqlide.mockMetadata` chung: `sqlide.backend.connections`, `.metadata`, `.query`, `.plan`, `.format`, `.update` | Mặc định tắt hết; bật dần sau từng mốc |
| D0.4 | Chuẩn bị DB thử: SQLite file mẫu; SQL Server và PostgreSQL bằng Docker, kèm script dữ liệu mẫu | Bạn cho biết có sẵn server thử không |
| D0.5 | Quy tắc an toàn khi nối thật: profile gắn PROD mặc định **chỉ đọc** (DML/DDL phải qua DLG-007b), timeout mặc định, giới hạn số dòng, không tự kết nối lúc khởi động (giữ fix T1) | |

## 3. Các mốc tích hợp

| Mốc | Khu vực | Nội dung | Phụ thuộc | Tiêu chí nghiệm thu |
|---|---|---|---|---|
| **D1** | Kết nối | Kiểm tra kết nối thật; ↻ database / schema thật; DLG-002 + vault; phiên theo tab; trạng thái Explorer lấy từ phiên thật; ngắt / kết nối lại; lỗi hiện bằng toast + "Chi tiết" | D0 | Kết nối được 3 loại DB thử; sai mật khẩu / timeout báo đúng; 2 tab dùng 2 database khác nhau không giẫm nhau |
| **D2** | Object Explorer | Bật đường metadata thật (12 loại đối tượng) + cache + làm mới; bộ lọc DLG-005/006 áp vào truy vấn; `LoadingOverlay` khi tải lâu; script SELECT / CREATE (`SqlScriptGenerator`) | D1 | Cây đúng với DB thử; mở rộng node > 1 s có lớp phủ đang tải và Hủy được |
| **D3** | Chạy câu lệnh | Chạy theo phiên của tab (database / schema của tab); chạy phần chọn / câu tại con trỏ; nhiều result set; Hủy thật (`Statement.cancel`); Messages / Timeline thật; Actions Guard + PROD guard bằng `SqlProtectionService`; giới hạn dòng từ toolbar | D1 | DELETE không WHERE bị chặn; Hủy dừng câu chạy dài; batch nhiều SELECT ra đúng số bảng |
| **D4** | Lưới kết quả | Kiểu dữ liệu thật (ngày giờ, nhị phân, NULL theo setting); xuất file (`DesktopGridExportService`); hiệu năng 100k dòng; **T10** nguồn bảng từ metadata của result set | D3 | 100k dòng cuộn mượt; xuất CSV / Excel đúng; T10 đúng với bảng tạm / biến bảng |
| **D5** | Execution plan | Plan ước tính / thực tế cho SQL Server, PostgreSQL, SQLite; plan thực tế trên PROD phải xác nhận vì nó chạy câu lệnh | D3 | Plan hiện đúng cây toán tử với DB thử |
| **D6** | Format SQL | Thay `SimpleSqlFormatter` bằng bộ format thật áp 31 tùy chọn của DLG-009; DLG-008 dùng bộ này | — | Kết quả format khớp LLTool trên bộ mẫu |
| **D7** | IntelliSense | Gợi ý bảng / cột / alias từ cache metadata (LLTool có `completion-engine.js`, `context-analyzer.js`…) | D2 | Cần bạn quyết định có làm ở GĐ 8 không |
| **D8** | Cập nhật + About | Kiểm tra cập nhật từ trang release thật; phiên bản lấy từ bản build (Maven) thay cho hằng số | — | Đúng 6 trạng thái DLG-013 với dữ liệu thật |
| **D9** | Review cuối | Test tích hợp trên DB thử; chạy lại toàn bộ kịch bản UI; báo cáo `04-reports/phase8-review.md` | D1…D8 | Gate D cuối |

Thứ tự đề xuất: D0 → D1 → D2 → D3 → D4 → D5, còn D6, D8 làm song song được (không cần DB). D7 tùy quyết định.

## 4. Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Chạy nhầm câu lệnh trên PROD | Công tắc theo khu vực; PROD chỉ đọc mặc định; DLG-007b; không tự kết nối lúc mở app |
| Đổi mô hình phiên (một kết nối → theo tab) ảnh hưởng nhiều controller | Làm ở D1 trước mọi khu vực khác; có test cho 2 tab / 2 database |
| UI treo khi truy vấn chậm | Mọi gọi DB chạy nền (virtual thread / `AsyncTaskManager`), có timeout, lớp phủ đang tải và Hủy |
| Lỗi UI tồn (T7, T8, T9, T11) làm khó kiểm thử | Xử lý ở D0.2 với bằng chứng chạy thật |

## 5. Quyết định của bạn (2026-11-05)

| # | Câu hỏi | Trả lời |
|---|---|---|
| 1 | Commit Git làm mốc (T6) | Đồng ý → `apply-phase8-d1.cmd` commit bản prototype (tag `ui-prototype-baseline`) rồi mới chép code D0 + D1 và commit tiếp |
| 2 | Thứ tự loại DB | SQL Server → PostgreSQL → SQLite. MySQL chưa cần |
| 3 | Server thử | SQL Server `localhost,1433`, PostgreSQL `localhost,5432` (user `postgres`, chỉ đọc). Mật khẩu không ghi vào repo hay tài liệu: nhập lúc kết nối (DLG-002) |
| 4 | Mô hình phiên | **Mỗi tab một kết nối**, tab hiển thị trạng thái phiên riêng, các tab độc lập |
| 5 | Chỉ đọc | **Tất cả kết nối chỉ đọc** ở giai đoạn này |
| 6 | IntelliSense | Tách ra sau GĐ 8 (D7 bỏ khỏi GĐ 8) |
| 7 | T7, T8, T9, T11 | Chỉ ghi log, chưa sửa |

## 6. Đã làm: D0 + D1 (2026-11-05)

Kết quả bạn kiểm 2026-11-05: ✅ 2 tab cùng kết nối PostgreSQL có 2 PID khác nhau (phiên theo tab đúng). Bạn tạm coi D1 là đạt; các mục chưa kiểm ghi ở mục 7.

Đọc lại logic LLTool (`models/connection_session.py`, `services/connection_manager.py`, `utils/sql_safety.py`) và áp vào Java, kèm một số cải tiến.

| Việc | Chi tiết | File |
|---|---|---|
| Công tắc theo khu vực (D0.3) | Mặc định **thật**. `-Dsqlide.backend=mock` hoặc biến môi trường `SQLIDE_BACKEND=mock` (`run-ui-mock.cmd`) về dữ liệu mẫu; từng khu vực `-Dsqlide.backend.connections|metadata|query|plan=mock`. Test Maven luôn chạy mock | `framework/backend/BackendFlags`, `pom.xml` |
| Chỉ đọc cho mọi kết nối (D0.5) | `-Dsqlide.readOnly=true` mặc định. 3 lớp: kiểm câu lệnh trước khi chạy (cả trước hộp xác nhận PROD), JDBC `setReadOnly(true)`, PostgreSQL `SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY` | `ConnectionSessionManager.openSession / applyReadOnly`, `MainController.handleExecute` |
| Bộ phân loại câu lệnh | Port `classify_statement` + `check_query_safety` của LLTool. Cải tiến: bỏ chuỗi / comment / tên trong `""` `[]` trước khi quét; `EXPLAIN ANALYZE` câu ghi = ghi; `SELECT … INTO`; CTE có DELETE; `DO $$…$$`; `SET … READ WRITE`, `default_transaction_read_only`; GRANT / BACKUP / DBCC / COPY / VACUUM… Có unit test | `framework/query/SqlStatementClassifier`, `SqlStatementClassifierTest` |
| **Phiên theo tab** (D1) | Mỗi tab mở kết nối riêng (khóa theo `documentId` của tab). Đổi database: SQL Server `setCatalog`, PostgreSQL mở lại kết nối. Đóng tab đóng phiên; đổi kết nối trên toolbar ngắt phiên cũ; thoát app đóng hết. Đọc PID server (`@@SPID` / `pg_backend_pid()`) | `framework/session/TabSessionManager`, `TabSession` |
| Giao diện theo tab | Chấm trạng thái trên tab: màu kết nối khi đã kết nối, vàng đang kết nối, đỏ lỗi, xám chưa kết nối; tooltip hiện database, PID, "Chỉ đọc" hoặc lỗi. Banner "Kết nối" mở phiên của chính tab đó. Status bar theo phiên của tab đang mở (trạng thái, PID, nhãn chỉ đọc) | `EditorController.refreshTabDecorations`, `SqlEditorTab.setSessionTooltip`, `MainController.connectTab / refreshActiveTabSession` |
| Mật khẩu | Trước đây mật khẩu nhập ở DLG-002 mà không lưu **không được dùng** khi kết nối. Nay mọi đường kết nối lấy theo thứ tự: mật khẩu vừa nhập → mật khẩu trong bộ nhớ → mật khẩu đã lưu (vault). Hỏi DLG-002 trước khi chạy câu lệnh hoặc bấm Kết nối nếu cần | `framework/session/CredentialResolver`, `MainController.ensurePasswordForRun` |
| Explorer (một phần D2) | Mỗi profile + database dùng lại **một** kết nối chỉ đọc cho Explorer (trước đây mỗi lần mở node mở rồi đóng một kết nối). Lỗi không còn bị nuốt: không lưu kết quả rỗng vào cache, trạng thái kết nối đỏ + toast lỗi. Sửa profile tạo kết nối mới | `DesktopMetadataService` |
| Quản lý kết nối | Kiểm tra kết nối, ↻ database thật; **↻ schema thật** (trước đây luôn là dữ liệu mẫu) | `ConnectionManagerController`, `ConnectionSessionManager.fetchSchemas` |
| Chạy câu lệnh (một phần D3) | Chạy trên phiên của tab; bỏ đường cũ tự kết nối theo tên profile; bỏ kết nối mặc định cứng `PostgreSQL - dev-local` và schema `public`; đóng `Statement` sau khi chạy (trước đây không đóng). Plan thực tế chỉ chạy khi câu lệnh chỉ đọc | `DesktopQueryExecutionService`, `DesktopExecutionPlanService` |
| Khác | Loại DB không hỗ trợ (MySQL…) báo lỗi rõ thay vì coi nhầm là PostgreSQL; không tự tạo profile mẫu (có profile trỏ `192.168.1.100`) khi chạy thật | `ConnectionSessionManager.mapDatabaseType`, `SidebarController` |

Còn lại cho các mốc sau: D2 phần còn lại (lọc, lớp phủ đang tải khi chậm), D3 (Messages / PRINT / NOTICE, nhiều tab chạy song song — hiện vẫn một câu lệnh một lúc cho cả app, Hủy theo tab), D4, D5, D6, D8, D9.

## 7. Danh sách cần kiểm lại (log theo yêu cầu 2026-11-05)

| # | Mốc | Việc cần kiểm | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|
| K1 | D1 | Chạy `UPDATE` / `DELETE` trên PostgreSQL | Bị chặn trước khi gửi lên server, toast + Messages "CHỈ ĐỌC" | ⏳ chưa kiểm |
| K2 | D1 | SQL Server `localhost,1433`: kết nối 2 tab, `SELECT @@SPID` | 2 PID khác nhau; status bar hiện PID của tab đang mở | ⏳ chưa kiểm |
| K3 | D1 | Nhập sai mật khẩu (DLG-002) rồi mở lại | Chấm đỏ + toast lỗi; mở lại thì hỏi mật khẩu lần nữa | ⏳ chưa kiểm |
| K4 | D1 | Đổi kết nối trên toolbar của 1 tab | Phiên cũ của tab bị ngắt, tab khác không ảnh hưởng | ⏳ chưa kiểm |
| K5 | D1 | Đóng tab đang kết nối / thoát app | Phiên đóng (PostgreSQL: `pg_stat_activity` không còn PID đó) | ⏳ chưa kiểm |
| K6 | D2 | Mở cây Explorer của SQL Server và PostgreSQL thật | Database → schema → bảng / view / thủ tục / hàm / trigger / sequence đúng | ⏳ chưa kiểm |
| K7 | D2 | Chuột phải view / thủ tục / hàm / trigger → View Definition, Script as CREATE / ALTER / DROP | Mở định nghĩa thật lấy từ server | ⏳ chưa kiểm |
| K8 | D2 | Chuột phải bảng → Script Table as CREATE / SELECT / INSERT | CREATE TABLE có cột, kiểu, NULL, default, identity, PK; DML liệt kê đủ cột | ⏳ chưa kiểm |
| K9 | D2 | Ô chọn database / schema trên toolbar | Danh sách thật; chọn database đổi database của **tab hiện tại** | ⏳ chưa kiểm |
| K10 | D2 | Tắt server hoặc đổi sai host rồi mở node | Dòng "⚠ Không tải được: …" dưới node, mở lại thì thử lại | ⏳ chưa kiểm |

## 8. Đã làm: D2 Object Explorer (2026-11-05, chờ kiểm K6–K10)

| Việc | Chi tiết | File |
|---|---|---|
| Định nghĩa thật | Port `get_object_definition` / `script_object` của LLTool. SQL Server: `sys.sql_modules`, `sys.sequences`, `sys.types`; PostgreSQL: `pg_get_viewdef`, `pg_get_functiondef` (mọi overload), `pg_get_triggerdef`, `pg_sequences`; SQLite: `sqlite_master`. CREATE TABLE dựng từ JDBC metadata (cột, kiểu, default, identity / serial, PK). CREATE / ALTER / DROP / DROP And CREATE đúng cú pháp từng loại (PostgreSQL DROP hàm theo từng chữ ký tham số, DROP TRIGGER … ON bảng). Chỉ chạy SELECT trên kết nối Explorer chỉ đọc | `framework/metadata/ObjectScriptService` |
| Menu Explorer | "Script … as", "View Definition", "Drop …" của bảng, view, materialized view, thủ tục, hàm, trigger, sequence, type dùng định nghĩa thật (chế độ mock vẫn dùng mẫu cũ). SELECT / INSERT / UPDATE / DELETE To tự lấy danh sách cột nếu bảng chưa mở | `DatabaseTreeCell` |
| Đặt tên an toàn | `[x]` cho SQL Server, `"x"` khi cần cho PostgreSQL / SQLite (tên có dấu cách, chữ hoa…) | `ObjectScriptService.quote` |
| Sequences | Thư mục "Sequences" trước đây luôn trống (không có hàm tải). Nay tải thật cho PostgreSQL và **thêm cho SQL Server** | `DesktopMetadataService.fetchSequences`, `LazyDatabaseTreeItem` |
| Lỗi tải | Dòng "⚠ Không tải được: …" dưới node thay cho thư mục trống; mở lại / Refresh thử lại. Sai mật khẩu thì quên mật khẩu tạm để lần sau hỏi lại | `LazyDatabaseTreeItem.showLoadError`, `PasswordPolicyService.forgetSessionPasswordIfAuthError` |
| Toolbar | Ô database / schema lấy danh sách thật khi mở; chọn database / schema đổi của **tab hiện tại** (trước đây chỉ đổi biến chung, lần chạy sau vẫn dùng database mặc định). Bỏ danh sách schema cứng `public / auth / audit_logs…` | `MainController.setupDatabaseSelector` |

Chưa làm trong D2: lớp phủ đang tải + Hủy cho Explorer (hiện dòng "Đang tải…"; API metadata chưa hủy được giữa chừng), Properties, Dependencies, Search Object Content (vẫn mờ "(chưa hỗ trợ)").
