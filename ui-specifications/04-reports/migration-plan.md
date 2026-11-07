# Kế hoạch chuyển đổi UI — LLTool (Python) → SQL IDE (JavaFX)
# UI Migration Plan

- Cập nhật: 2026-11-05 (GĐ 8: D1 tạm đạt, D2 đã code, chờ kiểm)
- Phạm vi đã duyệt: màn hình chính + Settings + dialog. Giữ Monaco. Title bar kiểu VS Code nhưng hành xử native. Không làm lại mục legacy. Mục tiêu là giao diện **tương đương VS Code**, không sao chép 100% bản Python.
- Quy trình: skill `java-desktop-ui-designer`. Mỗi màn hình đi qua: phân tích → đặc tả → **Gate B** (duyệt đặc tả) → làm prototype mock → kiểm tra trên máy → **Gate C** (duyệt prototype). Chỉ tích hợp backend thật ở **Gate D**, sau khi toàn bộ UI đã duyệt.

## 1. Đã hoàn thành

| Hạng mục | Trạng thái |
|---|---|
| Bước 01: khảo sát dự án | ✅ (`00-application/project-map.md`, `discovery-report.md`, …) |
| Bước 02: kiểm kê 21 màn hình | ✅ Gate A đã duyệt |
| SCR-SHELL-001: title bar | ✅ `implemented_mock`, Gate C tạm duyệt (system menu, trạng thái mờ khi mất focus, viền resize ngoài, menu bổ sung, logo, ngưỡng hamburger động) |
| SCR-MAIN-003 (một phần): thanh ngữ cảnh / toolbar | ✅ `implemented_mock`, tạm duyệt (breadcrumb, Run/Stop, menu ⋯, badge môi trường chuyển xuống toolbar; bo góc, chevron) |
| Giai đoạn 0 | ✅ T1–T5 xong (107 test xanh, token đã duyệt: font UI Inter, grid 12px). T6 commit Git: chưa làm |
| Giai đoạn 1: SCR-MAIN-001 + SCR-MAIN-005 | ✅ chốt 2026-11-05: card bo góc (toolbar, Explorer, Editor, Results, status bar), khe 6px, Explorer cố định px, rail 46px, Ctrl+B; status bar 22px màu theo connection; setting `appearance.panelStyle` (bo góc / phẳng). Còn lỗi T7 |
| Giai đoạn 2A: SCR-MAIN-002 Object Explorer | ✅ mock: cây theo nhóm, dữ liệu mẫu khi mở rộng, menu theo context-menu.js, mục ngoài phạm vi hiện mờ "(chưa hỗ trợ)", icon connection xám khi chưa kết nối. Còn lỗi T8 |
| Giai đoạn 7: review tổng | 🟡 `PASS_WITH_KNOWN_GAPS`. Review code + đối chiếu 21 màn hình (`04-reports/phase7-review.md`); đã thử sửa T7, T8, T9, T11 nhưng **chưa đạt** (bạn kiểm 2026-11-05), chuyển sang D0.2; quét R-UI-01 toàn bộ CSS (thêm 33 luật phẳng); **chặn chạy SQL / execution plan trên server thật khi đang mock** (chỉ SQLite trong bộ nhớ). Còn: so ảnh nguồn / đích (chờ ảnh của bạn), bộ đặc tả cho SCR-MAIN-004, SCR-SET-001, DLG-001…014 |
| Giai đoạn 6: DLG-011…014 | ✅ chốt 2026-11-05. Gate B 2026-11-05: DLG-011 toast tự vẽ (`ToastHost`, R-UI-01) góc phải dưới cửa sổ đang focus, tối đa 3, info/success ẩn sau 4 s, cảnh báo 6 s, lỗi giữ đến khi đóng, hover dừng đếm, link hành động + "Chi tiết" cho lỗi dài; DLG-012 About (logo, phiên bản, thư mục dữ liệu, nền tảng, runtime, Sao chép thông tin, Kiểm tra cập nhật; không có nút Đóng); DLG-013 kiểm tra cập nhật **mock** đủ trạng thái như LLTool (đang kiểm tra / mới nhất / có bản mới + ghi chú / đang tải có tiến trình / đã tải / lỗi mạng), chọn kịch bản ở chân hộp thoại, không gọi mạng; DLG-014 lớp phủ đang tải dùng chung (`LoadingOverlay`: hiện sau 150 ms, thời gian chạy, Hủy) áp cho Results; thay toàn bộ Alert hệ thống (`AppDialog`, Grid Aggregates, Visualize text, Find Column, About) bằng `MessageDialog` / DialogChrome |
| Giai đoạn 5: SCR-SET-001 + DLG-009 | ✅ chốt 2026-11-05. Bổ sung sau góp ý: cửa sổ Settings 900×640, tab JSON dùng Monaco, bỏ nút Đóng, nút Lưu ở chân cửa sổ; DLG-009 xem trước bằng Monaco cho phép gõ trực tiếp + nút Format, bỏ nút Đóng. Gate B 2026-11-05: Settings là cửa sổ riêng trong `DialogChrome` (R-UI-01), dòng cài đặt kiểu VS Code (tiêu đề đậm + tên tiếng Anh nhạt, mô tả, tên biến, ô nhập bên dưới; checkbox cùng dòng mô tả; vạch màu nhấn khi đã sửa; menu ⋯ Hoàn tác / Sao chép tên cài đặt), lưu bằng nút Lưu + "N thay đổi chưa lưu" + hỏi khi đóng; DLG-009 đủ giao diện: style hệ thống chỉ đọc + style người dùng (Mới / Nhân bản / Đổi tên / Đặt mặc định / Xuất / Nhập / Xóa), 15 nhóm / 31 tùy chọn từ metadata LLTool, tìm tùy chọn, phụ thuộc giữa tùy chọn, xem trước SQL Server / PostgreSQL bằng bộ format mẫu, lưu `sql-styles.json` cạnh settings.json. Bộ format thật: Gate D |
| Giai đoạn 4: SCR-MAIN-004 | ✅ chốt 2026-11-05 (còn T11). Thêm menu chuột phải theo LLTool cho mọi lưới (chạy đúng trên lưới được bấm), Visualize as Image, Page Setup; cột # rộng 30px. Gate B 2026-11-05: giữ bộ tab hiện tại (Results / Messages / Timeline / Execution Plan / Terminal), restyle kiểu tab panel VS Code (gạch chân màu nhấn); nhiều bảng kết quả **xếp dọc, thu gọn được** (Bảng N · số dòng · ms · đầu câu lệnh; mỗi lưới cao theo số dòng, tối đa 12 dòng rồi tự cuộn) thay cho SplitPane; giữ TableView; "So sánh" hiện mờ "(chưa hỗ trợ)"; chân bảng hiện kết nối · database · schema thật của lần chạy; huy hiệu thời gian đổi từ vàng sang màu theme. Bổ sung 2026-11-05: setting `grid.resultLayout` (Danh sách mặc định / Theo tab, tab dựng bảng khi mở lần đầu); mỗi bảng có thanh cuộn riêng (cao tối đa 3/4 khung); hộp lọc mới (điều kiện + danh sách giá trị có tìm kiếm, đếm, Chọn tất cả), bộ lọc riêng từng bảng; nút lọc bám mép phải cột; kiểu dữ liệu 0.8em nghiêng nhạt; bỏ dòng trống; tiêu đề bảng chỉ hiện tên bảng (T10); số dòng đã lọc/tổng mỗi bảng + tooltip chi tiết từng bộ lọc; cột # cố định khi cuộn ngang, rộng 50px, kéo giãn được |
| Giai đoạn 3: SCR-MAIN-003 + DLG-007a/b, 008, 010 | ✅ chốt 2026-11-05. Thanh tab tự vẽ (`EditorTabStrip`): icon kết nối trước tên, chấm trạng thái sau tên, ghim đưa tab lên đầu theo thứ tự ghim, nhóm nút tách cột riêng, một hàng có mờ mép phải + lăn chuột, "Hiển thị tất cả tab" xuống dòng, viền màu nhấn bao tab đang mở và đường dưới hở chỗ tab mở. Gate B: tab kiểu VS Code (chấm màu connection, ● chưa lưu thành × khi hover, ghim, chevron tab tràn, bấm chuột giữa để đóng); menu tab đủ như LLTool (So sánh… hiện mờ "(chưa hỗ trợ)"); banner "Chưa kết nối" (Kết nối / Đổi kết nối / ẩn); hộp đóng tab chưa lưu; DLG-007 **tách** Actions Guard (danh sách + dòng, nhấp đúp nhảy tới dòng, mặc định Hủy) và cảnh báo PRODUCTION (DML/DDL trên kết nối PROD); DLG-008 format có "Không hỏi lại"; DLG-010 bs.* (Comment hết và chạy; Run Debug hiện mờ) |
| Giai đoạn 2B + 2C: DLG-001…006, R-UI-01 | ✅ chốt 2026-11-05 (còn T9). Khung dialog chung `DialogChrome` (title bar tự vẽ, bo góc 8px, phẳng khi `panelStyle = flat`, Esc đóng). DLG-001 Connection Manager (danh sách theo nhóm + form, chọn loại DB kèm logo, Kiểm tra / ↻ database / ↻ schema trả dữ liệu mẫu, "Kết nối mới" từ nhóm điền sẵn nhóm). DLG-002 hỏi mật khẩu (lưu mã hóa 30 ngày tính từ lần lưu cuối). DLG-003 tên nhóm, DLG-004 xác nhận xóa, DLG-005/006 lọc database / đối tượng |

## 1b. Quy tắc giao diện bắt buộc (R-UI-01, 2026-11-05)

Mọi màn hình luôn vẽ theo biến cài đặt `appearance.panelStyle`, không hard-code bo góc:

- **`flat` (phẳng):** không bo góc bất kỳ element nào: cửa sổ, dialog, panel, card, nút, checkbox, ô chọn (ComboBox), ô nhập (TextField / PasswordField / Spinner), badge, popup menu. Panel liền nhau, ngăn bằng đường kẻ 1px.
- **`rounded` (bo góc):** các panel cách nhau 6px, mỗi panel là một card bo góc.
- **Mọi ô chọn (ComboBox, Spinner, menu thả xuống)** dùng icon mũi tên chevron down / up, không dùng tam giác mặc định.
- **Bo góc:** ô nhập, ô chọn, checkbox, nút đều phải bo góc (6px; checkbox 4px).
- **Danh sách của ô chọn (popup):** không padding quanh nội dung; dòng hover / đang chọn bo góc ở chế độ bo góc, vuông ở chế độ phẳng.
- **Tiêu đề ô nhập** căn phải, sát ô nhập.
- **Ô nhập** luôn hiện đủ nội dung (không đặt chiều cao cố định làm cắt chữ).
- Thực hiện: `ThemeService` nạp `shape.css` (luôn) và `shape-flat.css` (chỉ khi phẳng) **sau cùng** cho mọi Scene, đổi ngay khi đổi cài đặt; popup tự nhận stylesheet của cửa sổ cha. Không đặt bán kính bo góc riêng trong từng file nếu không có luật `.flat` tương ứng.

## 2. Việc kỹ thuật tồn đọng

| # | Vấn đề | Mức | Lý do |
|---|---|---|---|
| T1 | App **tự kết nối DB thật** lúc khởi động (`10.20.30.40:5433/staging_db`) do khôi phục workspace và tự mở rộng node | **Cao** | ✅ đã xử lý ở GĐ 0 |
| T2 | Đóng cửa sổ nhưng JVM chưa thoát | Cao | ✅ đã xử lý ở GĐ 0 |
| T3 | Cảnh báo CSS `ClassCastException String → Paint` | Trung bình | ✅ đã xử lý ở GĐ 0 |
| T4 | Cập nhật test theo title bar và toolbar mới | Trung bình | ✅ đã xử lý ở GĐ 0 |
| T5 | Bộ design token tập trung | Cao | ✅ đã xử lý ở GĐ 0 |
| T6 | Commit Git | Thấp | Đồng ý 2026-11-05: chạy `apply-phase8-d1.cmd` (commit mốc `ui-prototype-baseline` trước, rồi commit D0 + D1) |
| T7 | **Thanh kéo giữa Editor và Results không kéo được** (khe đã tách nhờ `editorSlot`, nhưng sash 2px không bắt chuột) | Trung bình | **Chưa sửa được** (bạn kiểm 2026-11-05). Đã thử ở GĐ 7: biến khe 4px dưới editor thành thanh kéo riêng (`installEditorSash`, `.editor-sash`). Lần sau: cần bằng chứng chạy thật (D0.2), nghi thanh kéo bị card / overlay của `RoundedCard` hoặc divider SplitPane che; hướng thay hẳn SplitPane dọc bằng layout tự vẽ |
| T8 | **Icon trong Object Explorer chưa thẳng hàng** (mũi tên / icon connection / icon thư mục lệch nhau giữa các cấp) | Trung bình | **Chưa sửa được** (bạn kiểm 2026-11-05). Đã thử ở GĐ 7: ô mũi tên 18px trên mọi dòng (giả thuyết TreeCellSkin chừa 18px cho dòng lá). Lần sau: chụp / ghi log vị trí node từng cấp (D0.2), hướng tự vẽ mũi tên trong graphic của ô |
| T9 | **Ô chọn (ComboBox) vẫn có viền border lệch ở chế độ bo góc** (thấy ở Quản lý kết nối, cũng ảnh hưởng các màn hình khác; đã thử: gộp viền ô chọn cho gõ tay thành một lớp, bỏ viền của nút mũi tên và ô nhập bên trong) | Trung bình | **Chưa sửa được** (bạn kiểm 2026-11-05). Đã thử ở GĐ 7: bán kính 2 lớp `6, 5` (AtlantaFX dùng `4px, 3px`), ô giá trị trong ô chọn không vẽ nền. Lần sau: phóng to ảnh chụp ô chọn ở 2 theme để biết lớp nào vẽ viền (D0.2) |
| T10 | **Nguồn của bảng kết quả**: tiêu đề "Bảng N" hiện chỉ lấy tên bảng sau FROM đầu tiên của câu lệnh | Thấp | Ghi lại 2026-11-05 theo yêu cầu, làm sau: xác định đúng nguồn khi kết quả đến từ routine (bảng tạm `#tmp`, biến bảng `@tv`, nhiều câu trong một batch, JOIN / sub-query, CTE); cần backend trả kèm thông tin câu lệnh của từng result set (Gate D) |
| T11 | **Cột # không cố định khi cuộn ngang lúc có nhiều bảng kết quả** (chỉ đúng với 1 bảng; đã thử: gắn lại từ ô khi ô đã hiện, chỉ lấy thanh cuộn của chính bảng) | Trung bình | **Chưa sửa được** (bạn kiểm 2026-11-05). Đã thử ở GĐ 7: bỏ `setFixedCellSize` ở lưới xếp chồng (giả thuyết TableRowSkin gỡ ô của cột ngoài tầm nhìn). Lần sau: làm cột số dòng riêng ngoài TableView, đồng bộ cuộn dọc, dùng chung cho mọi lưới |

## 3. Lộ trình theo giai đoạn

| GĐ | Nội dung | Màn hình | Đầu ra chính | Bạn cần quyết định |
|---|---|---|---|---|
| **0** | Nền tảng: T1–T6 | — | token, app sạch log, test xanh | ✅ |
| **1** | Bố cục chung + status bar | SCR-MAIN-001, SCR-MAIN-005 | card bo góc, status bar màu theo connection | ✅ |
| **2** | Object Explorer + dialog kết nối | SCR-MAIN-002, DLG-001…006 | cây mock, context menu; dialog kết nối, mật khẩu, nhóm, lọc | 2B + 2C: ✅ chốt 2026-11-05 (còn T9) |
| **2C** | Áp dụng R-UI-01 toàn app + làm gọn Connection Manager | Cửa sổ chính, DLG-001…006, Settings | CSS phẳng/bo góc dùng chung, chevron cho mọi ô chọn, form DLG-001 2 cột | ✅ chốt (còn T9) |
| **3** | Vùng soạn thảo (phần còn lại) + dialog khi chạy | SCR-MAIN-003, DLG-007, 008, 010 | Tab strip, banner mất kết nối, Monaco, dialog cảnh báo SQL nguy hiểm, format, debug extension | Mức hiển thị các nút hoãn |
| **4** | Bảng kết quả | SCR-MAIN-004 | Tab Results/Messages/Errors/Logs/Plan, nhiều bảng kết quả, footer thống kê | Grid: giữ TableView hay grid ảo hoá khác |
| **5** | Settings + quản lý code style | SCR-SET-001, DLG-009 | 9 nhóm, Visual/JSON, tìm kiếm, xem trước font | Settings là dialog hay tab |
| **6** | Dialog phụ | DLG-011…014 | Popup thông báo, About, kiểm tra cập nhật (mock), lớp phủ đang tải | — |
| **7** | Review tổng | Toàn bộ | So sánh ảnh nguồn và đích, báo cáo `04-reports/` | Gate C cuối |
| **8** | Tích hợp logic thật | — | Kế hoạch: `04-reports/phase8-plan.md` (D0 chuẩn bị, D1 kết nối, D2 Explorer, D3 chạy câu lệnh, D4 lưới, D5 plan, D6 format, D7 IntelliSense, D8 cập nhật, D9 review) | **Gate D**: chỉ làm khi bạn đồng ý rõ ràng; 7 câu hỏi ở mục 5 của kế hoạch |

## 4. Cách làm mỗi màn hình (lặp lại)

1. Chụp màn hình nguồn (bản `dist/LLTool_v1.3.1`) và đích (chạy bằng `run-ui.cmd`), cùng kích thước cửa sổ.
2. Viết đặc tả trong `01-screens/<ID>/`.
3. Đề xuất thiết kế kiểu VS Code kèm mockup để bạn duyệt (**Gate B**).
4. Sửa code JavaFX, chỉ đụng UI và mock data.
5. Build, chạy, chụp lại, so sánh và cập nhật mapping (**Gate C**).

## 5. Lưu ý vận hành

- Khi cần điều khiển máy để chạy và chụp app, bạn thu nhỏ Excel và cửa sổ Claude để chúng không che app.
- Sau mỗi lần ghi file, đối chiếu kích thước file trên máy.
- Chạy app bằng `run-ui.cmd` hoặc `mvn compile javafx:run` (riêng `mvn javafx:run` có thể không biên dịch lại).

## 6. Bước tiếp theo đề xuất

Giai đoạn 1–6 đã chốt 2026-11-05. Giai đoạn 7 xong phần review code (`04-reports/phase7-review.md`); T7, T8, T9, T11 đã thử sửa nhưng chưa đạt, cần bằng chứng chạy thật. **Giai đoạn 8**: Gate D đồng ý 2026-11-05; D0 + D1 tạm đạt (2 tab, 2 PID); D2 Explorer thật (định nghĩa / script từ server, sequences, lỗi tải, toolbar database / schema theo tab) đã code. Danh sách cần kiểm lại K1–K10: `04-reports/phase8-plan.md` mục 7. Lỗi tồn đọng: T7, T8, T9, T11 (chỉ ghi log theo yêu cầu), T10 (D4).
