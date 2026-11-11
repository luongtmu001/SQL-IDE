# SCR-TD-001 — Table Designer (New / Edit / Copy Table)
# Đặc tả màn hình: Thiết kế bảng (Tạo mới / Sửa / Sao chép bảng)

- Screen ID: `SCR-TD-001`
- Source: LLTool (Python + PyWebView): `app/services/table_designer_service.py` (1060 dòng), `app/api/designer_api.py` (87 dòng), `static/js/table-designer.js` (3821 dòng), `static/css/table-designer.css`
- Target: JavaFX 21: `app.ui.designer.TableDesignerTab`, `ColumnGrid`, `ColumnInspector`, `DdlPreviewPane`, `SaveDesignDialog`, `CopyTableDialog`, `core-domain` (`TableDesign`, `ColumnDesign`, `IndexDesign`, `ForeignKeyDesign`, `UniqueDesign`, `CheckDesign`, `TriggerDesign`), `core-application` (`TableDesignValidator`, `TableDesignDiffer`, `SqlServerDdlDialect`, `PostgresDdlDialect`), `infrastructure` (`TableDesignReader`)
- Status: `analyzed` (Gate 1 — Mốc TD0)
- Backend area: `metadata` / `query`
- Evidence confidence: `code_verified` (Python source + tests) + `observed` (mockups Canvas 01–04)
- Last inspected: `2026-11-09`
- Loại đặc tả: **Đặc tả thiết kế mới (Gate 1 / Mốc TD0)** theo [feature_10_table_designer_migration_plan.md](file:///docs/feature_10_table_designer_migration_plan.md).

---

## 1. Overview / Tổng quan

- **Mục đích**: Cung cấp giao diện trực quan mạnh mẽ để thiết kế bảng mới (New Table), chỉnh sửa cấu trúc bảng hiện có (Edit Table / Alter Table) và sao chép cấu trúc bảng (Copy Table) trên hai hệ quản trị CSDL chính là **Microsoft SQL Server** và **PostgreSQL** (SQLite giữ menu ở trạng thái `(chưa hỗ trợ)`).
- **Cách mở màn hình (Entry points)**:
  1. *New Table*: Click chuột phải vào node thư mục `Tables` trên cây Object Explorer (`DatabaseTreeCell`) ▸ chọn **"New Table..."** (thay thế menu deferred hiện tại).
  2. *Edit / Design Table*: Click chuột phải vào một bảng bất kỳ trên Object Explorer ▸ chọn **"Design Table"** hoặc chọn bảng rồi bấm phím tắt **`F4`**. Nếu bảng đã mở trong một tab designer trước đó thì tự động kích hoạt chuyển sang tab đó.
  3. *Copy Table*: Click chuột phải vào một bảng trên Object Explorer ▸ chọn **"Copy Table..."** ▸ mở hộp thoại `CopyTableDialog` cấu hình thông số sao chép ▸ bấm "Tạo bản sao" để mở tab New Table nạp sẵn metadata đã nhân bản.
- **Màn hình cha**: `SCR-MAIN-001` (Workspace chính) / `SCR-MAIN-003` (Vùng tab soạn thảo `EditorTabStrip`). Designer được tích hợp như một loại Tab tài liệu trong `EditorTabStrip` (icon `mdi2t-table-plus` hoặc `mdi2t-table-edit`), cho phép mở nhiều bảng song song với các tab truy vấn SQL.
- **Màn hình liên quan**:
  - `SCR-MAIN-002` (Object Explorer: phát lệnh mở và nhận thông báo làm mới cây sau khi DDL được áp dụng thành công).
  - `SCR-MAIN-003` (Editor Tab Strip: quản lý vòng đời tab, phím tắt `Ctrl+S`, cảnh báo tab dirty khi đóng `Ctrl+W`).
  - `SCR-MAIN-005` (Status Bar: hiển thị số lỗi validation và trạng thái tiến trình chạy DDL).

---

## 2. Visual reference / Tham chiếu hình ảnh

Mockup Canvas chính thức: https://claude.ai/artifact/8uNNxsjkr7p2V2DuG3hrYJ

| File ảnh mockup | Mô tả nội dung giao diện |
|---|---|
| `ui-specifications/01-screens/SCR-TD-001/mockups/01-new-table.png` | **Tạo bảng mới**: Tab editor, Header toolbar (Schema, Table Name, Action buttons), Lưới cột (Column Grid), Bảng thuộc tính mở rộng (Column Inspector), Live DDL Preview (Monaco read-only), Footer đếm cột & validation status. |
| `ui-specifications/01-screens/SCR-TD-001/mockups/02-edit-table.png` | **Sửa bảng hiện có**: Đánh dấu trực quan dòng thêm (`+`), sửa (`~`), đổi tên (`↻`), xóa (`−`), checkbox "Chỉ hiện cột đã đổi", banner cảnh báo thao tác phá hủy (destructive warning), Live Migration DDL Preview. |
| `ui-specifications/01-screens/SCR-TD-001/mockups/03-review-apply-dialog.png` | **Hộp thoại Lưu & Áp dụng (Save Design Dialog)**: Danh sách chi tiết các câu lệnh migration, phân loại rủi ro (Destructive badge), bộ lọc xem nhóm câu lệnh, Monaco SQL preview toàn văn, checkbox single transaction, hộp gõ tên bảng để xác nhận xóa dữ liệu, nút Áp dụng (Primary) & "Mở trong tab SQL". |
| `ui-specifications/01-screens/SCR-TD-001/mockups/04-copy-table-dialog.png` | **Hộp thoại Sao chép cấu trúc (Copy Table Dialog)**: Chọn Schema đích, Tên bảng mới (kiểm tra tồn tại DB & giới hạn độ dài), checklist các thành phần sao chép (Cột, PK, Indexes, FKs, Unique, Checks, Triggers), tùy chọn Dữ liệu `(chưa hỗ trợ)`. |

*Quy ước thiết kế*: Toàn bộ giao diện sử dụng hệ thống biến CSS token `-ide-*` và `-color-*`. Không hardcode mã màu hex. Bo góc theo `shape.css` và `shape-flat.css`.

---

## 3. Layout / Bố cục

Tab Designer có bố cục phân cấp 3 lớp co giãn linh hoạt:

1. **Toolbar trên cùng (Header / Action Toolbar)** (chiều cao chuẩn 36px):
   - Nhóm định danh: ComboBox Schema + TextField Tên bảng.
   - Nhóm lệnh chính: Nút **Lưu** (`Ctrl+S`), Nút **Undo** (`Ctrl+Z`), Nút **Redo** (`Ctrl+Y`), Nút **"Mở trong tab SQL"**, Nút **Làm mới** (`F5`), Nút **Đóng/Mở Inspector**, Nút **Đóng/Mở Live DDL Preview**.
2. **Sub-Tab Navigation Bar** (chiều cao 32px):
   - Chuyển đổi giữa các góc nhìn thiết kế:
     * **Cột (Columns)** (mặc định)
     * **Chỉ mục (Indexes)**
     * **Khóa ngoại (Foreign Keys)**
     * **Ràng buộc Unique**
     * **Ràng buộc Check**
     * **Trigger**
3. **Khu vực làm việc trung tâm (Central SplitPane)**:
   - **Vùng trái (Main Design Area)**:
     * **Thanh công cụ dòng (Row Toolbar)** (chiều cao 28px): Các nút Thêm (`Ctrl+N`), Chèn trên (`Ctrl+Insert`), Chèn dưới (`Ctrl+Shift+Insert`), Ô số + nút Thêm N dòng, Nhân bản (`Ctrl+D`), Xóa (`Delete`), Lên (`Alt+Up`), Xuống (`Alt+Down`), Đặt PK, Toggle Not Null, Checkbox "Chỉ hiện cột đã đổi" (ở Edit mode).
     * **Lưới cột (Column Grid TableView)**: Chiếm toàn bộ không gian còn lại, hỗ trợ cuộn ảo hóa (`fixedCellSize` tính theo `grid.fontSize`).
     * **Vùng xem trước Live DDL (Bottom Pane)**: SplitPane dọc (có thể kéo co giãn hoặc ẩn), chứa Monaco Editor read-only hiển thị DDL sinh trực tiếp trong bộ nhớ với debounce 200ms.
   - **Vùng phải (Column Inspector Pane)**:
     * SplitPane ngang, chiều rộng mặc định 290px, có thể kéo co giãn (min 240px, max 450px) hoặc thu gọn hoàn toàn.
     * Hiển thị danh sách thuộc tính nâng cao của cột đang focus: Collation, Identity/Sequence, Computed Column formula, Ràng buộc Default (giá trị vs biểu thức), Danh sách đối tượng phụ thuộc.
4. **Thanh trạng thái chân tab (Footer)** (chiều cao 24px):
   - Nhãn tổng số cột, số cột PK, số thay đổi chưa lưu (ở Edit mode).
   - Chỉ báo Validation: Icon trạng thái + Số lỗi/cảnh báo (click vào để focus ngay tới ô lỗi đầu tiên).

---

## 4. Regions / Các vùng chi tiết

| Region ID | Tên vùng | Mục đích | Vị trí | Kích thước / Co giãn |
|---|---|---|---|---|
| `REG-TD-001` | Header Toolbar | Định danh bảng và thao tác cấp bảng | Top | Width: fill, Height: 36px, cố định |
| `REG-TD-002` | Sub-tab Navigation | Chuyển đổi giữa Cột, Index, FK, Unique, Check, Trigger | Top (dưới REG-TD-001) | Width: fill, Height: 32px, cố định |
| `REG-TD-003` | Row Action Toolbar | Thao tác dòng (Thêm, Xóa, Nhân bản, Di chuyển, v.v.) | Top của sub-tab | Width: fill, Height: 28px, cố định |
| `REG-TD-004` | Column Grid Surface | Bảng dữ liệu cột (`TableView` ảo hóa) | Center trái | Width: flexible, Height: flexible (min 150px) |
| `REG-TD-005` | Column Inspector | Bảng thuộc tính chi tiết cột đang chọn | Right | Width: 290px (resizable 240-450px, collapsable) |
| `REG-TD-006` | Live DDL Preview Pane | Monaco Editor xem trước DDL / Migration SQL | Bottom trái | Height: 160px (resizable 80-400px, collapsable) |
| `REG-TD-007` | Sub-tab Index Surface | Bảng quản lý chỉ mục bảng | Center khi chọn tab Indexes | Width: flexible, Height: flexible |
| `REG-TD-008` | Sub-tab FK Surface | Bảng quản lý khóa ngoại | Center khi chọn tab Foreign Keys | Width: flexible, Height: flexible |
| `REG-TD-009` | Sub-tab Constraints Surface | Bảng quản lý Unique / Check | Center khi chọn tab Constraints | Width: flexible, Height: flexible |
| `REG-TD-010` | Sub-tab Trigger Surface | Bảng quản lý triggers | Center khi chọn tab Triggers | Width: flexible, Height: flexible |
| `REG-TD-011` | Designer Status Footer | Thống kê số lượng, trạng thái hợp lệ, lỗi | Bottom | Width: fill, Height: 24px, cố định |
| `REG-TD-012` | Save Dialog Chrome | Hộp thoại modal duyệt & chạy migration script | Modal window | Width: 820px, Height: 580px, resizable |
| `REG-TD-013` | Copy Dialog Chrome | Hộp thoại modal sao chép cấu trúc bảng | Modal window | Width: 480px, Height: 420px, fixed |

---

## 5. Components and Controls / Thành phần điều khiển

Chi tiết mã định danh các nút, ô nhập, cột được liệt kê tại `components.json`. Các control đáng chú ý:

1. **`COL-GRID-STATUS` (Cột trạng thái dòng)**:
   - Cột cố định đầu tiên, độ rộng 28px.
   - Hiển thị ký hiệu trạng thái: rỗng (chưa đổi), `+` (thêm mới), `~` (đổi thuộc tính), `↻` (đổi tên), `−` (đã đánh dấu xóa - dòng có chữ gạch ngang `strikethrough`).
   - Màu sắc theo token: `-color-success-*`, `-color-warning-*`, `-color-accent-*`, `-color-danger-*`.
2. **`COL-GRID-PK` (Cột khóa chính)**:
   - Độ rộng 32px, căn giữa, icon `mdi2k-key`. Click hoặc ấn phím bật/tắt cờ PK. Khi bật PK thì cờ Not Null (`COL-GRID-NN`) tự động bật và bị khóa không cho bỏ chọn.
3. **`COL-GRID-TYPE` (Ô chọn kiểu dữ liệu)**:
   - ComboBox có autocomplete gợi ý kiểu dữ liệu theo dialect CSDL kết nối (`get_supported_types`).
   - Tự động điền độ dài/scale mặc định phù hợp (VD: chọn `varchar` -> mặc định size 50, chọn `decimal` -> mặc định (18, 2)).
4. **`INSP-DEFAULT-KIND` (Lựa chọn loại Default)**:
   - RadioButton / SegmentedButton: **"Giá trị" (Literal Value)** vs **"Biểu thức" (Expression)** (Khắc phục lỗi **B7**). Nếu chọn "Giá trị", chuỗi nhập vào được tự động bọc nháy đơn và nhân đôi ký tự `'` an toàn qua `sqlLiteral()`.
5. **`SAVE-CONFIRM-INPUT` (Ô nhập tên bảng xác nhận xóa)**:
   - Nằm trong `SaveDesignDialog`. Khi migration plan chứa thao tác phá hủy (Drop Column, Drop Index, Truncate/Recreate Table), nút Áp dụng bị khóa cho đến khi người dùng gõ chính xác tên bảng đích vào ô này (Khắc phục lỗi **B2**).

---

## 6. Interactions / Danh mục tương tác & Quy tắc thao tác dòng

### 6.1 Bộ lệnh thao tác dòng bắt buộc (TD-ROW-01 → TD-ROW-15)

Mọi thao tác làm việc trên **tập dòng đang chọn** (Multi-row selection qua Shift / Ctrl), được quản lý qua `UndoManager` hỗ trợ `Ctrl+Z` / `Ctrl+Y`.

| ID | Lệnh | Phím tắt | Xử lý một / nhiều dòng | Vùng chọn & Focus sau lệnh |
|---|---|---|---|---|
| `TD-ROW-01` | Thêm dòng ở cuối | `Ctrl+N`; `Tab` ở ô cuối dòng cuối | Luôn thêm 1 dòng mới vào cuối bảng | Dòng mới, focus ô Tên, vào chế độ sửa |
| `TD-ROW-02` | Chèn dòng phía trên | `Ctrl+Insert` | Chèn trên dòng đầu tiên đang chọn | Dòng mới được chọn |
| `TD-ROW-03` | Chèn dòng phía dưới | `Ctrl+Shift+Insert` | Chèn dưới dòng cuối cùng đang chọn | Dòng mới được chọn |
| `TD-ROW-04` | Thêm nhiều dòng | Ô số + nút Thêm (1–100) | Chèn khối N dòng dưới dòng đang chọn (hoặc cuối bảng) | Chọn toàn bộ các dòng vừa thêm |
| `TD-ROW-05` | Nhân bản dòng | `Ctrl+D` | Sao chép các dòng đang chọn, đặt ngay dưới khối chọn. Tên tự đổi `_copy`, `_copy2`. `originalName = null`, `ColumnId` mới (Sửa **B1**) | Chọn các dòng bản sao vừa tạo |
| `TD-ROW-06` | Sao chép / Cắt | `Ctrl+C` / `Ctrl+X` | Nạp vào clipboard nội bộ + Clipboard hệ thống dạng **TSV** (dán được ra Excel) | Giữ nguyên / Chọn dòng kế tiếp |
| `TD-ROW-07` | Dán dòng | `Ctrl+V` | Chèn dưới dòng cuối đang chọn (không ghi đè - Sửa **B14**). Hỗ trợ parse TSV từ Excel | Chọn các dòng vừa được dán |
| `TD-ROW-08` | Di chuyển lên / xuống | `Alt+↑` / `Alt+↓` | Di chuyển cả khối dòng đang chọn lên/xuống 1 bậc; dừng khi chạm biên | Vùng chọn di chuyển theo các dòng |
| `TD-ROW-09` | Lên đầu / Xuống cuối | `Alt+Home` / `Alt+End` | Đưa cả khối dòng lên trên cùng / xuống dưới cùng | Giữ nguyên tập dòng đang chọn |
| `TD-ROW-10` | Kéo thả dòng | Chuột kéo | Kéo cả khối đang chọn, hiển thị vạch chỉ vị trí thả | Vùng chọn giữ nguyên trên các dòng kéo |
| `TD-ROW-11` | Xóa dòng | `Delete` (khi không sửa ô) | Xóa mọi dòng đang chọn. Nếu cột có ràng buộc phụ thuộc (FK, Index, Computed), hiện `ConfirmDialog` xác nhận (mặc định Hủy) (Sửa **B4**) | Chọn dòng ngay sau vùng xóa |
| `TD-ROW-12` | Chọn tất cả | `Ctrl+A` (khi không sửa ô) | Chọn toàn bộ các dòng trên lưới (không tự copy - Sửa **B14**) | Toàn bộ các dòng |
| `TD-ROW-13` | Đặt / bỏ Khóa chính | Phím tắt hoặc nút PK | PK nhiều cột theo thứ tự chọn; bật PK tự động bật Not Null | Giữ nguyên vùng chọn |
| `TD-ROW-14` | Bật / tắt Not Null | `Space` trên ô NN | Áp dụng toggle cho mọi dòng đang chọn | Giữ nguyên vùng chọn |
| `TD-ROW-15` | Hoàn tác / Làm lại | `Ctrl+Z` / `Ctrl+Y` | Khôi phục trạng thái mô hình trước lệnh gần nhất | Phục hồi vùng chọn tương ứng |

### 6.2 Tương tác lưu và áp dụng (Save & Migration Workflow)

1. Khi người dùng bấm `Ctrl+S` hoặc nút "Lưu":
   - Chạy `TableDesignValidator` kiểm tra tính hợp lệ toàn diện. Nếu có lỗi nghiêm trọng (tên rỗng, tên trùng, kiểu dữ liệu không hợp lệ), hiển thị popover đỏ chỉ rõ lỗi và dừng lưu.
   - Nếu bảng hợp lệ:
     * *New Table*: Nếu CSDL đang ở chế độ Read-Only (`-Dsqlide.readOnly=true`), hiển thị thông báo cấm chạy DDL và gợi ý mở sang tab SQL. Nếu cho phép ghi, mở `SaveDesignDialog` hiển thị DDL `CREATE TABLE`.
     * *Edit Table*: `TableDesignDiffer` tính toán danh sách `MigrationStatement` trong bộ nhớ. Mở `SaveDesignDialog`.
2. Trong `SaveDesignDialog`:
   - Hiển thị danh sách câu lệnh theo thứ tự chuẩn: Đổi tên bảng → Drop FK → Drop Index → Drop Unique → Drop Check → Drop Default → Drop PK → Đổi tên cột → Drop Cột → Thêm Cột → Alter Cột → Thêm Default → Thêm PK → Thêm Unique → Thêm Check → Thêm FK → Thêm Index → Comment → Drop Trigger → Tạo Trigger.
   - Nếu có câu lệnh phá hủy dữ liệu (Destructive): Ô xác nhận yêu cầu người dùng gõ tên bảng.
   - Bấm **"Áp dụng"**:
     * Đóng dialog, kích hoạt luồng chạy ngầm qua `TabSessionManager`.
     * Thực thi toàn bộ migration script trong một Transaction duy nhất (`execute_migration_transaction`). Nếu bất kỳ câu lệnh nào lỗi, toàn bộ transaction tự động Rollback.
     * Áp dụng thành công: Gửi tín hiệu làm mới metadata (`MetadataSnapshotStore.invalidateCache`), làm mới thư mục `Tables` trên Object Explorer (Sửa **B20**), cập nhật `originalData = modifiedData`, chuyển tab sang trạng thái Clean (`dirty = false`).
   - Bấm **"Mở trong tab SQL"**:
     * Mở một tab soạn thảo SQL mới trong `SCR-MAIN-003` chứa toàn bộ migration script để người dùng tự do xem xét, tinh chỉnh và chạy thủ công.

---

## 7. States / Các trạng thái giao diện

Chi tiết trong `states.json`. Các trạng thái tổng thể quan trọng:

1. **State: `new_clean` (Bảng mới tạo)**:
   - Tên mặc định `Table_1` (được kiểm tra không trùng với các bảng hiện hữu trong schema - Sửa **B15**).
   - Một dòng cột mặc định: SQL Server là `Id int IDENTITY(1,1) PRIMARY KEY`, PostgreSQL là `id serial PRIMARY KEY`.
   - Cờ `dirty = false`, Live DDL hiển thị câu lệnh `CREATE TABLE`.
2. **State: `editing_modified` (Đang sửa đổi cấu trúc)**:
   - Xuất hiện dấu `*` trên tiêu đề tab.
   - Các dòng trên Column Grid hiển thị ký hiệu và màu trạng thái tương ứng (`+`, `~`, `↻`, `−`).
   - DDL Preview liên tục cập nhật script migration qua luồng nền (debounce 200ms).
   - Nút Lưu (`Ctrl+S`) và nút "Mở trong tab SQL" được kích hoạt.
3. **State: `read_only_mode` (Chế độ CSDL chỉ đọc)**:
   - Kích hoạt khi chạy với `-Dsqlide.readOnly=true` hoặc cờ kết nối `read_only = true`.
   - Toàn bộ tính năng thiết kế, chỉnh sửa, xem trước DDL, sinh script vẫn hoạt động bình thường.
   - Nút **"Lưu"** bị vô hiệu hóa (disabled) kèm Tooltip: *"Kết nối đang ở chế độ CHỈ ĐỌC (Read-Only). Không thể áp dụng DDL trực tiếp. Hãy dùng nút 'Mở trong tab SQL'."*
4. **State: `validation_error` (Có lỗi cấu trúc)**:
   - Chân footer hiển thị số lỗi màu đỏ (VD: `⚠ 2 lỗi cấu trúc`).
   - Các ô bị lỗi trên lưới cột được viền đỏ (`-color-danger-border`).
   - Nút Lưu bị khóa, click vào lỗi ở footer sẽ tự cuộn và focus ngay vào ô nhập bị lỗi.

---

## 8. JavaFX Design & Architecture Proposal / Đề xuất kiến trúc JavaFX

### 8.1 Phân chia mô-đun chuẩn

1. **`core/core-domain`** (Thuần Java, không phụ thuộc JavaFX):
   - `TableDesign`: Định danh schema, name, engine/options, comment, danh sách columns, indexes, foreign keys, unique constraints, check constraints, triggers.
   - `ColumnDesign`:
     * `ColumnId id`: UUID bất biến nội bộ dùng xuyên suốt phiên thiết kế (Giải quyết triệt để lỗi **B1** và **B4**).
     * `String originalName`: Tên gốc trong CSDL (chỉ gán khi tải từ DB, nhân bản hoặc tạo mới luôn mang giá trị `null`).
     * `String name`, `String dataType`, `Integer length`, `Integer scale`, `boolean notNull`, `boolean primaryKey`.
     * `String defaultValue`, `boolean defaultIsExpression`, `String defaultConstraintName`.
     * `String collation`, `boolean identity`, `long identitySeed`, `long identityIncrement`.
     * `String sequenceName`, `String computedFormula`, `boolean computedPersisted`, `String comment`.
   - `IndexDesign`, `ForeignKeyDesign`, `UniqueDesign`, `CheckDesign`, `TriggerDesign`.
2. **`core/core-application`**:
   - `TableDesignValidator`: Kiểm tra quy tắc nghiệp vụ: tên rỗng, tên trùng (case-insensitive), độ dài hợp lệ, kiểu dữ liệu đòi hỏi length/scale.
   - `TableDesignDiffer`: So sánh `originalData` và `modifiedData`, sinh `MigrationPlan` gồm danh sách `MigrationStatement`, danh sách cảnh báo `warnings` và cờ `hasDestructiveChanges`.
   - `DdlDialect`: Interface định nghĩa sinh SQL; cài đặt cụ thể `SqlServerDdlDialect` và `PostgresDdlDialect`.
3. **`infrastructure`**:
   - `TableDesignReader`: Interface trích xuất metadata chi tiết từ hệ thống catalog DB (scale, identity seed/increment, collation, computed formula, comment, default constraint name). Cài đặt cho SQL Server (`sys.columns`, `sys.tables`, ...) và PostgreSQL (`information_schema`, `pg_catalog`).
4. **`app.ui.designer`**:
   - `TableDesignerTab`: Kế thừa `EditorTabItem`, tích hợp vào `EditorTabStrip`.
   - `ColumnGrid`: Bọc `TableView<ColumnDesignModel>`, cấu hình cell factories chuyên biệt cho từng kiểu cột, gán `fixedCellSize` đồng bộ với `grid.fontSize`.
   - `ColumnInspector`: VBox chứa các nhóm thuộc tính nâng cao dạng Accordion / TitledPane phẳng.
   - `DdlPreviewPane`: Nhúng `EmbeddedMonaco` chỉ đọc, áp dụng theme và font editor đồng bộ từ `Settings`.
   - `SaveDesignDialog`: Bọc qua `DialogChrome`, sử dụng `ConfirmDialog` cho các xác nhận nguy hiểm.

### 8.2 Tuân thủ nguyên tắc thiết kế & hiệu năng

- **Quy tắc Font chữ theo Settings**:
  - Không dùng cỡ chữ cố định (px) trong CSS. Dùng đơn vị `em` để kế thừa từ root scene.
  - Chữ giao diện (Buttons, Labels, Inspector): Đồng bộ theo `appearance.uiFontFamily` và `appearance.uiFontSize`.
  - Lưới cột (Column Grid): Đồng bộ theo `grid.fontFamily` và `grid.fontSize`. Chiều cao dòng `fixedCellSize` tự tính toán theo công thức `fontSize * 1.8 + 6`.
  - Vùng Monaco DDL Preview: Đồng bộ theo `editor.fontFamily` và `editor.fontSize`.
- **Hiệu năng (Performance Rules)**:
  - Bảng hỗ trợ mượt mà lên tới 300 cột mà không có độ trễ gõ phím.
  - Quá trình tính toán Diff và sinh script DDL luôn được đẩy sang luồng tính toán nền (`CompletableFuture`), debounce 200ms, hủy bỏ các tác vụ tính toán cũ khi có phím mới được gõ (Khắc phục lỗi **B19**).

