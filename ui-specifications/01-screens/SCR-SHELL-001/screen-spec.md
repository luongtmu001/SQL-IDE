# Window Shell & Title Bar — Screen Specification
# Đặc tả: Khung cửa sổ & Thanh tiêu đề

- Screen ID: `SCR-SHELL-001`
- Source: Python + PyWebView (luoBTool IDE v1.3.1, chạy bản `dist/LLTool_v1.3.1`)
- Target: JavaFX (SQL-IDE, chạy bằng `run-ui.cmd`)
- Status: `implemented_mock` (đã kiểm tra, chờ duyệt Gate C)
- Evidence confidence: observed + code_verified (chi tiết trong `evidence.md`)
- Last inspected: 2026-11-04

## 1. Tổng quan / Overview

- **Mục đích:** khung cửa sổ của IDE: title bar, menu ứng dụng, các nút toàn cục và nút điều khiển cửa sổ.
- **Cách mở:** khởi động app (sau splash).
- **Màn hình cha:** không có. Mọi màn hình khác đều nằm trong khung này.
- **Liên quan:** SCR-MAIN-001 (bố cục), SCR-SET-001 (Settings), SCR-DLG-012/013 (About/Update), SCR-DLG-009 (Code style).
- **Quyết định đã duyệt:** dùng title bar tùy biến kiểu VS Code (menu nhúng trong title bar) nhưng **phải hành xử như title bar native**. Mục tiêu là tương đương, không cần giống nguồn từng pixel.

## 2. Tham chiếu hình ảnh / Visual reference

| File | Nội dung | Khung |
|---|---|---|
| `source-main-titlebar-1456x816.jpg` | Nguồn: caption native + navbar (maximized, theme Ambiance dark) | 1456×816 |
| `source-main-rail.jpg` | Nguồn: navbar khi sidebar thu gọn | 1456×819 |
| `target-restored-1456x819.jpg` | Đích: cửa sổ restored, theme Primer Light | 1456×819 |
| `target-titlebar-zoom.jpg` | Đích: phóng to title bar | — |
| `target-close-hover.jpg` | Đích: hover nút Close | — |
| `target-maximized.jpg`, `target-maximized-taskbar.jpg` | Đích: maximize, taskbar vẫn hiện | — |
| `target-after-drag.jpg` | Đích: sau khi kéo cửa sổ | — |
| `target-corner-left.jpg`, `target-corner-right.jpg` | Đích: góc bo + đổ bóng | — |
| `target-narrow-1075.jpg`, `target-narrow-menu-ellipsis.jpg` | Đích: **lỗi** tên menu bị cắt khi rộng ~1075 px | — |

Display scale: `unknown`.

## 3. Bố cục / Layout

**Nguồn có 2 hàng:**
- Hàng 1: caption native của Windows (khoảng 30 px; "luoBTool IDE" bên trái; min/max/close).
- Hàng 2: navbar (khoảng 42 px) gồm:
  - logo
  - menu File · Edit · Format · View · Settings · Help
  - nút Snippets · Sheets · BRAVO Tool
  - (spacer)
  - nút Update (ẩn) · Theme · Settings · badge môi trường `DEV`
- Navbar có thể tự ẩn, hiện lại khi đưa chuột lên mép trên.

**Đích có 1 hàng 34 px kiểu VS Code:**
- icon
- MenuBar (File, Edit, View, Navigate, Tools, Window, Help)
- (caption)
- tiêu đề `<tài liệu> — SQL IDE`
- ô Search
- (caption)
- 5 nút layout · Theme · Settings
- − □ ×

Dưới 950 px, menu gộp vào nút hamburger.

Chi tiết vùng: `layout.json` (REG-SHELL-001…006).

## 4. Các vùng / Regions

| Region | Nguồn | Đích | Ghi chú |
|---|---|---|---|
| REG-SHELL-001 Khung native | Windows frame chuẩn | `UNDECORATED` + subclass WndProc (`WindowsWindowAdapter`) | góc bo + bóng đổ OK |
| REG-SHELL-002 Caption | native | gộp vào title bar tùy biến | |
| REG-SHELL-003 Hàng menu | navbar riêng, có auto-hide | gộp vào title bar | thiếu auto-hide |
| REG-SHELL-004 Trái | logo + 6 menu | icon + 7 menu | khác bộ menu |
| REG-SHELL-005 Giữa | Snippets/Sheets/BRAVO | tiêu đề + Search | launcher thuộc tính năng hoãn |
| REG-SHELL-006 Phải | Update, Theme, Settings, Env | layout toggles, Theme, Settings, − □ × | thiếu Env, Update |

## 5. Component / Components

`components.json` có 45 mục:
- 6 menu, 20 menu item
- 9 nút của nguồn, 5 nút chỉ có ở đích
- 3 nút điều khiển cửa sổ
- logo, tiêu đề, search, badge môi trường

Các nút thuộc tính năng ngoài phạm vi (Snippets, Sheets, BRAVO, Object search, Query History, Compare, Activity Monitor, Schema Compare, Google Sheets, Report Gen) được ghi nhận với trạng thái `deferred`.

## 6. Tương tác — kết quả kiểm tra hành vi native

| # | Hành vi | Nguồn | Đích (đã kiểm tra) | Kết luận |
|---|---|---|---|---|
| INT-001 | Kéo caption để di chuyển, Aero Snap | native | **Có**: kéo được | ✅ |
| INT-002 | Double-click caption để maximize/restore | native | **Có**, taskbar vẫn hiện, không bị cắt mép | ✅ |
| INT-003 | Chuột phải caption mở system menu | native | **Không hiện gì** | ❌ cần sửa |
| INT-004 | Alt+Space mở system menu | native | chưa kiểm được (máy này dùng Alt+Space cho ChatGPT) | ❓ |
| INT-005 | Hover nút Maximize hiện Snap Layouts (Win11) | native | không thấy trong ảnh chụp (có thể do bị che khi chụp) | ❓ bạn kiểm giúp |
| INT-006 | Resize 8 hướng | viền vô hình nằm **ngoài** cửa sổ | resize được, nhưng dải resize 6 DIP nằm **trong** nội dung | ⚠️ cải thiện |
| INT-007 | Minimize, bấm taskbar để thu/phóng | native | **Có** | ✅ |
| INT-009 | Cửa sổ mất focus thì caption mờ đi | native | không có style inactive | ❌ cần sửa |
| INT-012 | Thu hẹp cửa sổ | navbar cố định | tên menu bị cắt thành "…" trước khi chuyển sang hamburger | ❌ lỗi |
| INT-011/016 | Tự ẩn navbar | có (setting `appearance.autoHideNavbar`) | không có | ⚠️ thiếu |

## 7. Trạng thái / States

Xem `states.json`: restored, maximized, inactive, hover Close/Max, compact (hamburger), truncated (lỗi), auto-hidden, update badge.

## 8. Đề xuất JavaFX / Design proposal

Giữ kiến trúc hiện tại (`TitleBarController` + `WindowsWindowAdapter`) và chỉ sửa các khoảng trống sau.

**P1 — Hành vi native (ưu tiên cao)**
1. **System menu:**
   - Trong `WindowsWindowAdapter`, xử lý `WM_NCRBUTTONUP` khi chuột ở `HTCAPTION`: gọi `GetSystemMenu` rồi `TrackPopupMenu` tại vị trí con trỏ và gửi `WM_SYSCOMMAND`.
   - Cập nhật trạng thái các mục (Restore/Move/Size/Maximize) theo trạng thái cửa sổ.
   - Xử lý thêm `WM_SYSCOMMAND/SC_KEYMENU` (Alt+Space), giống cách Windows Terminal làm.
2. **Trạng thái inactive:** lắng nghe `stage.focusedProperty()` và gắn pseudo-class `:window-inactive` lên `.window-title-bar`. Khi inactive, CSS giảm độ đậm của chữ, icon, menu và nút điều khiển (như VS Code `titleBar.inactiveForeground`).
3. **Viền resize kiểu native:**
   - Khi restored, `WM_NCCALCSIZE` chỉ bỏ phần caption và giữ khung trái/phải/dưới theo `SM_CXSIZEFRAME + SM_CXPADDEDBORDER`. Windows 10/11 vẽ khung này vô hình nên nội dung không bị dải resize đè lên.
   - Giữ dải resize mép trên 6 DIP trong client (cách VS Code/Terminal làm).
   - Nếu cách này gây lệch vị trí, phương án dự phòng là giữ dải 6 DIP như hiện tại nhưng giảm còn 4 DIP ở mép trái/phải.
4. **Taskbar tự ẩn:** khi maximize và taskbar đang bật auto-hide, chừa 1 px ở cạnh có taskbar để còn gọi taskbar ra được.

**P2 — Bố cục title bar**

5. **Sửa lỗi menu bị cắt:**
   - Đặt `minWidth = USE_PREF_SIZE` cho các nút menu.
   - Bỏ ngưỡng cố định 950 px. Thay bằng: gộp vào hamburger khi bề rộng còn lại nhỏ hơn bề rộng ưu tiên của MenuBar cộng các vùng giữa/phải và khoảng caption tối thiểu (khoảng 40 px).
6. **Bộ menu:** giữ cấu trúc kiểu VS Code (File · Edit · Selection/Format · View · … · Help) nhưng bổ sung các mục còn thiếu so với nguồn:
   - Edit: Goto Begin (Alt+[), Goto End (Alt+]), Refresh Objects & IntelliSense (Ctrl+Shift+R).
   - Menu **Format**: Format Code (Ctrl+K, Ctrl+D), SQL Code Styles… (Ctrl+Alt+F).
   - View: Tự động ẩn menu (checkable).
   - Help: Kiểm tra cập nhật…
   - Các mục thuộc tính năng hoãn: hiện ở trạng thái disabled kèm tooltip "Chưa hỗ trợ".
   - **Cần bạn quyết định** phím tắt Format: `Ctrl+K, Ctrl+D` theo nguồn hay `Ctrl+Alt+L` như hiện tại.
7. **Vùng phải:**
   - Thêm badge môi trường (DEV/TEST/PROD, dùng `Badge` có sẵn).
   - Thêm nút Update (ẩn cho đến khi có bản mới).
   - 5 nút layout: VS Code chỉ có khoảng 3–4 nút. Đề xuất giữ Toggle Explorer, Toggle Results và một nút "Customize Layout" (menu gồm Maximize Results, Float, Reset).
8. **Logo:** dùng logo sản phẩm (ảnh) thay icon `fth-database`.
9. **Tên ứng dụng:** "SQL IDE" hay "luoBTool IDE"? Cần bạn chọn.

**P3 — Tùy chọn**

10. Tự ẩn menu (map sang setting `appearance.autoHideNavbar`): ẩn MenuBar và hiện khi nhấn Alt, giống VS Code `window.menuBarVisibility = toggle`. Đây là cách tương đương phù hợp hơn với desktop so với cảm biến hover của nguồn.

**Hiệu năng/tương thích:** mọi xử lý native đều chạy trên FX thread sẵn có. Phương án dự phòng thuần JavaFX (`WindowDragHandler`, `WindowResizeHandler`) giữ nguyên cho nền tảng khác.

## 9. Điểm chưa rõ / Known gaps

| # | Nội dung | Chặn? |
|---|---|---|
| Q1 | Snap Layouts có hiện khi hover nút Maximize không: bạn kiểm giúp trên máy | Không |
| Q2 | Phím tắt Format | Không |
| Q3 | Tên ứng dụng trên title bar | Không |
| Q4 | Rút 5 nút layout xuống 3 | Không |
| Q5 | Display scale của máy (100/125/150%) để đo kích thước chính xác | Không |

**Ghi chú phát sinh ngoài phạm vi title bar:**
- App JavaFX **tự kết nối DB thật** khi khởi động: log ghi nhận lần thử kết nối `10.20.30.40:5433/staging_db` (thất bại). Điều này vi phạm quy tắc "prototype chỉ dùng mock data" ở Gate 3, sẽ xử lý ở SCR-MAIN-002.
- Có cảnh báo CSS `ClassCastException String → Paint` lặp lại trong log.

## 10. Tiêu chí nghiệm thu / Acceptance criteria

- [x] Đủ các vùng (6 region).
- [x] Đủ control (45 component, gồm cả menu item và nút hoãn).
- [x] Tương tác đã ánh xạ (16).
- [x] Trạng thái liên quan (9).
- [x] Đã xem hành vi resize/maximize.
- [x] Đã có mapping.
- [ ] **Bạn duyệt đặc tả và đề xuất (Gate B).**

Sau khi được duyệt, bản sửa phải đạt:
- Chuột phải lên caption mở system menu.
- Title bar mờ đi khi cửa sổ mất focus.
- Không còn tên menu bị cắt ở mọi bề rộng ≥ 960.
- Resize không đè lên thanh cuộn hay nội dung ở mép.
- Snap, double-click và maximize tôn trọng taskbar như hiện tại.

## 11. Nhật ký triển khai / Implementation log (2026-11-04, sau Gate B)

Quyết định của bạn: duyệt cả 7 đề xuất; phím Format giữ `Ctrl+Alt+L`; tên app là "SQL IDE"; giữ 5 nút layout nhưng thiết kế lại toolbar bên dưới title bar (đề xuất riêng); Snap Layouts **có** hoạt động (bạn xác nhận).

| # | Thay đổi | File | Đã kiểm tra trên máy |
|---|---|---|---|
| 1 | System menu khi chuột phải caption (`WM_NCRBUTTONUP`) + Alt+Space (`SC_KEYMENU` và bộ lọc phím JavaFX, có chống bật 2 lần) | `WindowsWindowAdapter`, `WindowController` | ✅ bạn xác nhận |
| 2 | Pseudo-class `:window-inactive` làm mờ chữ, icon, nút khi cửa sổ mất focus | `TitleBarController`, `titlebar.css` | ✅ bạn xác nhận |
| 3 | Viền resize trái/phải/dưới nằm ngoài nội dung (khung native vô hình); mép trên 6 DIP; hit-test theo gốc client; có cờ `NATIVE_OUTER_RESIZE_FRAME` để quay lại cách cũ | `WindowsWindowAdapter` | ✅ bạn xác nhận |
| 4 | Taskbar tự ẩn: khi maximize chừa 1 px | `WindowsWindowAdapter` | ❓ |
| 5 | Ngưỡng hamburger tính động (≥ 950), menu không bao giờ bị cắt chữ | `TitleBarController` | ✅ ở ~1200 px chuyển sang ☰, không còn "…" |
| 6 | Menu bổ sung: Edit ▸ Go to Beginning/End (Alt+[ / Alt+], cả trong Monaco), Refresh Objects & IntelliSense (Ctrl+Shift+R); menu Format (Format SQL Ctrl+Alt+L, SQL Code Styles… Ctrl+Alt+F, mock); View ▸ Auto-hide Menu Bar (nhấn Alt để hiện); Tools ▸ 7 mục hoãn (disabled); Help ▸ Check for Updates… (mock) | `main-window.fxml`, `MainController`, `MonacoEditorHost`, `editor-bridge.js` | ❓ |
| 7 | Badge môi trường theo kết nối đang chọn (DEV/TEST/STAGING/PROD), nút Update (ẩn) | `titlebar.fxml`, `TitleBarController`, `MainController` | ✅ hiện "STAGING" |
| 8 | Logo sản phẩm trên title bar + icon taskbar/Alt+Tab; tên cửa sổ "SQL IDE" | `titlebar.fxml`, `AppLauncher`, `static/icons/app-logo-*.png` | ✅ |

Biên dịch: thành công qua `run-ui.cmd` (`mvnw -q compile javafx:run`). Backend/business logic: **không thay đổi**.

**Kiểm tra 2026-11-04 23:51:** bạn xác nhận system menu (chuột phải), trạng thái mờ khi mất focus và resize đều ổn.
**Sự cố công cụ:** một lần ghi file trước đó đã chép bản cũ lưu đệm. Đã ghi lại toàn bộ và đối chiếu kích thước từng file trên máy.
