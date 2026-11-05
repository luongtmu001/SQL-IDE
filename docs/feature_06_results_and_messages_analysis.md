# Feature Analysis: Feature 06 - Results and Messages (Lưới kết quả & Context Menu & Thông điệp)

## 1. Feature overview
Feature 6 cung cấp giao diện và nghiệp vụ hiển thị, thao tác và xuất kết quả truy vấn SQL:
- **Lưới hiển thị kết quả (Results Grid)**: Hiển thị bảng dữ liệu động từ câu lệnh SELECT, bao gồm cột chỉ số dòng `#`, phân biệt trực quan giá trị `NULL` (chữ nghiêng, màu nhạt) với chuỗi rỗng `""` và khoảng trắng.
- **Mô hình chọn vùng ô (Cell-Based Selection Model)**: Cho phép chọn từng ô, chọn khối ô (Range BoundingBox), chọn toàn bộ cột, chọn toàn bộ bảng (`Ctrl+A`).
- **Hệ thống Context Menu đa năng (15+ chức năng)**: Bám sát và kế thừa toàn bộ năng lực từ bản Python (được quy định chi tiết trong `.SKILL/result_sql.md` và `static/js/grid-result.js`):
  1. Freeze / Unfreeze cột được chọn.
  2. Copy all Headers / Copy selected Headers.
  3. Copy current cell 1:1 (giữ nguyên văn bản gốc).
  4. Copy as SQL values List (`('val1', 'val2', ...)` hoặc nhiều cột).
  5. Structured Data Quick View (nếu phát hiện ô chứa JSON/XML).
  6. Find data in Results Grid (tìm kiếm và nhảy đến ô khớp trong bảng).
  7. Find column in Results Grid (tìm kiếm cột theo tên).
  8. Show grid aggregates (thống kê Count, Num Count, Sum, Average, Min, Max trên vùng chọn).
  9. Script grid data / Script as INSERT (tự động sinh DDL `CREATE TABLE #tabletmp` và batch `INSERT INTO #tabletmp VALUES (...)` xử lý chuẩn xác NULL, escape nháy đơn, bảo toàn khoảng trắng).
  10. Copy selection as XML Spreadsheet (Excel) (dán trực tiếp vào MS Excel giữ nguyên cấu trúc bảng và kiểu dữ liệu).
  11. Visualize as (Text, Hex dump, JSON beautify, XML beautify, Image).
  12. Copy (`Ctrl+C`, chuẩn TSV RFC 4180) & Copy with Headers (`Ctrl+Shift+C`).
  13. Open in Excel (xuất file tạm `.xlsx`/`.csv`, khóa thuộc tính Read-Only và mở trực tiếp bằng phần mềm Excel của OS).
  14. Save Results As... (`Ctrl+Shift+S`, hỗ trợ CSV UTF-8 with BOM, TSV, JSON, XLSX).
- **Tab Messages (Thông điệp văn bản SQL)**: Hiển thị output dạng console/plain text cho các câu lệnh SQL (bao gồm lệnh `PRINT`, `RAISERROR`, đếm dòng `(X rows affected)`, chi tiết lỗi SQLException với mã lỗi SQLState) với thanh công cụ Clear, Copy, Toggle Word-Wrap.
- **Tab Timeline (Lịch sử thực thi)**: Bảng dữ liệu quản lý toàn bộ các lượt thực thi với thời gian, trạng thái, thời lượng, câu lệnh SQL, số dòng bị ảnh hưởng và thông điệp tóm tắt.
- **Thanh thống kê thực thi (Execution Stats Footer Bar)**: Hiển thị số dòng (e.g. `2,506 dòng`), thời gian chạy dạng badge vàng đồng hồ (e.g. `1649 ms`), dung lượng dữ liệu (e.g. `1.20 MB`), kết nối active và biểu tượng xu hướng trend.
- **Tùy biến hiển thị Grid theo chuẩn UI hiện đại**:
  - Tiêu đề cột nhỏ gọn (~26px) cân đối với thanh tab.
  - Đường kẻ phân tách ô (cell grid lines) rõ nét theo cả chiều ngang và dọc.
  - Phân màu giá trị: `NULL` (chữ in nghiêng, màu nhạt), boolean `false` (màu cam đất `#d97706`), boolean `true` (màu xanh lá `#16a34a`), số (căn phải, màu xanh dương `#2563eb`).
  - Hỗ trợ chọn linh hoạt: click cột `#` để bôi đen cả dòng, click tiêu đề cột để bôi đen cả cột, kéo chuột hoặc Shift+click để chọn vùng chữ nhật nhiều dòng nhiều cột.
  - Bộ lọc trên từng cột (Column Filter Popup) với ô tìm kiếm tức thì, sắp xếp A-Z / Z-A và viền cam đánh dấu cột đang có bộ lọc active.
  - Hỗ trợ hiển thị nhiều bảng kết quả (Multi-grid) dạng SplitPane xếp chồng dọc với thanh tiêu đề riêng biệt cho từng bảng (`Bảng #1`, `Bảng #2`).

---

## 2. Python source reference
- `static/js/grid-result.js` (3,760 dòng):
  - Khởi tạo grid, selection model (`anchorCell`, `leadCell`, `boundingBox`).
  - Hàm `showContextMenu(x, y)` (dòng 3152 - 3327) định nghĩa toàn bộ 4 nhóm context menu.
  - Hàm `handleAction(actionId)` (dòng 3330 - 3436).
  - Thuật toán copy: `copyText`, `copyAllHeaders`, `copySelectedHeaders`, `copyCurrentCell`, `copySqlValuesList`, `getSelectionCopyText` (chuẩn RFC 4180).
  - Thuật toán `scriptAsInsert`: Suy đoán kiểu dữ liệu `inferSqlType`, sinh `CREATE TABLE #tabletmp`, batch 1000 dòng `INSERT INTO`.
  - Thuật toán `copySelectionAsXmlSpreadsheet`: Sinh schema XML Spreadsheet 2003 cho Excel.
  - Thuật toán `showGridAggregates`: Duyệt qua các ô được chọn, tính `count`, `numCount`, `sum`, `avg`, `min`, `max`.
  - Thuật toán `findDataInGrid` và `findColumnInGrid`.
  - Thuật toán `visualizeAs('text' | 'hex' | 'json' | 'xml' | 'image')`.
  - Thao tác file: `openInExcel`, `saveResultsAs`, `printGrid`.
- `static/js/structured-data-viewer.js` (732 dòng): Nhận diện ứng viên JSON/XML `detectCandidate()` và cửa sổ xem nhanh có format/beautify.
- `static/js/query.js` (3,767 dòng):
  - Chuyển đổi tab Results, Messages, Plan (`switchResultView`).
  - Cập nhật số dòng và thời gian thực thi lên footer bar (`updateExecutionStats`).
  - Nút Copy All Messages (`#ide-btn-copy-messages`).
- `app/desktop/exports.py` (256 dòng):
  - Hàm `grid_open_in_excel()`: Lưu file vào `%TEMP%/SqlGridExports/Export_YYYYMMDD_HHMMSS_xxxxxx.xlsx`, đặt thuộc tính `FILE_ATTRIBUTE_READONLY` (1), và gọi `os.startfile()`.
  - Hàm `grid_save_results_as()`: Mở hộp thoại `saveFileDialog` và xuất file theo đuôi mở rộng (`.xlsx`, `.csv` với `utf-8-sig`, `.tsv`, `.json`).
- `templates/partials/results.html`: Cấu trúc HTML của panel kết quả, tab bar, empty state, footer stats.
- `.SKILL/result_sql.md`: Tài liệu đặc tả kỹ thuật chi tiết các quy chuẩn bắt buộc của Grid kết quả SQL.

---

## 3. UI and layout analysis
1. **Results Panel Root (`results.fxml`)**:
   - **Tab Bar**: Gồm các tab:
     - `Results` (icon `table-list`): chứa bảng dữ liệu.
     - `Messages` (icon `comment-alt`): chứa log văn bản thực thi và chi tiết lỗi.
     - `Plan` (icon `project-diagram`): chứa kế hoạch thực thi (Feature 7).
     - Phía bên phải tab bar: Nút "Copy Messages", nhãn trạng thái truy vấn (`statusLabel`).
   - **Center Content Area**:
     - Khi chưa có kết quả: Hiển thị `Empty State Box` ("Execute a query to see results").
     - Khi có kết quả: Hiển thị `TableView` đa năng với hỗ trợ cuộn ảo (`VirtualFlow`), định dạng ô chuyên biệt cho `NULL` (xám nghiêng), cột số dòng `#` cố định bên trái.
   - **Bottom Footer Area**:
     - Hiển thị nhãn thống kê: `Rows: 1,250 | Time: 15 ms | Size: 42 KB | Connection: PostgreSQL - Production`.
     - Nút "Copy Stats" (JSON metrics).
2. **Context Menu**:
   - Được gắn vào sự kiện chuột phải trên `TableView` hoặc các cell.
   - Menu phân nhóm rõ ràng bằng các Separator và có phím tắt hiển thị kèm theo.
   - Bổ sung các icon trực quan cho từng mục menu (sử dụng AtlantaFX / Ikonli icons).

---

## 4. Workflows and state logic
1. **Luồng hiển thị kết quả (Query Execution $\rightarrow$ Grid)**:
   - Nhận `QueryResult` từ `DesktopQueryExecutionService`.
   - Nếu `isResultSet == true` (SELECT):
     - Xóa dữ liệu cũ, tự động nạp cấu trúc cột mới từ `QueryResult.getColumnNames()`.
     - Đưa toàn bộ dòng dữ liệu vào `ObservableList<ObservableList<Object>>`.
     - Tự động chuyển tab sang `Results`.
     - Cập nhật số dòng và thời gian thực thi lên Footer bar.
   - Nếu `isResultSet == false` (DML/DDL):
     - Chuyển tab sang `Messages`.
     - Hiển thị dòng thông báo: `Query executed successfully in X ms. (Y rows affected)`.
2. **Luồng Copy & Format**:
   - `Copy`: Trích xuất vùng chọn theo dạng TSV (`\t` giữa các cột, `\r\n` giữa các dòng). Escape theo chuẩn RFC 4180 nếu ô chứa dấu `\t`, `\n`, hoặc `""`.
   - `Copy with Headers`: Thêm dòng tiêu đề cột phía trên trước khi ghép dữ liệu.
   - `Copy 1:1`: Lấy trực tiếp chuỗi của ô đang active (không escape, không TSV).
   - `Copy as SQL values List`:
     - Nếu chọn 1 cột: `('val1', 'val2', 'val3')`.
     - Nếu chọn nhiều cột: `('val1', 'val2'),\r\n    ('val3', 'val4')`.
3. **Luồng Script as INSERT**:
   - Thu thập danh sách cột và các dòng được chọn (hoặc toàn bộ bảng nếu chọn 1 ô).
   - Suy đoán kiểu dữ liệu (`INT`, `BIGINT`, `DECIMAL`, `BIT`, `DATETIME`, `NVARCHAR(MAX)`).
   - Tạo khối DDL:
     ```sql
     -- 1. Tạo bảng tạm #tabletmp
     IF OBJECT_ID('tempdb..#tabletmp') IS NOT NULL DROP TABLE #tabletmp;
     CREATE TABLE #tabletmp (
         [Col1] NVARCHAR(MAX) NULL,
         [Col2] INT NULL
     );

     -- 2. Chèn dữ liệu
     INSERT INTO #tabletmp ([Col1], [Col2]) VALUES
         ('Val1', 123),
         (NULL, 456);
     ```
   - Xử lý giá trị: `NULL` $\rightarrow$ `NULL`, chuỗi rỗng $\rightarrow$ `''`, khoảng trắng `"   "` $\rightarrow$ `'   '` (bảo toàn nguyên vẹn), nháy đơn `'` $\rightarrow$ escape `''`.
4. **Luồng Aggregates (Thống kê vùng chọn)**:
   - Duyệt qua tất cả các ô trong vùng chọn:
     - Tính tổng số ô (`count`).
     - Lọc các ô có thể parse thành số (`numCount`), tính tổng (`sum`), trung bình (`avg`), giá trị nhỏ nhất (`min`), giá trị lớn nhất (`max`).
   - Hiển thị kết quả trong một hộp thoại / popover thông tin thống kê gọn gàng.
5. **Luồng Find Data / Find Column**:
   - Tìm kiếm chuỗi trong các ô và tự động scroll/focus vào ô tìm thấy (Next / Previous).
6. **Luồng Open in Excel**:
   - Tạo file tạm tại `%TEMP%/SqlGridExports/Export_YYYYMMDD_HHMMSS_xxxxxx.csv` (với UTF-8 BOM) hoặc `.xlsx`.
   - Đặt thuộc tính file thành `ReadOnly`.
   - Dùng `Desktop.getDesktop().open(file)` để hệ điều hành kích hoạt Excel mở file.
7. **Luồng Save Results As**:
   - Mở `FileChooser` của JavaFX cho phép chọn `.xlsx`, `.csv`, `.tsv`, `.json`.
   - Ghi file theo đúng định dạng được chọn, hiển thị thông báo thành công.

---

## 5. Data structures and persistence
- `GridRangeSelection`: Đại diện cho vùng chọn ô:
  ```java
  public record GridRangeSelection(int minRow, int maxRow, int minCol, int maxCol) {
      public boolean isSingleCell() { return minRow == maxRow && minCol == maxCol; }
  }
  ```
- `GridCellData`: Giá trị và metadata của một ô (`Object rawValue`, `String formattedText`, `boolean isNull`).
- `GridExportService`: Lớp tiện ích xuất dữ liệu ra CSV (UTF-8 BOM), TSV, JSON, XML Spreadsheet và SQL Script.

---

## 6. External dependencies and OS integrations
- `java.awt.Desktop`: Mở file Excel tạm bằng ứng dụng mặc định của hệ điều hành.
- `javafx.scene.input.Clipboard`: Tương tác với System Clipboard (`ClipboardContent`).
- `javafx.stage.FileChooser`: Hộp thoại lưu file hệ thống chuẩn native.
- `java.nio.file`: Tạo thư mục và file tạm trong thư mục Temp của hệ thống (`java.io.tmpdir`).

---

## 7. Edge cases and failure modes
- **Dữ liệu lớn (10,000 dòng)**: JavaFX `TableView` với cơ chế virtual rendering chỉ tạo cell node cho các dòng đang hiển thị trên màn hình, đảm bảo tiêu thụ ít bộ nhớ và mượt mà 60fps.
- **Giá trị `NULL` vs Chuỗi rỗng**: Phải hiển thị và xuất khác biệt hoàn toàn: `NULL` không phải là `""`.
- **Bảo toàn khoảng trắng**: Tuyệt đối không dùng `.trim()` khi sinh câu lệnh INSERT để không làm sai lệch dữ liệu của người dùng.
- **Ký tự đặc biệt**: Dấu nháy đơn `'`, dấu ngoặc kép `"`, dấu phẩy `,`, dấu tab `\t`, dấu xuống dòng `\r\n` phải được escape theo đúng chuẩn của từng định dạng xuất.
- **Tiếng Việt và Unicode**: Xuất CSV bắt buộc ghi tiền tố UTF-8 BOM (`0xEF, 0xBB, 0xBF`) để Excel trên Windows hiển thị đúng font tiếng Việt.

---

## 8. Approved deviations
- Thay vì dùng AG Grid nhúng trong WebView thứ 2 (vi phạm Rule 6 Single WebView rule của `AGENTS.md`), toàn bộ lưới kết quả được xây dựng bằng **JavaFX Native TableView** với AtlantaFX Theme:
  - Cho hiệu năng native tối đa, khởi động tức thì 0ms, không tiêu tốn thêm tài nguyên WebKit.
  - Tích hợp sâu với OS Clipboard và System File Dialogs.

---

## 9. Modern native desktop targets
- Giao diện Dark/Light theme đồng bộ tuyệt đối với toàn bộ ứng dụng.
- Icon chuẩn FontAwesome/Material Design cho toàn bộ các mục Context Menu.
- Phím tắt desktop quen thuộc: `Ctrl+C`, `Ctrl+Shift+C`, `Ctrl+A`, `Ctrl+Shift+S`, `Ctrl+F`.

---

## 10. Acceptance criteria
- [x] Phân tích toàn diện mã nguồn Python (`grid-result.js`, `structured-data-viewer.js`, `exports.py`, `.SKILL/result_sql.md`).
- [ ] Xây dựng bộ tiện ích trích xuất và xuất dữ liệu `GridDataExportUtils.java`:
  - Trích xuất TSV (hỗ trợ RFC 4180).
  - Trích xuất SQL Values List.
  - Sinh script DDL `CREATE TABLE #tabletmp` và batch `INSERT INTO`.
  - Sinh XML Spreadsheet cho Excel.
  - Xuất CSV UTF-8 with BOM, TSV, JSON.
- [ ] Xây dựng dịch vụ thao tác file hệ thống `DesktopGridExportService.java`:
  - `openInExcel(columns, rows)`: Tạo file tạm Read-Only và kích hoạt Excel.
  - `saveResultsAs(columns, rows, window)`: Mở JavaFX `FileChooser` để lưu file.
- [ ] Nâng cấp `ResultsController.java`:
  - Cột `#` số dòng cố định.
  - Hiển thị giá trị `NULL` chuyên biệt (italic xám).
  - Cell-based selection model hỗ trợ chọn vùng (BoundingBox).
  - Context Menu đầy đủ các nhóm chức năng (Copy, Headers, 1:1, SQL Values, Script INSERT, XML Excel, Aggregates, Find, Open in Excel, Save As).
  - Tab Messages với nút Copy All Messages và hiển thị thông tin thực thi / lỗi chi tiết.
  - Footer bar thống kê số dòng, thời gian chạy và kết nối active.
- [ ] Viết test cases toàn diện trong `src/test/java/app/ResultsGridTest.java`.
- [ ] Cập nhật `MIGRATION_TRACKER.md`.

