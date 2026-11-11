# Khảo sát & Đánh giá Tính năng: Table Designer (Thiết kế Bảng)

> **Nguyên tắc chỉ đạo:** Phase 3 mục Table Designer đã được triển khai một phần trong Java. **Tuyệt đối KHÔNG tạo mới từ đầu**, tiếp tục kế thừa và hoàn thiện các module còn thiếu.

---

## 1. Khảo sát mã nguồn Python gốc

### 1.1. Các file và vị trí trong Python repository
- **Backend Service:** `app/services/table_designer_service.py` (64,225 bytes)
  - Class: `TableDesignerService`
  - Các hàm cốt lõi:
    - `generate_create_table_ddl(data)`: Sinh cú pháp `CREATE TABLE` hoàn chỉnh cho PostgreSQL và SQL Server (hỗ trợ column type, nullability, default values, primary keys, identity/serial, unique constraints, foreign keys, check constraints, indexes, comments).
    - `generate_diff(original, modified)`: So sánh cấu trúc bảng cũ và mới, tính toán danh sách câu lệnh ALTER (ADD/DROP/ALTER COLUMN, ADD/DROP CONSTRAINT, CREATE/DROP INDEX) có xem xét thứ tự ràng buộc khóa ngoại.
    - `format_column_type(col, engine)`: Chuẩn hóa kiểu dữ liệu theo dialect (xử lý `max`, precision/scale `(18,2)`, datetime precision).
    - `format_default_value(default_val, col_type, engine)`: Làm sạch giá trị mặc định, bóc tách hàm hệ thống (`GETDATE()`, `CURRENT_TIMESTAMP`, `gen_random_uuid()`).
    - `_quote_ident(name, engine)` / `_format_table_name(schema, table, engine)`: Trích dẫn an toàn theo dialect (`[dbo].[Table]` vs `"public"."table"`).
- **Backend API Mixin:** `app/api/designer_api.py` (4,349 bytes)
  - Class: `DesignerApiMixin`
  - Endpoints:
    - `get_table_design(connection_id, database, schema, table)`: Lấy cấu trúc bảng và sinh preview DDL.
    - `get_table_design_types(connection_id, database)`: Trả về danh sách kiểu dữ liệu được hỗ trợ tương ứng của máy chủ.
    - `preview_table_design(connection_id, data)`: Sinh live preview SQL khi thiết kế thay đổi.
    - `diff_table_design(connection_id, original, modified)`: Tính toán diff và script migration.
    - `apply_table_design(connection_id, database, statements, migration_sql)`: Thực thi DDL migration trong một transaction duy nhất; kiểm tra Read-Only guardrail.
- **Frontend UI & Controllers:**
  - `static/js/table-designer.js` (178,917 bytes): Quản lý toàn bộ giao diện thiết kế:
    - Grid bảng cột: Tên, kiểu dữ liệu, kích thước, Null, PK, Default, Identity/AutoIncrement, Diễn giải.
    - Sub-tabs: Cột (Columns), Chỉ mục (Indexes), Khóa ngoại (Foreign Keys), Unique Constraints, Check Constraints, Triggers.
    - Live DDL preview tự động cập nhật (debounced 200ms).
    - Undo/Redo stack.
    - Dialog lưu xác nhận migration (Save / Apply DDL dialog).
  - `templates/partials/table_design_modal.html` (5,839 bytes) & `static/css/table-designer.css`.
- **Test suite Python:**
  - `tests/test_table_designer.py`, `test_table_designer_phase1.py`, `test_table_designer_phase2.py`, `test_table_designer_phase3.py`, `test_phase4_table_designer_diff_quoting.py`.

---

## 2. Khảo sát hiện trạng mã nguồn Java

Dự án Java đã thực hiện một phần quan trọng theo chuẩn Clean Architecture:

### 2.1. Đã hoàn thành (Core & Domain)
1. **Core Domain (`core/core-domain`):**
   - [`TableDesign.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-domain/src/main/java/com/lltool/core/domain/tabledesigner/model/TableDesign.java): Model đại diện bảng, chứa danh sách `ColumnDesign`, `IndexDesign`, `ForeignKeyDesign`, `UniqueConstraintDesign`, `CheckConstraintDesign`. Hỗ trợ `deepCopy()`, `cloneForCopy()`.
   - Value objects: `ColumnDesign.java`, `IndexDesign.java`, `ForeignKeyDesign.java`, `UniqueConstraintDesign.java`, `CheckConstraintDesign.java`, `CopyTableOptions.java`.
2. **Core Application (`core/core-application`):**
   - [`TableDesignDiffer.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/src/main/java/com/lltool/core/application/tabledesigner/TableDesignDiffer.java): So sánh diff giữa cấu trúc gốc và cấu trúc chỉnh sửa, sinh `MigrationPlan` và `MigrationStatement`.
   - [`TableDesignValidator.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/src/main/java/com/lltool/core/application/tabledesigner/TableDesignValidator.java): Kiểm tra hợp lệ tên bảng, trùng tên cột, kiểu dữ liệu, khóa chính.
   - Dialects: `SqlServerDdlDialect.java`, `PostgresDdlDialect.java`.
3. **Infrastructure (`infrastructure/`):**
   - `TableDesignReader.java`, `PostgresTableDesignReader.java`, `SqlServerTableDesignReader.java`: Đọc metadata bảng từ CSDL qua JDBC catalog queries.
4. **Desktop Framework & UI (`src/main/java/app`):**
   - [`TableDesignerService.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/framework/designer/TableDesignerService.java): Service singleton điều phối tải metadata, sinh script migration, thực thi giao dịch DDL có kiểm tra Read-Only guardrail (`-Dsqlide.readOnly=true` và `profile.readOnly()`).
   - [`TableDesignerTab.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/TableDesignerTab.java): Tab kế thừa `SqlEditorTab`, tích hợp `ColumnGrid`, `ColumnInspector`, `DdlPreviewPane`, `TableDesignerUndoManager`, Header toolbar, Sub-tab strip bar, Footer status.
   - [`ColumnGrid.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/ColumnGrid.java): Bảng TableView chỉnh sửa cột.
   - [`ColumnInspector.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/ColumnInspector.java): Bảng thuộc tính chi tiết cột bên phải.
   - [`DdlPreviewPane.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/DdlPreviewPane.java): Panel preview live DDL dưới đáy.
   - [`CopyTableDialog.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/dialogs/CopyTableDialog.java): Dialog sao chép cấu trúc bảng bằng `DialogChrome`.
   - Tích hợp Menu ngữ cảnh Explorer trong [`DatabaseTreeCell.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/components/DatabaseTreeCell.java).

---

## 3. Các khoảng trống (Gaps) cần bổ sung & hoàn thiện

Mặc dù khung cơ bản đã chạy, các hạng mục sau chưa hoàn tất và cần hoàn thiện trực tiếp:

1. **Sub-tabs chuyển đổi giữa các đối tượng bảng:**
   - Trong `TableDesignerTab.java`, thanh `buildSubTabBar()` đã có các nút: `Cột (Columns)`, `Chỉ mục (Indexes)`, `Khóa ngoại (FK)`, `Unique`, `Check`, `Trigger`. Tuy nhiên khi click chuyển tab, giao diện trung tâm vẫn chỉ cố định `ColumnGrid`.
   - Chưa có các View/Grid tương ứng: `IndexGrid`, `ForeignKeyGrid`, `UniqueConstraintGrid`, `CheckConstraintGrid`.
2. **Hộp thoại Lưu Migration DDL (`SaveDesignDialog`):**
   - Trong `TableDesignerTab.java` hàm `handleSave()`, hiện tại đang để ghi chú:
     `// TD4 will implement the full SaveDesignDialog`
     và chỉ hiển thị thông báo tạm thời qua `NotificationService`.
   - Cần hiện `SaveDesignDialog` chuẩn `DialogChrome` hiển thị tóm tắt các thay đổi cấu trúc, preview danh sách câu lệnh ALTER, cảnh báo an toàn dữ liệu, nút "Thực thi (Execute)", nút "Mở trong tab SQL", nút "Hủy".
3. **Danh mục Data Types động theo Provider:**
   - Hiện tại `ColumnGrid` dùng danh sách kiểu dữ liệu tĩnh; cần hỗ trợ nạp danh sách kiểu dữ liệu tương thích động từ `TableDesignerService.getSupportedTypes(engine)`.
4. **Trigger View:**
   - Đối với Sub-tab Trigger, hiển thị danh sách trigger hiện có và cho phép xem DDL trigger hoặc tạo mới trigger mẫu.

---

## 4. Chi tiết danh sách File cần Sửa & File Thêm Mới

### 4.1. Các file cần sửa (Modified Files)

| File Path | Class | Phương thức cần sửa/thêm | Lý do & Nội dung sửa |
|---|---|---|---|
| `src/main/java/app/ui/designer/TableDesignerTab.java` | `TableDesignerTab` | - `buildSubTabBar()`<br>- `onSubTabChanged(SubTab tab)`<br>- `handleSave()`<br>- `updateCurrentModel()` | - Bổ sung logic hoán đổi View trung tâm giữa `columnGrid`, `indexGrid`, `foreignKeyGrid`, `uniqueGrid`, `checkGrid`.<br>- Kết nối `handleSave()` mở `SaveDesignDialog` khi người dùng bấm Lưu (Ctrl+S).<br>- Đồng bộ các thay đổi từ constraint grids vào `currentDesign`. |
| `src/main/java/app/framework/designer/TableDesignerService.java` | `TableDesignerService` | - `getSupportedTypes(String engine)`<br>- `getTypeDefaults(String engine, String type)` | Cung cấp danh sách kiểu dữ liệu chuẩn và độ dài mặc định theo dialect PostgreSQL và SQL Server. |
| `src/main/java/app/ui/designer/ColumnGrid.java` | `ColumnGrid` | - `setSupportedTypes(List<String> types)` | Nhận danh sách kiểu dữ liệu từ bên ngoài để populate vào ComboBox của cột Kiểu Dữ Liệu thay vì hardcode. |
| `src/main/resources/css/table-designer.css` | CSS Stylesheet | Bổ sung selectors `.td-constraint-grid`, `.td-save-dialog` | Thiết kế giao diện cho các bảng phụ thuộc và hộp thoại Lưu tuân thủ `02-design-principles.md` & `03-design-tokens.md`. |

### 4.2. Các file thêm mới (New Files)

| File Path | Class / Resource | Trách nhiệm & Phương thức chính |
|---|---|---|
| `src/main/java/app/ui/dialogs/SaveDesignDialog.java` | `SaveDesignDialog` | Hộp thoại xác nhận lưu cấu trúc bảng bằng `DialogChrome`. Hiển thị: tóm tắt số thay đổi (thêm/sửa/xóa cột, constraint), vùng Monaco xem preview các câu lệnh ALTER, cảnh báo an toàn, các nút `Thực thi ngay`, `Mở trong tab SQL`, `Hủy`. |
| `src/main/java/app/ui/designer/IndexGrid.java` | `IndexGrid` | Bảng TableView quản lý danh sách Index: Tên index, kiểu (Index/Unique), danh sách cột, cột Include, sắp xếp (ASC/DESC), nút Thêm/Xóa index. |
| `src/main/java/app/ui/designer/ForeignKeyGrid.java` | `ForeignKeyGrid` | Bảng TableView quản lý Khóa ngoại (FK): Tên FK, cột bảng hiện tại, bảng tham chiếu (Foreign Table), cột tham chiếu, quy tắc ON DELETE, ON UPDATE. |
| `src/main/java/app/ui/designer/UniqueConstraintGrid.java` | `UniqueConstraintGrid` | Bảng TableView quản lý Ràng buộc Unique: Tên Constraint, danh sách cột tham gia, nút Thêm/Xóa. |
| `src/main/java/app/ui/designer/CheckConstraintGrid.java` | `CheckConstraintGrid` | Bảng TableView quản lý Ràng buộc Check: Tên Constraint, biểu thức điều kiện (Expression/Clause), nút Thêm/Xóa. |
| `src/test/java/app/ui/dialogs/SaveDesignDialogTest.java` | `SaveDesignDialogTest` | Unit test kiểm tra khởi tạo dialog, hiển thị danh sách lệnh DDL, bắt sự kiện click và cờ Read-Only. |
| `src/test/java/app/ui/designer/ConstraintGridsTest.java` | `ConstraintGridsTest` | Unit test kiểm tra thêm/xóa/sửa Index, FK, Unique, Check và ánh xạ vào `TableDesign`. |

### 4.3. Thư viện & Extension bổ sung
- Không cần thư viện bên ngoài mới; tận dụng trọn vẹn JavaFX TableView, AtlantaFX tokens, Ikonli Feather & MaterialDesign2 packs, và Clean Architecture hiện có.

