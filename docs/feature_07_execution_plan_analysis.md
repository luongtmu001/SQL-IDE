# Feature Analysis: Feature 07 - Execution Plan (Kế hoạch thực thi truy vấn)

## 1. Feature Overview
Feature 7 cung cấp chức năng phân tích và hiển thị Kế hoạch thực thi truy vấn (Execution Plan / Query Plan) cho các hệ quản trị cơ sở dữ liệu phổ biến (PostgreSQL, SQL Server, SQLite):
- **Phân loại Plan**:
  - **Estimated Execution Plan (Kế hoạch ước tính - Ctrl+L)**: Sinh kế hoạch dựa trên bộ tối ưu hóa (Optimizer) và thống kê dữ liệu mà KHÔNG thực thi câu lệnh SQL (an toàn tuyệt đối, zero risk).
  - **Actual Execution Plan (Kế hoạch thực tế - Ctrl+M)**: Thực thi câu lệnh SQL để thu thập số đo runtime thực tế (số dòng thực tế trả về, thời gian thực thi của từng toán tử, số lượt lặp loops, bộ đệm buffer I/O).
- **Mô hình chuẩn hóa (Normalized Physical Plan Model)**:
  - Chuẩn hóa cây toán tử (`PlanOperatorNode`) từ các định dạng đặc thù của từng DBMS (XML ShowPlan của SQL Server, JSON của PostgreSQL, Tabular của SQLite).
  - Tính toán chi phí phần trăm (`cost_percent`), cấp độ nghiêm trọng của chi phí (`cost_severity`: High $\ge 30\%$, Medium $\ge 10\%$, Low $< 10\%$), tổng chi phí cây con (`subtree_cost`), chi phí riêng của toán tử (`operator_cost`).
  - So sánh độ lệch giữa số dòng ước tính (`estimated_rows`) và số dòng thực tế (`actual_rows`) để phát hiện thống kê lỗi thời (stale statistics).
- **Giao diện hiển thị trực quan (Visual & Raw Plan)**:
  - **Visual Plan Tree**: Cây toán tử trực quan với phân cấp cha-con, thanh tiến trình màu sắc chi phí (đỏ/cam cho High, vàng cho Medium, xanh cho Low), icon đặc trưng cho từng loại toán tử (Scan, Seek, Hash Join, Nested Loops, Sort, Aggregate, Filter).
  - **Operator Inspector (Chi tiết toán tử)**: Hiển thị thuộc tính chi tiết khi click vào từng node (Physical Op, Logical Op, Bảng/Schema, Cost, Rows, Execution Time, Warnings, Output columns).
  - **Raw Text / Source Plan View**: Xem kế hoạch dạng văn bản gốc do engine DB trả về.
  - **Thanh tóm tắt (Plan Summary)**: Dialect, Plan Type (Estimated / Actual), Total Cost, Operators count, Duration.

---

## 2. Python Source Reference
- `app/services/query_plan_service.py` (668 dòng):
  - Lớp `PlanOperatorNode`: node_id, operator_type, physical_op, logical_op, relation_name, schema, database, estimated_rows, actual_rows, subtree_cost, operator_cost, cost_percent, cost_severity, actual_time_ms, warnings, output_columns, details, children.
  - Lớp `PhysicalExecutionPlan`: dialect, plan_type, total_cost, root_node, nodes, edges, `to_dict()`.
  - Hàm `get_execution_plan(connection_session, sql, plan_type, database, schema, dialect)`.
  - Hàm `_fetch_sqlserver_plan`: sử dụng `SET SHOWPLAN_XML ON / OFF` cho estimated plan, `SET STATISTICS XML ON / OFF` cho actual plan; hàm `parse_sqlserver_xml_plan` bóc tách XML ShowPlan.
  - Hàm `_fetch_postgres_plan`: `EXPLAIN (FORMAT JSON, COSTS, VERBOSE)` cho estimated, `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` cho actual; hàm `parse_postgres_json_plan` bóc tách JSON plan.
  - Hàm `_fetch_sqlite_plan`: `EXPLAIN QUERY PLAN <sql>`; hàm `parse_sqlite_plan` xây dựng cây toán tử từ các dòng `(id, parent, notused, detail)`.
  - Hàm `_generate_mock_plan`: sinh cây kế hoạch giả lập thông minh dựa trên từ khóa SQL (JOIN, GROUP BY, ORDER BY, FROM...) để phục vụ kiểm thử và chạy offline.
- `app/api/query_api.py` (dòng 162-197 & 309-330):
  - `explain_query(connection_id, sql, database)`: thực hiện `EXPLAIN (FORMAT TEXT)` cho Postgres hoặc `SET SHOWPLAN_TEXT ON/OFF` cho SQL Server, luôn bảo đảm tắt `SET SHOWPLAN_TEXT OFF` trong khối `finally`.
  - `get_query_plan(...)`: trả về kết quả chuẩn hóa dạng dictionary JSON.
- `static/js/query.js` (dòng 2849-2895 & 3507-3515):
  - Hàm `requestPlan()`: lấy SQL selection hoặc toàn bộ script trong editor, gọi API, hiển thị thông báo "Generating execution plan...", mở tab Plan (`ide-results-plan-view`).
- `templates/partials/results.html` (dòng 12-14 & 73-77):
  - Nút tab `Plan` (`#ide-rtab-plan`, icon `fa-diagram-project`).
  - Khung nội dung plan `#ide-results-plan-view` và thẻ `<pre id="ide-results-plan">`.
- `tests/test_phase3_bug2_explain_query.py`:
  - Đảm bảo `SET SHOWPLAN_TEXT OFF` luôn được thực thi trong khối `finally` ngay cả khi có lỗi SQLException.

---

## 3. UI and Layout Analysis
1. **Results Tab Bar**:
   - Tab 4: **`Execution Plan`** (`executionPlanTab`, icon `fth-git-commit`).
2. **Execution Plan Action Bar (Thanh công cụ Plan)**:
   - Nút **Estimated Plan** (`fth-file-text`, phím tắt `Ctrl+L`): Lấy kế hoạch ước tính an toàn không chạy câu lệnh.
   - Nút **Actual Plan** (`fth-play`, phím tắt `Ctrl+M`): Chạy câu lệnh lấy kế hoạch thực tế (kèm cảnh báo nếu có DML).
   - Nút **Toggle View** (`fth-list` / `fth-align-left`): Chuyển đổi giữa chế độ Cây trực quan (Visual Tree) và Văn bản gốc (Raw Text).
   - Nút **Copy Plan** (`fth-copy`): Sao chép văn bản kế hoạch hoặc JSON metrics vào clipboard.
   - Các badge tóm tắt:
     - Dialect badge: `SQL Server` / `PostgreSQL` / `SQLite`.
     - Plan Type badge: `ESTIMATED` (xanh nhạt) / `ACTUAL` (vàng hổ phách).
     - Total Cost: e.g. `Cost: 4.258`.
     - Operator count: e.g. `3 operators`.
3. **Execution Plan Center Content**:
   - **SplitPane (Visual View)**:
     - Bên trái: `TreeView` / `TreeTableView` phân cấp hiển thị các toán tử thực thi. Mỗi toán tử hiển thị:
       - Icon đại diện (Scan, Seek, Hash Join, Sort, Aggregate, Compute Scalar...).
       - Tên toán tử (`Hash Match`, `Clustered Index Scan`, `Seq Scan`...).
       - Tên bảng / relation đích.
       - Thanh tiến trình mini hiển thị `% Cost` kèm màu cảnh báo (đỏ nếu $\ge 30\%$, cam nếu $\ge 10\%$, xanh nếu $< 10\%$).
       - Số dòng: `Est: 1,200` (Estimated) hoặc `Act: 1,200 / Est: 1,200` (Actual).
     - Bên phải: `Operator Inspector Panel` hiển thị bảng thuộc tính chi tiết của toán tử đang chọn:
       - Physical Operation / Logical Operation.
       - Schema & Database.
       - Subtree Cost / Operator Cost.
       - Estimated Rows / Actual Rows / Estimate vs Actual Difference.
       - Execution Time (ms) / Number of Executions (Loops).
       - Warnings (nếu có: ví dụ thiếu chỉ mục, chuyển đổi kiểu ngầm định).
       - Output Columns / Predicates.
   - **Raw Text View**:
     - `TextArea` font đơn cách (Monospace), hỗ trợ Word-Wrap và sao chép.
   - **Empty State**:
     - Hiển thị hướng dẫn bấm `Ctrl+L` (Estimated Plan) hoặc `Ctrl+M` (Actual Plan) khi chưa có kế hoạch nào được tải.

---

## 4. Database Engine Integration Logic
1. **PostgreSQL**:
   - Estimated:
     ```sql
     EXPLAIN (FORMAT JSON, COSTS, VERBOSE) <sql>
     ```
   - Actual:
     ```sql
     EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) <sql>
     ```
   - Trích xuất JSON mảng `[{ "Plan": { "Node Type": "Hash Join", ... } }]`.
2. **SQL Server**:
   - Estimated:
     ```sql
     SET SHOWPLAN_XML ON;
     -- execute sql
     SET SHOWPLAN_XML OFF;
     ```
   - Actual:
     ```sql
     SET STATISTICS XML ON;
     -- execute sql
     SET STATISTICS XML OFF;
     ```
   - Bắt buộc luôn đặt `SET SHOWPLAN_XML OFF;` / `SET STATISTICS XML OFF;` trong khối `finally` qua JDBC connection lock.
   - Trích xuất XML `<ShowPlanXML>` và phân tích thẻ `<RelOp NodeId="..." PhysicalOp="..." LogicalOp="..." EstimatedTotalSubtreeCost="...">`.
3. **SQLite**:
   - Query:
     ```sql
     EXPLAIN QUERY PLAN <sql>
     ```
   - Trích xuất các dòng `(id, parent, notused, detail)` và dựng cây quan hệ cha-con.
4. **Offline / Simulated Fallback**:
   - Nếu không có kết nối cơ sở dữ liệu hoặc trong môi trường test: tự động phân tích cú pháp SQL (tìm các mệnh đề `JOIN`, `GROUP BY`, `ORDER BY`, `FROM`) và sinh cây kế hoạch chuẩn hóa mô phỏng chân thực.

---

## 5. Scope Definition
- **In Scope**:
  - Triển khai Data Model: `PlanOperatorNode`, `PhysicalExecutionPlan`.
  - Triển khai Service: `DesktopExecutionPlanService` xử lý bất đồng bộ (Java 21 Virtual Threads) cho PostgreSQL, SQL Server, SQLite và Mock Fallback.
  - Bộ phân tích cú pháp (Parser):
    - PostgreSQL EXPLAIN JSON Parser.
    - SQL Server ShowPlan XML Parser.
    - SQLite EXPLAIN QUERY PLAN Parser.
  - Giao diện người dùng:
    - Cập nhật `results.fxml` với Tab `Execution Plan` hoàn chỉnh (Action bar, Visual Tree, Operator Inspector, Raw Text Area).
    - Thêm nút `Plan` trên thanh công cụ chính và menu `Tools -> Display Estimated Execution Plan (Ctrl+L)` / `Include Actual Execution Plan (Ctrl+M)`.
    - Kết nối sự kiện phím tắt `Ctrl+L` và `Ctrl+M`.
  - Unit tests bao phủ toàn diện 100% các parser và service.
- **Out of Scope (để dành cho Phase 3 - Advanced Workspaces)**:
  - Visual Query Designer kéo thả biểu đồ (Query Flow Visualization full canvas D3).

---

## 6. Acceptance Criteria
- [ ] User can request Estimated Execution Plan (`Ctrl+L` / nút Est Plan) mà không thực thi SQL.
- [ ] User can request Actual Execution Plan (`Ctrl+M` / nút Act Plan) để đo thông số thời gian và số dòng thực tế.
- [ ] Model chuẩn hóa hỗ trợ đầy đủ PostgreSQL, SQL Server, SQLite và Fallback offline.
- [ ] Trực quan hóa cây toán tử với phân cấp cha con, icon tương ứng, và thanh chi phí `% Cost` có màu cảnh báo trực quan.
- [ ] Bấm vào từng toán tử hiển thị chi tiết đầy đủ trong Inspector panel.
- [ ] Hỗ trợ xem văn bản thô (Raw Plan) và sao chép kế hoạch vào Clipboard.
- [ ] Luôn bảo đảm `SET SHOWPLAN_XML OFF` hoặc tương đương được dọn dẹp an toàn trong khối `finally`.
- [ ] Tất cả unit tests và regression suite đều PASS 100%.

