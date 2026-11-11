# Kế hoạch Thực thi: Hoàn thiện Table Designer (Phase 3 - Slice 1)

> **Mục tiêu:** Tiếp tục hoàn thiện tính năng Table Designer đang dở dang (TD4 & TD5), bổ sung `SaveDesignDialog` và các Sub-tab Grids (Index, Foreign Key, Unique, Check), đảm bảo tuân thủ 100% các nguyên tắc thiết kế desktop.

---

## 1. Tuân thủ Nguyên tắc & Token Thiết kế

### 1.1. Tuân thủ `04-component-rules.md` (Quy tắc Component)
- **Khung Dialog:** Dùng `DialogChrome.create(...)` với title bar chuẩn 34px, có icon `fth-save`, nút Esc đóng, kế thừa theo `panelStyle`. **Không dùng `Alert` hay `Dialog<>` mặc định của JavaFX**.
- **Nút bấm & Trợ năng:** Dùng `AppButton` hoặc JavaFX Button với style class của AtlantaFX (`accent`, `danger`, `small`). Tất cả các nút chỉ có icon (như nút Undo, Redo, Refresh, Toggle Inspector) bắt buộc phải có `Tooltip` và thuộc tính trợ năng `accessibleText`.
- **Thông báo & Trạng thái:** Hiển thị kết quả qua `NotificationService` (ToastHost) và `ReadOnlyGuardDialog` khi kết nối ở chế độ chỉ đọc.
- **Xem trước DDL:** Tận dụng `DdlPreviewPane` và `EmbeddedMonaco` để syntax-highlight DDL migration.

### 1.2. Tuân thủ `02-design-principles.md` (Nguyên tắc Thiết kế Desktop)
- **Mật độ thông tin cao:** Bố cục chia ngăn `SplitPane` có thể co giãn linh hoạt; thanh toolbar nhỏ gọn 32px; thanh sub-tab 28px; dòng trong bảng 24px; status bar 22px.
- **Hỗ trợ đầy đủ 2 chế độ `panelStyle`:** Mọi component và dialog tự động nhận diện chế độ `rounded` (bo 8px/6px) hoặc `flat` (vuông góc 0px) từ `ThemeService` và `shape.css`/`shape-flat.css`.
- **Thao tác phá hủy có xác nhận:** Khi lưu DDL làm thay đổi cấu trúc bảng cũ (như xóa cột, đổi kiểu dữ liệu có nguy cơ mất dữ liệu), `SaveDesignDialog` phải hiển thị cảnh báo đỏ và mặc định trỏ focus vào nút "Hủy".
- **Phím tắt chuẩn Desktop:**
  - `Ctrl+S`: Mở hộp thoại Lưu thay đổi.
  - `Ctrl+Z` / `Ctrl+Y`: Hoàn tác (Undo) / Làm lại (Redo).
  - `F5`: Nạp lại cấu trúc từ CSDL.
  - `Delete`: Xóa dòng cột hoặc constraint được chọn.

### 1.3. Tuân thủ `03-design-tokens.md` (Hệ thống Design Tokens)
- Tuyệt đối không dùng mã màu Hex cố định; sử dụng hệ thống token `-color-*` (AtlantaFX) và `-ide-*`:
  - Màu nền thanh công cụ: `-ide-toolbar-bg` / `-color-bg-subtle`.
  - Màu viền ngăn cách: `-ide-border-color` / `-color-border-subtle`.
  - Badge cảnh báo / thành công: `-color-success-emphasis`, `-color-danger-emphasis`, `-color-warning-emphasis`.
  - Typography: JetBrains Mono 12px cho preview DDL; Inter / Segoe UI cho các nhãn và trường nhập.

---

## 2. Kiến trúc & Phân rã Nhiệm vụ

### Bước 1: Xây dựng các Sub-tab Grids cho Ràng buộc & Chỉ mục
1. Tạo [`IndexGrid.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/IndexGrid.java):
   - TableView hiển thị các cột: Tên Index, Is Unique (CheckBox), Columns (danh sách cột tham gia), Included Columns (cho SQL Server), Sort Order (ASC/DESC).
   - Thanh công cụ nhỏ phía trên Grid: Thêm Index (`fth-plus`), Xóa Index (`fth-trash-2`), Di chuyển lên/xuống.
2. Tạo [`ForeignKeyGrid.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/ForeignKeyGrid.java):
   - TableView hiển thị: Tên Khóa Ngoại, Local Columns, Bảng Tham Chiếu (Foreign Table), Foreign Columns, On Delete (`CASCADE`, `NO ACTION`, `SET NULL`), On Update.
3. Tạo [`UniqueConstraintGrid.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/UniqueConstraintGrid.java) & [`CheckConstraintGrid.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/designer/CheckConstraintGrid.java):
   - TableView trực quan, cho phép người dùng nhập trực tiếp biểu thức điều kiện (Check expression) hoặc chọn các cột tham gia ràng buộc Unique.

### Bước 2: Tích hợp hoán đổi Sub-tab trong `TableDesignerTab`
1. Cập nhật `TableDesignerTab.java`:
   - Lắng nghe sự kiện click trên các nút của `subTabBar` (`Cột`, `Chỉ mục`, `Khóa ngoại`, `Unique`, `Check`).
   - Hoán đổi component ở nửa trên của `leftSplit` tương ứng với tab được chọn (`columnGrid`, `indexGrid`, `foreignKeyGrid`, `uniqueGrid`, `checkGrid`).
   - Khi chuyển tab hoặc dữ liệu thay đổi, gọi `onDesignChanged()` để cập nhật model `currentDesign` và tự động cập nhật preview DDL trong `ddlPreviewPane`.

### Bước 3: Hiện thực Hộp thoại Lưu Migration DDL (`SaveDesignDialog`)
1. Tạo [`SaveDesignDialog.java`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/src/main/java/app/ui/dialogs/SaveDesignDialog.java) bằng `DialogChrome`:
   - Phía trên: Tiêu đề "Áp dụng thay đổi cấu trúc bảng", phụ đề hiển thị `[schema].[table]`.
   - Vùng tóm tắt: Hiển thị Badge thống kê (Ví dụ: `+2 cột mới`, `~1 cột đổi kiểu`, `-1 constraint`).
   - Vùng code DDL: Hiển thị toàn bộ script migration sinh ra từ `MigrationPlan`.
   - Cảnh báo dữ liệu: Nếu có lệnh `DROP COLUMN` hoặc đổi kiểu dữ liệu có thể cắt ngắn dữ liệu, hiển thị hộp cảnh báo màu vàng/đỏ (`-color-warning-muted`).
   - Nút hành động:
     - `Thực thi ngay (Execute)`: Chạy giao dịch migration qua `TableDesignerService.applyMigration()`.
     - `Mở trong tab SQL`: Mở script trong một Query Tab mới để người dùng tự tinh chỉnh.
     - `Hủy`: Đóng hộp thoại không thực hiện thay đổi nào.

### Bước 4: Danh mục Kiểu dữ liệu Động & Tích hợp Read-Only Guardrail
1. Trong `TableDesignerService.java`, thêm `getSupportedTypes(engine)`:
   - PostgreSQL: `integer`, `bigint`, `smallint`, `varchar`, `text`, `boolean`, `numeric`, `double precision`, `timestamp`, `timestamptz`, `date`, `uuid`, `jsonb`, `bytea`.
   - SQL Server: `int`, `bigint`, `smallint`, `tinyint`, `varchar`, `nvarchar`, `text`, `ntext`, `bit`, `decimal`, `numeric`, `float`, `datetime2`, `datetime`, `date`, `uniqueidentifier`, `varbinary`.
2. Truyền danh sách này vào ComboBox gợi ý kiểu dữ liệu trong `ColumnGrid`.
3. Kiểm tra bảo vệ chỉ đọc: Nếu kết nối là Read-Only, khi bấm Lưu hoặc Thực thi sẽ kích hoạt `ReadOnlyGuardDialog` xác nhận trước khi tiếp tục.

---

## 3. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] Người dùng có thể tạo bảng mới (`New Table`), sửa bảng cũ (`Design Table`), và sao chép cấu trúc (`Copy Table`).
- [ ] Chuyển đổi mượt mà giữa các Sub-tab (Columns, Indexes, FKs, Unique, Check Constraints) mà không làm mất trạng thái chỉnh sửa.
- [ ] Mọi thay đổi trong Grid lập tức được phản ánh vào live DDL preview (sau 200ms debounce).
- [ ] Khi bấm Lưu (Ctrl+S), `SaveDesignDialog` xuất hiện hiển thị chi tiết các câu lệnh ALTER/CREATE kèm cảnh báo.
- [ ] Thực thi migration chạy trong 1 transaction duy nhất, tự động rollback và báo lỗi chi tiết nếu có ngoại lệ CSDL.
- [ ] Sau khi lưu thành công, Object Explorer tự động làm mới metadata của bảng.
- [ ] Đạt 100% test case trong `SaveDesignDialogTest` và `ConstraintGridsTest`.

