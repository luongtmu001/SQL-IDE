# Design Principles
# Nguyên tắc thiết kế

## Định hướng đã chốt / Agreed direction

- **Tương đương VS Code:** title bar có menu, card Explorer, thanh tab, Monaco, panel kết quả, status bar 22px.
- **Luồng làm việc CSDL như DataGrip:** cây đối tượng, script từ object, tab truy vấn, lưới kết quả.
- **Hành xử như app Windows thật:** Aero Snap, Snap Layouts, system menu, maximize không che taskbar (`WindowsWindowAdapter`, JNA).
- Mật độ thông tin cao, không dùng thẻ to, gradient trang trí hay điều hướng kiểu mobile.

## Quy tắc R-UI-01 (bắt buộc cho mọi màn hình)

Mọi màn hình vẽ theo `appearance.panelStyle`. Không hard-code bo góc.

| | `rounded` (mặc định) | `flat` |
|---|---|---|
| Panel | Card bo 8px, status bar 6px, khe 6px | Liền nhau, ngăn bằng đường kẻ 1px |
| Nút, ô nhập, ô chọn | Bo 6px | Vuông |
| Checkbox | Bo 4px | Vuông |
| Dialog, popup, badge | Bo góc | Vuông |

Quy tắc chung:

- Mọi ComboBox, Spinner, menu thả xuống dùng chevron, không dùng tam giác mặc định.
- Popup của ô chọn không có padding quanh nội dung.
- Nhãn của ô nhập căn phải, sát ô nhập.
- Ô nhập luôn hiện đủ chữ, không đặt chiều cao cố định làm cắt chữ.
- `ThemeService` nạp `shape.css` và, khi phẳng, `shape-flat.css` sau cùng cho mọi Scene. **Thêm bán kính bo góc ở CSS khác thì phải thêm luật phẳng tương ứng.**

## Bố cục đã chốt / Agreed layout

```text
titleBar 34px     logo, menu (gộp hamburger khi hẹp), tìm kiếm, nút layout, theme, settings, nút cửa sổ
contextToolBar 32 kết nối > database > schema, limit, Run, Stop, Plan, Format, Refresh, menu ...
mainHorizontalSplit
  Explorer        260px, kéo được 180px đến 1/2 cửa sổ, rail thu gọn 46px (Ctrl+B, Alt+1)
  centerVerticalSplit
    Editor        thanh tab tự vẽ, Monaco, split phải/dưới
    Results       Results, Messages, Timeline, Execution Plan, Terminal
statusBar 22px    màu theo kết nối
```

Cửa sổ chính khởi đầu 1280×820, tối thiểu 960×620. Settings là cửa sổ riêng 900×640.

## Quy tắc desktop / Desktop rules

1. Tối ưu cho cửa sổ co giãn, không dùng breakpoint kiểu web.
2. Ưu tiên phím tắt và chuột có thể đoán trước. Giữ phím tắt đã có, ví dụ Format là `Ctrl+Alt+L`.
3. Thao tác phá hủy phải nổi bật và có xác nhận, mặc định chọn Hủy.
4. Mỗi control có focus nhìn thấy được và dùng được bằng bàn phím.
5. Việc dài có tiến trình, thời gian chạy và nút Hủy.

## Ánh xạ JavaFX đã chốt / Agreed JavaFX mapping

| Thành phần | Dùng |
|---|---|
| Lưới kết quả | `TableView` + `ResultGridRow`, `fixedCellSize`, lazy theo dòng và cột. Thay thế cần được duyệt: spike nằm ở `spikes/canvas-grid` |
| Nhiều bảng kết quả | `StackedGridsPane`, hoặc tab theo `grid.resultLayout` |
| Editor SQL | Monaco trong một WebView, mỗi tab một model. `SqlCodeArea` chỉ là dự phòng khi WebView chưa sẵn sàng |
| Dialog | Khung `DialogChrome`. **Không dùng `Alert`, `Dialog<>`, `TextInputDialog` của hệ thống** |
| Thông báo | `ToastHost` qua `NotificationService` |
| Thanh tab | `EditorTabStrip` tự vẽ |
| Đang tải | `LoadingOverlay` |
| Splash | `SplashWindow` bằng Swing, chạy trên luồng riêng để không phụ thuộc FX |
