# Báo Cáo Tiến Độ Backend Tính Năng 05: Dependency Graph & Impact Analysis

> Ngày thực hiện: 2026-11-11
> Người thực hiện: Antigravity (Google DeepMind pair programming agent)
> Đối chiếu kế hoạch: `migrate_phase3/07_review_va_toi_uu/02_PLAN_BACKEND.md` — Mục 3.5

---

## 1. Bảng Trạng Thái Chi Tiết Từng ID

| ID | Mức | Mô tả lỗi gốc | Trạng thái | Giải pháp & Thay đổi phương án (nếu có) |
|---|---|---|---|---|
| **DG-1** | P0 | `loadGraph()` luôn gọi `createSampleOrdersGraph(...)`: chọn đối tượng nào cũng ra đồ thị `dbo.orders`. Tham số `objectName`, `columnName`, `objectType`, `profile` chỉ dùng cho tiêu đề | **XONG** | Xây dựng `DependencyGraphLoader.load(...)`: duyệt quan hệ phụ thuộc theo thuật toán BFS từng cấp độ từ root. Truy vấn catalog thật:<br>• **SQL Server**: `sys.sql_expression_dependencies` (cả 2 chiều: referencing và referenced), `sys.triggers`, `sys.foreign_keys`, `sys.partitions` (số dòng).<br>• **PostgreSQL**: `pg_depend` + `pg_rewrite` (view references), `pg_constraint` (foreign keys), `pg_trigger`, `pg_proc` (so khớp text trong `prosrc` với mức tin cậy `Confidence.POSSIBLE`), `pg_class.reltuples` (số dòng).<br>• Khống chế trần 300 nodes (`MAX_NODES = 300`) để tránh tràn bộ nhớ. Chế độ mock tự động kích hoạt khi `profile == null`, `BackendFlags.mock(METADATA)` hoặc `MockMetadataProvider.isEnabled()`. |
| **DG-2** | P0 | Combo "Đối tượng gốc" là các mục cứng (`dbo.orders`, `dbo.customers`...); tách tên bằng `split(" ")` / `split("\\.")` gây lỗi với tên có dấu chấm hoặc khoảng trắng; đổi mục không cập nhật `objectType` | **XONG** | Tạo record `RootObjectItem(String schema, String name, DependencyNodeType type)` lưu trữ thông tin chuẩn hóa mà không tách chuỗi. `cbRootObject` được nạp danh sách đối tượng thật trong schema (bảng, view, thủ tục, hàm) qua `DesktopMetadataService` trên luồng nền. Hỗ trợ hiển thị icon theo loại đối tượng qua `CellFactory` và `ButtonCell`. |
| **DG-3** | P0 | "Các bước đề xuất" sinh SQL từ dữ liệu mẫu với định nghĩa giả; "Mở toàn bộ kịch bản" đưa script không liên quan đối tượng thật sang tab SQL. Số dòng (`rowCount`) là số mẫu | **XONG** | Tái cấu trúc `generateSuggestedSteps`: duyệt toàn bộ cây dependents reachable từ đối tượng mục tiêu. Thứ tự gỡ (Dismantle): sắp xếp theo layer giảm dần (phụ thuộc sâu nhất gỡ trước: View 2 → View 1). Bước giữa: sinh placeholder ghi chú rõ ràng (`-- TODO: Áp dụng thay đổi...`), không bịa kiểu cột hay lệnh DDL giả. Thứ tự dựng lại (Recreate): sắp xếp theo layer tăng dần (View 1 → View 2). DDL lấy từ định nghĩa thật (`dep.getDefinitionSql()`); nếu không đọc được DDL thì ghi chú rõ ràng `"-- Không đọc được định nghĩa của " + dep.getDisplayName()`. Số dòng đọc từ `sys.partitions` (SQL Server) hoặc `pg_class.reltuples` (PG). |
| **DG-4** | P1 | Luồng tải là `new Thread` không daemon, có `Thread.sleep(120)`; không hỗ trợ hủy; đổi hướng/độ sâu nhanh liên tiếp làm kết quả về sai thứ tự | **XONG** | Sử dụng `LoadTask<DependencyGraphModel>` với generation tracking và hủy an toàn khi có tác vụ mới. Thêm bộ nhớ đệm `graphCache` theo khóa `targetKey + "|" + direction + "|" + depth` trong tab. Nhấn `F5` hoặc nút Làm mới sẽ xóa cache và nạp lại từ CSDL. |
| **DG-5** | P1 | Thuật toán bố cục chỉ xếp theo lớp, không giảm cắt chéo cạnh; node cùng lớp xếp theo thứ tự chèn. "Tất cả" bị giới hạn ở độ sâu 5 | **XONG** | Hiện thực thuật toán **2-pass Barycenter Heuristic** (`reduceCrossingsBarycenter`) trong `DependencyGraphService.calculateHierarchicalLayout`: pass 1 xuôi từ lớp 1 đến lớp n, pass 2 ngược từ lớp n-1 về lớp 0. Sắp xếp vị trí Y của từng node theo tọa độ trung bình của các node lân cận ở lớp trước/sau, giảm thiểu tối đa giao cắt cạnh. Tùy chọn "Tất cả" đặt `currentDepth = 100` (duyệt đến khi hết quan hệ hoặc chạm trần 300 nodes). |
| **DG-6** | P2 | Xuất PNG sao chép từng điểm ảnh bằng `setRGB` lặp lồng trên FX thread; nuốt exception | **XONG** | Viết lại `ZoomableGraphPane.exportToPng` sử dụng mảng int buffer với `PixelReader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), buffer, 0, width)` và `bImage.setRGB(...)` chuyển toàn bộ mảng pixel trong một thao tác duy nhất. Bắt exception và ghi log rõ ràng. |
| **DG-7** | P2 | Phụ thuộc ở mức cột (mở từ node cột trong Explorer) dùng chung đồ thị mẫu | **XONG** | `DependencyGraphLoader` hỗ trợ tham số `columnName`. Khi mở từ cột, tạo node gốc `DependencyNode.ofColumn(...)`, truy vấn lọc quan hệ theo `referenced_minor_id` (SQL Server) hoặc `pg_depend.refobjsubid` (PostgreSQL). |

---

## 2. Danh Sách Unit Test & Kết Quả Chạy

Tất cả các test đã được thực thi và vượt qua 100% qua lệnh `.\mvnw.cmd test "-Dtest=DependencyGraphServiceTest,DependencyGraphTabTest"`:

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running app.ui.dependency.DependencyGraphServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.445 s -- in app.ui.dependency.DependencyGraphServiceTest
[INFO] Running app.ui.dependency.DependencyGraphTabTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.087 s -- in app.ui.dependency.DependencyGraphTabTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Chi tiết các ca kiểm thử:
1. `DependencyGraphServiceTest.testSampleGraphBothDirections`: Kiểm tra đồ thị mẫu cả 2 chiều chứa đầy đủ node nguồn và node phụ thuộc, layer đúng quy chuẩn.
2. `DependencyGraphServiceTest.testSampleGraphDependentsOnly`: Kiểm tra chế độ DEPENDENTS chỉ chứa các đối tượng phụ thuộc xuôi.
3. `DependencyGraphServiceTest.testSampleGraphDependenciesOnly`: Kiểm tra chế độ DEPENDENCIES chỉ chứa các đối tượng nguồn ngược.
4. `DependencyGraphServiceTest.testLayoutVerticalSpacing`: Kiểm tra thuật toán bố cục phân bố tọa độ Y phân biệt giữa các node trong cùng layer.
5. `DependencyGraphServiceTest.testGenerateSuggestedSteps`: Kiểm tra sinh các bước đề xuất cơ bản (Drop view/trigger → Modify table → Recreate view/trigger).
6. `DependencyGraphServiceTest.testGenerateSuggestedStepsForColumn`: Kiểm tra sinh bước đề xuất khi đối tượng mục tiêu là một cột.
7. `DependencyGraphServiceTest.testDismantleAndRecreationOrderingMultiLevel`: **(DG-3)** Kiểm tra đồ thị chuỗi phụ thuộc nhiều tầng Table → View 1 → View 2: thứ tự gỡ là View 2 rồi View 1; bước giữa là placeholder `MODIFY_TABLE`; thứ tự dựng lại là View 1 rồi View 2; khi thiếu DDL hiển thị thông báo không đọc được định nghĩa.
8. `DependencyGraphServiceTest.testBarycenterCrossingReduction`: **(DG-5)** Kiểm tra thuật toán Barycenter tự động sắp xếp lại thứ tự node trong layer để xóa bỏ giao cắt cạnh (edge crossing).
9. `DependencyGraphTabTest.testTabInitialization`: Kiểm tra khởi tạo tab, thanh công cụ header, canvas và inspector.
10. `DependencyGraphTabTest.testRootObjectItem`: **(DG-2)** Kiểm tra record `RootObjectItem` xử lý an toàn tên schema/object có dấu chấm hoặc khoảng trắng mà không dùng string split.
11. `DependencyGraphTabTest.testDependencyGraphLoaderMock`: **(DG-1 & DG-7)** Kiểm tra `DependencyGraphLoader` tự động fallback sang đồ thị mẫu an toàn khi profile là null hoặc mock mode bật.
12. `DependencyGraphTabTest.testCycleResilience`: **(DG-1)** Kiểm tra khả năng chống lặp vô hạn khi hai đối tượng phụ thuộc chéo lẫn nhau (A ↔ B) qua khóa ngoại hai chiều.

---

## 3. Kiểm Tra Trên Cơ Sở Dữ Liệu Thật

- **Môi trường đã kiểm thử tự động:** H2 in-memory, SQLite & Mock Metadata Provider.
- **PostgreSQL & SQL Server:** Các câu lệnh truy vấn catalog view và DMV (`sys.sql_expression_dependencies`, `sys.triggers`, `sys.foreign_keys`, `sys.partitions`, `pg_depend`, `pg_rewrite`, `pg_constraint`, `pg_trigger`, `pg_proc`, `pg_class`) được xây dựng theo chuẩn ANSI/catalog views có sẵn từ SQL Server 2008+ và PostgreSQL 10+. Chưa kiểm tra trực tiếp qua mạng trên instance live server thật tại turn này.

---

## 4. Điểm Khác Biệt Giữa Kế Hoạch & Mã Nguồn Thực Tế

1. **TableMetadata Accessors:** `TableMetadata` là một record trong `infrastructure/database-api`, các method lấy schema và tên là `t.schema()` và `t.name()`, không phải getter JavaBeans `t.getSchema()` / `t.getName()`.
2. **DatabaseType Package:** `DatabaseType` nằm trong `com.lltool.infrastructure.database.api.DatabaseType`, không phải `com.lltool.core.domain.connection.DatabaseType`.
3. **ConnectionProfileDto Field:** Trường cơ sở dữ liệu mặc định là `profile.databaseName()`, không phải `defaultDatabase()`.
4. **Fast PNG Export Method:** `exportToPng` nằm ở `ZoomableGraphPane` (lớp cha dùng chung cho các Canvas đồ thị), không nằm ở `DependencyGraphCanvas`. Đã tối ưu trực tiếp tại `ZoomableGraphPane` để toàn bộ các canvas đồ thị đều được hưởng lợi từ việc tối ưu bulk pixel buffer.
