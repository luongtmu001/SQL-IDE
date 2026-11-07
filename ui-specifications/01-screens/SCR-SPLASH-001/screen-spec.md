# SCR-SPLASH-001 — Splash khởi động / Start-up splash

| Field | Value |
|---|---|
| Nguồn Python | `templates/splash.html`, chuyển cửa sổ trong `main.py` (WIN-SPLASH) |
| Đích Java | `app.ui.splash.SplashWindow` (view), `app.application.StartupSequence` (điều phối) |
| Mockup đã duyệt | https://claude.ai/artifact/EBsFRNTQo5KXCEYRS5BNTY |
| Trạng thái | Mockup đã duyệt; đã tích hợp vào luồng khởi động thật |

## 1. Nguyên lý hiển thị / Display rule

1. Splash hiện đầu tiên, trước khi nạp backend và dựng cửa sổ chính.
2. Splash chỉ ẩn khi **IDE sẵn sàng**: bootstrap xong, cửa sổ chính dựng xong (ẩn), Monaco đã báo `onEditorReady`.
3. Nếu IDE sẵn sàng sớm, splash vẫn hiện **tối thiểu 1,75 giây**, tính từ lúc splash xuất hiện.
4. Cửa sổ chính hiện phía sau splash, 120 ms sau splash mờ dần trong 180 ms rồi đóng.
5. Lưới an toàn: IDE không báo sẵn sàng sau 30 giây thì vẫn mở cửa sổ chính và ghi cảnh báo vào log.

## 2. Bố cục / Layout

Cửa sổ 720×440, không viền, bo góc 12 px, luôn nằm trên cùng, căn giữa màn hình, kéo được bằng chuột ở bất kỳ đâu.

| Vùng | Vị trí | Nội dung |
|---|---|---|
| Nút thu nhỏ | trên phải, cách mép 8 px, 36×28 | Biểu tượng gạch ngang, tooltip "Thu nhỏ" |
| Nhận diện | trái, x=40, từ y≈40 | Tên app 44 px, mô tả 14 px, danh sách driver 12 px |
| Hình chữ L kép | x=408→mép phải, y=48→392 | Hình logo phóng lớn, tràn mép phải |
| Trạng thái | trái, đáy ở y=392 | Tên bước + đếm `n/6`, phiên bản |
| Thanh tiến trình | mép dưới, cao 3 px | Tỉ lệ số bước đã xong |
| Khối lỗi | trái, y=270→392 (thay khối trạng thái) | Tiêu đề, chi tiết tối đa 110 ký tự, 3 nút |

## 3. Các bước khởi động / Steps

| # | Nhãn | Nguồn tín hiệu |
|---|---|---|
| 1 | Đang chuẩn bị thư mục ứng dụng | `DesktopBootstrap`, bước 1 |
| 2 | Đang đọc cấu hình | `DesktopBootstrap`, bước 2 |
| 3 | Đang mở kho thông tin xác thực | `DesktopBootstrap`, bước 3 |
| 4 | Đang nạp driver cơ sở dữ liệu | `DesktopBootstrap`, bước 4 (gồm cả repository và core platform) |
| 5 | Đang dựng giao diện và workspace | Nạp FXML, `MainController.initialize` |
| 6 | Đang khởi tạo trình soạn thảo | Chờ `EditorController.whenEditorReady` |
| – | Sẵn sàng | IDE sẵn sàng, có thể đang chờ đủ 1,75 giây |

## 4. Tương tác / Interactions

| ID | Điều khiển | Kết quả |
|---|---|---|
| BTN-MINIMIZE | Nút thu nhỏ | Thu splash xuống taskbar. Khi IDE sẵn sàng, cửa sổ chính mở ở trạng thái thu nhỏ, không tự nhảy lên trước |
| BTN-RETRY | Thử lại (khi lỗi) | Chạy lại từ bước 1 |
| BTN-OPEN-LOGS | Mở thư mục log (khi lỗi) | Mở thư mục `logs` của ứng dụng |
| BTN-EXIT | Thoát (khi lỗi) | Thoát ứng dụng |
| – | Kéo chuột trên cửa sổ | Di chuyển splash |
| – | Đóng từ taskbar | Thoát ứng dụng |

Khi có lỗi mà splash đang thu nhỏ, splash tự hiện lại để lỗi không bị bỏ sót.

## 5. Theme

Splash chọn sáng/tối theo khoá `theme` trong `ide-settings.json`, đọc thẳng file để không kích hoạt bootstrap. Theme nhập từ VS Code được đoán theo tên (chứa "light" là sáng). Chưa có file cấu hình thì dùng giao diện tối.

## 6. Thay đổi so với Python / Changes from Python

| Python | Java |
|---|---|
| 800×500, banner trắng trong khung | 720×440, logo là bố cục chính |
| Thanh chạy qua lại + spinner, một câu trạng thái cố định | Thanh tiến trình theo bước thật, có nhãn từng bước |
| Tối thiểu 2 giây, hết 10 giây tự mở cửa sổ chính | Tối thiểu 1,75 giây (theo yêu cầu), lưới an toàn 30 giây |
| Không có nút thu nhỏ, không có màn lỗi | Có cả hai |
| Câu slogan vui | Câu mô tả sản phẩm (dòng đang bị comment trong `splash.html`) |

Không có chức năng nào của splash Python bị bỏ.

## 7. Câu hỏi còn mở / Open questions

- `unknown`: tên hiển thị cuối cùng. Hiện dùng `AppInfo.NAME` = "SQL IDE"; bản Python là "LLTool" / "luoBTool IDE".
- `inferred`: mở cửa sổ chính ở trạng thái thu nhỏ khi người dùng đã thu nhỏ splash. Đây là lựa chọn của người thiết kế, chưa được xác nhận.
