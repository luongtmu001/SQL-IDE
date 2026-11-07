# Kế hoạch đối chiếu IntelliSense và Check Syntax: Python → Java

> **Trạng thái:** bản kế hoạch, chờ duyệt. Chưa sửa code theo kế hoạch này.
> **Nguồn mẫu (chỉ đọc):** `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`
> **Đích:** nhánh `claude/intellisense-python-logic-548ce4`
> **Ngày khảo sát:** 2026-11-07

Mức độ bằng chứng dùng trong tài liệu:

- **[Đã xác nhận]**: đọc thấy trực tiếp trong code, có dẫn file.
- **[Giả thuyết]**: suy luận, chưa đo. Phải đo ở Giai đoạn 0 rồi mới được dùng làm căn cứ sửa.

---

## 1. Nguyên tắc bắt buộc

| # | Nguyên tắc | Cách áp dụng |
|---|---|---|
| 1 | Chỉ chỉnh logic, không chỉnh giao diện | Không sửa FXML, CSS, DOM popup, bố cục, màu, chữ hiển thị (danh sách ở mục 5.2). Việc nào cần đổi giao diện thì dừng và hỏi |
| 2 | Không sửa dự án Python | Chỉ đọc. Không chạy lệnh ghi vào thư mục đó |
| 3 | Python là mẫu | Mọi logic Java phải ánh xạ được về Python, hoặc được ghi là khác biệt đã duyệt |
| 4 | Sửa theo cấu trúc và quy tắc dự án | Theo `docs/performance-rules.md`, các gói `app.framework.*`, thứ tự `core` → `infrastructure` → `app` |
| 5 | Không tự suy diễn | Chỗ mập mờ, khác nhau hoặc chưa biết làm thì đưa vào mục 9 và chờ trả lời |
| 6 | Luôn so sánh | Mỗi thay đổi đều có bảng so sánh theo mẫu ở mục 8 |
| 7 | Nêu trước khi làm | Mỗi việc ghi rõ phạm vi ảnh hưởng, nguyên nhân và file sẽ tạo hoặc sửa |
| 8 | Có số đo mới kết luận | `performance-rules.md` §5: không nói "đã nhanh" khi chưa đo trước và sau |

---

## 2. Tự kiểm: các lần sửa trước chưa đúng quy trình này

Các thay đổi sau được làm **khi chưa có bảng đối chiếu với Python**. Chúng đang nằm trong worktree, chưa commit. Bạn cần quyết định giữ hay bỏ từng mục (câu hỏi Q2).

| # | Java đã tự thêm | Python làm thế nào | Rủi ro |
|---|---|---|---|
| D1 | `MetadataSnapshotStore`: nạp sẵn **toàn bộ** object và cột khi kết nối | Nạp lười theo từng schema hoặc bảng rồi nhớ trong phiên (`metadata_service.py`). Chỉ nạp trước object **sau khi Refresh** (`explorer.js:4258`) | Máy chủ lớn tốn truy vấn và bộ nhớ. Đây là kiến trúc mới, không phải bản ánh xạ |
| D2 | Câu truy vấn object của Java lọc `is_ms_shipped = 0`, không có trigger | `sqlserver.py:909` không lọc `is_ms_shipped`, có thêm kiểu `X`, `AF`, `TR`, trigger cấp database, bảng `##` trong tempdb, synonym mọi schema | **Thiếu gợi ý so với Python** |
| D3 | Bỏ hết hạn ở cache JS (TTL = 0) | JS: 5, 10, 15, 30 phút. Backend: không hết hạn, xóa khi DDL hoặc Refresh | Lệch mẫu. Bạn đã yêu cầu hướng này nhưng cần chốt lại |
| D4 | Khi nạp lại sau DDL: đọc lại cả connection | `handleDdlExecution`: chỉ xóa đúng object, có tách tên `db.schema.object` (`metadata-cache.js:758`) | Thô hơn mẫu |
| D5 | `_typedWordChar`, `_noMatchWord`: bỏ qua phân tích khi gõ thêm chữ | Mỗi lần đổi nội dung đều phân tích ngay, refresh sau 25 ms, trigger sau 40 hoặc 100 ms (`intellisense.js:1326-1361`) | Có thể làm lệch ngữ cảnh ở trường hợp biên |
| D6 | `sql-completion-rules.js` (749 dòng): lớp luật ngữ pháp | Không có | Làm từ yêu cầu "gợi ý sai ngữ cảnh". Thứ tự gợi ý nay khác Python |
| D7 | Batch view trong `host.js`: script > 16.000 ký tự chỉ phân tích batch quanh con trỏ | Luôn phân tích toàn văn bản | `#temp` khai báo ở batch trước không được gợi ý |
| D8 | Popup mở sau dấu cách không chọn sẵn mục nào | Chọn sẵn mục đầu | Đổi cách phím Enter hoạt động |

---

## 3. Kết quả khảo sát: so sánh hai dự án

### 3.1. Nền chạy: khác biệt lớn nhất

| Hạng mục | Python | Java | Mức bằng chứng |
|---|---|---|---|
| Trình duyệt nhúng | Edge WebView2, Chromium (`main.py:385`, `gui="edgechromium"`) | JavaFX 21 WebView, WebKit | [Đã xác nhận] |
| JS và giao diện | JS chạy ở tiến trình trình duyệt, tách khỏi Python | JS chung luồng FX với **toàn bộ ứng dụng** | [Đã xác nhận] |
| Mỗi phím gõ qua cầu nối | Không gửi text sang Python, trừ yêu cầu check syntax sau 500 ms | `onContentChanged(id, model.getValue())`: **gửi toàn bộ nội dung sang Java mỗi phím** (`editor-bridge.js:346`), kèm `onCursorPositionChanged` (`:283`) | [Đã xác nhận] |
| Metadata | HTTP tới Flask, không chặn giao diện | Gọi đồng bộ qua `javaBridge`, trả lời qua `Platform.runLater` | [Đã xác nhận] |
| Tốc độ thực thi JS | Chromium V8 | WebKit của JavaFX | [Giả thuyết] chậm hơn nhiều, cần đo cùng một đoạn code |
| Monaco worker | Cả hai đều chạy trên luồng chính (`getWorker` ném lỗi hoặc fallback) | Giống nhau | [Đã xác nhận] |

**Hệ quả:** code engine JS giống Python vẫn có thể giật trên Java. Vì vậy phải đo ở Giai đoạn 0 trước khi kết luận lỗi nằm ở đâu. Lần sửa trước tôi đã đi tối ưu khi chưa có số đo.

### 3.2. IntelliSense: engine JS

Số dòng khác lấy từ `diff` file Python với file Java:

| File | Dòng khác | Ghi chú |
|---|---|---|
| `sql-tokenizer`, `scope-resolver`, `metadata-provider`, `sql-function-signatures`, `signature-help-resolver`, `hover-resolver`, `trigger-controller`, `editor-adapter` | 0 | Giống hệt |
| `candidate-filter` | 6 | Từ khóa chỉ khớp tiền tố; hàm không khớp mờ. **Java tự đổi** |
| `completion-ranker` | 9 | Điểm luật, điểm schema và database. **Java tự đổi** |
| `context-analyzer` | 12 | Cần đọc từng dòng ở Giai đoạn 1 |
| `metadata-cache` | 15 | TTL (D3) |
| `candidate-generator` | 23 | `@@` không kèm ngoặc; còn lại cần đọc |
| `sql-dialects` | 27 | Thêm từ khóa |
| `completion-engine` | 950 | Phần lớn do tách resolver thành file riêng; cần xác nhận không mất logic |
| `sql-completion-rules` | Chỉ Java | D6 |
| `sql-context-engine` | Chỉ Python | Đã bỏ khỏi Java; cần xác nhận không còn gì đang dùng |

### 3.3. IntelliSense: luồng điều khiển và dữ liệu

| Hạng mục | Python | Java hiện tại | Khác biệt |
|---|---|---|---|
| Khi đổi nội dung | Phân tích ngay, rồi quyết định | Chờ 20 ms, có đường tắt D5 | Khác |
| Độ trễ refresh | 25 ms | Ngay, hoặc 120 ms nếu danh sách > 1.500 mục | Khác |
| Độ trễ trigger | 40 ms ký tự, 100 ms còn lại | 20 ms và 80 ms | Khác |
| Mở popup sau dấu cách | Theo `isPostKeywordObjectTrigger` | Thêm `_grammarWantsPopup` | Khác (D6) |
| Truy vấn object | **1 câu UNION** (`sqlserver.py:909`) | Bulk toàn database (D1); trước đó là 5 câu riêng | Khác |
| Nạp cột | Từng bảng, nhớ trong phiên | Bulk toàn database | Khác (D1) |
| Tham số routine | `get_intellisense_parameters`, `get_routine_signatures` | Bulk với SQL Server, từng routine với PostgreSQL | Cần đối chiếu |
| Làm mới thủ công | Ctrl+Shift+R: xóa backend → xóa JS → đóng popup → nạp trước object → làm mới cây Explorer → thông báo (`editor.js:3015`, `explorer.js:4185`) | Ctrl+Shift+R: đọc lại snapshot, thông báo. **Lệnh trong editor không làm mới cây Explorer** | Thiếu |
| Sau DDL | Backend `handle_ddl_execution`, JS `handleDdlExecution` theo object; xóa cache syntax nếu có `CREATE/ALTER/DROP/TRUNCATE` (`query.py:107-119`) | Đọc lại cả connection bằng regex riêng | Khác (D4) |
| Trigger và `##temp` | Có trong danh sách object | Không có | **Thiếu** (D2) |
| Popup khi metadata chưa về | Chờ `await`, rồi hiện | Hiện từ khóa trước, tự điền sau | Khác (bạn yêu cầu) |
| Giao diện popup | Theo Python | Theo mockup bạn đã duyệt | Đã duyệt, giữ nguyên |

**Vi phạm `performance-rules.md` đọc thấy trong Java:**

- `EditorController.pushIntellisense` gọi `DefaultSchemaService.get`, tức đọc `Preferences` (registry), mỗi lần trạng thái thay đổi. Vi phạm §6. [Đã xác nhận]
- `onContentChanged` gửi toàn bộ văn bản mỗi phím, chưa có debounce. §6, cần đo ảnh hưởng. [Đã xác nhận phần code]

### 3.4. Check Syntax

Đây là phần **chênh lệch lớn nhất**:

- **Python:** 6.830 dòng phía server, 1.639 dòng JS.
- **Java:** 3 file, tổng khoảng 400 dòng.

| Lớp logic | Python | Java | Trạng thái |
|---|---|---|---|
| Điều phối pipeline | `coordinator.py`: cache → adapter → semantic → best practice → khử trùng | Gọi thẳng `SqlServerSyntaxChecker` | **Thiếu** |
| Cache theo câu lệnh | SHA-256, TTL 5 giây, tối đa 512 mục, xóa khi DDL | Không có | **Thiếu** |
| Parser SQL Server | **ScriptDOM** (không cần server), tự chọn parser theo phiên bản server | Không có | **Thiếu**, cần quyết định (Q4) |
| Dự phòng SQL Server | `SET PARSEONLY ON` qua kết nối phụ | `SET PARSEONLY ON` qua kết nối riêng | Có một phần |
| Dự phòng không kết nối | `sqlglot` | Không có, không có marker | **Thiếu** (Q5) |
| Tìm nhiều lỗi trong một batch | Che dòng lỗi rồi parse lại, tối đa 5 lần | Chỉ lấy lỗi server trả về | **Thiếu** |
| Vị trí lỗi | Dòng từ server, bù dòng theo tên procedure, `_compute_token_position` | Tìm token trong batch, **lấy lần xuất hiện đầu**. Code ghi JDBC không có số dòng | **Yếu hơn**. Cần kiểm tra `SQLServerError.getLineNumber()` của driver |
| Tách `GO` | `split_sqlserver_batches` | `ScriptRunner.pieces` | Cần đối chiếu theo corpus `batches_go.json` |
| Dò lỗi cấu trúc | SELECT rỗng, JOIN thiếu ON, dấu phẩy thừa, CASE chưa đóng, TRY/CATCH | Không có | **Thiếu** |
| PostgreSQL | `EXPLAIN (VERBOSE)` khi có kết nối, kiểm tra khối PL/pgSQL, che hàm `bs.*` | **Không kiểm tra** | **Thiếu hoàn toàn** |
| Phân tích ngữ nghĩa | `semantic_analyzer.py` (3.168 dòng): cột, bảng, alias không tồn tại; `#temp`, `@table`, CTE, bảng dẫn xuất | Không có | **Thiếu hoàn toàn** |
| Best practice | BP001 đến BP006, cảnh báo chất lượng từ ScriptDOM | Không có | **Thiếu** (Q7) |
| Mô hình lỗi | severity, code, source, token, status | `SyntaxDiagnostic` chỉ có vị trí và message, cố định là Error | **Thiếu** |
| Khử trùng lỗi | `deduplicate_diagnostics` | Không có | **Thiếu** |
| Debounce và bỏ yêu cầu cũ | 500 ms, AbortController, requestId, theo tab | 500 ms, version theo tab | Tương đương |
| Kiểm tra ngay khi mở | Mở editor, đổi tab, đổi connection | Khi phiên đổi | Cần đối chiếu |
| Lấy cột từ cache client | Gửi `cached_tables` từ `AppMetadataCache` | Không có | **Thiếu** |
| Marker | `expandTokenRange`, owner `syntax-check` | Vị trí tính sẵn, owner `sql-syntax` | Khác |
| Đếm lỗi trên status bar | Lỗi và cảnh báo | `setProblems(errors, warnings)` đã có, nhưng warnings luôn = 0 | Logic thiếu, giao diện đã có |
| Parse thủ công (Ctrl+F5, nút Parse) | Có | Không có | Phím là logic; nút là giao diện (Q8) |
| Tô cột sai (`ide-sql-invalid-column`) | Có | Không có | Cần CSS, tức giao diện (Q8) |
| Quick-fix, bảng Error List | Có | Không có | Giao diện, **ngoài phạm vi** nếu không có yêu cầu khác (Q8) |
| XML và JSON | `structured-syntax-checker.js` | Không có | Cần hỏi (Q8) |

**Lưu ý về tài liệu Python:** `syntax_check_current_architecture.md` ghi PostgreSQL dùng `BEGIN...ROLLBACK`, nhưng code hiện tại dùng `EXPLAIN (VERBOSE)` (`validator_pg.py:128, 217`). Kế hoạch này **lấy code làm chuẩn**, không lấy tài liệu.

---

## 4. Nguyên nhân

### 4.1. Đã xác nhận

| # | Nguyên nhân | Bằng chứng |
|---|---|---|
| N1 | Check syntax mới làm được một phần nhỏ của Python | Mục 3.4 |
| N2 | Lần port trước đổi logic mà không có bảng đối chiếu | Mục 2, từ D1 đến D8 |
| N3 | Danh sách object thiếu trigger, `##temp`, kiểu `X`, `AF` | `sqlserver.py:909` so với `MetadataSnapshotStore` |
| N4 | Gửi toàn bộ văn bản sang Java ở mỗi phím | `editor-bridge.js:346` |
| N5 | Đọc `Preferences` trong code chạy thường xuyên | `EditorController.pushIntellisense` |

### 4.2. Giả thuyết, phải đo ở Giai đoạn 0

> **Đã đo ngày 2026-11-07:** G1 đúng, G2 sai, G3 sai với script thường, G4 chờ đo trên server thật. N4 vì thế chỉ còn ảnh hưởng nhỏ. Chi tiết: [feature_09_baseline_measurements.md](feature_09_baseline_measurements.md).

| # | Giả thuyết | Cách kiểm chứng |
|---|---|---|
| G1 | JS trên WebKit của JavaFX chậm hơn Chromium nhiều lần, là nguyên nhân chính gây giật | Chạy cùng một đoạn `analyze` trên cả hai, so sánh mili giây |
| G2 | Phần giật chủ yếu nằm ở dựng DOM popup | Đo riêng `render` và `updateDetailPreview` |
| G3 | `onContentChanged` đáng kể với script lớn | Đo thời gian trên luồng FX mỗi phím, với 1 KB, 16 KB, 100 KB |
| G4 | 3 giây sau kết nối đến từ một nguồn khác ngoài metadata | Đo mốc: bắt đầu kết nối → phiên sẵn sàng → danh sách object → popup hiện |

---

## 5. Phạm vi ảnh hưởng

### 5.1. Được sửa (chỉ logic)

- **Engine:** `src/main/resources/monaco/intellisense/engine/*.js`
- **Điều khiển:** `host.js`; trong `intellisense.js` chỉ phần `IntelliSenseDataProvider`, `IntelliSenseController`
- **Cầu nối editor:** `editor-bridge.js`, chỉ cách gửi sự kiện, marker
- **Java:** `app/framework/intellisense/*`, `app/framework/syntax/*`, `app/framework/metadata/*`
- **Nơi nối:** `EditorController`, `MainController`, `MonacoEditorHost`, `StatusBarController` (chỉ truyền số liệu sẵn có)

### 5.2. Không được sửa

- **Dự án Python:** toàn bộ thư mục mẫu.
- **Giao diện Java:**
    - `src/main/resources/fxml/*`, `intellisense/intellisense.css`, `editor.css`
    - Lớp `IntelliSensePopupUI` (HTML, kích thước, vị trí, chữ hiển thị) trong `intellisense.js`
    - `editor.html`, `SettingsDialogController`, `SettingsRowStyler` (trừ khi bạn duyệt riêng)

### 5.3. Chức năng có thể bị ảnh hưởng

Gợi ý, hover và signature; marker lỗi và số lỗi trên status bar; thời gian kết nối (thêm truy vấn nền); số kết nối mở tới server (kết nối riêng cho syntax và metadata); bộ nhớ khi giữ metadata; làm mới Explorer nếu nối chung lệnh Refresh.

---

## 6. Các giai đoạn

Mỗi giai đoạn dừng lại để bạn duyệt báo cáo so sánh rồi mới sang giai đoạn sau.

### Giai đoạn 0: Đo và chốt hiện trạng (không đổi hành vi)

- **Việc:** thêm log đo thời gian, bật tắt được, không đổi logic; đo G1 đến G4 trên server thật của bạn, cùng thao tác trên Python và Java.
- **Kết quả:** bảng số đo `docs/feature_09_baseline_measurements.md`, kết luận nguyên nhân giật.
- **Điều kiện qua:** biết rõ thời gian đi vào đâu; có số để so sánh sau khi sửa.

### Giai đoạn 1: Đối chiếu IntelliSense (chỉ đọc)

- **Việc:**
    1. Đọc từng dòng các file ở mục 3.2 và phân loại: *giữ theo Python*, *khác đã duyệt*, *phải khôi phục*.
    2. Đối chiếu từng hàm phía server: `get_intellisense_objects/columns/parameters`, `get_foreign_keys`, `get_routine_signatures`, `list_databases`, `list_schemas`, `list_linked_servers`, `handle_ddl_execution`, cho cả SQL Server và PostgreSQL.
    3. Chạy **cùng một corpus** trên hai bên (`tests/corpus/intellisense/*.json`), xuất bảng câu nào cho kết quả khác nhau.
- **Kết quả:** `docs/feature_09_intellisense_diff.md`, mỗi khác biệt có một dòng theo mẫu mục 8.
- **Điều kiện qua:** bạn duyệt từng dòng "giữ, bỏ hay sửa".

### Giai đoạn 2: Sửa IntelliSense

Thứ tự:

1. **Khôi phục phần thiếu so với Python:** trigger, `##temp`, kiểu `X`, `AF`, synonym.
2. **Theo Q2:** giữ, sửa hoặc bỏ D1 đến D8.
3. **Refresh đầy đủ theo Python:** gồm làm mới cây Explorer, nạp trước object.
4. **DDL theo object:** dùng `handleDdlExecution`, nối từ `MainController`.
5. **Gỡ vi phạm hiệu năng** theo số đo ở Giai đoạn 0 (N4, N5, G1 đến G3).

**Điều kiện qua:** corpus giống Python, trừ khác biệt đã duyệt; số đo tốt hơn mốc ban đầu.

### Giai đoạn 3: Đối chiếu Check Syntax (chỉ đọc)

- **Việc:** đọc `validator_mssql.py`, `sqlserver_scriptdom.py`, `postgresql_adapter.py`, `validator_pg.py`, `proc_body_detector.py`, `semantic_analyzer.py`, `best_practice_rules.py`, `diagnostics_mapper.py`, `coordinator.py`, `syntax-check-service.js`, `position-mapper.js`, `diagnostic-model.js`. Với mỗi hàm: đầu vào, đầu ra, luật, trường hợp biên.
- **Kết quả:** `docs/feature_09_syntax_diff.md`, kèm kết quả corpus Python làm chuẩn (`tests/corpus/syntax/*.json`, `test_corpus_syntax_*.py`).
- **Điều kiện qua:** bạn duyệt danh sách và trả lời Q4 đến Q8.

### Giai đoạn 4: Port Check Syntax theo lớp

| Bước | Nội dung | Python | Java dự kiến |
|---|---|---|---|
| S1 | Mô hình lỗi: severity, code, source, token | `diagnostics_mapper.py`, `diagnostic-model.js` | `SyntaxDiagnostic` (mở rộng), `DiagnosticNormalizer` |
| S2 | Điều phối, cache 5 giây, khử trùng | `coordinator.py` | `SyntaxCoordinator` |
| S3 | Giao diện adapter | `adapters/base.py` | `SyntaxAdapter` |
| S4 | SQL Server: parser theo Q4, batch, vị trí lỗi, nhiều lỗi | `sqlserver_scriptdom.py`, `validator_mssql.py` | `SqlServerSyntaxAdapter`, `SqlServerSyntaxChecker` (sửa) |
| S5 | Dò lỗi cấu trúc | `validator_mssql.py` | `StructuralSqlChecks` |
| S6 | Thân procedure | `proc_body_detector.py` | `ProcBodyDetector` |
| S7 | PostgreSQL: `EXPLAIN (VERBOSE)`, khối PL/pgSQL | `validator_pg.py`, `postgresql_adapter.py` | `PostgresSyntaxAdapter` |
| S8 | Ngữ nghĩa, chia nhóm nhỏ: temp, `@table`, CTE, bảng, cột, alias | `semantic_analyzer.py` | Gói `app.framework.syntax.semantic` |
| S9 | Best practice, theo Q7 | `best_practice_rules.py` | `BestPracticeRules` |
| S10 | Nối cache: xóa khi DDL, lấy cột từ `MetadataSnapshotStore` hoặc thứ thay thế | `query.py:107-119` | `SyntaxCheckService`, `MainController` (sửa) |
| S11 | Đổ số cảnh báo lên status bar sẵn có | `syntax-check-service.js` | `EditorController` (sửa) |

**Điều kiện qua mỗi bước:** các corpus liên quan cho kết quả như Python.

### Giai đoạn 5: Nghiệm thu

Chạy đủ bộ test JS và Java; đo lại và so với Giai đoạn 0; bảng tổng kết "giống Python", "khác đã duyệt", "chưa làm". Bạn test tay trên server thật. Chỉ sau đó mới đưa vào `master` và commit, khi bạn yêu cầu.

---

## 7. File dự kiến tạo hoặc sửa

Danh sách cuối sẽ được chốt và báo lại trước khi làm từng giai đoạn, phụ thuộc câu trả lời ở mục 9.

**Tài liệu (tạo mới):**

- `docs/feature_09_baseline_measurements.md`
- `docs/feature_09_intellisense_diff.md`
- `docs/feature_09_syntax_diff.md`

**Java, gói `app.framework.syntax` (tạo mới):**

- `SyntaxCoordinator.java`, `SyntaxAdapter.java`, `DiagnosticNormalizer.java`
- `SqlServerSyntaxAdapter.java`, `PostgresSyntaxAdapter.java`
- `StructuralSqlChecks.java`, `ProcBodyDetector.java`, `BestPracticeRules.java`
- `semantic/*.java`

**Java (sửa):** `SyntaxDiagnostic`, `SyntaxCheckService`, `SqlServerSyntaxChecker`, `EditorController`, `MainController`, `MonacoEditorHost`.

**Java (giữ, sửa hoặc xóa theo Q2):** `MetadataSnapshotStore`, `IntellisenseMetadataBridge`.

**JS (sửa):** `host.js`, `editor-bridge.js`, các file engine theo Giai đoạn 1, phần controller của `intellisense.js`.

**Test (tạo mới):** corpus syntax chép từ Python (`src/test/resources/corpus/syntax/*.json`, chỉ chép dữ liệu); test Java tương ứng; thêm bài so sánh corpus cho IntelliSense.

**Công cụ so sánh:** script chỉ đọc đặt trong worktree, gọi Python ở chế độ không ghi; đầu ra nằm trong `target/`.

---

## 8. Mẫu so sánh bắt buộc cho mỗi thay đổi

```text
### [Mã] Tên logic
Python : file:dòng, tóm tắt hành vi
Java   : file:dòng, tóm tắt hành vi
Khác   : khác ở đâu, ví dụ đầu vào cho kết quả khác
Nguyên nhân: vì sao lệch
Phạm vi ảnh hưởng: chức năng, file, có đụng giao diện không (phải là KHÔNG)
Đề xuất: Giữ theo Python | Giữ khác biệt | Cải tiến (nêu lý do)
File tạo/sửa: ...
Quyết định của bạn: ...
Kiểm chứng: corpus/test nào, số đo trước và sau
```

---

## 9. Câu hỏi cần bạn trả lời

**Trước Giai đoạn 0:**

- **Q1. "Chưa đạt" cụ thể là gì sau lần sửa gần nhất?** Vẫn giật khi gõ, vẫn chờ khi kết nối, gợi ý sai, hay cả ba? Bạn thử với SQL Server hay PostgreSQL, script dài khoảng bao nhiêu, database có khoảng bao nhiêu bảng?
    - Nếu có, cho tôi dòng log `IntelliSense metadata of ...`.
- **Q2. Với các mục D1 đến D8 ở mục 2:** bỏ hết để về đúng Python rồi mới cải tiến lại, hay giữ tạm trong lúc đối chiếu? Đề xuất của tôi: **đưa về đúng Python trước**, để có mốc so sánh sạch; khi đó D1 được xét lại theo số đo.
- **Q3. Mức "giống Python":** thứ tự gợi ý và thời điểm mở popup phải giống hệt, hay chấp nhận cải tiến nếu bạn duyệt từng mục? Lần trước bạn nói Python "gợi ý sai ngữ cảnh, thứ tự chưa tốt", nên hai yêu cầu này có thể mâu thuẫn.

**Trước Giai đoạn 4:**

- **Q4. Parser SQL Server khi không dùng server.** Python dùng `ScriptDom.dll` của .NET qua pythonnet; Java không gọi trực tiếp được. Các lựa chọn:
    - (a) Chỉ dùng `SET PARSEONLY` (cần kết nối, đang có).
    - (b) Viết chương trình .NET nhỏ đi kèm app để gọi ScriptDOM, cần cài .NET và đóng gói thêm.
    - (c) Dùng thư viện parser Java; chưa kiểm chứng độ phủ T-SQL.
    - Bạn chọn hướng nào? Có thể đòi hỏi app phải kèm .NET runtime không?
- **Q5. Khi tab chưa kết nối:** Python vẫn kiểm tra offline (`sqlglot`), Java thì không có thư viện đó. Không hiện marker, hay chỉ chạy các luật cấu trúc?
- **Q6. PostgreSQL có port ngay không?** Python chạy `EXPLAIN (VERBOSE)` trên chính câu SQL của người dùng. Lệnh này không thực thi, nhưng vẫn gửi lên server mỗi lần dừng gõ. Có chấp nhận với kết nối chỉ đọc và môi trường production không? Hàm debug `bs.*` có cần che không (Java đã có debug chưa)?
- **Q7. Best practice BP001 đến BP006:** Python mặc định tắt theo cấu hình `syntax.best_practice`. Java có port không? Nếu có, thì không thêm ô Settings mới, nghĩa là cố định bật hoặc tắt. Bạn chọn gì?
- **Q8. Các phần chạm giao diện:** tô cột sai, Quick-fix, bảng Error List, nút Parse và XML/JSON cần giao diện hoặc CSS mới. Theo nguyên tắc tôi sẽ **không làm**. Riêng cảnh báo cột sai thì hiện bằng marker sẵn có của Monaco, phím Ctrl+F5 thì thêm không cần giao diện. Bạn đồng ý không?

**Về metadata:**

- **Q9. Nạp theo connection, đúng ý bạn là gì?** Bạn đề xuất "load theo connection", còn Python nạp lười theo schema. Bạn muốn:
    - (a) Nạp sẵn hết như hiện tại, tốn hơn nhưng phản hồi nhanh.
    - (b) Giống Python: chỉ gọi lần đầu cần, rồi nhớ đến khi có DDL hoặc Refresh.
    - (c) Nạp sẵn tên object, còn cột thì lười.
    - Đề xuất của tôi: quyết định sau khi có số đo Giai đoạn 0.
- **Q10. Máy chủ có database rất lớn** (hàng chục nghìn object) có phải trường hợp bạn dùng thường xuyên không? Điều này quyết định có cần giới hạn số lượng hay không.

---

## 10. Rủi ro

| Rủi ro | Mức | Cách giảm |
|---|---|---|
| WebKit của JavaFX chậm tự nhiên, không thể bằng Chromium dù logic giống hệt | Cao | Đo sớm ở Giai đoạn 0, báo bạn trước khi port tiếp |
| `semantic_analyzer.py` lớn, dựa nhiều vào regex; port cả khối dễ sai | Cao | Chia nhóm nhỏ, mỗi nhóm có corpus Python làm chuẩn |
| Không có ScriptDOM thì phát hiện lỗi kém hơn Python | Trung bình | Chờ Q4, ghi rõ khác biệt đã chấp nhận |
| Thêm nhiều lớp kiểm tra làm tăng độ trễ | Trung bình | Chạy nền, đo từng bước, có cache 5 giây như Python |
| Bản chạy khác `master` vì `master` có thay đổi chưa commit | Trung bình | Chỉ gộp khi bạn yêu cầu; nêu rõ các file chồng nhau khi đó |
