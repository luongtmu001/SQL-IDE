# Khảo sát & Đánh giá Tính năng: Dependency Graph (Biểu đồ Quan hệ Phụ thuộc Đối tượng)

---

## 1. Khảo sát mã nguồn Python gốc

### 1.1. Các file và vị trí trong Python repository
- **Backend Services:** Thư mục `app/services/postgres_dependency/` (và `bravo_relationship_service.py`)
  - `models.py` (17,444 bytes): Định nghĩa các model đồ thị:
    - `DependencyNode`: id, name, schema, type (`TABLE`, `VIEW`, `MATVIEW`, `FUNCTION`, `PROCEDURE`, `TRIGGER`, `COLUMN`), status, metadata.
    - `DependencyEdge`: source, target, type (`NORMAL`, `AUTO`, `INTERNAL`, `EXTENSION`, `FOREIGN_KEY`).
    - `DependencyGraph`: Danh sách nodes, edges, root_node, direction, depth.
    - `SuggestedStep`: Bước đề xuất để thay đổi/xóa an toàn (action, object, description, sql).
  - `catalog_analyzer.py` (69,636 bytes):
    - Khai thác bảng hệ thống PostgreSQL (`pg_depend`, `pg_rewrite`, `pg_class`, `pg_attribute`, `pg_proc`, `pg_trigger`) để trích xuất quan hệ phụ thuộc giữa các đối tượng và phụ thuộc chi tiết tới từng cột (Column-level dependency).
  - `source_analyzer.py` (20,008 bytes):
    - Phân tích cú pháp DDL/SQL để trích xuất tên cột và bảng được tham chiếu trong thân của Views và Functions.
  - `service.py` (34,919 bytes):
    - `PostgresDependencyService`: Xây dựng đồ thị phụ thuộc theo hướng (Dependents: các đối tượng phụ thuộc vào nó; Dependencies: các đối tượng mà nó cần) và độ sâu (Depth 1-5).
    - `get_suggested_steps(schema, table, column, action)`: Tính toán các bước xử lý phụ thuộc tuần tự khi muốn DROP hoặc ALTER một cột (Ví dụ: 1. Drop View phụ thuộc -> 2. Alter cột -> 3. Recreate lại View).
  - `app/services/bravo_relationship_service.py` (10,560 bytes): Khai thác quan hệ Foreign Key giữa các bảng trong hệ thống Bravo.
- **Backend REST Routes:** `app/routes/postgres_dependency.py` (251 lines)
  - Endpoints:
    - `/<connection_id>/column`: Phụ thuộc của cột bảng.
    - `/<connection_id>/object`: Phụ thuộc của đối tượng (View, Function, Table).
    - `/<connection_id>/definition`: Lấy DDL của node được chọn.
    - `/<connection_id>/suggested-steps`: Lấy danh sách bước xử lý an toàn.
- **Frontend Modules:** Thư mục `static/js/postgresql/dependency/`
  - `dependency-api.js`: Gọi API backend.
  - `dependency-controller.js` (46,560 bytes): Điều phối toàn bộ vòng đời tương tác.
  - `dependency-graph.js` (21,084 bytes): Render đồ thị tương tác bằng Canvas / D3 / SVG (kéo thả node, phóng to/thu nhỏ, pan).
  - `dependency-filter.js`: Lọc theo loại đối tượng, độ sâu depth, hướng duyệt.
  - `dependency-panel.js`: Panel bên phải chứa 3 tab: Thuộc tính Node (Properties), Mã nguồn DDL (Definition), Các bước đề xuất (Suggested Steps).
- **Test suite Python:**
  - `tests/test_postgres_dependency_ipc.py`, `test_postgres_dependency_models.py`, `test_postgres_dependency_routes.py`, `test_postgres_dependency_service.py`, `test_bravo_relationship_*.py`.

---

## 2. Khảo sát hiện trạng mã nguồn Java

Hiện tại tính năng Dependency Graph **chưa được triển khai trong Java**.
- Tuy nhiên:
  - Mô hình đồ thị (DAG) đã có kinh nghiệm triển khai trong phần `ExecutionPlan` (Tree/DAG của Query Execution Plan).
  - Giao diện tab có thể kế thừa trực tiếp từ `SqlEditorTab`.
  - Panel xem DDL có thể tái sử dụng `EmbeddedMonaco`.
  - Hỗ trợ mở rộng: Cần hỗ trợ cho cả PostgreSQL (thông qua `pg_depend`) và SQL Server (thông qua `sys.dm_sql_referencing_entities` / `sys.foreign_keys`).

---

## 3. Chi tiết danh sách File cần Sửa & File Thêm Mới

### 3.1. Các file cần sửa (Modified Files)

| File Path | Class | Phương thức cần sửa/thêm | Lý do & Nội dung sửa |
|---|---|---|---|
| `src/main/java/app/ui/components/DatabaseTreeCell.java` | `DatabaseTreeCell` | - `createContextMenuForTable(...)`<br>- `createContextMenuForView(...)`<br>- `createContextMenuForColumn(...)` | Bổ sung mục menu ngữ cảnh: **"Xem biểu đồ phụ thuộc (View Dependency Graph)..."**. |
| `src/main/java/app/controllers/MainController.java` | `MainController` | `openDependencyGraph(profile, catalog, schema, objectName, column, objectType)` | Phương thức điều phối tạo và mở tab `DependencyGraphTab` trong `EditorTabStrip`. |

### 3.2. Các file thêm mới (New Files)

| File Path | Class / Resource | Trách nhiệm & Phương thức chính |
|---|---|---|
| `core/core-domain/src/main/java/com/lltool/core/domain/dependency/model/DependencyNode.java` | `DependencyNode` | Đại diện node: `id`, `name`, `schema`, `type` (`TABLE`, `VIEW`, `FUNCTION`, `TRIGGER`, `COLUMN`), `metadata`. |
| `core/core-domain/src/main/java/com/lltool/core/domain/dependency/model/DependencyEdge.java` | `DependencyEdge` | Đại diện cạnh: `sourceId`, `targetId`, `edgeType` (`NORMAL`, `FOREIGN_KEY`, `TRIGGER_ON`, `COLUMN_USAGE`). |
| `core/core-domain/src/main/java/com/lltool/core/domain/dependency/model/DependencyGraphModel.java` | `DependencyGraphModel` | Toàn bộ đồ thị: danh sách nodes, edges, rootNodeId, direction (`DEPENDENTS`, `DEPENDENCIES`, `BOTH`), depth. |
| `core/core-domain/src/main/java/com/lltool/core/domain/dependency/model/SuggestedStep.java` | `SuggestedStep` | Một bước đề xuất: `stepNumber`, `actionType` (`DROP_VIEW`, `ALTER_COLUMN`, `RECREATE_VIEW`), `objectName`, `description`, `sqlScript`. |
| `core/core-application/src/main/java/com/lltool/core/application/dependency/DependencyGraphService.java` | `DependencyGraphService` | Service điều phối: phân tích phụ thuộc CSDL theo dialect (PostgreSQL catalog queries hoặc SQL Server DMV queries), tính toán đồ thị theo độ sâu, sinh danh sách `SuggestedStep`. |
| `infrastructure/database-postgresql/src/main/java/com/lltool/infrastructure/database/postgresql/PostgresDependencyExtractor.java` | `PostgresDependencyExtractor` | Truy vấn `pg_depend`, `pg_rewrite`, `pg_class`, `pg_attribute` để trích xuất phụ thuộc chuẩn xác cho PostgreSQL. |
| `infrastructure/database-sqlserver/src/main/java/com/lltool/infrastructure/database/sqlserver/SqlServerDependencyExtractor.java` | `SqlServerDependencyExtractor` | Truy vấn `sys.dm_sql_referencing_entities` và `sys.foreign_keys` cho SQL Server. |
| `src/main/java/app/ui/dependency/DependencyGraphTab.java` | `DependencyGraphTab` | Giao diện chính kế thừa `SqlEditorTab`: Thanh toolbar trên (chọn đối tượng gốc, cột, slider độ sâu depth 1-5, direction toggle, nút Export ảnh, Refresh), SplitPane chia 2 (trái: canvas đồ thị tương tác, phải: inspector panel). |
| `src/main/java/app/ui/dependency/DependencyGraphCanvas.java` | `DependencyGraphCanvas` | Component hiển thị đồ thị tương tác: Hỗ trợ pan (kéo chuột), zoom (cuộn chuột), chọn node, bố cục phân cấp (Sugiyama/Hierarchical DAG Layout). |
| `src/main/java/app/ui/dependency/DependencyInspectorPanel.java` | `DependencyInspectorPanel` | Panel bên phải gồm 3 tab: Thuộc tính Node, Xem DDL (`EmbeddedMonaco`), Các bước đề xuất (`Suggested Steps`). |
| `src/main/resources/css/dependency-graph.css` | CSS Stylesheet | Token giao diện theo `-ide-*` và `-color-*` cho node đồ thị, các đường nối edge, và inspector cards. |
| `src/test/java/app/ui/dependency/DependencyGraphServiceTest.java` | Unit Test | Test thuật toán trích xuất phụ thuộc và sinh bước xử lý an toàn (Impact steps). |

---

## 4. Thư viện & Phụ thuộc
- Canvas đồ thị sử dụng JavaFX `Canvas` hoặc `Pane` kết hợp thuật toán sắp xếp đồ thị phân cấp Hierarchical DAG. Không bắt buộc phải thêm thư viện ngoài nặng nề.

