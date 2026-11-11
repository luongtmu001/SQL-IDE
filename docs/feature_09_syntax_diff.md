# Đối chiếu kiến trúc Check Syntax: Python → Java (Giai đoạn 3)

> **Tài liệu tham chiếu chuẩn:** [feature_09_intellisense_syntax_parity_plan.md](file:///docs/feature_09_intellisense_syntax_parity_plan.md)  
> **Nguồn mẫu (chỉ đọc):** `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`  
> **Ngày lập:** 2026-11-08  
> **Trạng thái:** Đã hoàn thành đối chiếu Giai đoạn 3, sẵn sàng triển khai Giai đoạn 4 theo các lớp S1 – S11.

---

## 1. Tổng quan kiến trúc kiểm tra cú pháp

### 1.1. Hiện trạng Python (Mẫu chuẩn)
Hệ thống Check Syntax của Python được thiết kế theo mô hình đa tầng (layered pipeline) độc lập, an toàn và có hiệu năng cao:
1. **Cache tầng câu lệnh (Statement-level Cache):** Khóa SHA-256 từ `(sql, dialect, conn_id, db, schema, debug, bp)`, TTL 5.0 giây, kích thước tối đa 512 entries, tự động xóa khi kết nối đổi hoặc sau DDL.
2. **Bộ điều phối (Coordinator):** `coordinator.py` tiếp nhận yêu cầu, tra cứu cache, chọn adapter, kích hoạt semantic analyzer, best-practice rules, và khử trùng (deduplication).
3. **Mô hình lỗi chuẩn (Diagnostic Model):** Gồm `severity` (error/warning/info), `code`, `source`, `token`, `line`, `column`, `end_column`, `message` đã làm sạch header ODBC/driver.
4. **Adapter theo Dialect:**
   - **T-SQL (SQL Server):** Tách batch theo lệnh `GO` độc lập (`split_sqlserver_batches`), xử lý line offset chính xác cho từng batch, bóc tách dòng lỗi `Line <num>` từ message server (hoặc ScriptDOM), hỗ trợ dò lỗi cấu trúc.
   - **PostgreSQL:** Chiến lược Hybrid: Online dùng `EXPLAIN (VERBOSE)` trên kết nối chỉ đọc; Offline kiểm tra khối cấu trúc procedural (`DO $$`, dollar-quote balancing, `BEGIN...END`).
5. **Phân tích ngữ nghĩa (Semantic Analyzer):** Kiểm tra sự tồn tại của cột trong bảng vật lý, bảng tạm `#temp`/`##temp`, biến bảng `@table`, và CTEs mà không gây tác dụng phụ.
6. **Best-Practice Rules:** Cảnh báo chất lượng mã nguồn BP001 – BP006 (SELECT *, thiếu schema, thiếu ORDER BY khi dùng TOP...).
7. **Khử trùng & Giao tiếp Editor:** Gom nhóm lỗi theo `(line, column, token_lower)`, gửi marker tới Monaco Editor và cập nhật số lượng lỗi/cảnh báo lên Status Bar (`setProblems(errors, warnings)`).

### 1.2. Hiện trạng Java (Trước Giai đoạn 4)
- Chỉ có 3 checker cơ bản: `SqlServerSyntaxChecker` (`SET PARSEONLY ON`), `PostgresSyntaxChecker` (`EXPLAIN (VERBOSE)`), `OfflineSqlSyntaxChecker` (ngoặc đơn/kép/nháy).
- `SyntaxDiagnostic` chỉ là record đơn giản `(line, column, endLine, endColumn, message)` với severity mặc định là Error, chưa có `code`, `token`, `source`, `severity`.
- Chưa có Cache SHA-256 tầng câu lệnh.
- SQL Server chưa trích xuất `Line <num>` từ error message server, mà chỉ tìm token đầu tiên trong batch bằng `indexOfToken`, dẫn đến lệch dòng khi token xuất hiện nhiều lần.
- Chưa hỗ trợ Semantic Analyzer (chưa kiểm tra cột sai trong `#temp`, CTE, `@table` hay bảng vật lý).
- Chưa có Best-Practice Rules.
- Chưa phân tách số lỗi (errors) và cảnh báo (warnings) riêng biệt trên Status Bar.

---

## 2. Bảng đối chiếu chi tiết 11 lớp logic (S1 – S11)

### [S1] Mô hình chẩn đoán lỗi & Chuẩn hóa (Diagnostic Model & Normalizer)
- **Python:** `diagnostics_mapper.py:36` (`normalize_diagnostic`), `diagnostic-model.js`. Bóc tách sạch chuỗi ODBC `[Microsoft][ODBC...]`, `Msg 102, Level 15...`, `Line 3:`. Tự động tính `end_column = column + max(1, len(token))` và căn chỉnh token xuất hiện trên dòng.
- **Java:** `SyntaxDiagnostic.java` (record 5 trường), chưa bóc tách message ODBC/driver, chưa có `token`, `source`, `code`, `severity`.
- **Khác biệt:** Java thiếu các trường phân loại và message còn lẫn thông tin rác của JDBC/ODBC.
- **Nguyên nhân:** Lần port đầu tiên tạo class tối giản cho Monaco marker.
- **Phạm vi ảnh hưởng:** `app.framework.syntax`, `MonacoEditorHost`. Không đổi giao diện (Monaco marker chỉ hiển thị đẹp hơn).
- **Đề xuất:** Cải tiến theo Python:
  1. Mở rộng `SyntaxDiagnostic` với `Severity severity`, `String code`, `String source`, `String token`, `int line`, `int column`, `int endLine`, `int endColumn`, `String message`.
  2. Tạo `DiagnosticNormalizer.java` chứa `cleanMessage()` và `normalize()`.
- **File tạo/sửa:** `SyntaxDiagnostic.java`, `DiagnosticNormalizer.java`.
- **Kiểm chứng:** Test đơn vị so khớp thông điệp làm sạch từ các chuỗi lỗi thực tế của SQL Server và Postgres.

---

### [S2] Bộ điều phối Pipeline, Cache câu lệnh & Khử trùng (Coordinator, Cache & Deduplication)
- **Python:** `coordinator.py:51, 93, 210`. Cache SHA-256 TTL 5 giây, tối đa 512 entries, thread-safe bằng lock. Hàm `deduplicate_diagnostics` loại bỏ các marker trùng nhau tại cùng `(line, column, token_lower)`.
- **Java:** `SyntaxCheckService.java` điều phối bằng timer debounce 500ms, không có cache SHA-256 câu lệnh, không có khử trùng diagnostics.
- **Khác biệt:** Khi người dùng xóa ký tự rồi gõ lại câu lệnh cũ trong vòng 5 giây, Java phải gửi lại truy vấn JDBC lên server; trùng marker có thể xảy ra nếu nhiều rule cùng phát hiện.
- **Nguyên nhân:** Chưa port lớp điều phối `SyntaxCoordinator`.
- **Phạm vi ảnh hưởng:** `app.framework.syntax.SyntaxCoordinator`.
- **Đề xuất:** Giữ theo Python:
  1. Tạo `SyntaxCoordinator.java` chứa cache SHA-256 (TTL 5.0s, LRU tối đa 512 entries).
  2. Cung cấp hàm `invalidateCache(connectionId)` được gọi khi connection đổi hoặc DDL chạy.
  3. Bổ sung `deduplicate(List<SyntaxDiagnostic>)`.
- **File tạo/sửa:** `SyntaxCoordinator.java`, `SyntaxCheckService.java`.
- **Kiểm chứng:** Test cache hit khi gọi lại cùng SQL trong 5s, cache miss sau 5s, cache clear khi invalidate.

---

### [S3] Giao diện Adapter theo Dialect (Syntax Adapter Interface)
- **Python:** `adapters/base.py` (`BaseSyntaxAdapter`), `adapters/__init__.py:get_syntax_adapter(dialect)`.
- **Java:** Gọi cứng `SqlServerSyntaxChecker` và `PostgresSyntaxChecker` trong `SyntaxCheckService.run()`.
- **Khác biệt:** Thiếu tính đa hình để mở rộng hoặc thay thế parser theo dialect.
- **Đề xuất:** Giữ theo Python: Tạo interface `SyntaxAdapter.java` với phương thức `validate(String sql, Connection connection, String database, String schema, boolean isDebug)`.
- **File tạo/sửa:** `SyntaxAdapter.java`, `SqlServerSyntaxAdapter.java`, `PostgresSyntaxAdapter.java`.
- **Kiểm chứng:** Test gọi adapter thông qua factory / registry.

---

### [S4] SQL Server Adapter: Tách batch `GO` & Bóc tách vị trí dòng lỗi
- **Python:** `validator_mssql.py:19` (`split_sqlserver_batches`), `:131` (`_compute_token_position`). Tách chính xác các batch `GO` (bỏ qua `GO` trong chuỗi hoặc comment), trích xuất `Line <num>` từ server message để xác định dòng lỗi trong batch, sau đó dò vị trí token theo word-boundary regex.
- **Java:** `SqlServerSyntaxChecker.java` dùng `ScriptRunner.pieces()`. Tuy nhiên, vị trí lỗi đang dựa vào `indexOfToken(pieceSql, token)` (luôn lấy lần xuất hiện đầu tiên của token trong batch), bỏ qua số dòng `Line X` có trong exception message!
- **Khác biệt:** Nếu một token lỗi (ví dụ dấu `,` hoặc tên cột) đã xuất hiện ở dòng 1 mà lỗi thực sự nằm ở dòng 10, Java sẽ cắm cờ marker sai ở dòng 1.
- **Nguyên nhân:** Ghi chú trong code cũ cho rằng JDBC không trả về số dòng, nhưng thực tế `e.getMessage()` chứa chuỗi `Line <num>` hoặc `SQLServerError.getLineNumber()`.
- **Đề xuất:** Cải tiến theo Python:
  1. Trích xuất regex `Line\s+(\d+)` từ message (hoặc qua `getLineNumber()` nếu driver cung cấp).
  2. Tính dòng tuyệt đối: `targetLine = piece.startLine() + errorLine - 1`.
  3. Tìm tọa độ cột chính xác của token trên đúng dòng `targetLine` đó.
  4. Hỗ trợ bộ test corpus `batches_go.json` (12 trường hợp biên tách `GO`).
- **File tạo/sửa:** `SqlServerSyntaxAdapter.java` (thay thế/nâng cấp `SqlServerSyntaxChecker.java`).
- **Kiểm chứng:** Chạy toàn bộ 12 test case trong `batches_go.json` và `positions_l7.json`.

---

### [S5] Dò lỗi cấu trúc offline (Structural SQL Checks)
- **Python:** `validator_mssql.py:157` (`_find_all_empty_select_tokens`), `adapters/postgresql_adapter.py:25` (`check_offline_postgres_structural_blocks`).
- **Java:** Đã có `OfflineSqlSyntaxChecker.java` kiểm tra nháy đơn, comment khối, ngoặc đơn/vuông, dấu phẩy thừa.
- **Khác biệt:** Java chưa kiểm tra `SELECT` rỗng không có biểu thức (ví dụ `SELECT FROM Orders`), chưa kiểm tra cân bằng khối `DO $$ BEGIN ... END; $$;` của PostgreSQL.
- **Đề xuất:** Bổ sung vào `StructuralSqlChecks.java`:
  1. Kiểm tra SELECT rỗng trước mệnh đề `FROM`/`WHERE`.
  2. Tích hợp kiểm tra khối dollar-quoted của PostgreSQL theo corpus `postgres_offline.json`.
- **File tạo/sửa:** `StructuralSqlChecks.java` (mở rộng từ `OfflineSqlSyntaxChecker.java`).
- **Kiểm chứng:** Đạt 100% test case trong `postgres_offline.json` (14 cases).

---

### [S6] Nhận diện thân Procedure / Function & Bù dòng (Procedural Body Detector)
- **Python:** `proc_body_detector.py:18-120`. Nhận diện `CREATE PROCEDURE/FUNCTION/TRIGGER`, xác định `start_line`, trích xuất danh sách tham số `@param` và biến cục bộ `DECLARE @var`.
- **Java:** Hoàn toàn chưa có.
- **Khác biệt:** Khi SQL Server kiểm tra syntax của Stored Procedure, lỗi `Line 5` là tính từ dòng bắt đầu của Procedure, chứ không phải dòng 1 của batch/script. Không có detector thì dòng lỗi bị lệch. Ngoài ra, Semantic Analyzer sẽ báo lỗi giả cho các tham số và biến cục bộ.
- **Đề xuất:** Giữ theo Python: Tạo `ProcBodyDetector.java` để trích xuất `start_line` và danh sách tham số/biến cục bộ của routines.
- **File tạo/sửa:** `ProcBodyDetector.java`.
- **Kiểm chứng:** Test bù dòng cho các Stored Procedure nhiều dòng và nhận diện biến `@var`.

---

### [S7] PostgreSQL Adapter: `EXPLAIN (VERBOSE)` & Khối PL/pgSQL
- **Python:** `validator_pg.py:128`, `adapters/postgresql_adapter.py`. Chạy `EXPLAIN (VERBOSE) <sql>` trên connection chỉ đọc. Bóc tách `Position: <pos>` và `at or near "<token>"`. Với khối `DO $$`, chuyển sang kiểm tra structural offline hoặc rollback transaction.
- **Java:** Đã có `PostgresSyntaxChecker.java` cơ bản chạy `EXPLAIN (VERBOSE)`.
- **Khác biệt:** Chưa chuyển đổi sang mô hình `SyntaxAdapter` chung; chưa xử lý chi tiết các khối PL/pgSQL phức tạp có lồng nhau (`$outer$ ... $inner$`).
- **Đề xuất:** Tinh chỉnh `PostgresSyntaxAdapter.java` thừa kế `SyntaxAdapter`, kế thừa kết quả đã kiểm chứng của `PostgresSyntaxChecker` và bổ sung kiểm tra block PL/pgSQL.
- **File tạo/sửa:** `PostgresSyntaxAdapter.java`.
- **Kiểm chứng:** `PostgresSyntaxCheckerTest` và `postgres_offline.json`.

---

### [S8] Phân tích ngữ nghĩa (Semantic Analyzer - Bảng, Cột, CTE, #temp, @table)
- **Python:** `semantic_analyzer.py` (3.168 dòng). Phân tích AST SQL:
  - Bảng tạm: `#temp`, `##temp` (theo dõi vòng đời: `CREATE TABLE`, `ALTER TABLE ADD/DROP`, `SELECT INTO`).
  - Biến bảng: `DECLARE @tbl TABLE (...)`.
  - CTE: `WITH cte AS (...) SELECT ...`.
  - Bảng vật lý: Tra cứu cột từ `MetadataSnapshotStore` hoặc client cache.
  - Đánh dấu gạch chân đỏ cho cột không tồn tại (`INV-001` đến `INV-013`).
- **Java:** Chưa có lớp phân tích ngữ nghĩa; chỉ có kiểm tra cú pháp ngữ pháp tĩnh từ server.
- **Khác biệt:** Java chưa phát hiện được lỗi gõ sai tên cột trong câu truy vấn nếu cú pháp T-SQL tổng thể vẫn hợp lệ.
- **Đề xuất:** Xây dựng module `SemanticAnalyzer.java` tinh gọn trong `app.framework.syntax.semantic`:
  1. Hỗ trợ trích xuất bảng/alias từ mệnh đề `FROM` / `JOIN`.
  2. Tra cứu cột từ `MetadataSnapshotStore` sẵn có trong RAM.
  3. Bỏ qua các cột thuộc bảng mở (`EXEC usp_... #temp`).
  4. Hỗ trợ kiểm thử theo corpus `invalid.json` và `temp_tables.json`.
- **File tạo/sửa:** `app.framework.syntax.semantic.SemanticAnalyzer.java`, `TempTableTracker.java`.
- **Kiểm chứng:** Kiểm thử bộ corpus `invalid.json` (23 test cases) và `temp_tables.json`.

---

### [S9] Cảnh báo Best Practice (Best Practice Rules)
- **Python:** `best_practice_rules.py:20`. Kiểm tra các cảnh báo non-fatal BP001 – BP006:
  - BP001: `SELECT *` không có danh sách cột rõ ràng (Warning).
  - BP002: Dùng `WITH (NOLOCK)` trong T-SQL (Warning).
  - BP003: Thiếu schema qualification trên bảng (Hint/Info).
  - BP004: `TOP N` không có mệnh đề `ORDER BY` (Warning).
  - BP005: `ISNULL()` thay vì chuẩn ANSI `COALESCE()` (Hint).
  - BP006: `CONVERT()` thay vì chuẩn ANSI `CAST()` (Hint).
- **Java:** Chưa có rule cảnh báo nào.
- **Khác biệt:** Không có cảnh báo chất lượng viết mã.
- **Đề xuất:** Tạo `BestPracticeRules.java` hỗ trợ cấu hình bật/tắt (mặc định chỉ bật khi cấu hình `includeBestPractice = true` hoặc theo setting).
- **File tạo/sửa:** `BestPracticeRules.java`.
- **Kiểm chứng:** Test đơn vị cho từng rule BP001 – BP006 với chuỗi SQL có comment/string được mask an toàn.

---

### [S10] Đồng bộ Cache khi chạy DDL & Kết nối
- **Python:** `query.py:107-119`. Khi câu lệnh DDL (`CREATE|ALTER|DROP|TRUNCATE`) thực thi thành công, backend gọi `invalidate_syntax_cache(connection_id)`.
- **Java:** Đã kết nối `reloadIntellisenseAfterRun` ở Giai đoạn 2.
- **Đề xuất:** Bổ sung lời gọi `SyntaxCoordinator.getInstance().invalidateCache(connectionId)` trong `EditorController.reloadIntellisenseAfterRun` và `SyntaxCheckService.closeFor(profileId)`.
- **File tạo/sửa:** `EditorController.java`, `SyntaxCoordinator.java`.
- **Kiểm chứng:** Chạy DDL và xác nhận cache syntax câu lệnh liên quan bị xóa lập tức.

---

### [S11] Phân tách số lượng Lỗi & Cảnh báo trên Status Bar
- **Python:** `syntax-check-service.js:320`. Đếm số `errors` (severity === 'error') và `warnings` (severity === 'warning'), cập nhật UI status bar: `$(errors) errors, $(warnings) warnings`.
- **Java:** `MonacoEditorHost.java` và `StatusBarController.java` đã có hàm `setProblems(int errors, int warnings)`. Tuy nhiên, trước đây `warnings` luôn truyền vào giá trị `0`.
- **Khác biệt:** Giao diện Java đã sẵn sàng nhận 2 tham số, nhưng backend Java chưa đếm riêng `severity`.
- **Đề xuất:**
  1. Trong `SyntaxCheckService`: Gom nhóm `diagnostics` thành `errorsCount` và `warningsCount`.
  2. Truyền cả hai giá trị thật qua `EditorController` -> `StatusBarController.setProblems(errors, warnings)`.
- **File tạo/sửa:** `SyntaxCheckService.java`, `EditorController.java`.
- **Kiểm chứng:** Kiểm tra thanh trạng thái hiển thị đúng số lỗi đỏ và số cảnh báo vàng khi câu SQL có lỗi và cảnh báo BP.

---

## 3. Lộ trình triển khai Giai đoạn 4 (Implementation Steps)

Chúng ta sẽ thực hiện Giai đoạn 4 theo 3 gói công việc tuần tự:
- **Gói 1 (Core Pipeline & SQL Server Parity - S1 đến S6):**
  - Tạo `DiagnosticNormalizer`, mở rộng `SyntaxDiagnostic`.
  - Tạo `SyntaxAdapter`, `SqlServerSyntaxAdapter` (tách batch `GO`, bóc tách `Line <num>`, định vị cột chính xác).
  - Tạo `StructuralSqlChecks` (SELECT rỗng, cấu trúc ngoặc, dấu phẩy).
  - Tạo `ProcBodyDetector` (start line offset, parameter whitelist).
  - Tạo `SyntaxCoordinator` tích hợp Cache SHA-256 (5s TTL) và Deduplication.
- **Gói 2 (PostgreSQL Parity & Best Practice - S7, S9, S10, S11):**
  - Chuyển đổi `PostgresSyntaxAdapter` thừa kế `SyntaxAdapter`, hỗ trợ kiểm tra cấu trúc dollar-quoted blocks PL/pgSQL.
  - Tạo `BestPracticeRules` (BP001 – BP006).
  - Nối DDL cache invalidation và đẩy `setProblems(errors, warnings)` lên Status Bar.
- **Gói 3 (Semantic Analyzer & Corpus Test Verification - S8):**
  - Xây dựng `SemanticAnalyzer` tra cứu cột bảng vật lý (`MetadataSnapshotStore`), `#temp`, `@table`, CTEs.
  - Chép bộ corpus test từ Python sang `src/test/resources/corpus/syntax/`.
  - Chạy toàn bộ test Java và JS xác minh không có hồi quy.

