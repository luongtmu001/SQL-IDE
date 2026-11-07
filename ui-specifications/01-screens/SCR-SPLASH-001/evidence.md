# SCR-SPLASH-001 — Evidence

## Nguồn / Sources

| Nguồn | Nội dung | Mức |
|---|---|---|
| `IDE-SQL-Lite_New_Pyweb_Claude/templates/splash.html` | Bố cục, màu và nội dung splash Python | `code_verified` |
| `ui-specifications/00-application/project-map.md` (mục khởi động) | Tối thiểu 2 giây, fallback 10 giây, frameless, on-top, kéo được | `code_verified` |
| `DesktopBootstrap` | Thứ tự các bước khởi động | `code_verified` |
| `MonacoEditorHost.BridgeCallback.onEditorReady` | Tín hiệu editor sẵn sàng | `code_verified` |
| `WindowsWindowAdapter.install` (`FindWindow` theo tiêu đề) | Lý do tiêu đề splash phải khác cửa sổ chính | `code_verified` |

## Lý do chọn Swing / Why Swing

Bản đầu viết bằng JavaFX. Khi chạy thật, `FXMLLoader.load` và các bước đi kèm chặn luồng FX 5–10 giây, nên splash trên cùng luồng đứng hình và nút thu nhỏ không phản hồi. Vì vậy splash chạy trên luồng AWT, độc lập với luồng FX. Các khoảng chờ cũng dùng `ScheduledExecutorService` thay vì `PauseTransition`: trong một lần chạy, `PauseTransition` với thời gian 0 mất 5,4 giây khi không có cửa sổ FX nào hiển thị.

## Kiểm chứng / Verification (2026-11-07, chế độ mock, thư mục dữ liệu riêng)

| Trường hợp | Kết quả | Mức |
|---|---|---|
| Khởi động bình thường | Splash hiện đến khi cửa sổ chính mở; IDE sẵn sàng sau 11,5–16 giây | `observed` |
| IDE sẵn sàng ngay (IDE giả, sẵn sàng ở +34 ms) | Cửa sổ chính hiện ở +1755 ms | `observed` |
| Bấm thu nhỏ bằng chuột | Splash xuống taskbar sau khoảng 0,1 giây; cửa sổ chính mở thu nhỏ; khôi phục được, Monaco đầy đủ | `observed` |
| Theme sáng đã lưu | Splash sáng | `observed` |
| Không tạo được thư mục dữ liệu | Hiện màn lỗi "bước 1/6" | `observed` |
| Thử lại với backend giả lỗi lần đầu (chuột thật qua `Robot`) | Backend chạy lần 2, cửa sổ chính mở | `observed` |
| Mở thư mục log, Thoát, đóng từ taskbar | Chưa chạy thử | `unknown` |
| Hết 30 giây vẫn chưa sẵn sàng | Chưa chạy thử | `unknown` |

## Chưa rõ / Unresolved

- Ở lần chạy đầu của bản JavaFX, splash chuyển sang thu nhỏ đúng lúc luồng FX hết bận mà script không bấm gì. Không tái hiện lại trên bản Swing. Nguyên nhân chưa xác định (`unknown`).
- Một cú bấm Thử lại từ script bên ngoài trên app thật không kích hoạt lần chạy lại, trong khi bấm bằng `Robot` trên cửa sổ thật thì có. Nguyên nhân chưa xác định; cần thử tay.

## Ảnh / Screenshots

`screenshots/` chụp cửa sổ thật trên nền magenta do script kiểm tra tạo ra.
