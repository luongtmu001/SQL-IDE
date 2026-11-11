# Báo cáo tiến độ Backend Feature 06: SQL Visualizer

> Thời gian hoàn thành: 2026-11-11  
> Người thực hiện: Antigravity Assistant  
> Tài liệu gốc: `migrate_phase3/07_review_va_toi_uu/02_PLAN_BACKEND.md` (Mục 3.6 - SQL Visualizer)  
> Commit mã nguồn: `1378560` (`feat(visualizer): ast query pipeline analyzer, sequential join chaining, view header stripping, debouncing, and estimated plan metrics (SV-1, SV-2, SV-3, SV-4, SV-5, SV-6, SV-7)`)

---

## 1. Bảng trạng thái chi tiết từng Task ID (SV-1 -> SV-7)

| ID | Mức | Mô tả yêu cầu | Trạng thái | Chi tiết triển khai |
|---|---|---|---|---|
| **SV-1** | P0 | Xoá bỏ các số liệu giả lập bịa cứng (`89210`, `12450`, `32%`, `14%`...) | **HOÀN THÀNH** | Xoá toàn bộ các dòng gán cứng `rowCount` và `estimatedCost`. Mặc định các trường này mang giá trị `null` (UI sẽ ẩn badge metric khi null). |
| **SV-2** | P0 | Viết lại bộ phân tích pipeline 2 lớp: Tokenizer / Clause Splitter tại depth 0, xử lý CTE, subquery, HAVING, DISTINCT, TOP/LIMIT/OFFSET/FETCH, APPLY, comment/literal | **HOÀN THÀNH** | Triển khai phương pháp 2 lớp: duyệt token bỏ qua chú thích (`--`, `/* */`) và chuỗi nháy đơn (`'...'`), tách mệnh đề `SELECT`, `FROM`, `WHERE`, `GROUP BY`, `HAVING`, `ORDER BY`, `LIMIT` chỉ ở độ sâu ngoặc đơn = 0; trích xuất CTE `WITH ... AS (...)`; hỗ trợ subquery trong FROM (`(SELECT ...) AS sq`). Khi gặp câu lệnh không hỗ trợ hoặc không phải SELECT/VIEW/CTE, trả về `QueryFlowGraph.unsupported(...)` kèm lý do để hiển thị `EmptyState`. |
| **SV-3** | P0 | Chặn từ khoá biến thành alias (WHERE, INNER, JOIN...); nối chuỗi JOIN tuần tự; tạo ID node duy nhất cho self-join | **HOÀN THÀNH** | Thêm danh sách `RESERVED_KEYWORDS` trong `extractAlias()`. Nối tuần tự các toán tử JOIN (`currentStream` là luồng trái, bảng join là luồng phải, đầu ra toán tử join trở thành `currentStream` cho join tiếp theo). ID sinh tự động tăng dần (`source_1`, `join_src_2`, `join_op_2`...) tránh xung đột ID khi self-join. |
| **SV-4** | P1 | Tính `SourceRange` 1-based chính xác từ fullSql nguyên bản (không trim), xử lý cả CRLF (`\r\n`) và LF (`\n`) | **HOÀN THÀNH** | Viết hàm `computeSourceRange(fullSql, startOffset, endOffset)` đếm chính xác dòng và cột, phân biệt CRLF và LF; không trim chuỗi trước khi phân tích; đồng bộ vùng chọn và cursor vị trí chính xác với Monaco Editor. |
| **SV-5** | P1 | Bỏ `new Thread` và `Thread.sleep(120)`, thêm debounce gõ phím 400ms | **HOÀN THÀNH** | Thêm `typingDebounce` (400ms `PauseTransition`) gắn vào `monacoSql.setOnUserEdit(...)`. Phân tích trực tiếp (thời gian xử lý < 10ms đối với câu truy vấn thông thường). |
| **SV-6** | P2 | Nút "Kế hoạch ước lượng": kết nối `DesktopExecutionPlanService` để lấy số liệu thực | **HOÀN THÀNH** | Tích hợp nút "Kế hoạch ước lượng" (`btnEstimatedPlan`) gọi `DesktopExecutionPlanService.getInstance().getExecutionPlanAsync(...)`, ánh xạ operator node (Table Scan, Hash Join, Aggregate, Sort) từ plan vào các node trong `activeGraph` để điền `rowCount` và `costPercent`. |
| **SV-7** | P2 | Cắt bỏ phần đầu `CREATE [OR ALTER/REPLACE] [MATERIALIZED] VIEW [name] AS` trước khi phân tích | **HOÀN THÀNH** | Thêm regex pattern `VIEW_HEADER_PATTERN` phát hiện và bỏ phần khai báo header của View, điều chỉnh offset tương ứng. |

---

## 2. Kết quả kiểm thử Unit Test & Tích hợp

- Lệnh thực thi:
  ```powershell
  .\mvnw.cmd test "-Dtest=SqlVisualizerTabTest,QueryFlowAnalyzerTest"
  ```
- Kết quả: **28/28 tests PASS (100%)**
  - `QueryFlowAnalyzerTest`: 26 tests (SV-1, SV-2, SV-3, SV-4, SV-5, SV-7, subquery, CTE, self-join, comments, literals, quoted identifiers, distinct, limit/offset, having, apply, unsupported queries, performance < 100ms).
  - `SqlVisualizerTabTest`: 2 tests (Khởi tạo tab, giao diện trực quan hoá và Monaco synchronization).
- Kết quả kiểm tra chuỗi số giả:
  - `grep -n "12450\|89210\|\"32%\"" core/core-application/src/main/java/com/lltool/core/application/visualizer/QueryFlowAnalyzer.java` = 0 kết quả.

---

## 3. Khác biệt giữa Kế hoạch và Thực tế

1. **EmbeddedMonaco API:**
   - Thay vì `setOnTextChanged`, `EmbeddedMonaco` cung cấp phương thức `setOnUserEdit(Consumer<String>)` để lắng nghe thao tác gõ của người dùng mà không bị kích hoạt bởi các lệnh `setText` từ Java.
2. **EmptyState API:**
   - Sử dụng `emptyState.setMessage(String)` thay vì `emptyState.setDescription(String)`.
3. **Execution Plan Async Callback:**
   - `DesktopExecutionPlanService.getInstance().getExecutionPlanAsync` nhận các tham số `(connectionName, database, schema, sql, planType, callback)` và yêu cầu callback `onStart(String planType, String dialect)`. Mã đã được cập nhật chuẩn theo phương thức này.
