# PHASE 00 -- INTELLISENSE PERFORMANCE AUDIT

**Project:** LLTool\
**Phase:** 00\
**Type:** Analysis Only\
**Priority:** Critical\
**Status:** Planning

------------------------------------------------------------------------

## 1. OBJECTIVE -- MỤC TIÊU

Phân tích toàn bộ cơ chế tải Database Objects và Code Completion hiện
tại của LLTool.

Mục tiêu:

1.  Xác định chính xác nguyên nhân IntelliSense phản hồi chậm.
2.  Xác định cách LLTool đang lấy metadata từ SQL Server và PostgreSQL.
3.  Kiểm tra cơ chế cache metadata hiện tại.
4.  Phân tích luồng xử lý từ CodeMirror đến Python Backend và Database.
5.  Đánh giá khả năng áp dụng kiến trúc Introspection tương tự DataGrip.
6.  Đưa ra phương án tối ưu mà không phá vỡ logic hiện tại.

**IMPORTANT:** Phase này chỉ phân tích. Không triển khai thay đổi logic.

------------------------------------------------------------------------

## 2. STRICT RULES -- QUY TẮC BẮT BUỘC

Agent MUST:

-   Đọc và hiểu kiến trúc hiện tại trước khi phân tích.
-   Truy tìm code thực tế, không suy đoán dựa trên tên hàm.
-   Xác định chính xác các API và SQL query đang được sử dụng.
-   Đo thời gian xử lý tại từng tầng nếu có thể.
-   Phân biệt thời gian xử lý Backend, Database và Frontend.
-   Ghi nhận các vấn đề về concurrency và request duplication.
-   Giữ nguyên toàn bộ logic hiện tại.

Agent MUST NOT:

-   Sửa code.
-   Xóa module.
-   Thay đổi API.
-   Thay đổi SQL query.
-   Thay đổi giao diện.
-   Thay đổi CodeMirror.
-   Thay đổi SQL Resolver.
-   Thêm thư viện mới.
-   Tự ý triển khai cache mới.
-   Refactor các module không liên quan.

Nếu cần thêm logging để đo hiệu năng, chỉ được đề xuất. Không tự ý triển
khai trong Phase này.

------------------------------------------------------------------------

## 3. AUDIT SCOPE -- PHẠM VI PHÂN TÍCH

### 3.1. Metadata Loading

Kiểm tra cách LLTool tải:

-   Database.
-   Schema.
-   Tables.
-   Views.
-   Columns.
-   Functions.
-   Procedures.
-   Parameters.
-   Triggers.
-   Indexes.
-   Constraints.
-   Object definitions.

Đối với từng loại object, xác định:

  Information       Description
  ----------------- -----------------------------------------
  Loading trigger   Khi nào metadata được tải?
  Source            Lấy từ system catalog hay API nào?
  SQL query         Query thực tế được sử dụng
  Scope             Database, schema hay toàn bộ connection
  Cache             Có cache hay không?
  Refresh           Khi nào metadata được tải lại?
  Latency           Thời gian tải
  Dependencies      Module nào sử dụng metadata?

Phải phân biệt metadata được tải phục vụ Database Explorer và metadata
phục vụ IntelliSense.

### 3.2. IntelliSense Execution Flow

Truy tìm toàn bộ luồng:

``` text
User Typing
    |
    v
CodeMirror Event
    |
    v
Completion Trigger
    |
    v
Frontend Completion Provider
    |
    v
API Request (if any)
    |
    v
Python Backend
    |
    v
Metadata Retrieval
    |
    v
SQL Resolver
    |
    v
Candidate Filtering
    |
    v
Completion Response
    |
    v
CodeMirror Suggestions
```

Không mặc định luồng trên là chính xác. Agent phải xây dựng lại theo
source code thực tế.

### 3.3. Database Query Audit

Kiểm tra:

-   Có query metadata mỗi lần người dùng gõ không?
-   Có query riêng cho từng object không?
-   Có N+1 query problem không?
-   Có query lặp lại cùng một metadata không?
-   Có sử dụng system catalog hiệu quả không?
-   Có tải toàn bộ metadata không cần thiết không?
-   Có sử dụng connection pool không?
-   Có chạy metadata query trên cùng session với query người dùng không?
-   Có xử lý timeout không?

Phân tích riêng:

**SQL Server**

Kiểm tra các nguồn metadata đang được sử dụng, chẳng hạn:

-   `sys.objects`
-   `sys.tables`
-   `sys.views`
-   `sys.columns`
-   `sys.schemas`
-   `sys.types`
-   `sys.parameters`
-   `sys.procedures`
-   `sys.sql_modules`

**PostgreSQL**

Kiểm tra:

-   `pg_class`
-   `pg_namespace`
-   `pg_attribute`
-   `pg_type`
-   `pg_proc`
-   `pg_constraint`
-   `information_schema`

Không yêu cầu thay đổi SQL query trong Phase này.

### 3.4. Cache Audit

Kiểm tra:

-   Cache hiện tại nằm ở đâu?
-   Memory cache hay persistent cache?
-   Cache có phân biệt connection không?
-   Cache có phân biệt database không?
-   Cache có phân biệt schema không?
-   Có cache columns không?
-   Có cache object definitions không?
-   Có TTL không?
-   Có invalidation không?
-   Có xảy ra stale metadata không?
-   Có tải lại toàn bộ metadata khi chuyển tab không?

Đánh giá khả năng tái sử dụng metadata giữa các SQL Editor sessions cùng
connection.

### 3.5. SQL Resolver Audit

Kiểm tra khả năng xử lý:

-   Table resolution.
-   Schema resolution.
-   Alias resolution.
-   Column resolution.
-   JOIN.
-   Multiple JOIN.
-   CTE.
-   Subquery.
-   Nested query.
-   Temporary tables.
-   Variables.
-   Multiple database contexts.

Đặc biệt kiểm tra:

``` sql
SELECT *
FROM dbo.Customers c
JOIN dbo.Orders o
    ON c.CustomerID = o.CustomerID
WHERE c.
```

Resolver có lấy đúng column của Customers không?

Kiểm tra Resolver có phân tích lại toàn bộ SQL document mỗi lần người
dùng gõ hay không.

### 3.6. Frontend Audit

Kiểm tra:

-   Completion Provider.
-   Debounce.
-   Request cancellation.
-   Duplicate requests.
-   Response ordering.
-   Stale response.
-   Candidate filtering.
-   Suggestion ranking.
-   Rendering performance.
-   Số lượng completion candidates.

Kiểm tra CodeMirror có đang xử lý quá nhiều dữ liệu không cần thiết
không.

------------------------------------------------------------------------

## 4. PERFORMANCE MEASUREMENT

Nếu có thể đo được bằng cơ chế hiện tại, thu thập:

  Metric   Description
  -------- -------------------------
  T1       Completion trigger
  T2       Frontend processing
  T3       API request
  T4       Backend processing
  T5       Database metadata query
  T6       SQL Resolver
  T7       Candidate filtering
  T8       Response rendering

Tính:

``` text
Total Completion Latency = T8 - T1
```

Đo riêng các trường hợp:

1.  Lần đầu mở connection.
2.  Lần đầu mở SQL Editor.
3.  Gợi ý table.
4.  Gợi ý column.
5.  Gợi ý alias.
6.  Gợi ý sau JOIN.
7.  Gợi ý khi cache đã có.
8.  Gợi ý khi cache chưa có.

Không tự đặt số liệu nếu không thể đo.

Ghi rõ phần nào là measured và phần nào là estimated.

------------------------------------------------------------------------

## 5. BOTTLENECK CLASSIFICATION

Phân loại nguyên nhân:

  Code       Category
  ---------- -----------------------------------
  META-001   Metadata loading quá chậm
  META-002   Metadata query quá nhiều
  META-003   Thiếu metadata cache
  META-004   Cache invalidation không hiệu quả
  RES-001    SQL Resolver xử lý chậm
  RES-002    Phân tích lại toàn bộ SQL
  FE-001     Frontend completion xử lý chậm
  FE-002     Request duplication
  FE-003     Rendering quá nhiều candidates
  API-001    Backend API latency
  CON-001    Connection/session contention

Mỗi vấn đề phải có:

-   Mô tả.
-   File/module liên quan.
-   Function liên quan.
-   Bằng chứng từ source code.
-   Mức độ ảnh hưởng.
-   Cách tái hiện.
-   Đề xuất xử lý.

------------------------------------------------------------------------

## 6. EXPECTED DELIVERABLES

Agent phải tạo:

### 6.1. `INTELLISENSE_CURRENT_ARCHITECTURE.md`

Mô tả kiến trúc hiện tại.

Bao gồm:

-   Module map.
-   Metadata loading flow.
-   Completion flow.
-   API dependencies.
-   Database query dependencies.
-   Cache architecture.
-   SQL Resolver architecture.

### 6.2. `INTELLISENSE_PERFORMANCE_REPORT.md`

Báo cáo:

-   Performance measurements.
-   Bottleneck classification.
-   Slowest operations.
-   Duplicate operations.
-   Missing cache.
-   Database query inefficiencies.

### 6.3. `INTELLISENSE_OPTIMIZATION_PROPOSAL.md`

Đề xuất kiến trúc mới.

So sánh:

  Current                    Proposed
  -------------------------- ---------------------------
  Current metadata loading   Introspection Engine
  Current cache              Layered Metadata Cache
  Current resolver           Metadata-aware Resolver
  Current completion         Local-first Completion
  Current refresh            Incremental Smart Refresh

Không triển khai trong Phase này.

### 6.4. `IMPLEMENTATION_ROADMAP.md`

Đưa ra thứ tự triển khai các Phase tiếp theo.

Phải chỉ ra:

-   Module cần giữ.
-   Module cần mở rộng.
-   Module cần thay thế (nếu thực sự cần).
-   Module mới.
-   Dependencies.
-   Risks.
-   Regression tests.

------------------------------------------------------------------------

## 7. ACCEPTANCE CRITERIA

Phase 00 chỉ được xem là hoàn thành khi:

-   [ ] Đã xác định được luồng metadata loading.
-   [ ] Đã xác định được luồng IntelliSense.
-   [ ] Đã xác định các API liên quan.
-   [ ] Đã xác định SQL query lấy metadata.
-   [ ] Đã đánh giá cache hiện tại.
-   [ ] Đã phân tích SQL Resolver.
-   [ ] Đã phân tích Frontend Completion Provider.
-   [ ] Đã phân loại bottleneck.
-   [ ] Có báo cáo hiệu năng.
-   [ ] Có phương án tối ưu.
-   [ ] Không có source code logic nào bị thay đổi.

**FINAL RULE:**

Không được chuyển sang Phase 01 nếu chưa xác định được nguyên nhân chính
khiến IntelliSense chậm.

Báo cáo phải dựa trên source code thực tế của LLTool, không dựa trên giả
định về kiến trúc.
