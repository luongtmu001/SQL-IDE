# Screen Inventory — Workflow 02
# Kiểm kê màn hình (phạm vi: màn hình chính, Settings, dialog)

| | |
|---|---|
| Ngày | 2026-11-04 |
| Nguồn | luoBTool IDE v1.3.1 — ảnh chụp từ bản đóng gói `dist/LLTool_v1.3.1` đang chạy trên máy |
| Khung chụp | 1456×816 (logical), theme Ambiance [beta] (dark); display scale: `unknown` |
| Quyết định phạm vi (đã duyệt) | Chỉ màn hình chính + Settings + dialog · giữ Monaco · title bar kiểu VS Code nhưng phải hành xử như native · loại bỏ mục legacy · mục tiêu **tương đương VS Code**, không cần giống 100% |
| Dữ liệu máy | `screen-inventory.json` |

Status: `observed` = đã thấy trên app đang chạy · `partially_observed` = thấy một phần · `discovered` = chỉ có bằng chứng từ code.

## 1. Danh sách màn hình / Inventory

### Khung và màn hình chính

| ID | Màn hình | Status | Quan trọng | Nguồn chính | Đích JavaFX hiện có |
|---|---|---|---|---|---|
| SCR-SHELL-001 | Khung cửa sổ & title bar + menu | partially_observed | critical | `main.py`, `dwm.py`, navbar trong `index.html` | `titlebar.fxml`, `ui/titlebar/*`, `ui/window/*`, `WindowsWindowAdapter` |
| SCR-MAIN-001 | Bố cục workspace chính | observed | critical | `index.html#ide-main-area` | `main-window.fxml`, `MainController`, `LayoutService` |
| SCR-MAIN-002 | Object Explorer (toolbar, search, tree, rail, context menu) | observed | critical | `explorer.js`, `context-menu.js` | `sidebar.fxml`, `SidebarController`, `DatabaseTreeCell` |
| SCR-MAIN-003 | Vùng soạn thảo (breadcrumb, action bar, tab, banner, Monaco, status bar editor) | observed | critical | `editor.js`, `tabs.js`, `monaco-init.js` | `editor.fxml`, `EditorController`, `MonacoEditorHost` |
| SCR-MAIN-004 | Bảng kết quả (Results/Messages/Errors/Logs/Plan, footer) | partially_observed | critical | `query.js`, `grid-result.js`, `execution-plan-tab.js` | `results.fxml`, `ResultsController` |
| SCR-MAIN-005 | Thanh trạng thái | observed | high | `#ide-editor-status-bar`, `#ide-result-footer` | `status-bar.fxml`, `StatusBarController` |
| SCR-SET-001 | Cửa sổ Cài đặt (9 nhóm, Visual/JSON) | observed | high | `settings_window.html`, `settings.js` | `settings-dialog.fxml`, `SettingsDialogController` |

### Dialog

| ID | Dialog | Status | Trigger | Đích JavaFX |
|---|---|---|---|---|
| SCR-DLG-001 | Tạo/sửa kết nối | observed | Connect (toolbar Explorer), Edit Connection | `connection-manager-dialog.fxml` |
| SCR-DLG-002 | Nhập mật khẩu khi kết nối lại | discovered | Kết nối profile chưa có mật khẩu | — |
| SCR-DLG-003 | Tạo / đổi tên nhóm | observed | New Group, Rename Group | — |
| SCR-DLG-004 | Xác nhận xóa nhóm | discovered | Delete Group | — |
| SCR-DLG-005 | Lọc database | discovered | Filter DB (toolbar Explorer) | `DatabaseFilterDialog` |
| SCR-DLG-006 | Lọc object | discovered | Filter trên node thư mục | `ObjectFilterDialog` |
| SCR-DLG-007 | Cảnh báo câu lệnh nguy hiểm | discovered | Run DELETE/UPDATE không WHERE, TRUNCATE | logic `SqlProtectionService`; UI chưa rõ |
| SCR-DLG-008 | Xác nhận format code | discovered | Format | — |
| SCR-DLG-009 | Quản lý định dạng mã SQL | discovered | Format ▸ SQL Style Manager | — |
| SCR-DLG-010 | Cảnh báo extension debug | discovered | Run khi có marker debug | — |
| SCR-DLG-011 | Popup thông báo chung | discovered | nhiều nơi | `AppDialog` (ứng viên) |
| SCR-DLG-012 | About | discovered | Help ▸ About | — |
| SCR-DLG-013 | Kiểm tra cập nhật | discovered | Help ▸ Check updates | — |
| SCR-DLG-014 | Lớp phủ đang tải | discovered | thao tác dài | `LoadingView` (ứng viên) |

**Tổng:** 21 màn hình/bề mặt — 7 observed, 2 partially_observed, 12 discovered.

## 2. Ngoài phạm vi / Excluded

- Các tab chức năng trong workspace: table designer, data editor, dependency graph, object search, SQL visualizer, structured data, activity monitor, schema compare, user permissions, diff.
- Các cửa sổ phụ: Profiler, Query History, BRAVO, Activity Monitor, Schema Compare, Result Comparator, Google Sheets, Syntax Reference, Report Gen.
- Modal thuộc tính năng ngoài phạm vi: table-design apply preview, result comparator.
- Mục legacy (theo quyết định của bạn): `templates/partials/*`, Event Listener, listener Profiler không gắn phần tử.

Các nút mở tính năng ngoài phạm vi (ví dụ Snippets, Sheets, BRAVO, History, Object search) **vẫn được ghi lại** trong danh mục component của màn hình chứa nó, nhưng đánh dấu `deferred`.

## 3. Ghi chú quan sát / Observation notes

1. Dropdown của navbar (File/Edit/Format/View/Help) **không chụp được**: chúng do tiến trình `msedgewebview2.exe` vẽ, không cấp quyền được. Cấu trúc menu lấy từ code (`index.html:139–368`). Context menu của Explorer và menu overflow của action bar chụp bình thường.
2. Nguồn **không có status bar toàn cửa sổ**. Chỉ có status bar của editor (zoom, issues, PID, Ln/Col, TABS, LF, UTF-8) và footer của result (số dòng, thời gian, kích thước, session id). JavaFX hiện có status bar toàn cục kiểu VS Code, cần quyết định ánh xạ ở SCR-MAIN-005.
3. Khi tab chưa kết nối thì result panel ẩn đi và hiện banner vàng có nút "Kết nối" / "Đổi kết nối" (`main-disconnected-tab`).
4. Dialog kết nối tự điền sẵn thông tin từ node đang chọn (Copy). Mật khẩu chỉ được giữ trong RAM.
5. Settings là cửa sổ riêng. Có nút "Đóng" ngay trong trang, nhưng ở lần chụp, nút này không đóng được cửa sổ khi gọi từ instance thứ hai (`inferred`: có thể do chạy 2 instance cùng lúc). Cần kiểm lại ở bước 03.

## 4. Thứ tự đề xuất / Proposed order

| Bước | Màn hình | Lý do |
|---|---|---|
| 1 | SCR-SHELL-001 | Bạn ưu tiên title bar hành xử như native; mọi màn hình nằm trong khung này |
| 2 | SCR-MAIN-001, SCR-MAIN-005 | Bố cục tổng thể kiểu VS Code, splitter, ánh xạ status bar |
| 3 | SCR-MAIN-002 + DLG-001…006 | Explorer và các dialog kết nối/nhóm/lọc dùng chung luồng |
| 4 | SCR-MAIN-003 + DLG-007, 008, 010 | Editor và các dialog khi thực thi |
| 5 | SCR-MAIN-004 | Bảng kết quả |
| 6 | SCR-SET-001 + DLG-009 | Settings và quản lý code style |
| 7 | DLG-011…014 | Dialog phụ, rủi ro thấp |

## 5. Ảnh tham chiếu / Reference screenshots

`00-application/screenshots/source/` (12 ảnh): `main-full-1456x816`, `main-disconnected-tab-1456x816`, `main-sidebar-collapsed-rail`, `actionbar-overflow-menu`, `ctx-explorer-connection`, `ctx-explorer-group`, `dialog-new-connection`, `dialog-new-group`, `settings-editor`, `settings-grid`, `settings-appearance`, `settings-sql`.

Ở bước 03, ảnh sẽ được sao chép vào `01-screens/<screen-id>/screenshots/`.
