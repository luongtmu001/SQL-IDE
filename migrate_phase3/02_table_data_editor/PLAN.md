# Kế hoạch Thực thi: Table Data Editor (Phase 3 - Slice 2)

> **Mục tiêu:** Xây dựng không gian làm việc chỉnh sửa dữ liệu bảng trực quan (Table Data Editor) với tính năng chỉnh sửa ô inline, theo dõi thay đổi (Dirty Tracking), kiểm tra kiểu dữ liệu, bảo vệ kết nối chỉ đọc và cam kết giao dịch an toàn (Atomic Transaction).

---

## 1. Tuân thủ Quy tắc & Token Thiết kế

### 1.1. Tuân thủ `04-component-rules.md` (Quy tắc Component)
- **Khung Dialog:** Dùng `DialogChrome.create(...)` cho `PreviewDataChangesDialog` (xem trước kịch bản DML), hỗ trợ phím `Esc` để đóng, đồng bộ theo `appearance.panelStyle`.
- **Nút công cụ & Tooltip:** Sử dụng JavaFX Button với class `.accent` cho nút "Lưu thay đổi" (Save Changes), `.danger` cho nút "Xóa dòng" (Delete Row). Mọi icon button đều có `Tooltip` và `accessibleText`.
- **Trạng thái Trống & Đang tải:** Sử dụng `LoadingOverlay` khi đang nạp dữ liệu từ CSDL; dùng `EmptyState` khi bảng không có dữ liệu hoặc bộ lọc WHERE không tìm thấy kết quả.
- **Badge & Status:** Dùng `Badge` hiển thị tổng số dòng bị sửa đổi (`3 modified`, `1 inserted`, `1 deleted`).

### 1.2. Tuân thủ `02-design-principles.md` (Nguyên tắc Thiết kế Desktop)
- **Chuẩn Desktop DataGrip / VS Code:**
  - Lưới dữ liệu mật độ cao, chiều cao dòng 24px, font `JetBrains Mono 12px`.
  - Phím tắt bàn phím hoàn chỉnh:
    - `Tab` / `Shift+Tab`: Chuyển sang ô tiếp theo/trước đó.
    - `Enter`: Bắt đầu chỉnh sửa ô hoặc xác nhận giá trị mới và chuyển xuống dòng dưới.
    - `Esc`: Hủy bỏ giá trị đang nhập, khôi phục giá trị cũ của ô.
    - `Ctrl+0`: Đặt giá trị ô thành `NULL`.
    - `Ctrl+N`: Thêm dòng mới ở cuối bảng.
    - `Ctrl+Delete`: Đánh dấu xóa dòng hiện tại.
    - `Ctrl+S`: Lưu các thay đổi vào CSDL.
    - `F5`: Nạp lại dữ liệu (Refresh).
- **Hỗ trợ 2 chế độ `panelStyle` (`rounded` vs `flat`):** Ô nhập liệu, ComboBox chọn số lượng dòng, thanh tìm kiếm WHERE tự động phẳng hóa khi người dùng chọn `flat`.
- **Thao tác phá hủy có xác nhận:** Khi lưu các thao tác `DELETE` hoặc `UPDATE` nhiều dòng, nếu người dùng bật tùy chọn "Xem trước kịch bản", `PreviewDataChangesDialog` sẽ hiển thị script SQL cụ thể và mặc định focus vào nút "Hủy".

### 1.3. Tuân thủ `03-design-tokens.md` (Design Tokens)
- Không hardcode màu hex:
  - Ô bị sửa (dirty): Border `-color-warning-emphasis` (hoặc `-ide-dirty-border`), nền `-color-warning-subtle`.
  - Ô mang giá trị `NULL`: Chữ nghiêng `italic`, màu `-color-fg-muted`, văn bản `[NULL]`.
  - Dòng mới thêm: Dấu `+` màu xanh lá `-color-success-emphasis`.
  - Dòng đánh dấu xóa: Gạch ngang văn bản, màu xám `-color-fg-muted`, nền đỏ nhạt `-color-danger-subtle`.

---

## 2. Kiến trúc & Phân rã Nhiệm vụ

### Bước 1: Xây dựng Domain Models & Service Core
1. Tạo package `com.lltool.core.domain.tableeditor.model`:
   - `TableEditorMetadata.java`: Cấu trúc cột, định danh khóa chính, cột tự tăng, cột tính toán.
   - `DataRowChange.java`: Chi tiết thay đổi dòng (Insert / Update / Delete), lưu giá trị cũ và mới.
   - `DataChangeBatch.java`: Quản lý danh sách thay đổi và trạng thái dirty.
2. Tạo `com.lltool.core.application.tableeditor.TableDataEditorService`:
   - `buildSelectQuery(...)`: Sinh câu lệnh `SELECT TOP n` (SQL Server) hoặc `SELECT ... LIMIT n` (PostgreSQL) kèm `WHERE` và `ORDER BY`.
   - `buildDmlStatements(...)`: Sinh câu lệnh INSERT/UPDATE/DELETE. Với UPDATE, áp dụng Optimistic Concurrency:
     ```sql
     UPDATE "schema"."table" SET "col" = 'new_val'
     WHERE "id" = 10 AND ("col" = 'old_val' OR ("col" IS NULL AND 'old_val' IS NULL));
     ```
   - `applyChanges(...)`: Mở kết nối JDBC, tắt AutoCommit (`setAutoCommit(false)`), thực thi tuần tự các câu lệnh, `commit()`, và `rollback()` nếu phát sinh `SQLException`.

### Bước 2: Bổ sung Metadata Reader trong Infrastructure
1. Cập nhật `PostgresDatabaseProvider.java`: Truy vấn `pg_index`, `pg_attribute` để xác định khóa chính và các cột `GENERATED ALWAYS AS IDENTITY` hoặc default `nextval(...)`.
2. Cập nhật `SqlServerDatabaseProvider.java`: Truy vấn `sys.identity_columns` để xác định cột Identity (`is_identity = 1`) và `is_computed = 1`.

### Bước 3: Hiện thực Giao diện Lưới & Inline Cell Editor
1. Tạo `ObservableDataRow.java`: Đại diện cho dòng trong JavaFX `TableView`. Lưu trữ `Map<String, ObjectProperty<Object>>` và `Map<String, Object> originalValues`.
2. Tạo `EditableDataTableCell.java`:
   - Quản lý chuyển đổi giữa chế độ hiển thị (Label) và chế độ nhập liệu (TextField / DatePicker / ComboBox).
   - Xử lý phím `Ctrl+0` để gán giá trị `null` và hiển thị nhãn `[NULL]`.
   - Hiển thị style class `.cell-dirty` khi giá trị hiện tại khác `originalValue`.
3. Tạo `TableDataEditorTab.java`:
   - Thanh công cụ trên: Table title badge, ComboBox chọn số lượng dòng (100, 200, 500, 1000, Tất cả), TextField nhập điều kiện lọc nhanh (WHERE clause), nút Run Filter, Refresh (`F5`), Add Row (`Ctrl+N`), Delete Row (`Ctrl+Del`), Discard Changes, Save Changes (`Ctrl+S`), Script DML.
   - Bảng trung tâm: `TableView<ObservableDataRow>` tự động dựng cột động từ `TableEditorMetadata`. Cột đầu tiên là cột số thứ tự và chỉ báo trạng thái dòng (`*`, `~`, `-`).
   - Thanh trạng thái dưới: Hiển thị tổng số dòng, số ô đã sửa đổi, trạng thái kết nối (Read-Only indicator).

### Bước 4: Hộp thoại Xem trước Kịch bản (`PreviewDataChangesDialog`) & Read-Only Guard
1. Tạo `PreviewDataChangesDialog.java` dùng `DialogChrome`:
   - Hiển thị danh sách câu lệnh INSERT, UPDATE, DELETE sinh ra trước khi chạy.
   - Nút "Thực thi ngay", "Mở trong tab SQL", "Hủy".
2. Kiểm tra cờ Read-Only:
   - Nếu kết nối có `profile.readOnly() == true` hoặc hệ thống chạy `-Dsqlide.readOnly=true`, chặn thao tác sửa ô hoặc hiển thị `ReadOnlyGuardDialog` yêu cầu người dùng xác nhận mở khóa quyền ghi trước khi lưu.

---

## 3. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] Người dùng có thể click chuột phải vào bảng trong Object Explorer và chọn "Chỉnh sửa dữ liệu (Edit Data)".
- [ ] Dữ liệu được nạp nhanh chóng kèm phân trang (mặc định 200 dòng), hỗ trợ lọc biểu thức WHERE tùy ý.
- [ ] Cho phép sửa ô trực tiếp (Double click / Enter), hỗ trợ giá trị `NULL` qua phím tắt `Ctrl+0`.
- [ ] Theo dõi trạng thái dirty chính xác: viền vàng ô bị sửa, hiển thị số thay đổi trên thanh trạng thái.
- [ ] Hỗ trợ thêm dòng mới (tự bỏ qua hoặc xử lý cột tự tăng Identity/Serial).
- [ ] Hỗ trợ đánh dấu xóa dòng và hoàn tác dòng bị xóa trước khi commit.
- [ ] Lưu thay đổi thực thi trong một transaction duy nhất, tự động rollback nếu gặp lỗi khóa ngoại hoặc vi phạm ràng buộc dữ liệu.
- [ ] Hỗ trợ xuất kịch bản DML ra tab SQL mới mà không cần thực thi trực tiếp.
- [ ] Unit test và integration test đạt 100% pass.

