# Báo Cáo Hoàn Thành: Feature 06 — SQL Visualizer & Query Flow (Phase 3 - Slice 6)

> **Ngày hoàn thành:** 2026-11-11  
> **Trạng thái:** `COMPLETE`  
> **Kiểm thử tự động:** 7/7 tests passed (100% BUILD SUCCESS), tổng regression Phase 3 đạt 42/42 tests passed.

---

## 1. Tổng quan & Đối chiếu Yêu cầu

Tính năng **SQL Visualizer & Query Flow (Trực quan hóa luồng thực thi câu truy vấn SQL)** đã được triển khai hoàn chỉnh trên Java Desktop theo đúng mockup đã phê duyệt (`phase3_mockup_studio.html`) và các tiêu chuẩn:
- `04-component-rules.md`: Bố cục Canvas pipeline chia cột theo các công đoạn biến đổi dữ liệu kết hợp Inspector Panel chi tiết (SplitPane dọc), Badge màu sắc theo loại toán tử (`SOURCE_TABLE`, `JOIN`, `FILTER`, `AGGREGATE`, `SORT`, `OUTPUT`).
- `02-design-principles.md`: Giao diện trực quan Master-Detail, điều hướng canvas tự do (Pan bằng kéo chuột, Zoom bằng con lăn chuột centered tại con trỏ, kéo di chuyển vị trí node tùy ý, Reset zoom `Ctrl+0`, Zoom In/Out `Ctrl++/Ctrl+-`, phím tắt `F5` / `Ctrl+Enter` phân tích, xuất ảnh PNG).
- `03-design-tokens.md`: Sử dụng hệ thống token chuẩn (`-color-bg-default`, `-color-bg-subtle`, `-ide-editor-bg`, `-color-border-subtle`, `-color-accent-emphasis`, màu accent cho từng nhóm toán tử: xanh lam cho Nguồn, cam cho JOIN, cyan cho Filter, tím cho Gom nhóm, hồng cho Sắp xếp, xanh lục cho Đầu ra).
- `SOURCE_ANALYSIS.md` & `AGENTS.md`: Tách biệt rành mạch tầng Core Domain (`com.lltool.core.domain.visualizer`), Core Application (`com.lltool.core.application.visualizer`), và UI JavaFX Native SceneGraph.
- **Đồng bộ hai chiều (Two-way Synchronization):** Bấm chọn một node trên sơ đồ canvas sẽ tự động highlight đoạn mã SQL tương ứng trong trình soạn thảo thông qua `SourceRange` (1-based line, col, offset), và ngược lại khi di chuyển con trỏ trong mã SQL sẽ tự động chọn node tương ứng trên canvas.

---

## 2. Các thành phần đã triển khai

### 2.1. Domain & Application Model (`core/core-domain` & `core/core-application`)
1. **Model Classes (`com.lltool.core.domain.visualizer`):**
   - `SourceRange`: Quản lý tọa độ 1-based (`startLine`, `startCol`, `endLine`, `endCol`, `startOffset`, `endOffset`) và hàm `contains(line, col)` phục vụ đồng bộ hai chiều chính xác.
   - `QueryNodeType`: Phân loại các toán tử pipeline (`SOURCE_TABLE`, `JOIN_TABLE`, `CTE`, `SUBQUERY`, `JOIN`, `FILTER`, `AGGREGATE`, `SORT`, `PROJECTION`, `OUTPUT`).
   - `QueryEdgeType`: Các loại luồng dữ liệu (`DATA_FLOW`, `JOIN_INPUT`, `FILTER_PASS`, `AGGREGATION_FLOW`, `OUTPUT_STREAM`).
   - `QueryFlowNode`: Đại diện một bước trong quy trình xử lý SQL, lưu trữ `stageIndex`, `stepIndex`, `title`, `subtitle`, `details`, `estimatedCost`, `rowCount`, `sqlFragment`, `sourceRange`, và tọa độ bố cục (`x`, `y`).
   - `QueryFlowEdge`: Cạnh nối có hướng giữa 2 bước xử lý kèm nhãn ngữ nghĩa (`Left Stream`, `Right Stream`, `Filtered`, `Grouped Stream`, `Result Stream`).
   - `QueryFlowGraph`: Mô hình đồ thị hoàn chỉnh gồm danh sách tên 6 giai đoạn, danh mục nodes, danh mục edges, và phương thức `findNodeAtPosition(line, col)`.

2. **Core Service (`com.lltool.core.application.visualizer.QueryFlowAnalyzer`):**
   - Bóc tách câu truy vấn SQL theo cấu trúc ngữ pháp thành 6 giai đoạn chuẩn:
     - **Giai đoạn 1 — Nguồn Dữ liệu (FROM & JOIN tables):** Quét các bảng dữ liệu gốc.
     - **Giai đoạn 2 — Kết nối (JOIN):** Toán tử kết nối với điều kiện ON.
     - **Giai đoạn 3 — Bộ lọc (WHERE):** Điều kiện loại bỏ các dòng không hợp lệ.
     - **Giai đoạn 4 — Gom nhóm (GROUP BY):** Tính toán các hàm tổng hợp theo khóa.
     - **Giai đoạn 5 — Sắp xếp & Giới hạn (ORDER BY & TOP/LIMIT):** Thứ tự luồng bản ghi.
     - **Giai đoạn 6 — Đầu ra (SELECT Output):** Chiếu các cột kết quả cuối cùng.
   - `calculatePipelineLayout(QueryFlowGraph graph)`: Thuật toán tự động căn chỉnh tọa độ ngang (X cách đều theo từng giai đoạn 260px) và căn giữa theo trục đứng Y cho các bảng nguồn.
   - `findSourceRange(fullSql, fragment)`: Thuật toán ánh xạ chính xác vị trí dòng, cột và offset trong văn bản SQL.
   - `getPresetSql(presetIndex)`: Cung cấp sẵn 3 mẫu truy vấn mẫu khớp 100% mockup cho việc thử nghiệm tức thì.

### 2.2. Giao diện Desktop UI (`src/main/java/app/ui/visualizer/`)
1. **`SqlVisualizerCanvas.java`:**
   - Canvas trực quan hóa quy trình dữ liệu:
     - Render nhãn tiêu đề các cột giai đoạn (Pills bo tròn) và các đường phân cách nét đứt tinh tế.
     - Render các thẻ toán tử (Node Cards) với huy hiệu số bước (Step Badge), biểu tượng FontIcon phân loại màu sắc, tên toán tử in đậm, biểu thức chi tiết, huy hiệu chi phí ước tính (Cost Badge), và số dòng dữ liệu.
     - Render đường cong Bezier (`CubicCurve`) kết nối giữa ngõ ra cạnh phải của bước trước đến ngõ vào cạnh trái của bước sau, kèm mũi tên chỉ hướng và nhãn luồng dữ liệu.
     - Hỗ trợ kéo di chuyển thẻ tự do (Drag & Drop) và tự động tính toán lại đường cong bezier real-time.
     - Hỗ trợ Pan mượt mà bằng kéo chuột, Zoom mượt mà bằng con lăn chuột lấy tâm tại con trỏ (tỷ lệ 0.25x đến 3.0x).
     - Nút `Căn vừa màn hình` (`Fit to Screen`) tự động scale và căn giữa toàn bộ đồ thị.
     - Nút `Xuất PNG`: Chụp ảnh snapshot nền tối chuẩn vector và ghi ra file ảnh PNG thông qua `PixelReader` thuần Java Desktop.
2. **`SqlVisualizerInspector.java`:**
   - Panel chi tiết toán tử được chọn đặt tại nửa dưới:
     - Tiêu đề hiển thị số bước, tên toán tử và mã phân loại.
     - Lưới thông số: Loại thao tác, Giai đoạn, Chi phí ước tính, Dữ liệu ước tính, Mô tả chi tiết, Tọa độ dòng/cột câu lệnh.
     - Khung hiển thị đoạn trích SQL (AST Fragment) dạng monospace kèm nút `Sao chép SQL (Copy)` có hiệu ứng đổi trạng thái tạm thời "Đã sao chép!".
3. **`SqlVisualizerTab.java`:**
   - Kế thừa `SqlEditorTab`, tích hợp vào hệ thống tab chính của IDE:
     - Thanh công cụ trên cùng:
       - ComboBox chọn 3 mẫu truy vấn có sẵn hoặc tùy biến câu lệnh.
       - Nút `Phân tích SQL` (`fth-play`) với phím tắt `F5` hoặc `Ctrl+Enter`.
       - Nút toggle `Hiện/Ẩn SQL` để linh hoạt thu phóng không gian vẽ.
       - ToggleGroup chuyển đổi chế độ xem: `Luồng dữ liệu (Pipeline)` vs `Cây cú pháp (AST)`.
       - Cụm nút điều khiển zoom: Phóng to (`Ctrl++`), Thu nhỏ (`Ctrl+-`), Căn vừa (`Ctrl+0`), Reset 100%.
       - Nút `Xuất PNG` (`fth-download`).
     - Khung soạn thảo/xem trước SQL có thể co giãn, tự động highlight vùng văn bản khi click vào node tương ứng trên sơ đồ và ngược lại.
     - Tích hợp `LoadingOverlay` và `EmptyState`.

### 2.3. Tích hợp Hệ thống (IDE Integration)
1. **Thực đơn ngữ cảnh `DatabaseTreeCell.java`:**
   - Mục `Design View (Trực quan hóa SQL)` trên các View và Materialized View được liên kết trực tiếp mở `SqlVisualizerTab` với định nghĩa SQL của View đó.
2. **Thực đơn Menu chính `main-window.fxml`:**
   - Menu `Tools -> SQL Visualizer & Query Flow...` với phím tắt `Shortcut+Shift+V` (`Ctrl+Shift+V` trên Windows).
3. **Điều khiển Tabs trong `EditorController.java` & `MainController.java`:**
   - Phương thức `openSqlVisualizerTab(...)` hỗ trợ tái sử dụng tab đang mở nếu cùng query key hoặc mở tab mới.
   - Thêm `handleOpenSqlVisualizer()` và `openSqlVisualizer(...)`.

---

## 3. Kết quả Kiểm thử Tự động (Automated Test Verification)

Toàn bộ các test suite đã được thực thi và xác minh qua Maven:
- `QueryFlowAnalyzerTest.java`: **5/5 tests passed** (Kiểm tra bóc tách Sample 1, Sample 2, Sample 3, tính toán SourceRange đồng bộ 2 chiều, xử lý câu lệnh rỗng/null).
- `SqlVisualizerTabTest.java`: **2/2 tests passed** (Khởi tạo giao diện JavaFX không lỗi, gán dữ liệu canvas, kiểm tra inspector và các thao tác node selection).
- **Tổng kiểm thử Phase 3 Regression:** **42/42 tests passed (100% BUILD SUCCESS)**.
  - Table Designer: 7 tests passed
  - Schema Compare: 6 tests passed
  - Table Data Editor: 7 tests passed
  - Dependency Graph: 7 tests passed
  - Object Content Search: 1 test passed
  - SQL Visualizer: 7 tests passed
  - Core Domain/Application/API: 12 tests passed
