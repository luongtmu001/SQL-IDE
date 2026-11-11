# Evidence Log — SCR-TD-001 Table Designer
# Nhật ký bằng chứng — Màn hình thiết kế bảng

- Ngày lập: 2026-11-09
- Phạm vi: Gate 1 — Đặc tả thiết kế mới theo tài liệu kế hoạch [feature_10_table_designer_migration_plan.md](file:///docs/feature_10_table_designer_migration_plan.md)

---

## 1. Nguồn đối chiếu / Sources

| Nguồn | File / Đường dẫn | Vai trò & Nội dung | Mức tin cậy |
|---|---|---|---|
| Python Backend Service | `app/services/table_designer_service.py` (1060 dòng) | Sinh DDL `CREATE TABLE`, sinh diff/migration script, đánh giá thay đổi phá hủy (destructive changes) | `code_verified` |
| Python API Mixin | `app/api/designer_api.py` (87 dòng) | API endpoints: `get_table_design`, `get_table_design_types`, `preview_table_design`, `diff_table_design`, `apply_table_design` | `code_verified` |
| Python Web UI | `static/js/table-designer.js` (3821 dòng) | Toàn bộ luồng UI: mở bảng, tạo bảng, copy bảng, dựng lưới cột, inspector, live DDL, modal lưu | `code_verified` |
| Mockup Canvas | Artifact `8uNNxsjkr7p2V2DuG3hrYJ` | 4 ảnh mockup chi tiết (`01-new-table.png`, `02-edit-table.png`, `03-review-apply-dialog.png`, `04-copy-table-dialog.png`) | `observed` |
| Python Test Suite | `tests/test_table_designer*.py`, `test_phase4_*` | Bộ test gốc của Python dùng làm corpus đối chiếu cho bộ Unit Test Java (mốc TD1) | `code_verified` |

---

## 2. Bằng chứng lỗi phát hiện trong mã Python (B1 → B20)

| # | Mức độ | Lỗi | Bằng chứng mã nguồn | Trạng thái tái hiện | Giải pháp thiết kế JavaFX |
|---|---|---|---|---|---|
| B1 | **Nghiêm trọng** | Dán hoặc nhân bản cột ở bảng đã có: bản sao giữ `original_name` của cột gốc, làm diff hiểu nhầm là **đổi tên cột gốc** (ví dụ `Email` sinh `sp_rename ... [Email] -> Email_copy`) | `table-designer.js:595-606`, `table_designer_service.py:777-801` | **Đã tái hiện** | Mỗi cột có `ColumnId` nội bộ bất biến. `originalName` chỉ gán khi tải từ DB. Bản sao luôn có `originalName = null` |
| B2 | **Cao** | Hộp thoại Lưu đang lọc theo nhóm câu lệnh thì chỉ chạy phần script đã lọc, sau đó đánh dấu là đã lưu và tải lại DB, **làm mất phần thay đổi chưa chạy** | `table-designer.js:3225-3275`, `3346-3399` | Đọc code | Bộ lọc chỉ dùng để quan sát. Khi bấm Lưu, luôn thực thi toàn bộ script sinh từ mô hình. Muốn sửa tay thì bấm "Mở trong tab SQL" |
| B3 | **Cao** | PostgreSQL: index mới mặc định `index_type = 'NONCLUSTERED'` sinh câu lệnh lỗi `USING nonclustered` | `table-designer.js:705`, `table_designer_service.py:382-384` | **Đã tái hiện** | Kiểu index là enum theo dialect (SQL Server: CLUSTERED/NONCLUSTERED; PostgreSQL: btree/hash/gin/gist/brin) |
| B4 | **Cao** | Đổi tên hoặc xóa cột không cập nhật index / FK / unique đang dùng cột đó -> DDL tham chiếu cột không tồn tại | `table-designer.js:2614-2617`, `995`, `2116-2131` | Đọc code | Mọi ràng buộc liên kết cột qua `ColumnId`. Đổi tên tự cập nhật; xóa cột thì kiểm tra và hỏi người dùng qua `ConfirmDialog` |
| B5 | **Cao** | SQL Server `ALTER COLUMN` lỗi khi cột nằm trong index/unique/PK/FK/default. Đồng thời `ALTER COLUMN` không ghi `COLLATE`, làm collation bị đổi ngầm về DB default | `table_designer_service.py:875-885` | Đọc code | Differ tính toán tập đối tượng phụ thuộc, drop trước và re-create sau. Luôn giữ collation hiện tại cho kiểu chuỗi |
| B6 | Trung bình | `size` là `None` ở bản gốc và `''` ở bản sửa bị coi là đổi kiểu (`str(None) != ''`) -> sinh `ALTER COLUMN` thừa | `table_designer_service.py:864` | **Đã tái hiện** | Chuẩn hóa độ dài và scale về `OptionalInt` trước khi so sánh diff |
| B7 | Trung bình | Default kiểu chuỗi có dấu ngoặc (vd `Hello (world)`) bị coi là biểu thức, không được bọc nháy -> SQL sai | `table_designer_service.py:100-101` | **Đã tái hiện** | Inspector có lựa chọn rõ: "Giá trị" hoặc "Biểu thức". Mặc định là Giá trị (tự động bọc nháy và escape) |
| B8 | Trung bình | Sửa trigger có sẵn: chạy lại `CREATE TRIGGER` không drop -> lỗi already exists trên SQL Server | `table_designer_service.py:966-975` | Đọc code | SQL Server dùng `CREATE OR ALTER TRIGGER`. PostgreSQL dùng `CREATE OR REPLACE TRIGGER` hoặc drop/create |
| B9 | Trung bình | Thêm cột vào bảng có sẵn bỏ qua identity, cột tính toán và collation | `table_designer_service.py:826-850` | Đọc code | Dùng chung hàm sinh định nghĩa cột hoàn chỉnh cho cả `CREATE TABLE` và `ALTER TABLE ADD` |
| B10 | Trung bình | Copy Table: `replaceAll` thay cả chuỗi con (`Order` thay luôn trong `OrderItems`), trigger trùng tên, không kiểm tra tên bảng đích đã tồn tại, tên vượt quá 128 / 63 ký tự | `table-designer.js:389`, `474-480` | Đọc code | Hộp thoại `CopyTableDialog`: kiểm tra tên với DB, cắt tên theo giới hạn độ dài, trigger mặc định bỏ chọn |
| B11 | Trung bình | Menu chuột phải chỉ xóa 1 dòng dù đang chọn nhiều dòng; phím `Delete` không được xử lý | `table-designer.js:988-1000`, `2470-2509` | Đọc code | Dùng chung `RowCommands` áp dụng trên toàn bộ tập dòng đang chọn |
| B12 | Thấp | Di chuyển lên/xuống chỉ chạy với 1 dòng và làm mất vùng chọn | `table-designer.js:533-557` | Đọc code | Di chuyển cả khối dòng đang chọn và bảo toàn vùng chọn |
| B13 | Thấp | Tên cột mới `Column_{n+1}` trùng sau khi xóa; không kiểm tra tên rỗng hoặc trùng | `table-designer.js:646`, `table_designer_service.py:521-522` | Đọc code | `TableDesignValidator` kiểm tra liên tục trong bộ nhớ: tên rỗng, tên trùng (case-insensitive) |
| B14 | Thấp | `Ctrl+A` vừa chọn hết vừa chép vào clipboard; dán khi chọn nhiều dòng sẽ ghi đè thay thế mất dữ liệu | `table-designer.js:2490-2495`, `609-619` | Đọc code | `Ctrl+A` chỉ chọn; `Ctrl+V` luôn chèn dưới dòng cuối của khối chọn |
| B15 | Thấp | Tên mặc định `Table_N` không kiểm tra với bảng trong DB | `table-designer.js:256-268` | Đọc code | Kiểm tra với danh sách bảng hiện hữu trong schema lấy từ metadata cache |
| B16 | Thấp | Tên đối tượng ghép vào chuỗi `N'...'` không nhân đôi dấu `'` (`sp_rename`, extended property) | `table_designer_service.py:550`, `139-153`, `789` | Đọc code | Hàm `sqlLiteral()` dùng chung để escape mọi literal an toàn |
| B17 | Thấp | Python dùng `Optional` nhưng không import (chỉ chạy trên Python 3.14) | `table_designer_service.py:4` | **Đã tái hiện** | Không áp dụng cho Java (Java có kiểu dữ liệu tĩnh mạnh) |
| B18 | Thấp | Check constraint so sánh theo chuỗi, SQL Server lưu dạng chuẩn hóa `([a]>(0))` -> drop/recreate thừa | `table_designer_service.py:708-712` | Đọc code | Đánh dấu dirty flag theo thao tác sửa của người dùng, không so khớp chuỗi thô |
| B19 | Thấp | Mỗi phím gõ đều serialize cả mô hình gửi HTTP request tính diff | `table-designer.js:1375-1386`, `1160-1251` | Đọc code | Diff chạy thuần Java in-memory trên luồng nền, debounce 200ms, hủy tác vụ cũ khi có phím mới |
| B20 | Thấp | Sau khi tạo bảng mới, Explorer làm mới node bảng (chưa có trong cây) thay vì thư mục `Tables` | `table-designer.js:3402-3410` | Đọc code | Làm mới node thư mục `Tables` của schema tương ứng |

---

## 3. Lưu ý an toàn dữ liệu & Chế độ chỉ đọc

1. Khi hệ thống chạy với cờ `-Dsqlide.readOnly=true` (mặc định), người dùng vẫn có thể xem cấu trúc, thêm sửa xóa cột giả lập trên bộ nhớ và xem Live DDL preview. Nút **"Lưu"** (`BTN-TD-SAVE`) bị khóa, kèm tooltip hướng dẫn. Người dùng có thể bấm **"Mở trong tab SQL"** để xem và lưu file script ra ngoài mà không vi phạm quy tắc an toàn.
2. Khi thực thi migration (áp dụng DDL), toàn bộ batch câu lệnh luôn được bọc trong một **Single Transaction** (`execute_migration_transaction`). Nếu một câu lệnh gặp lỗi (ví dụ không thể convert kiểu dữ liệu do dữ liệu hiện có không tương thích), transaction tự động rollback 100%, bảo đảm database không bao giờ bị rơi vào trạng thái dở dang.
3. Khi có thao tác xóa (Drop Column, Drop Index), `SaveDesignDialog` bắt buộc người dùng nhập đúng tên bảng mới cho phép bấm "Áp dụng".

