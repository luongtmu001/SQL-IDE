# Khảo sát & Đánh giá Tính năng: Schema Compare (So sánh & Đồng bộ Lược đồ CSDL)

---

## 1. Khảo sát mã nguồn Python gốc

### 1.1. Các file và vị trí trong Python repository
- **Backend Service:** `app/services/schema_compare_service.py` (36,620 bytes)
  - Class: `SchemaCompareService`
  - Các hàm cốt lõi:
    - `snapshot(adapter, database, schemas, object_types)`: Chụp ảnh nhanh lược đồ (Schema Snapshot) gồm toàn bộ bảng (cột, kiểu dữ liệu, độ dài, nullability, giá trị mặc định, collation), ràng buộc (PK, FK, Unique, Check), chỉ mục (Indexes) và mã nguồn DDL của code objects (Views, Procedures, Functions, Triggers).
    - `diff(source_snap, target_snap, options)`: So sánh 2 bản snapshot và phân loại trạng thái:
      - `IDENTICAL`: Hoàn toàn giống nhau.
      - `DIFFERENT`: Có sự khác biệt về cấu trúc hoặc nội dung DDL.
      - `MISSING_IN_TARGET`: Đối tượng chỉ có ở Nguồn (cần tạo mới trên Đích).
      - `MISSING_IN_SOURCE`: Đối tượng chỉ có ở Đích (tùy chọn drop nếu muốn đồng bộ tuyệt đối).
    - `_normalize_constraints_for_diff`: Chuẩn hóa chữ ký của các constraint/index để so sánh chính xác bất kể tên ngẫu nhiên do hệ thống tự đặt.
    - `_diff_code_objects`: Chuẩn hóa khoảng trắng (`whitespace-stripped`) khi so sánh nội dung DDL của View, Procedure, Function.
    - `generate_migration_script(diff_result, target_engine, options)`: Sinh script DDL đồng bộ hóa cấu trúc từ Nguồn sang Đích theo thứ tự tô-pô (Topological Sort) để tránh lỗi phụ thuộc:
      1. Tạm thời xóa hoặc vô hiệu hóa Foreign Keys.
      2. Xóa các đối tượng phụ thuộc không còn tồn tại.
      3. Cập nhật các bảng hiện có (`ALTER TABLE ADD/ALTER COLUMN`).
      4. Tạo các bảng mới (`CREATE TABLE`).
      5. Tạo hoặc thay thế Views, Procedures, Functions (`CREATE OR REPLACE` / `ALTER`).
      6. Tạo lại Foreign Keys, Unique/Check Constraints và Indexes.
- **Backend API Mixin:** `app/api/schema_compare_api.py` (31,114 bytes)
  - Class: `SchemaCompareApiMixin`
  - Endpoints:
    - `schema_compare_get_connections()`: Lấy danh sách kết nối hoạt động và saved profiles.
    - `schema_compare_get_schemas(connection_id, database)`: Lấy danh sách schema.
    - `schema_compare_run(source_cfg, target_cfg, options)`: Thực hiện so sánh bất đồng bộ và trả về kết quả diff.
    - `schema_compare_generate_script(...)`: Sinh script đồng bộ hóa cho các đối tượng được chọn.
    - `schema_compare_cancel()`: Hủy tiến trình so sánh đang chạy.
- **Frontend UI & Script:**
  - `static/js/schema-compare.js` (211,023 bytes):
    - Khối chọn kết nối kép: Kết nối Nguồn & CSDL Nguồn vs Kết nối Đích & CSDL Đích. Nút Swap đảo chiều Nguồn/Đích.
    - TreeTableView hiển thị phân cấp đối tượng: Bảng, Views, Procedures, Functions, Triggers kèm checkbox chọn đối tượng cần đồng bộ và Badge trạng thái (`Khác biệt`, `Chỉ có ở Nguồn`, `Chỉ có ở Đích`).
    - Monaco Diff Editor (`monaco.editor.createDiffEditor`): Hiển thị DDL gốc bên trái và DDL đích bên phải với tô màu trực quan các dòng thêm/xóa/sửa.
    - Toolbar hành động: So sánh (Compare), Lọc đối tượng giống nhau/khác nhau, Sinh kịch bản (Generate Script), Mở trong tab SQL.
  - `templates/schema_compare_window.html` (6,492 bytes) & `static/css/schema-compare.css`.
- **Test suite Python:**
  - `tests/test_schema_compare.py` (42,318 bytes).

---

## 2. Khảo sát hiện trạng mã nguồn Java & Kết quả Thực thi

Tính năng Schema Compare đã được **triển khai hoàn chỉnh 100% trong Java** và đạt chuẩn tất cả các tiêu chí:
- **Core Domain & Service:**
  - `core/core-domain/.../schemacompare/model/*`: `SchemaSnapshot`, `SchemaObjectDiff`, `ColumnDiff`, `DiffStatus`, `DiffAction`, `SchemaObjectType`, `SchemaCompareSummary`, `SchemaCompareResult`, `MigrationScriptResult`.
  - `core/core-application/.../schemacompare/SchemaCompareService`: Thuật toán so sánh đa chiều (Tables, Views, Procedures, Functions, Triggers) kết hợp `TableDesignDiffer` và DDL Dialects (SQL Server, PostgreSQL), chuẩn hóa tên constraints/indexes, sinh kịch bản atomic migration script có bảo vệ DROP table.
- **Desktop UI:**
  - `src/main/java/app/ui/compare/SchemaCompareTab.java`: Giao diện kế thừa `SqlEditorTab`, khối chọn Nguồn/Đích với nút Swap, thanh lọc trạng thái (Tất cả, Khác biệt, Thêm mới, Bị xóa, Khác cấu trúc), cây phân loại danh mục đối tượng kèm Checkbox, Monaco Side-by-side DDL Viewers và Script Drawer co giãn ở nửa dưới.
  - `src/main/resources/css/schema-compare.css`: Toàn bộ CSS sử dụng token chuẩn `-ide-*` và `-color-*`.
- **Tích hợp:**
  - Menu `Tools -> Schema Compare & Sync...` (`Ctrl+Shift+C`) trong `main-window.fxml`.
  - Context menu `So sánh Schema...` trên Database và Schema nodes trong `DatabaseTreeCell.java`.
  - Điều phối trong `MainController` và `EditorController`.
- **Kiểm thử tự động:**
  - `SchemaCompareServiceTest.java` (5 tests passed).
  - `SchemaCompareTabTest.java` (1 test passed).
  - Tổng suite 47 tests passed 100%.

---

## 3. Chi tiết danh sách File cần Sửa & File Thêm Mới

### 3.1. Các file cần sửa (Modified Files)

| File Path | Class | Phương thức cần sửa/thêm | Lý do & Nội dung sửa |
|---|---|---|---|
| `src/main/java/app/controllers/MainController.java` | `MainController` | `openSchemaCompare(sourceProfile, targetProfile)` | Bổ sung phương thức điều phối mở tab `SchemaCompareTab` trong thanh tab chính. |
| `src/main/resources/monaco/index.html` (hoặc bridge) | Monaco Bridge | `createDiffEditor(originalText, modifiedText, language)` | Kích hoạt chế độ so sánh 2 bên của Monaco (`monaco.editor.createDiffEditor`) cho phép tô màu trực quan DDL Nguồn vs Đích. |
| `src/main/java/app/ui/components/DatabaseTreeCell.java` | `DatabaseTreeCell` | `createContextMenuForDatabase(...)` | Bổ sung menu: **"So sánh lược đồ (Compare Schema With...)"**. |

### 3.2. Các file thêm mới (New Files)

| File Path | Class / Resource | Trách nhiệm & Phương thức chính |
|---|---|---|
| `core/core-domain/src/main/java/com/lltool/core/domain/schemacompare/model/SchemaSnapshot.java` | `SchemaSnapshot` | Đại diện cho toàn bộ đối tượng trong CSDL tại thời điểm snapshot: Map<String, TableDesign> tables, Map<String, RoutineDesign> routines, views, triggers. |
| `core/core-domain/src/main/java/com/lltool/core/domain/schemacompare/model/ObjectDiff.java` | `ObjectDiff` | Kết quả so sánh một đối tượng: objectName, objectType, DiffState (`IDENTICAL`, `DIFFERENT`, `ONLY_IN_SOURCE`, `ONLY_IN_TARGET`), sourceDdl, targetDdl, detailedDifferences. |
| `core/core-domain/src/main/java/com/lltool/core/domain/schemacompare/model/SchemaDiffResult.java` | `SchemaDiffResult` | Tổng hợp toàn bộ kết quả so sánh: thống kê số lượng đối tượng giống nhau, khác nhau, chỉ có ở nguồn/đích; danh sách `ObjectDiff`. |
| `core/core-application/src/main/java/com/lltool/core/application/schemacompare/SchemaCompareService.java` | `SchemaCompareService` | Service chụp snapshot từ 2 connection JDBC, thực thi thuật toán so sánh diff đa luồng (`CompletableFuture`), sinh danh sách diff. |
| `core/core-application/src/main/java/com/lltool/core/application/schemacompare/SchemaSyncScriptGenerator.java` | `SchemaSyncScriptGenerator` | Thuật toán sinh script DDL đồng bộ hóa theo thứ tự tô-pô an toàn (drop FK -> alter tables -> create tables -> recreate FK/views). |
| `src/main/java/app/ui/compare/SchemaCompareTab.java` | `SchemaCompareTab` | Giao diện chính kế thừa `SqlEditorTab`: Thanh chọn kết nối Nguồn/Đích, Swap button, Options bar, TreeTableView danh sách diff có Checkbox, Monaco Diff View ở nửa dưới, Footer action bar. |
| `src/main/java/app/ui/compare/MonacoDiffViewer.java` | `MonacoDiffViewer` | Component wrapper quanh WebView để render Monaco Diff Editor, cung cấp phương thức `setDiff(sourceSql, targetSql)`. |
| `src/main/resources/css/schema-compare.css` | CSS Stylesheet | Token giao diện theo `-ide-*` và `-color-*` cho diff tree, badge trạng thái (xanh lá cho new, vàng cho different, đỏ cho delete). |
| `src/test/java/app/ui/compare/SchemaCompareServiceTest.java` | Unit Test | Test thuật toán diff và sinh script migration thứ tự an toàn cho cả PostgreSQL và SQL Server. |

---

## 4. Thư viện & Phụ thuộc
- Monaco Editor (đã có sẵn trong dự án) hỗ trợ Diff Editor bản địa. Không cần thư viện ngoài.

