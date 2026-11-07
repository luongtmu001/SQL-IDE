<!-- Bản trong repo của project doc "claude/06-performance-rules.md". Sửa ở một nơi thì cập nhật nơi kia. -->

# Nguyên tắc hiệu năng / Performance rules (SQL-IDE)

> Người dùng đặt ngày 2026-11-06. Áp dụng cho mọi code mới và mọi lần sửa code cũ. Khi review hoặc báo cáo xong một việc, kiểm lại theo danh sách này.

## 1. Dựng lưới (grid) chỉ một lần, ít, theo phần đang nhìn thấy
- Khi buộc phải dựng lưới: dựng **một lần**, với **số lượng nhỏ** (phần nhìn thấy), cuộn tới đâu dựng tới đó.
- Không dựng lại lưới khi chỉ đổi chỗ hiển thị (chuyển tab, ẩn/hiện panel, đổi theme): giữ node đã dựng và gắn lại.
- Thêm cột / dòng: một thay đổi cho cả lô (`setAll`, `addAll`), không thêm từng phần tử.
- Không quét hết dữ liệu để tính toán giao diện (độ rộng cột, chiều cao…): chỉ dùng tên cột hoặc phần đã tải / đang hiện (ví dụ: co cột vừa nội dung chỉ đo 1.000 dòng đầu).
- Hiện có: `fixedCellSize` + `ResultGridRow` (lazy cả dòng lẫn cột), lưới riêng cho mỗi tab (`ResultsController.MainGrid`), `StackedGridsPane` giữ node khi chuyển tab.

## 2. Luôn lazy load khi nạp dữ liệu vào một khung cố định
- Khung có kích thước cố định (lưới, cây, danh sách, combo dài) → chỉ nạp / vẽ phần trong khung, phần còn lại nạp khi cuộn hoặc khi mở.
- Dữ liệu từ server → đọc và đổ dần theo lô (hiện lô đầu ngay, ví dụ 200 dòng, các lô sau lớn hơn), không chờ đọc hết; vẫn hủy được giữa chừng.
- Cây Explorer → chỉ tải con khi mở node; con của một node được thêm một lần (`addAll`).
- Hiện có: `ScriptRunner.RowSink` (200 → 2.000 dòng/lô), PostgreSQL đọc bằng con trỏ, `LazyDatabaseTreeItem`.

## 3. Dùng cache khi có thể
- Dữ liệu đọc lại nhiều lần mà ít đổi → cache (trong bộ nhớ, có hạn dùng hoặc xóa khi biết dữ liệu đã đổi).
- Cache phải có cách làm mới: xóa (invalidate) khi người dùng sửa / làm mới, hoặc hạn dùng ngắn.
- Hiện có: `DesktopMetadataCache` (metadata Explorer), `ProfileLookup` (danh sách kết nối, 2 s, xóa khi lưu kết nối / tải lại Explorer), `ConnectionColorService` (màu kết nối).

## 4. Chỉ đọc lại file kết nối, trạng thái… khi cần
- Không đọc file / gọi store trong code chạy thường xuyên (vẽ ô, chuyển tab, listener của trạng thái, mỗi lần refresh giao diện).
- Đọc khi: khởi động, người dùng mở / lưu / làm mới, hoặc cache hết hạn.
- Trạng thái (kết nối, phiên tab…) đọc từ bộ nhớ (`ExplorerConnectionState`, `TabSessionManager`), không suy ra lại từ file.

## 5. Kiểm chứng
- Thay đổi có ảnh hưởng tốc độ → đo trước / sau (ví dụ `GridPerfIT`, log `[tab-switch]`), ghi số đo vào báo cáo; không kết luận "đã nhanh" khi chưa đo.
- Không làm việc nặng trên FX thread (I/O, JDBC, đọc file): chạy nền, trả kết quả bằng `Platform.runLater` theo lô.

## 6. Mẫu cần tránh (rút ra từ lần rà soát 2026-11-06)
- **Thêm / chọn từng phần tử trong vòng lặp** trên control đang hiển thị (`getChildren().add` cho mỗi bảng, `select(r, col)` cho mỗi ô): mỗi lần là một sự kiện → O(n²). Dùng `addAll` / `setAll`, `selectRange`, `selectAll`.
- **Quét toàn bộ dữ liệu theo mỗi phím gõ** (lọc nhanh, đếm cho tooltip): debounce ~150–200 ms, và chỉ tính nội dung tooltip khi tooltip mở.
- **Đọc `Preferences` trong code chạy thường xuyên**: trên Windows mỗi lần `get` là một lần đọc registry → giữ trong bộ nhớ, cập nhật khi ghi (ví dụ `ConnectionColorService`).
- **Tạo đối tượng nặng trong `updateItem`** (Tooltip, ContextMenu, FontIcon mới): tạo một lần cho mỗi cell, chỉ đổi text / trạng thái.
- **Cập nhật control ẩn theo mỗi thay đổi** (CodeArea dự phòng tô màu lại toàn bộ văn bản theo mỗi phím của Monaco): đánh dấu "cũ", đồng bộ khi control được hiển thị.
