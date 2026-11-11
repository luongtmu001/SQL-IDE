# Kế hoạch Thực thi: Dependency Graph & Impact Analysis (Phase 3 - Slice 5)

> **Mục tiêu:** Xây dựng tính năng Trực quan hóa Biểu đồ Phụ thuộc (Dependency Graph) cho các đối tượng CSDL (bảng, view, function, procedure, trigger, cột), cung cấp khả năng điều hướng tương tác (pan/zoom/click) và phân tích tác động (Impact Analysis) đề xuất các bước thay đổi an toàn.

---

## 1. Tuân thủ Quy tắc & Token Thiết kế

### 1.1. Tuân thủ `04-component-rules.md` (Quy tắc Component)
- **Badge Loại đối tượng:** Sử dụng `Badge` phân biệt rõ các loại node trên biểu đồ:
  - Bảng: Badge xanh lam `-color-accent-emphasis`.
  - View / MatView: Badge xanh ngọc `-color-info-emphasis`.
  - Function / Procedure: Badge tím `-color-purple-emphasis`.
  - Trigger: Badge vàng cam `-color-warning-emphasis`.
  - Cột: Badge xám viền `-color-fg-muted`.
- **Thanh công cụ:**
  - Slider chọn độ sâu (Depth 1 đến 5).
  - ToggleButton chọn hướng duyệt: `Đối tượng phụ thuộc (Dependents)`, `Đối tượng nguồn (Dependencies)`, `Cả hai (Both)`.
  - Nút Refresh (`fth-refresh-cw`), Nút Xuất ảnh PNG/SVG (`fth-image`).
- **Trạng thái Trống & Đang tải:**
  - Dùng `LoadingOverlay` khi đang truy vấn catalog CSDL xây dựng đồ thị.
  - Dùng `EmptyState` khi đối tượng độc lập hoàn toàn không có quan hệ phụ thuộc nào.
- **Xem DDL:** Tái sử dụng `EmbeddedMonaco` trong inspector panel bên phải.

### 1.2. Tuân thủ `02-design-principles.md` (Nguyên tắc Thiết kế Desktop)
- **Bố cục Master-Detail:**
  - `SplitPane` ngang:
    - Nửa bên trái (~70% diện tích): Vùng hiển thị biểu đồ đồ thị (Graph Canvas) hỗ trợ zoom vô cấp bằng con lăn chuột, kéo pan tự do bằng chuột giữa hoặc chuột trái, double-click để căn giữa node được chọn.
    - Nửa bên phải (~30% diện tích): Inspector panel gồm 3 tab tiện ích:
      1. `Thuộc tính (Properties)`: Tên, schema, kiểu, số kết nối vào/ra.
      2. `Mã DDL (Definition)`: Monaco editor hiển thị DDL của node.
      3. `Bước đề xuất (Suggested Steps)`: Danh sách các bước an toàn khi muốn sửa/xóa cột/đối tượng này.
- **Hỗ trợ 2 chế độ `panelStyle`:** Node đồ thị vẽ dạng Card bo góc 8px (ở chế độ `rounded`) hoặc góc vuông phẳng (ở chế độ `flat`).
- **Phím tắt chuẩn Desktop:**
  - `Ctrl + Cuộn chuột`: Phóng to / Thu nhỏ đồ thị.
  - `Ctrl+0`: Reset zoom về mức 100% và căn giữa đồ thị.
  - `F5`: Nạp lại đồ thị từ CSDL.

### 1.3. Tuân thủ `03-design-tokens.md` (Design Tokens)
- Tuyệt đối không dùng mã hex cố định:
  - Màu nền Canvas: `-ide-editor-bg` / `-color-bg-default`.
  - Đường nối cạnh (Edges): `-ide-border-color` / `-color-border-default`.
  - Cạnh mũi tên khi hover hoặc chọn: `-color-accent-fg-emphasis`.
  - Node được chọn: Viền đậm `-color-accent-fg-emphasis`, đổ bóng nhẹ.

---

## 2. Kiến trúc & Phân rã Nhiệm vụ

### Bước 1: Xây dựng Domain Model & Service Thuật toán Đồ thị
1. Tạo package `com.lltool.core.domain.dependency.model`:
   - `DependencyNode.java`: id, label, schema, name, objectType, column.
   - `DependencyEdge.java`: sourceId, targetId, edgeType.
   - `DependencyGraphModel.java`: Map<String, DependencyNode>, Set<DependencyEdge>, rootId, direction, depth.
   - `SuggestedStep.java`: Số thứ tự bước, loại hành động, tên đối tượng ảnh hưởng, đoạn script SQL tương ứng.
2. Tạo `com.lltool.core.application.dependency.DependencyGraphService`:
   - Hỗ trợ xây dựng đồ thị theo chiều rộng (BFS traversal) có giới hạn độ sâu (Depth limit) để chống tràn bộ nhớ hoặc lặp vô tận (Cyclic dependencies).
   - Module tính toán `ImpactAnalysisService`: Phân tích thứ tự phụ thuộc để sinh các bước:
     1. Tạm xóa hoặc sửa các View phụ thuộc.
     2. Thay đổi hoặc xóa cột nguồn.
     3. Tạo lại các View theo định nghĩa mới.

### Bước 2: Hiện thực Module Trích xuất Catalog (PostgreSQL & SQL Server)
1. Tạo `PostgresDependencyExtractor.java`:
   - Thực thi câu lệnh truy vấn catalog đệ quy trên `pg_depend`:
     ```sql
     SELECT refobjid::regclass, objid::regclass, deptype
     FROM pg_depend
     WHERE ...
     ```
   - Trích xuất quan hệ giữa các cột thông qua `pg_attribute` và `pg_rewrite`.
2. Tạo `SqlServerDependencyExtractor.java`:
   - Thực thi `sys.dm_sql_referencing_entities` và `sys.dm_sql_referenced_entities`.

### Bước 3: Hiện thực Trực quan hóa Biểu đồ (DependencyGraphCanvas)
1. Tạo `DependencyGraphCanvas.java` kế thừa JavaFX `Pane`:
   - Tự động sắp xếp node theo thuật toán phân cấp Hierarchical Layout (các node nguồn ở bên trái/trên, các node phụ thuộc ở bên phải/dưới).
   - Vẽ đường cong Bézier hoặc đường gấp khúc có mũi tên chỉ hướng phụ thuộc.
   - Hỗ trợ tương tác chuột:
     - Kéo di chuyển vị trí node.
     - Pan toàn bộ biểu đồ khi kéo nền.
     - Zoom mượt mà theo vị trí con trỏ chuột.
   - Highlight toàn bộ chuỗi đường đi (path) liên quan khi click chọn một node.

### Bước 4: Hiện thực `DependencyGraphTab` & Inspector
1. Tạo `DependencyInspectorPanel.java`:
   - Tab 1: Chi tiết thuộc tính của node được chọn.
   - Tab 2: `EmbeddedMonaco` nạp mã DDL đối tượng tương ứng.
   - Tab 3: Danh sách các bước `Suggested Steps` kèm nút "Mở toàn bộ script trong tab SQL".
2. Tạo `DependencyGraphTab.java`:
   - Kết nối Toolbar (chọn Depth, Direction, Search Node trong graph, Export Image) với Canvas và Inspector.

---

## 3. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] Người dùng có thể mở biểu đồ phụ thuộc từ menu chuột phải của Bảng, View, hoặc Cột trong Object Explorer.
- [ ] Biểu đồ dựng chính xác các quan hệ phụ thuộc cho cả PostgreSQL và SQL Server.
- [ ] Thao tác tương tác chuột (pan, zoom, click chọn node, kéo node) mượt mà, không giật lag với đồ thị dưới 200 nodes.
- [ ] Đổi độ sâu (Depth slider) hoặc hướng duyệt (Dependents / Dependencies) tự động cập nhật lại đồ thị ngay lập tức.
- [ ] Khi chọn một node, panel bên phải lập tức hiển thị thuộc tính và mã DDL tương ứng trong Monaco.
- [ ] Tab "Suggested Steps" tính toán chính xác chuỗi câu lệnh DDL cần thiết khi thực hiện thao tác DROP/ALTER cột.
- [ ] Hỗ trợ xuất biểu đồ ra định dạng ảnh PNG để chia sẻ hoặc đính kèm báo cáo.
- [ ] Đạt 100% test cases trong `DependencyGraphServiceTest`.

