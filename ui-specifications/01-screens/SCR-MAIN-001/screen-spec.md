# Main Workspace Layout — Screen Specification
# Đặc tả: Bố cục workspace chính

- Screen ID: `SCR-MAIN-001`
- Source: LLTool v1.3.1, `templates/index.html#ide-main-area`, `static/css/app.css`, `explorer.css`
- Target: `main-window.fxml`, `layout.css`, `LayoutService`, `MainController`
- Status: `implemented_mock` (Gate B duyệt 2026-11-05; Gate C chờ xác nhận khe editor/results)
- Evidence: observed (ảnh nguồn `main-full-1456x816.jpg`) + code_verified
- Last inspected: 2026-11-05

## 1. Tổng quan

Vùng làm việc nằm giữa context toolbar và status bar. Gồm 3 panel: Object Explorer (trái), Editor (trên phải), Results (dưới phải). Các panel ngăn cách bằng sash kéo được.

## 2. So sánh nguồn và đích

| # | Hạng mục | Nguồn (observed / code_verified) | Đích hiện tại (code_verified) | Khoảng cách |
|---|---|---|---|---|
| 1 | Kiểu panel | Panel phẳng sát nhau, nền `--ide-bg-main` | 3 **card** bo góc 8px, khe 4px trên nền subtle | Khác VS Code. Cần quyết định (Q1) |
| 2 | Độ rộng sidebar | **260px cố định**, không đổi khi resize cửa sổ | Tỉ lệ `0.22`, sidebar phình/co theo cửa sổ | **Lỗi**: cần cố định theo px |
| 3 | Rail thu gọn | Rail icon riêng | Tỉ lệ `0.038` (chỉ đúng ~46px ở 1210px) | **Lỗi**: cần cố định 46px |
| 4 | Sash | 6px, trong suốt, **đổi màu accent khi hover/kéo** | Divider 4px trong suốt, không có phản hồi hover | Thiếu phản hồi |
| 5 | Editor/Results | Tỉ lệ kéo được | 0.65 / 0.35 | Giữ |
| 6 | Ẩn Explorer | Thu gọn → rail; ẩn hẳn → nút mở lại | Alt+1 bật/tắt, có rail | Gần tương đương |
| 7 | Ẩn Results | Ẩn khi tab chưa kết nối (banner) | Ctrl+R, Ctrl+Alt+R (maximize), float | Banner thuộc SCR-MAIN-003 |
| 8 | Lưu bố cục | localStorage | `Preferences` (`LayoutService`) | Đổi sang lưu px cho sidebar |

## 3. Đề xuất (Gate B)

- **Q1 — Panel phẳng (A, đề xuất)**: bỏ card và khe; panel sát nhau, ngăn bằng hairline 1px `border-subtle`, không bo góc. Header panel 28px, nền `surface-2`. Lựa chọn B: giữ card như hiện tại.
- Sidebar: mặc định **260px**, min 180px, max 50% cửa sổ. `SplitPane.setResizableWithParent(sidebar, false)` để giữ nguyên px khi resize. Lưu px thay vì tỉ lệ.
- Rail: cố định **46px** (min = max = 46).
- Sash: vùng bắt chuột 4px, nhìn thấy 1px hairline; hover/kéo → 2px `accent` (giống VS Code `sash.hoverBorder`).
- Editor/Results: 65/35, mỗi panel min 120px. Results co giãn theo cửa sổ.
- Phím tắt: giữ Alt+1, Ctrl+R; **thêm Ctrl+B** (bật/tắt Explorer, giống VS Code).

## 4. Ngoài phạm vi

Banner "chưa kết nối" (SCR-MAIN-003), nội dung Explorer (SCR-MAIN-002), nội dung Results (SCR-MAIN-004).

## 5. Quyết định đã duyệt (Gate B, 2026-11-05)

- Q1 = **B, giữ card bo góc**, sửa lỗi viền bị che ở góc. Bổ sung: toolbar và status bar cũng là card; mọi khe 6px.
- Thêm Ctrl+B (giữ Alt+1).
- **Setting mới** `appearance.panelStyle` = `rounded` (mặc định) | `flat` (VS Code). Settings → Giao diện → Kiểu khung giao diện.

## 6. Nhật ký triển khai

| Ngày | Thay đổi | File |
|---|---|---|
| 2026-11-05 | Viền card vẽ bằng overlay nằm trên nội dung + clip bo góc (`RoundedCard`) | `ui/components/RoundedCard.java`, `layout.css`, `editor.css`, theme css |
| 2026-11-05 | Explorer cố định px (260, min 180, max 50%), rail 46px, lưu `sidebar_width_px`; preset thực sự đổi kích thước | `LayoutService`, `MainController` |
| 2026-11-05 | Toolbar + status bar thành card, margin 6px; nền canvas `-color-bg-inset`, viền `border-default`; bỏ grabber | `MainController`, `layout.css` |
| 2026-11-05 | Combo toolbar: chevron xuống/lên, padding phải 8px | `layout.css` |
| 2026-11-05 | Khe editor/results: style divider không hiệu lực (và làm mất con trỏ resize) → chuyển sang padding đáy 4px của `editorSlot` + sash 2px | `main-window.fxml`, `MainController`, `layout.css` |
| 2026-11-05 | Kiểu Phẳng: `.flat-panels` (không margin, radius 0, hairline 1px) | `IdeSettings`, `SettingsService`, `SettingsDialogController`, `settings-dialog.fxml`, `layout.css` |

## 7. Còn mở

- Khe editor/results: bản sửa `editorSlot` chưa được xác nhận trên máy.
