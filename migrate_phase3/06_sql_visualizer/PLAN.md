# Kế hoạch Thực thi: SQL Visualizer (Phase 3 - Slice 6)

> **Mục tiêu:** Xây dựng tính năng Trực quan hóa Luồng Truy vấn SQL (Query Flow Visualizer) dựa trên phân tích cú pháp AST, hiển thị sơ đồ luồng dữ liệu logic từ bảng nguồn qua các phép Join, Filter, Group By, Sắp xếp đến kết quả đầu ra; đồng bộ tương tác 2 chiều thời gian thực với Monaco Editor.

---

## 1. Tuân thủ Quy tắc & Token Thiết kế

### 1.1. Tuân thủ `04-component-rules.md` (Quy tắc Component)
- **Badge phân loại bước biến đổi:**
  - Nguồn dữ liệu (`Source / CTE`): Badge xanh lam `-color-accent-emphasis`.
  - Phép nối (`JOIN`): Badge tím `-color-purple-emphasis`.
  - Bộ lọc (`WHERE / HAVING`): Badge vàng cam `-color-warning-emphasis`.
  - Phép chiếu (`SELECT Projection`): Badge xanh ngọc `-color-info-emphasis`.
  - Phép gom nhóm & Sắp xếp (`GROUP BY / ORDER BY`): Badge chàm `-color-indigo-emphasis`.
  - Đầu ra (`Output Stream`): Badge xanh lá `-color-success-emphasis`.
- **Thanh công cụ Visualizer:**
  - Nút Đồng bộ 2 chiều (`fth-refresh-cw`, class `.toggle-button`).
  - Nút Hướng luồng: Trái-qua-Phải (LR) hoặc Trên-xuống-Dưới (TD).
  - Nút Zoom In, Zoom Out, Zoom Reset (`100%`).
  - Nút Xuất sơ đồ sang PNG (`fth-camera`).
- **Trạng thái Trống & Lỗi cú pháp:**
  - Dùng `EmptyState` khi câu truy vấn rỗng ("Nhập câu lệnh SELECT để trực quan hóa luồng dữ liệu...").
  - Khi SQL bị lỗi cú pháp chưa phân tích được: Hiển thị thanh thông báo lỗi cú pháp mỏng nhẹ dưới thanh công cụ mà không làm đơ giao diện hay xóa sơ đồ cũ.

### 1.2. Tuân thủ `02-design-principles.md` (Nguyên tắc Thiết kế Desktop)
- **Bố cục Song song (Side-by-side Split):**
  - Tích hợp trực tiếp vào `SqlEditorTab`: Nửa bên trái là Monaco Editor viết code SQL, nửa bên phải là `SqlVisualizerView` hiển thị sơ đồ luồng dữ liệu tương ứng.
  - Người dùng có thể ẩn/hiện hoặc kéo thanh chia độ rộng linh hoạt.
- **Hỗ trợ 2 chế độ `panelStyle`:** Các node logic trong sơ đồ vẽ dạng thẻ Card bo góc 6px (ở chế độ `rounded`) hoặc góc vuông phẳng (ở chế độ `flat`).
- **Đồng bộ tương tác 2 chiều mượt mà:**
  - Khi click vào một node trên sơ đồ -> Monaco editor lập tức bôi màu đoạn text SQL tương ứng và đưa con trỏ đến vị trí đó.
  - Khi di chuyển con trỏ chuột trong Monaco editor -> Node logic chứa vị trí con trỏ chuột trên sơ đồ tự động sáng viền nổi bật.
  - Khi người dùng gõ sửa SQL -> Sau 300ms dừng gõ (debounce), sơ đồ tự động cập nhật lại cấu trúc mới.

### 1.3. Tuân thủ `03-design-tokens.md` (Design Tokens)
- Tuyệt đối không dùng mã hex cố định:
  - Màu nền Visualizer Canvas: `-ide-editor-bg` / `-color-bg-default`.
  - Màu viền các node luồng: `-ide-border-color` / `-color-border-subtle`.
  - Màu đường nối dòng dữ liệu (Edges): `-color-accent-muted` (có hoạt ảnh dòng chảy chấm nhẹ nếu là luồng dữ liệu active).
  - Font nhãn node: `JetBrains Mono 12px` cho biểu thức SQL, `Inter / Segoe UI 11px` cho tiêu đề loại phép biến đổi.

---

## 2. Kiến trúc & Phân rã Nhiệm vụ

### Bước 1: Tích hợp JSqlParser & Xây dựng Domain Models
1. Khai báo dependency trong `core/core-application/pom.xml`:
   ```xml
   <dependency>
       <groupId>com.github.jsqlparser</groupId>
       <artifactId>jsqlparser</artifactId>
       <version>4.9</version>
   </dependency>
   ```
2. Tạo package `com.lltool.core.domain.visualizer.model`:
   - `SourceRange.java`: Lưu trữ `startLine`, `startCol`, `endLine`, `endCol`, `startOffset`, `endOffset`, và hàm `contains(line, col)`.
   - `QueryFlowNode.java`: id, loại node (`SOURCE_TABLE`, `CTE`, `SUBQUERY`, `JOIN`, `FILTER`, `GROUP_BY`, `HAVING`, `PROJECTION`, `ORDER_BY`, `OUTPUT`), label, chi tiết biểu thức, `sourceRange`.
   - `QueryFlowEdge.java`: fromNodeId, toNodeId, label.
   - `QueryFlowGraph.java`: danh sách nodes, edges, rootNodeId, sqlText.

### Bước 2: Hiện thực AST Analyzer (`QueryFlowAnalyzer`)
1. Tạo `com.lltool.core.application.visualizer.QueryFlowAnalyzer`:
   - Dùng `CCJSqlParserUtil.parse(sql)` để tạo AST.
   - Áp dụng Visitor Pattern (`SelectVisitor`, `FromItemVisitor`, `ExpressionVisitor`):
     1. Khai thác `WithItem` (CTEs) thành các sub-graph nguồn.
     2. Khai thác `FromItem` (Base tables, subqueries) thành các Source Nodes.
     3. Khai thác `Join` (INNER, LEFT, RIGHT, FULL) và điều kiện `ON` thành Join Nodes.
     4. Khai thác `Where` clause thành Filter Nodes.
     5. Khai thác `GroupBy` & `Having` thành Aggregation Nodes.
     6. Khai thác `SelectItem` thành Projection Nodes.
     7. Khai thác `OrderBy` thành Sorter Nodes.
     8. Tạo Output Node kết thúc luồng.
   - Tính toán `SourceRange` bằng cách xác định vị trí chuỗi con trong văn bản SQL gốc (sử dụng thuật toán `findSourceRange`).

### Bước 3: Hiện thực Giao diện Visualizer Canvas & Đồng bộ Monaco
1. Tạo `SqlVisualizerView.java`:
   - Bố cục trực quan hóa sơ đồ: Các node được bố trí tuần tự từ trái qua phải theo các cột giai đoạn:
     `[Sources] -> [Joins] -> [Filters] -> [Aggregations] -> [Projections] -> [Output]`
   - Vẽ đường nối Bezier mượt mà giữa các node.
   - Lắng nghe click chuột vào node: Phát ra sự kiện `onNodeSelected(SourceRange range)`.
2. Tạo JavaScript Bridge trên `MonacoEditor`:
   - Hàm `highlightRange(startLine, startCol, endLine, endCol)`: Sử dụng `monaco.editor.deltaDecorations` để tạo viền sáng quanh đoạn text SQL tương ứng.
   - Hàm `addCursorPositionListener()`: Gửi ngược tọa độ con trỏ `(line, col)` về Java khi người dùng di chuyển con trỏ trong editor.

### Bước 4: Tích hợp vào `SqlEditorTab`
1. Cập nhật `SqlEditorTab.java`:
   - Bổ sung nút "Query Flow Visualizer" (`fth-git-branch`) trên thanh công cụ truy vấn.
   - Khi bấm bật, mở một SplitPane bên phải chứa `SqlVisualizerView`.
   - Gắn debounce executor 300ms: Khi nội dung SQL thay đổi, tự động chạy `QueryFlowAnalyzer.analyzeAsync(sql)` trên background thread và cập nhật lại sơ đồ.

---

## 3. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] Phân tích chính xác cấu trúc luồng của các câu truy vấn SELECT từ đơn giản đến phức tạp (nhiều JOIN, CTE, Subquery, Group By, Order By).
- [ ] Không làm đơ giao diện khi gõ SQL: Quá trình phân tích cú pháp AST chạy hoàn toàn trên background thread có debounce 300ms.
- [ ] Biểu đồ trực quan hóa dữ liệu hiển thị rõ ràng các giai đoạn biến đổi dữ liệu từ trái qua phải hoặc từ trên xuống dưới.
- [ ] Bấm vào bất kỳ node nào trên biểu đồ luồng lập tức highlight chính xác đoạn code tương ứng trong Monaco Editor.
- [ ] Di chuyển con trỏ trong Monaco Editor tự động kích hoạt highlight node logic tương ứng trên biểu đồ luồng.
- [ ] Sơ đồ tự động nhận diện và cập nhật theo theme sáng / tối và chế độ `panelStyle` phẳng / bo góc của IDE.
- [ ] Đạt 100% test cases trong `QueryFlowAnalyzerTest`.

