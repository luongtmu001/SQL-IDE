# Khảo sát & Đánh giá Tính năng: SQL Visualizer (Query Flow AST)

---

## 1. Khảo sát mã nguồn Python gốc

### 1.1. Các file và vị trí trong Python repository
- **Backend Service:** `app/services/query_flow_service.py` (46,412 bytes)
  - Class / Modules: `SourceRange`, `find_source_range`, `QueryFlowService`
  - Các hàm cốt lõi:
    - `analyze_query_flow(sql, dialect)`: Sử dụng thư viện phân tích cú pháp AST (`sqlglot`) để bóc tách câu truy vấn phức tạp:
      - Các nguồn dữ liệu: Base tables, Views, CTE (`WITH` clause), Subqueries lồng nhau.
      - Các phép biến đổi: Điều kiện lọc `WHERE`, liên kết bảng `JOIN` (INNER, LEFT, RIGHT, FULL, CROSS), phép gom nhóm `GROUP BY` & `HAVING`.
      - Các phép chiếu: Danh sách cột `SELECT` và bí danh `AS alias`.
      - Sắp xếp: `ORDER BY`.
      - Phép hợp: `UNION`, `UNION ALL`, `INTERSECT`, `EXCEPT`.
    - `SourceRange`: Tính toán tọa độ vị trí chuẩn xác 1-based (`start_line`, `start_column`, `end_line`, `end_column`, `start_offset`, `end_offset`) cho từng thành phần cú pháp trong văn bản SQL gốc.
- **Frontend Modules:** Thư mục `static/js/visualization/`
  - `sql-parser.js` (31,141 bytes): Trình phân tích token SQL phía client.
  - `query-model.js` (21,496 bytes): Model cấu trúc dữ liệu luồng truy vấn.
  - `query-flow-analyzer.js` (40,269 bytes): Xử lý phân cấp cây AST và quan hệ cha con giữa các node luồng.
  - `query-flow-graph.js` (15,710 bytes): Định dạng node, edge và metadata.
  - `d3-renderer.js` (107,483 bytes): Render biểu đồ luồng dữ liệu (Data-Flow Diagram) bằng D3.js / SVG với bố cục trực quan từ trái qua phải (Sources -> Joins -> Filters -> Groups -> Projections -> Output).
  - `sql-visualizer.js` (106,969 bytes): Điều phối đồng bộ 2 chiều (Bidirectional Synchronization) với Monaco Editor:
    - Bấm vào một node trên biểu đồ -> Monaco editor lập tức bôi màu và cuộn tới đoạn code tương ứng.
    - Đặt con trỏ chuột trong Monaco editor -> Biểu đồ tự động highlight node logic tương ứng.
    - Gõ sửa SQL -> Tự động phân tích lại (debounced 300ms) và vẽ lại biểu đồ luồng.
- **Test suite Python:**
  - `tests/test_query_flow_visualization.py` (50,044 bytes), `tests/test_sql_visualization.py` (17,955 bytes).

---

## 2. Khảo sát hiện trạng mã nguồn Java

Hiện tại tính năng SQL Visualizer **chưa được triển khai trong Java**.
- Trong `pom.xml` hiện tại chưa có thư viện phân tích cú pháp SQL AST hoàn chỉnh (như JSqlParser).
- Tuy nhiên:
  - Dự án Java đã có sẵn `MonacoEditor` hoàn chỉnh với WebView và JavaScript bridge.
  - Các khái niệm SplitView và Tab đã được chuẩn hóa trong `SqlEditorTab`.

---

## 3. Chi tiết danh sách File cần Sửa & File Thêm Mới

### 3.1. Các file cần sửa (Modified Files)

| File Path | Class | Phương thức cần sửa/thêm | Lý do & Nội dung sửa |
|---|---|---|---|
| `core/core-application/pom.xml` | Maven POM | Thêm dependency `com.github.jsqlparser:jsqlparser` | Bổ sung thư viện phân tích cú pháp SQL AST chuẩn công nghiệp của hệ sinh thái Java. |
| `src/main/java/app/ui/editor/SqlEditorTab.java` | `SqlEditorTab` | - `toggleVisualizerPane()`<br>- `onCursorPositionChanged(int line, int col)` | Hỗ trợ mở SplitPane chia đôi màn hình giữa Monaco Editor và SQL Visualizer, gửi sự kiện vị trí con trỏ chuột sang Visualizer. |
| `src/main/java/app/controllers/EditorController.java` | `EditorController` | Nút công cụ Visualizer trên toolbar tab | Thêm nút bấm icon `fth-git-branch` trên thanh công cụ truy vấn để bật/tắt cửa sổ trực quan hóa luồng dữ liệu. |

### 3.2. Các file thêm mới (New Files)

| File Path | Class / Resource | Trách nhiệm & Phương thức chính |
|---|---|---|
| `core/core-domain/src/main/java/com/lltool/core/domain/visualizer/model/SourceRange.java` | `SourceRange` | Tọa độ văn bản 1-based: `startLine`, `startCol`, `endLine`, `endCol`, `startOffset`, `endOffset`, phương thức kiểm tra `contains(line, col)`. |
| `core/core-domain/src/main/java/com/lltool/core/domain/visualizer/model/QueryFlowNode.java` | `QueryFlowNode` | Đại diện node luồng: `id`, `type` (`SOURCE_TABLE`, `CTE`, `SUBQUERY`, `JOIN`, `FILTER`, `GROUP_BY`, `HAVING`, `PROJECTION`, `ORDER_BY`, `OUTPUT`), `label`, `details`, `sourceRange`. |
| `core/core-domain/src/main/java/com/lltool/core/domain/visualizer/model/QueryFlowEdge.java` | `QueryFlowEdge` | Cạnh kết nối: `fromNodeId`, `toNodeId`, `label`, `cardinality`. |
| `core/core-domain/src/main/java/com/lltool/core/domain/visualizer/model/QueryFlowGraph.java` | `QueryFlowGraph` | Toàn bộ đồ thị luồng: danh sách nodes, edges, querySql, rootNodeId. |
| `core/core-application/src/main/java/com/lltool/core/application/visualizer/QueryFlowAnalyzer.java` | `QueryFlowAnalyzer` | Service phân tích SQL: Dùng JSqlParser duyệt AST (Visitor Pattern) bóc tách các mệnh đề SELECT, Table, Join, Where, GroupBy, OrderBy, tính toán `SourceRange` và dựng `QueryFlowGraph`. |
| `src/main/java/app/ui/visualizer/SqlVisualizerView.java` | `SqlVisualizerView` | Component giao diện hiển thị biểu đồ luồng dữ liệu (bằng JavaFX Canvas hoặc WebView SVG), lắng nghe tương tác chuột để đồng bộ vùng chọn sang Monaco. |
| `src/main/java/app/ui/visualizer/SqlVisualizerTab.java` | `SqlVisualizerTab` | Tab độc lập xem trực quan hóa câu truy vấn lớn hoặc làm việc song song. |
| `src/main/resources/css/sql-visualizer.css` | CSS Stylesheet | Token giao diện theo `-ide-*` và `-color-*` cho node các giai đoạn (Sources màu xanh, Joins màu tím, Filters màu vàng cam, Projections màu xanh lá). |
| `src/test/java/app/ui/visualizer/QueryFlowAnalyzerTest.java` | Unit Test | Test phân tích câu lệnh SELECT đơn giản, câu lệnh nhiều JOIN, CTE lồng nhau, UNION, và kiểm tra tọa độ SourceRange. |

---

## 4. Thư viện & Phụ thuộc
- Thêm dependency `com.github.jsqlparser:jsqlparser:4.9` vào `core-application/pom.xml`. Đây là thư viện mã nguồn mở tiêu chuẩn, không phụ thuộc UI, xử lý cực kỳ tốt các dialect ANSI SQL, PostgreSQL, SQL Server, Oracle và SQLite.

