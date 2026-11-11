# Feature 10 — Table Designer (New / Edit / Copy Table): phân tích Python và kế hoạch migrate sang Java

> Ngày: 2026-11-09 · Trạng thái: `IN_PROGRESS` (TD0, TD1, TD2, TD3, TD5 đã hoàn thành, chuẩn bị TD4/TD6) · Màn hình mới: **SCR-TD-001**
> Tuân thủ: `docs/java-desktop-ui-designer-skill-v1.0/.../rules/01…06`, `docs/performance-rules.md`.

## 1. Nguồn đã đọc (Python, `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`)

| File | Dòng | Vai trò |
|---|---|---|
| `app/services/table_designer_service.py` | 1060 | Sinh `CREATE TABLE`, sinh diff/migration, cảnh báo phá hủy |
| `app/api/designer_api.py` | 87 | API: `get_table_design`, `get_table_design_types`, `preview_table_design`, `diff_table_design`, `apply_table_design` |
| `app/database/{sqlserver,postgresql,sqlite}.py` | — | `execute_migration_transaction` (một transaction, lỗi thì rollback) |
| `static/js/table-designer.js` | 3821 | Toàn bộ UI: `openTable`, `openNewTable`, `copyTable`, thao tác dòng, inspector, live SQL, hộp thoại lưu |
| `static/css/table-designer.css` | — | Giao diện |
| `tests/test_table_designer*.py`, `test_phase4_*` | — | Test hiện có (dùng làm corpus đối chiếu) |

Phạm vi Python: **SQL Server và PostgreSQL**. SQLite chỉ có xử lý tên, không có designer thực sự.

## 2. Mockup

Canvas (sửa được): https://claude.ai/artifact/8uNNxsjkr7p2V2DuG3hrYJ

| Ảnh | Nội dung |
|---|---|
| `ui-specifications/01-screens/SCR-TD-001/mockups/01-new-table.png` | Tạo bảng: tab editor, lưới cột, inspector, xem trước DDL, menu chuột phải đủ thao tác dòng, chọn nhiều dòng |
| `…/02-edit-table.png` | Sửa bảng: đánh dấu thêm / sửa / đổi tên / xóa, script thay đổi, cảnh báo phá hủy |
| `…/03-review-apply-dialog.png` | Hộp thoại Lưu: danh sách câu lệnh, transaction, gõ tên bảng để xác nhận xóa |
| `…/04-copy-table-dialog.png` | Sao chép cấu trúc: tên mới, hậu tố ràng buộc, chọn thành phần |

Ảnh dùng màu Primer Dark và font dự phòng của máy chụp. Đây là ảnh tham khảo, không phải đặc tả token: khi làm thật phải dùng biến `-ide-*` / `-color-*` (mục 6).

## 3. Luồng hiện tại trong Python

**Mở**
- *Design Table*: `openTable` → `GET table-design` + `table-design/types` (có cache theo `connId_database`) → tạo session `{originalData, modifiedData}`; mỗi cột gắn `original_name` để nhận diện đổi tên. Nếu bảng đã mở thì chuyển sang tab đó.
- *New Table*: `openNewTable` → tên `Table_N`, một cột mặc định (`Id int IDENTITY` cho SQL Server, `id serial` cho PostgreSQL), `originalData = null`.
- *Copy Table*: `copyTable` → tải metadata bảng nguồn, đổi tên thành `<tên>_Copy`, thêm hậu tố `_Copy` cho index/FK/unique/check/trigger, mở như bảng mới.

**Sửa**: lưới cột (tên, kiểu, độ dài, scale, NN, mô tả) + inspector (default, collation, identity, sequence, computed) + sub-tab Index / Khóa ngoại / Unique / Check / Trigger. Mỗi lần sửa gọi `markDirty` → POST `/diff` (debounce 300 ms) để cập nhật Live SQL.

**Lưu**: `openSaveConfirmationModal` → POST `/diff` → hiện audit, cảnh báo phá hủy, script sửa được (Monaco/CodeMirror), lọc theo nhóm → `/apply` với `migration_sql` → backend tách theo `GO`, bỏ BEGIN/COMMIT, chạy trong một transaction → tải lại metadata, làm mới node Explorer.

**Thứ tự câu lệnh trong diff** (giữ nguyên khi port): đổi tên bảng → drop FK → drop index → drop unique → drop check → drop default → drop PK → đổi tên cột → drop cột → thêm cột → alter cột → thêm default → thêm PK → unique → check → FK → index → comment → drop trigger → tạo trigger.

## 4. Lỗi phát hiện

Cột "Tái hiện": `có` = đã chạy thử hàm Python trong venv của dự án và thấy kết quả sai; `đọc code` = kết luận từ việc đọc code, chưa chạy.

| # | Mức | Lỗi | Bằng chứng | Tái hiện | Cách sửa khi port |
|---|---|---|---|---|---|
| B1 | **Nghiêm trọng** | Dán hoặc nhân bản cột ở bảng đã có: bản sao giữ `original_name` của cột gốc, nên diff hiểu là **đổi tên cột gốc**. Ví dụ copy `Email` → sinh `sp_rename … [Email] → Email_copy`, cột gốc bị đổi tên thay vì thêm cột mới | `table-designer.js:595-606` (`clone` giữ `original_name`), `table_designer_service.py:777-801` | có | Mỗi cột có `ColumnId` nội bộ bất biến và `originalName` chỉ gán khi tải từ DB. Bản sao luôn có `originalName = null` |
| B2 | Cao | Hộp thoại Lưu đang lọc theo nhóm thì chỉ chạy phần script đã lọc, nhưng sau đó đánh dấu toàn bộ thay đổi là đã lưu và tải lại từ DB, **làm mất phần thay đổi chưa chạy** | `table-designer.js:3225-3275`, `3346-3399` | đọc code | Lọc chỉ để xem. Lưu luôn chạy toàn bộ script sinh từ mô hình. Muốn sửa tay thì mở sang tab SQL |
| B3 | Cao | PostgreSQL: index mới mặc định `index_type = 'NONCLUSTERED'` → sinh `USING nonclustered`, câu lệnh lỗi | `table-designer.js:705`, `table_designer_service.py:382-384` | có | Kiểu index là enum theo dialect (SQL Server: CLUSTERED/NONCLUSTERED; PostgreSQL: btree/hash/gin/gist/brin) |
| B4 | Cao | Đổi tên hoặc xóa cột không cập nhật index / FK / unique đang dùng cột đó → DDL tham chiếu cột không tồn tại | `table-designer.js:2614-2617`, `995`, `2116-2131` | đọc code | Ràng buộc tham chiếu cột theo `ColumnId`. Đổi tên tự cập nhật; xóa thì hỏi: xóa luôn ràng buộc phụ thuộc hay hủy |
| B5 | Cao | SQL Server `ALTER COLUMN` lỗi khi cột đang nằm trong index / unique / PK / FK / default (không drop rồi tạo lại). Đồng thời `ALTER COLUMN` không ghi `COLLATE`, nên collation của cột bị đổi ngầm về mặc định của DB | `table_designer_service.py:875-885` | đọc code | Diff tính tập đối tượng phụ thuộc, drop trước và tạo lại sau. Luôn ghi `COLLATE` hiện tại cho kiểu ký tự |
| B6 | Trung bình | `size` là `None` ở bản gốc và `''` ở bản sửa bị coi là đổi kiểu (`str(None) != ''`) → sinh `ALTER COLUMN` thừa | `table_designer_service.py:864` | có | Chuẩn hóa độ dài và scale về `OptionalInt` trước khi so sánh |
| B7 | Trung bình | Default kiểu chuỗi có dấu ngoặc (vd. `Hello (world)`) bị coi là biểu thức, không được bọc nháy → SQL sai | `table_designer_service.py:100-101` | có | Inspector có lựa chọn rõ: "Giá trị" hoặc "Biểu thức". Mặc định là Giá trị |
| B8 | Trung bình | Sửa trigger có sẵn: chạy lại `CREATE TRIGGER` mà không drop → lỗi "already exists" trên SQL Server | `table_designer_service.py:966-975` | đọc code | SQL Server dùng `CREATE OR ALTER TRIGGER`. PostgreSQL dùng `CREATE OR REPLACE TRIGGER`, hoặc drop rồi tạo |
| B9 | Trung bình | Thêm cột vào bảng có sẵn bỏ qua identity, cột tính toán và collation | `table_designer_service.py:826-850` | đọc code | Dùng chung một hàm sinh định nghĩa cột cho cả CREATE và ADD |
| B10 | Trung bình | Copy Table: `definition.replaceAll(name, …)` thay cả chuỗi con (bảng `Order` thay luôn trong `OrderItems`). Tên trigger bên trong definition không được đổi nên bị trùng. Không kiểm tên bảng đích đã tồn tại. Tên có hậu tố có thể vượt giới hạn 128 (SQL Server) / 63 (PostgreSQL) | `table-designer.js:389`, `474-480` | đọc code | Hộp thoại Copy (mockup 04): kiểm tên với DB, cắt tên theo giới hạn, trigger mặc định không chép và nếu chép thì đánh dấu cần rà lại |
| B11 | Trung bình | Xóa bằng menu chuột phải chỉ xóa 1 dòng, còn nút toolbar xóa mọi dòng đang chọn. Menu ghi phím tắt `Delete` nhưng `onkeydown` không xử lý `Delete` | `table-designer.js:988-1000`, `2470-2509` | đọc code | Mọi lệnh dòng dùng chung một `RowCommand` theo tập dòng đang chọn (mục 5) |
| B12 | Thấp | Di chuyển lên/xuống chỉ chạy với 1 dòng và không cập nhật `selectedFieldIndices` | `table-designer.js:533-557` | đọc code | Di chuyển cả khối dòng đang chọn và giữ nguyên vùng chọn |
| B13 | Thấp | Tên cột mới `Column_{n+1}` có thể trùng sau khi xóa. Không kiểm tên trùng hoặc rỗng; diff lập dict theo tên nên hai cột trùng tên bị gộp lặng lẽ | `table-designer.js:646`, `table_designer_service.py:521-522` | đọc code | Validator chạy trên mô hình: tên rỗng, tên trùng (không phân biệt hoa thường), độ dài, kiểu thiếu độ dài. Có lỗi thì không cho Lưu |
| B14 | Thấp | `Ctrl+A` vừa chọn hết vừa chép vào clipboard. Dán khi đang chọn nhiều dòng sẽ **thay thế** các dòng đó mà không hỏi | `table-designer.js:2490-2495`, `609-619` | đọc code | `Ctrl+A` chỉ chọn. Dán luôn chèn bên dưới dòng cuối đang chọn |
| B15 | Thấp | Tên mặc định `Table_N` chỉ tránh trùng với các tab đang mở, không kiểm với bảng trong DB | `table-designer.js:256-268` | đọc code | Kiểm với danh sách bảng của schema (Explorer cache) |
| B16 | Thấp | Tên đối tượng ghép vào chuỗi `N'…'` (`sp_rename`, extended property) mà không nhân đôi dấu `'` | `table_designer_service.py:550`, `139-153`, `789` | đọc code | Hàm `sqlLiteral()` dùng chung cho mọi literal |
| B17 | Thấp | `Optional` dùng trong chữ ký hàm nhưng không import. Chỉ chạy được trên Python 3.14 (chú thích kiểu được đánh giá trễ); Python ≤ 3.13 sẽ lỗi khi import | `table_designer_service.py:4`, `130`, `173` | có (chạy được trên venv hiện tại) | Không áp dụng cho Java. Nên sửa ở Python nếu còn bảo trì |
| B18 | Thấp | Check constraint so sánh theo chuỗi; SQL Server lưu lại dạng chuẩn hóa `([a]>(0))` → drop và tạo lại thừa | `table_designer_service.py:708-712` | đọc code | Đánh dấu "đã sửa" theo thao tác người dùng (dirty flag từng mục), không so chuỗi |
| B19 | Thấp | Mỗi phím gõ đều `JSON.stringify` cả mô hình và gửi request diff (debounce 300 ms) | `table-designer.js:1375-1386`, `1160-1251` | đọc code | Diff chạy trong bộ nhớ trên luồng nền, debounce 200 ms, hủy lần tính cũ |
| B20 | Thấp | Sau khi tạo bảng mới, Explorer làm mới node bảng (chưa có trong cây) thay vì thư mục Tables. Nút reset filter bị gắn thêm listener mỗi lần mở hộp thoại | `table-designer.js:3402-3410`, `3297-3301` | đọc code | Làm mới thư mục `Tables` của schema. Hộp thoại dựng mới mỗi lần mở |

## 5. Thao tác dòng (bắt buộc đủ, áp dụng cho lưới Cột; các lưới Index/FK/Unique/Check dùng cùng bộ lệnh trừ khi ghi khác)

Quy tắc chung: mọi lệnh làm việc trên **tập dòng đang chọn** (chọn liền bằng Shift, rời rạc bằng Ctrl). Mỗi lệnh là một bước Undo/Redo. Sau lệnh, vùng chọn và focus phải rõ ràng như bảng dưới.

| ID | Lệnh | Phím tắt | Một dòng / Nhiều dòng | Vùng chọn sau lệnh |
|---|---|---|---|---|
| TD-ROW-01 | Thêm dòng ở cuối | `Ctrl+N`; `Tab` ở ô cuối của dòng cuối | — | Dòng mới, focus ô Tên, đang sửa |
| TD-ROW-02 | Chèn dòng phía trên | `Ctrl+Insert` | Chèn trên dòng đầu tiên đang chọn | Dòng mới |
| TD-ROW-03 | Chèn dòng phía dưới | `Ctrl+Shift+Insert` | Chèn dưới dòng cuối đang chọn | Dòng mới |
| TD-ROW-04 | Thêm nhiều dòng | Ô số + nút "dòng" trên toolbar, hoặc menu "Thêm nhiều dòng…" (1–100) | Chèn dưới dòng cuối đang chọn, hoặc cuối bảng nếu chưa chọn | Các dòng vừa thêm |
| TD-ROW-05 | Nhân bản | `Ctrl+D` | Bản sao đặt ngay dưới khối đang chọn, giữ thứ tự. Tên tự đổi `X_copy`, `X_copy2`… Không chép cờ PK. `originalName = null` (sửa B1) | Các bản sao |
| TD-ROW-06 | Sao chép / Cắt | `Ctrl+C` / `Ctrl+X` | Clipboard nội bộ + clipboard hệ thống (dạng TSV để dán ra Excel) | Không đổi / dòng kế tiếp |
| TD-ROW-07 | Dán | `Ctrl+V` | Chèn dưới dòng cuối đang chọn, không thay thế (sửa B14). Nhận cả TSV từ Excel (Tên, Kiểu, Độ dài, Scale, NN, Mặc định, Mô tả) | Các dòng vừa dán |
| TD-ROW-08 | Di chuyển lên / xuống | `Alt+↑` / `Alt+↓` | Di chuyển cả khối; khối rời rạc thì từng dòng dịch 1 bậc, dừng ở biên | Giữ nguyên các dòng |
| TD-ROW-09 | Lên đầu / Xuống cuối | `Alt+Home` / `Alt+End` | Như trên | Giữ nguyên |
| TD-ROW-10 | Kéo thả | Chuột | Kéo cả khối đang chọn, có vạch chỉ vị trí thả | Giữ nguyên |
| TD-ROW-11 | Xóa | `Delete` (khi không đang sửa ô) | Xóa mọi dòng đang chọn. Cột có ràng buộc phụ thuộc thì hỏi qua `ConfirmDialog` (mặc định nút Hủy) | Dòng ngay sau vùng đã xóa |
| TD-ROW-12 | Chọn tất cả | `Ctrl+A` (khi không đang sửa ô) | Chỉ chọn, không chép (sửa B14) | Mọi dòng |
| TD-ROW-13 | Đặt / bỏ khóa chính | Menu | PK nhiều cột theo thứ tự dòng; bật PK thì tự đặt Not null | Không đổi |
| TD-ROW-14 | Bật / tắt Not null | `Space` trên ô NN | Áp dụng cho mọi dòng đang chọn | Không đổi |
| TD-ROW-15 | Hoàn tác / Làm lại | `Ctrl+Z` / `Ctrl+Y` | Mọi lệnh trên và mọi lần sửa ô | Như trước lệnh |

Đổi thứ tự cột trên bảng **đã có**: SQL Server và PostgreSQL không đổi được vị trí cột bằng `ALTER`. Mặc định chỉ đổi thứ tự trong DDL xem trước và hiện ghi chú "Thứ tự cột chỉ áp dụng khi tạo bảng". Tạo lại bảng để đổi thứ tự là tính năng riêng, cần duyệt.

## 6. Thiết kế Java

### 6.1 Phân lớp (theo `rules/06-backend-and-performance.md`)

| Tầng | Thành phần mới | Ghi chú |
|---|---|---|
| `core-domain` | `TableDesign`, `ColumnDesign` (có `ColumnId` bất biến, `originalName`), `IndexDesign`, `ForeignKeyDesign`, `UniqueDesign`, `CheckDesign`, `TriggerDesign`, `DesignChange` | Không phụ thuộc JavaFX. Ràng buộc tham chiếu cột theo `ColumnId` (sửa B4) |
| `core-application` | `TableDesignValidator`, `TableDesignDiffer` → `MigrationPlan{List<MigrationStatement>, warnings, hasDestructive}` | Thứ tự câu lệnh như mục 3. Diff thuần trong bộ nhớ, chạy được trong unit test |
| `core-application` | `DdlDialect` + `SqlServerDdlDialect`, `PostgresDdlDialect` | Mọi khác biệt SQL nằm ở đây. `SqliteDdlDialect` để sau (mục 8) |
| `infrastructure/database-*` | `TableDesignReader` cho từng provider | `ColumnMetadata` hiện thiếu scale, identity seed/increment, collation, computed, mô tả, tên default constraint: cần câu truy vấn riêng, port từ `metadata_service.get_table_design_metadata` |
| `app.framework` | `TableDesignerService` đi qua `BackendFlags` (khu vực `metadata`), có bản mock | Đọc và áp dụng chạy trên luồng nền, có timeout và nút Hủy |
| `app.ui.designer` | `TableDesignerTab`, `ColumnGrid` (`TableView`, `fixedCellSize`), `ColumnInspector`, `DdlPreviewPane` (`EmbeddedMonaco`, chỉ đọc), `SaveDesignDialog`, `CopyTableDialog` | Tab mới trong `EditorTabStrip`, không dùng modal cho phần thiết kế |

Áp dụng script: dùng kết nối riêng của tab (`TabSessionManager`), tách batch bằng `SqlStatementSplitter` (`GO` cho SQL Server). SQL Server và PostgreSQL chạy trong một transaction. Thứ tự kiểm tra trước khi chạy giữ như hiện tại: chỉ đọc → Actions Guard → cảnh báo PROD → thực thi.

**Chế độ chỉ đọc** (`-Dsqlide.readOnly`, mặc định `true`): designer vẫn mở và xem được, nút Lưu bị khóa và có tooltip giải thích, "Mở trong tab SQL" vẫn dùng được. Không có đường vòng qua lớp bảo vệ.

### 6.2 Tuân thủ quy tắc giao diện

- **R-UI-01**: mọi bo góc lấy từ `shape.css`. Nếu thêm bo góc trong `table-designer.css` thì phải thêm luật tương ứng ở `shape-flat.css`. Hộp thoại dùng `DialogChrome`, xác nhận dùng `ConfirmDialog` (mặc định Hủy), không dùng `Alert` / `Dialog<>`. Thông báo dùng `NotificationService`.
- **Màu**: không viết mã hex. Mapping đề xuất:

  | Vùng | Token |
  |---|---|
  | Nền lưới, header, dòng xen kẽ, dòng chọn | `-ide-grid-bg`, `-ide-grid-header-bg`, `-ide-grid-row-alt-bg`, `-ide-grid-selection-bg` |
  | Dòng thêm / sửa / đổi tên / xóa | `-color-success-*`, `-color-warning-*`, `-color-accent-*`, `-color-danger-*` |

  Mỗi trạng thái đều có thêm ký hiệu `+ ~ ↻ −` ở cột đầu và gạch ngang cho dòng xóa, để không truyền đạt trạng thái chỉ bằng màu.
- **Font chữ và cỡ chữ theo Settings** (yêu cầu bắt buộc). Toàn màn hình không đặt `-fx-font-size` bằng px trong CSS mà dùng `em`, để đổi Settings là áp dụng ngay, không cần mở lại tab.

  | Vùng | Nguồn thiết lập | Cách áp dụng |
  |---|---|---|
  | Chữ giao diện (nhãn, ô nhập, nút, sub-tab, inspector, hộp thoại) | `appearance.uiFontFamily`, `appearance.uiFontSize` | Kế thừa từ root, đang được đặt qua `MainController.applyUiTypography`. Hộp thoại là Scene riêng nên phải áp dụng cùng style cho root của `DialogChrome` |
  | Lưới cột | `grid.fontFamily`, `grid.fontSize` | Áp dụng như lưới kết quả; tính lại `fixedCellSize` theo cỡ chữ |
  | Xem trước DDL và script trong hộp thoại Lưu | Font editor (`fontFamily`, `fontSize` của nhóm editor) | Truyền vào `EmbeddedMonaco`, đồng bộ khi Settings đổi |

  Kiểm thử ở cỡ 10, 13 và 18: ô nhập không cắt chữ, chiều cao dòng tự giãn.
- **Icon**: Ikonli, các mã đã kiểm có trong `ikonli-materialdesign2-pack 12.3.1` và Feather.

  | Chỗ dùng | Icon |
  |---|---|
  | Tab bảng mới | `mdi2t-table-plus` |
  | Tab sửa bảng | `mdi2t-table-edit` |
  | Copy table | `mdi2t-table-multiple` |
  | Chèn dòng trên / dưới | `mdi2t-table-row-plus-before` / `mdi2t-table-row-plus-after` |
  | Xóa dòng | `mdi2t-table-row-remove` |
  | Nhân bản | `mdi2c-content-duplicate` |
  | Khóa chính | `mdi2k-key` |
  | Lên / xuống | `fth-arrow-up` / `fth-arrow-down` |

  Nút chỉ có icon phải có `accessibleText` và tooltip ghi kèm phím tắt.
- **Kích thước**: tab strip 32, panel header 28, control 24, dòng lưới theo cỡ chữ. Inspector rộng mặc định 290px và kéo được.
- **Lối vào**: thay 3 mục đang `deferred` trong `DatabaseTreeCell` (`New Table...`, `Copy Table`, `Design Table`, dòng 727, 740, 1026-1028) bằng action thật. Có phím `F4` để thiết kế bảng đang chọn.

### 6.3 Cải tiến so với Python (cần duyệt)

1. Undo/Redo cho mọi thao tác.
2. Validator chạy liên tục, hiện số lỗi ở footer và tô đỏ ô lỗi.
3. Hộp thoại Copy Table thay cho việc chép thẳng.
4. Hiện danh sách đối tượng phụ thuộc của cột trong inspector.
5. Dán từ Excel dạng TSV.
6. "Chỉ hiện cột đã đổi".
7. Gõ tên bảng để xác nhận khi có thao tác xóa.

Mỗi mục ghi theo `templates/parity-diff.template.md` (Gate 4).

## 7. Lộ trình

| Mốc | Việc | Tiêu chí xong |
|---|---|---|
| TD0 | Đặc tả SCR-TD-001 đủ bộ file (`screen-spec.md`, `layout.json`, `components.json`, `interactions.json`, `states.json`, `mapping.json`, `evidence.md`) theo Gate 1; đăng ký vào `screen-inventory` | ✅ Đã hoàn thành bộ file đặc tả & đăng ký inventory (2026-11-09) |
| TD1 | Domain + `TableDesignValidator` + `TableDesignDiffer` + `SqlServerDdlDialect` + `PostgresDdlDialect` | ✅ Đã hoàn thành (35/35 test PASS 100%, port từ Python + test B1..B16) |
| TD2 | `TableDesignReader` cho SQL Server và PostgreSQL + mock + `TableDesignerService` | ✅ Đã hoàn thành (`SqlServerTableDesignReader`, `PostgresTableDesignReader`, `TableDesignerService`, 6/6 test PASS) |
| TD3 | UI New Table: tab, lưới, inspector, xem trước DDL, toàn bộ TD-ROW-01…15 | ✅ Đã hoàn thành (`TableDesignerTab`, `ColumnGrid` 15 row operations, `ColumnInspector`, `DdlPreviewPane`, `TableDesignerUndoManager`, menu action `New Table...` / `Design Table`, phím tắt `F4`, `TableDesignerTabTest` 5/5 test PASS) |
| TD4 | UI Edit Table: diff, đánh dấu dòng, hộp thoại Lưu, áp dụng trong transaction, tải lại, làm mới Explorer | Sửa B2; khi chỉ đọc thì Lưu bị khóa |
| TD5 | Copy Table dialog | ✅ Đã hoàn thành (`CopyTableDialog`, `TableDesign.cloneForCopy`, `makeSafeIdentifier` giới hạn độ dài DB, sửa lỗi Python B1 & B10, gắn menu `DatabaseTreeCell`, unit test `TableDesignCopyTest` & `TableDesignerTabTest` PASS 100%) |
| TD6 | Sub-tab Index / FK / Unique / Check / Trigger | Kiểu index theo dialect (B3); trigger dùng `CREATE OR ALTER` (B8) |
| TD7 | Đối chiếu Python + hiệu năng | Gate 4 và 5: bảng 300 cột, gõ phím không trễ, diff ≤ 50 ms trên luồng nền |

## 8. Câu hỏi mở

1. Có làm designer cho **SQLite** không? SQLite gần như không ALTER được, phải tạo lại bảng và chép dữ liệu. Đề xuất: TD1–TD7 chỉ làm SQL Server và PostgreSQL; với SQLite thì giữ menu ở trạng thái `(chưa hỗ trợ)`.
2. Có cho **sửa tay script** trong hộp thoại Lưu như Python không? Đề xuất: không (xem B2), sửa tay thì mở sang tab SQL.
3. Có cần **đổi thứ tự cột của bảng đã có** bằng cách tạo lại bảng không? Đề xuất: để sau, làm như một tính năng riêng.
4. Copy Table có cần **chép dữ liệu** (`INSERT … SELECT`) không? Mockup đang để mục này mờ, ghi "chưa hỗ trợ".
