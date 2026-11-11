# Khảo sát & Đánh giá Tính năng: Table Data Editor (Chỉnh sửa Dữ liệu Bảng)

---

## 1. Khảo sát mã nguồn Python gốc

### 1.1. Các file và vị trí trong Python repository
- **Backend Service:** `app/services/table_data_editor_service.py` (65,538 bytes)
  - Class: `TableDataEditorService`
  - Các hàm cốt lõi:
    - `get_metadata(adapter, database, schema, table)`: Lấy cấu trúc chi tiết phục vụ chỉnh sửa dữ liệu (Primary Keys, Auto Increment/Identity, Computed columns, Data types, Max length, Nullable, Default values).
    - `fetch_data(adapter, database, schema, table, select_cols, criteria, top_n, sort_by)`: Sinh câu lệnh SELECT có giới hạn (TOP trong SQL Server, LIMIT trong PostgreSQL), hỗ trợ lọc điều kiện WHERE và sắp xếp đa cột.
    - `build_dml_statements(database, schema, table, changes, engine, concurrency_mode)`:
      - `INSERT`: Bỏ qua các cột computed, tự động quản lý `SET IDENTITY_INSERT` khi người dùng nhập giá trị cho cột Identity trong SQL Server.
      - `UPDATE`: Sử dụng Optimistic Concurrency Control (kiểm tra `WHERE pk = :old_pk AND col = :old_val`) để phát hiện xung đột dữ liệu đồng thời (Lost Updates).
      - `DELETE`: Xóa dòng theo khóa chính hoặc tập giá trị định danh.
    - `validate_rows(rows, metadata, engine)`: Kiểm tra tính hợp lệ về kiểu dữ liệu (số nguyên, số thực, UUID, ngày giờ, chuỗi vượt quá độ dài) trước khi đẩy xuống CSDL.
    - `execute_changes(adapter, database, statements, transactional)`: Chạy toàn bộ DML trong một transaction duy nhất; rollback tức thì nếu có lỗi.
- **Backend API Mixin:** `app/api/table_editor_api.py` (12,869 bytes)
  - Class: `TableEditorApiMixin`
  - Endpoints:
    - `get_table_editor_metadata(connection_id, database, schema, table)`
    - `fetch_table_data(connection_id, database, schema, table, ...)`
    - `preview_data_changes(connection_id, ...)`: Trả về kịch bản SQL INSERT/UPDATE/DELETE và token xem trước.
    - `execute_data_changes(connection_id, ...)`: Thực thi kịch bản đã duyệt.
    - `tx_begin`, `tx_commit`, `tx_rollback`, `tx_status`: Quản lý phiên giao dịch thủ công.
    - `set_connection_read_only`: Đổi trạng thái an toàn kết nối.
- **Frontend UI & Script:**
  - `static/js/table-data-editor.js` (273,569 bytes):
    - Lưới dữ liệu tương tác cao: chỉnh sửa trực tiếp trên ô (inline editing), chọn ô, điều hướng phím mũi tên/Tab/Enter.
    - Phân biệt rõ ràng giữa giá trị rỗng `""` và giá trị `NULL` (hiển thị nghiêng màu xám `[NULL]`). Phím tắt `Ctrl+0` đặt ô thành `NULL`.
    - Trạng thái Dirty tracking: Viền vàng cho ô bị sửa, dấu `+` màu xanh lá cho dòng mới thêm, gạch ngang màu đỏ cho dòng bị đánh dấu xóa.
    - Thanh điều khiển: Chọn số lượng dòng tải (100, 200, 500, 1000, Tất cả), ô nhập bộ lọc WHERE nhanh, nút Thêm dòng, Xóa dòng, Hủy thay đổi, Lưu thay đổi (Commit), Rollback, Sinh script SQL.
- **Test suite Python:**
  - `tests/test_table_data_editor.py` (66,648 bytes), `tests/test_phase4_table_data_editor_quoting.py`.

---

## 2. Khảo sát hiện trạng mã nguồn Java

Hiện tại tính năng Table Data Editor **chưa được triển khai trong Java**.
- Trong `DatabaseTreeCell.java` hiện có mục menu ngữ cảnh `Select Top 1000 Rows` mở tab truy vấn thông thường, nhưng chưa có chế độ `Edit Data` cho phép chỉnh sửa dữ liệu trực tiếp với giao dịch an toàn.
- Tuy nhiên, hạ tầng Java hiện có sẵn các thành phần dùng lại rất tốt:
  - `SqlEditorTab`: Có thể mở rộng để chứa `TableDataEditorTab`.
  - `DialogChrome`: Dùng cho `PreviewDataChangesDialog`.
  - `ReadOnlyGuardDialog`: Dùng để bảo vệ kết nối chỉ đọc khi người dùng chỉnh sửa dữ liệu.
  - `DesktopMetadataService` & `ConnectionSessionManager`: Kết nối JDBC có quản lý session và transaction.

---

## 3. Chi tiết danh sách File cần Sửa & File Thêm Mới

### 3.1. Các file cần sửa (Modified Files)

| File Path | Class | Phương thức cần sửa/thêm | Lý do & Nội dung sửa |
|---|---|---|---|
| `src/main/java/app/ui/components/DatabaseTreeCell.java` | `DatabaseTreeCell` | `createContextMenuForTable(...)` | Bổ sung mục menu ngữ cảnh: **"Chỉnh sửa dữ liệu (Edit Data - Top 200)"** kích hoạt mở `TableDataEditorTab`. |
| `src/main/java/app/controllers/MainController.java` | `MainController` | `openTableDataEditor(profile, catalog, schema, table)` | Phương thức điều phối tạo và mở tab `TableDataEditorTab` trong thanh tab chính `EditorTabStrip`. |
| `infrastructure/database-api/src/main/java/com/lltool/infrastructure/database/api/DatabaseProvider.java` | `DatabaseProvider` | `getTableEditorMetadata(conn, catalog, schema, table)` | Định nghĩa interface lấy thông tin metadata phục vụ chỉnh sửa dữ liệu (PK, Identity, Computed). |
| `infrastructure/database-postgresql/src/main/java/com/lltool/infrastructure/database/postgresql/PostgresDatabaseProvider.java` | `PostgresDatabaseProvider` | `getTableEditorMetadata(...)` | Cài đặt truy vấn `information_schema` và `pg_attribute` để lấy PK, kiểu dữ liệu, cờ auto-increment (`serial`/`generated`). |
| `infrastructure/database-sqlserver/src/main/java/com/lltool/infrastructure/database/sqlserver/SqlServerDatabaseProvider.java` | `SqlServerDatabaseProvider` | `getTableEditorMetadata(...)` | Cài đặt truy vấn `sys.columns`, `sys.tables`, `sys.identity_columns` để lấy PK, Identity, Computed. |

### 3.2. Các file thêm mới (New Files)

| File Path | Class / Resource | Trách nhiệm & Phương thức chính |
|---|---|---|
| `core/core-domain/src/main/java/com/lltool/core/domain/tableeditor/model/TableEditorMetadata.java` | `TableEditorMetadata` | Chứa danh sách cột, cột khóa chính (`primaryKeyColumns`), cột tự tăng (`identityColumns`), cột tính toán (`computedColumns`), kiểu dữ liệu và nullability. |
| `core/core-domain/src/main/java/com/lltool/core/domain/tableeditor/model/DataRowChange.java` | `DataRowChange` | Model đại diện một thay đổi: Loại thay đổi (`INSERT`, `UPDATE`, `DELETE`), giá trị cũ (`oldValues`), giá trị mới (`newValues`), khóa chính (`pkValues`). |
| `core/core-domain/src/main/java/com/lltool/core/domain/tableeditor/model/DataChangeBatch.java` | `DataChangeBatch` | Tập hợp các thay đổi trên bảng đang chờ commit, hỗ trợ đếm số ô/dòng dirty, kiểm tra xung đột. |
| `core/core-application/src/main/java/com/lltool/core/application/tableeditor/TableDataEditorService.java` | `TableDataEditorService` | Service xử lý nghiệp vụ: sinh câu lệnh SELECT có phân trang/lọc/sắp xếp; sinh kịch bản SQL DML (Insert, Update, Delete) theo dialect; thực thi batch thay đổi trong 1 JDBC transaction có rollback. |
| `src/main/java/app/ui/dataeditor/TableDataEditorTab.java` | `TableDataEditorTab` | Tab kế thừa `SqlEditorTab`. Chứa: Header toolbar (bộ lọc WHERE, chọn giới hạn dòng, nút Thêm/Xóa dòng, Hủy, Lưu, Script SQL), Center Grid (`TableView` có cell editor tương tác), Footer status bar. |
| `src/main/java/app/ui/dataeditor/ObservableDataRow.java` | `ObservableDataRow` | View-model cho mỗi dòng dữ liệu trên Grid, hỗ trợ dirty tracking từng ô, trạng thái dòng (`UNCHANGED`, `INSERTED`, `MODIFIED`, `DELETED`). |
| `src/main/java/app/ui/dataeditor/EditableDataTableCell.java` | `EditableDataTableCell` | Custom TableCell của JavaFX: Cho phép double-click chỉnh sửa inline; đổi ô thành `NULL` khi ấn `Ctrl+0`; hiển thị `[NULL]` nghiêng màu xám; highlight viền vàng khi dirty. |
| `src/main/java/app/ui/dialogs/PreviewDataChangesDialog.java` | `PreviewDataChangesDialog` | Hộp thoại xem trước các câu lệnh INSERT/UPDATE/DELETE sinh ra trước khi chạy thật; xây dựng bằng `DialogChrome`. |
| `src/main/resources/css/table-data-editor.css` | CSS Stylesheet | Token màu sắc theo `-ide-*` và `-color-*` cho trạng thái ô (`.cell-dirty`, `.cell-null`, `.row-inserted`, `.row-deleted`). |
| `src/test/java/app/ui/dataeditor/TableDataEditorServiceTest.java` | Unit Test | Test sinh câu lệnh SELECT, DML INSERT/UPDATE/DELETE, xử lý quote identifier và optimistic locking. |
| `src/test/java/app/ui/dataeditor/ObservableDataRowTest.java` | Unit Test | Test tracking dirty state, hoàn tác ô, chuyển đổi giá trị kiểu dữ liệu. |

---

## 4. Thư viện & Phụ thuộc
- Sử dụng JavaFX TableView, AtlantaFX tokens và JDBC Drivers sẵn có. Không cần thêm thư viện ngoài.

