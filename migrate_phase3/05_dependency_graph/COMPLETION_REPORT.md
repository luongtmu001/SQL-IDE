# Báo Cáo Hoàn Thành: Feature 05 — Dependency Graph & Impact Analysis (Phase 3 - Slice 5)

> **Ngày hoàn thành:** 2026-11-11  
> **Trạng thái:** `COMPLETE`  
> **Kiểm thử tự động:** 7/7 tests passed (100% BUILD SUCCESS), tổng regression Phase 3 đạt 54/54 tests passed.

---

## 1. Tổng quan & Đối chiếu Yêu cầu

Tính năng **Dependency Graph & Impact Analysis** đã được di chuyển từ mã nguồn Python sang Java Desktop tuân thủ nghiêm ngặt các quy chuẩn:
- `04-component-rules.md`: Bố cục Canvas kết hợp Inspector Panel chia ngăn (SplitPane), Badge nhận diện loại node (TABLE, VIEW, PROCEDURE, FUNCTION, TRIGGER, COLUMN), Toggle hướng duyệt (`Both`, `Depends On`, `Referenced By`), Slider chọn độ sâu (`Cấp 1`, `Cấp 2`, `Cấp 3`, `Tất cả`).
- `02-design-principles.md`: Giao diện trực quan Master-Detail, điều hướng canvas tự do (Pan bằng kéo chuột, Zoom bằng con lăn chuột centered tại con trỏ, kéo di chuyển vị trí node tùy ý, Reset zoom `Ctrl+0`, Zoom In/Out `Ctrl++/Ctrl+-`, phím tắt `F5` reload, xuất ảnh PNG).
- `03-design-tokens.md`: Sử dụng hệ thống token chuẩn (`-color-bg-default`, `-color-bg-subtle`, `-ide-editor-bg`, `-color-border-subtle`, `-color-accent-emphasis`, `-color-info-emphasis`, `-color-warning-emphasis`, `-color-purple-emphasis`).
- `SOURCE_ANALYSIS.md` & `AGENTS.md`: Tách biệt rành mạch tầng Core Domain (`com.lltool.core.domain.dependency`), Core Application (`com.lltool.core.application.dependency`), và UI JavaFX Native Canvas.

---

## 2. Các thành phần đã triển khai

### 2.1. Domain & Application Model (`core/core-domain` & `core/core-application`)
1. **Model Classes (`com.lltool.core.domain.dependency`):**
   - `DependencyNodeType`: Hỗ trợ đầy đủ các loại đối tượng CSDL (`DATABASE`, `SCHEMA`, `TABLE`, `COLUMN`, `VIEW`, `MATERIALIZED_VIEW`, `FUNCTION`, `PROCEDURE`, `TRIGGER`, `INDEX`, `FOREIGN_KEY`).
   - `RelationType`: Quan hệ ngữ nghĩa (`USES_COLUMN`, `USES_TABLE`, `REFERENCES_VIEW`, `CALLS_FUNCTION`, `HAS_TRIGGER`, `HAS_INDEX`, `FOREIGN_KEY_TO`, `DEPENDS_ON`).
   - `DependencyType`: Phân loại trực tiếp (`DIRECT`) và gián tiếp (`INDIRECT`).
   - `Confidence`: Mức độ tin cậy (`CONFIRMED`, `LIKELY`, `POSSIBLE`).
   - `GraphDirection`: Hướng duyệt đồ thị (`DEPENDENTS`, `DEPENDENCIES`, `BOTH`).
   - `DependencyNode`: Node đối tượng CSDL chứa tọa độ layout (`x`, `y`), cấp độ phân lớp (`layer`), số dòng (`rowCount`), DDL Script định nghĩa.
   - `DependencyEdge`: Cạnh nối có hướng giữa source và target kèm nhãn ngữ nghĩa (`FK`, `SELECT`, `UPDATE DML`, `TRIGGER`).
   - `DependencyGraphModel`: Toàn bộ đồ thị, lưu trữ Map Nodes, List Edges, RootNode, và các phương thức truy vấn in/out degree.
   - `SuggestedStep`: Một bước đề xuất an toàn trong kịch bản Impact Analysis (Số bước, hành động `DROP_VIEW`, `ALTER_COLUMN`, `RECREATE_VIEW`, mô tả và câu lệnh SQL).

2. **Core Service (`com.lltool.core.application.dependency.DependencyGraphService`):**
   - `calculateHierarchicalLayout(DependencyGraphModel model)`: Thuật toán bố cục phân cấp Sugiyama (Layer 0 là Root, Layer âm là Upstream dependencies đặt bên trái, Layer dương là Downstream dependents đặt bên phải; tự động dãn cách đều trục Y chống đè node).
   - `generateSuggestedSteps(DependencyGraphModel model, String targetId)`: Phân tích tác động (Impact Analysis) để sinh kịch bản di chuyển an toàn theo thứ tự: Tạm hủy/vô hiệu hóa các View/Trigger phụ thuộc $\rightarrow$ Áp dụng thay đổi cấu trúc bảng/cột $\rightarrow$ Tạo lại các View/Trigger với định nghĩa tương thích.
   - `createSampleOrdersGraph(direction, depth)`: Dựng đồ thị mẫu `dbo.orders` hoàn chỉnh (kèm quan hệ upstream `dbo.customers`, downstream `dbo.v_customer_orders`, `dbo.v_monthly_revenue`, `dbo.usp_close_period`, `dbo.trg_orders_audit`, và cấp 2 `dbo.v_annual_summary`).

### 2.2. Giao diện Desktop UI (`src/main/java/app/ui/dependency/`)
1. **`DependencyGraphCanvas.java`:**
   - Canvas đồ thị tương tác cao cấp:
     - Render đường cong Bezier (`CubicCurve`) mượt mà kết hợp mũi tên chỉ hướng (`Polygon`) và nhãn cạnh (`edge-label`).
     - Node card dạng `VBox` bo góc 6px-8px, có badge màu phân biệt theo loại đối tượng, hiển thị tên bảng/cột, schema, số dòng dữ liệu.
     - Root Target Node được tạo điểm nhấn đặc biệt (`root-node` với border xanh dương nổi bật, đổ bóng glow).
     - Hỗ trợ tương tác kéo node (Drag & Drop) di chuyển vị trí tự do và tự động cập nhật lại các đường nối real-time.
     - Hỗ trợ Pan mượt mà bằng kéo chuột, Zoom mượt mà bằng con lăn chuột lấy tâm tại con trỏ (tỷ lệ 0.25x đến 3.0x).
     - Nút `Fit to Screen` tự động tính bounding box của tất cả các node để căn vừa màn hình.
     - Nút `Xuất PNG`: Chụp ảnh snapshot canvas chuẩn màu vector và lưu thành file `.png`.
2. **`DependencyInspectorPanel.java`:**
   - Panel xem thuộc tính và kịch bản di chuyển bên phải (3 Tabs):
     - **Tab 1 — Thuộc tính (Properties):** Tên đầy đủ, schema, loại đối tượng, vai trò sơ đồ, số liên kết đến (In), số liên kết ra (Out), số dòng, ghi chú phân tích tác động.
     - **Tab 2 — Mã DDL (Definition):** Trình xem DDL sử dụng `EmbeddedMonaco` (chế độ Read-only), nút `Sao chép DDL` và `Mở trong Tab SQL`.
     - **Tab 3 — Bước đề xuất (Suggested Steps):** Danh sách các bước di chuyển an toàn (card có badge số bước, loại thao tác, mô tả, và code snippet SQL), nút `Mở toàn bộ kịch bản trong Tab SQL`.
3. **`DependencyGraphTab.java`:**
   - Kế thừa `SqlEditorTab`, tích hợp vào thanh tab của IDE:
     - Header bar: ComboBox chọn đối tượng gốc, Toggle buttons đổi hướng duyệt (`Cả hai chiều`, `Phụ thuộc vào`, `Được dùng bởi`), ComboBox độ sâu (`Cấp 1`, `Cấp 2`, `Cấp 3`, `Tất cả`), ComboBox bố cục.
     - Nhóm nút điều khiển: Phóng to (`+`), Thu nhỏ (`-`), Căn vừa màn hình (`fth-maximize-2`), Reset zoom (`100%`), Làm mới (`F5`), Xuất PNG (`fth-download`).
     - Tích hợp `LoadingOverlay` và `EmptyState`.

### 2.3. Tích hợp Hệ thống (IDE Integration)
1. **Thực đơn ngữ cảnh `DatabaseTreeCell.java`:**
   - Click chuột phải vào bất kỳ **Bảng (Table)** $\rightarrow$ `Dependencies (Phụ thuộc)...`
   - Click chuột phải vào **View / Materialized View** $\rightarrow$ `Dependencies (Phụ thuộc)...`
   - Click chuột phải vào **Stored Procedure / Function** $\rightarrow$ `Dependencies (Phụ thuộc)...`
   - Click chuột phải vào **Cột (Column)** $\rightarrow$ `Dependencies (Phụ thuộc)...`
2. **Thanh Menu chính (`main-window.fxml`):**
   - Menu `Tools -> Dependency Graph & Impact Analysis...` với phím tắt `Shortcut+Shift+D`.
3. **Điều phối Tab (`EditorController.java` & `MainController.java`):**
   - Phương thức `openDependencyGraphTab(...)` và `openDependencyGraph(...)` quản lý đóng/mở/ghim tab không bị duplicate.

---

## 3. Kết quả Kiểm thử Tự động (Test Verification)

### Unit Tests mới của Feature 05:
| Test Class | Số lượng test | Kết quả | Chi tiết |
|---|---|---|---|
| `DependencyGraphServiceTest` | 6 tests | **100% PASS** | • `testSampleGraphBothDirections`<br>• `testSampleGraphDependentsOnly`<br>• `testSampleGraphDependenciesOnly`<br>• `testLayoutVerticalSpacing`<br>• `testGenerateSuggestedSteps`<br>• `testGenerateSuggestedStepsForColumn` |
| `DependencyGraphTabTest` | 1 test | **100% PASS** | • `testTabInitialization` (Kiểm tra vòng đời tạo tab, gán profile, khởi tạo canvas & inspector trên luồng JavaFX) |

### Kết quả Full Regression Suite Phase 3 (54/54 tests pass):
```text
[INFO] Running app.framework.designer.TableDesignerServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.compare.SchemaCompareServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.compare.SchemaCompareTabTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.dataeditor.CriteriaGridTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.dataeditor.ObservableDataRowTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.dataeditor.TableDataEditorTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.dependency.DependencyGraphServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.dependency.DependencyGraphTabTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.designer.ConstraintGridsTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.designer.TableDesignerTabTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.search.ObjectContentSearchTabTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running app.ui.search.ObjectSearchServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time: 29.868 s
[INFO] ------------------------------------------------------------------------
```
