# Design Tokens
# Hệ thống token thiết kế

Token đã được duyệt ngày 2026-11-05. **Không tự tạo bộ token mới.**

- Tham chiếu: `ui-specifications/02-design-system/tokens.md`, `tokens.json`.
- Thực thi: `src/main/resources/css/`.

## Thứ tự ưu tiên khi chọn màu

1. Biến vùng `-ide-*` trong `ide-vars.css`, ứng với khóa workbench của VS Code. Theme nhập vào có thể ghi đè nhóm này.
2. Biến AtlantaFX `-color-*`.
3. Hai biến bổ sung trong `base.css`: `-color-bg-muted`, `-color-accent-fg-emphasis`.

Không viết mã hex trong Java hoặc CSS. Ngoại lệ đã duyệt: nút Close của cửa sổ `#e81123`.

`tokens.md`, nguyên tắc 2, ghi "không dùng thêm biến nào" là **đã cũ**: nhóm `-ide-*` được thêm ngày 2026-11-06. Biến `-ide-*` mới phải được khai báo ở `.root` trong `ide-vars.css`, có giá trị mặc định lấy từ `-color-*`, và có khóa VS Code tương ứng nếu có.

## Giá trị đã chốt

| Nhóm | Giá trị |
|---|---|
| Font giao diện | Inter, dự phòng Segoe UI Variable, Segoe UI. Đổi được trong Settings. **File font chưa được nhúng** |
| Editor | JetBrains Mono 13, line-height 1.5 |
| Lưới kết quả | JetBrains Mono 12 |
| Messages | Cascadia Mono, dự phòng Consolas, 12 |
| Khoảng cách | 2, 4, 6, 8, 12, 16, 24 px |
| Chiều cao | Giá trị tham chiếu tại cỡ chữ 13px (xem R-UI-14 trong `rules/07-ui-consistency-rules.md` để dùng đơn vị em tương đối): title bar 34, toolbar 32, tab strip 32, panel header 28, status bar 22, control 24, dòng cây 22 |
| Bo góc | Xem R-UI-01 trong `rules/02-design-principles.md` |

## Theme

- Sẵn có: Primer Dark/Light, Dracula, Nord Dark/Light, Cupertino Dark/Light (AtlantaFX).
- `ThemeRegistry` và `VsixThemeImporter`: nhập theme VS Code từ `.vsix`, lưu vào `themes/<id>/theme.css`, nạp sau cùng. `MonacoThemes` đồng bộ theme cho Monaco. Theme dạng `.tmTheme` chưa hỗ trợ.
- `ThemeService`: thứ tự nạp là `base.css`, `ide-vars.css`, theme sáng hoặc tối, các CSS thành phần, theme nhập vào, rồi `shape.css` và `shape-flat.css`. Kiểm lại thứ tự thực tế trong code trước khi đổi.
- Đổi theme hoặc panelStyle phải có tác dụng ngay, không cần khởi động lại. Không dựng lại lưới khi đổi theme.

## Khi cần token mới

Ghi: tên, mục đích, giá trị sáng và tối, ví dụ sử dụng, độ tương phản, nguồn (LLTool, VS Code hay người dùng duyệt). Kiểm tra ít nhất một theme sáng, một theme tối và cả hai chế độ `panelStyle`.
