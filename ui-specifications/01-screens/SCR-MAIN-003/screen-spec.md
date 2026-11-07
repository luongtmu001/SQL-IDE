# Editor workspace — Screen Specification
# Đặc tả màn hình: Vùng soạn thảo

- Screen ID: `SCR-MAIN-003`
- Source: `templates/index.html#ide-editor-pane`, `static/js/editor.js`, `tabs.js`, `monaco-init.js`, `syntax-catalog.js` (theo screen-inventory.json)
- Target: `fxml/editor.fxml`, `EditorController`, `ui/editor/{SqlEditorTab,EditorTabStrip,MonacoEditorHost,EmbeddedMonaco,SqlCodeArea}`; thanh ngữ cảnh nằm trong `main-window.fxml` (`MainController`)
- Status: `implemented_mock`
- Evidence confidence: observed (nguồn + thanh ngữ cảnh đích) + code_verified (đích)
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Soạn thảo SQL theo tab (Monaco), chọn kết nối/database/schema, chạy/dừng câu lệnh, chia đôi editor.
- Entry point / Cách mở: Luôn hiển thị trong workspace chính. Tab mới: nút +, menu ⋯ (New), nút "New Query Tab" ở trạng thái rỗng.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Người dùng đang làm việc với một hoặc nhiều tab SQL; mỗi tab có thể trỏ tới một kết nối và database riêng (phiên theo tab).
- Related screens / Liên quan: SCR-SHELL-001 (title bar), SCR-MAIN-002 (Explorer: chọn kết nối), SCR-MAIN-004 (kết quả), SCR-MAIN-005 (status bar), DLG-007/008/010 (khi Run/Format), DLG-009 (code style)

## 2. Visual reference / Tham chiếu hình ảnh

Nguồn: `00-application/screenshots/source/main-full-1456x816.jpg`, `actionbar-overflow-menu.jpg`, `main-disconnected-tab-1456x816.jpg` (theme Ambiance, 1456×816). Đích: `01-screens/SCR-MAIN-003/screenshots/{approved-mockup.png, target-context-bar.jpg, target-context-overflow.jpg, target-titlebar-no-badge.jpg}`. Chưa có ảnh đích của dải tab, banner chưa kết nối, trạng thái chia đôi.

## 3. Layout / Bố cục

Từ trên xuống trong vùng editor:

1. **Thanh ngữ cảnh** (đã có `toolbar-redesign.md`, được duyệt 2026-11-04): `[● kết nối ▾ ENV] › [database ▾] › [schema ▾] ══ Giới hạn [All ▾] [▶ Run] [■ Stop] | plan format refresh ⋯`.
2. **Dải tab** (`EditorTabStrip`): một hàng cuộn ngang, mờ dần bên phải, nút + và nút danh sách tab; chế độ "hiện tất cả" xếp nhiều hàng (`FlowPane`, lưu bằng `Preferences`). Cụm nút phải (Format, Split phải, Split xuống, Đóng split) nằm cùng hàng với tab qua `StackPane` căn TOP_RIGHT, cao 34 px.
3. **Banner "Chưa kết nối"** (mỗi tab, chỉ hiện khi tab trỏ tới kết nối chưa kết nối và chưa bị ẩn).
4. **Bề mặt editor**: Monaco dùng chung trong `WebView` (hoặc `SqlCodeArea` RichTextFX làm dự phòng).

Chia đôi: `SplitPane` ngang chứa `primaryTabPane` và `secondaryTabPane`; có API chia sang phải/trái/xuống/lên (`enableSplit`). Khi không có tab: hộp trạng thái rỗng thay thế.

## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-ED-001` | Editor workspace | Khung chứa toàn bộ vùng soạn thảo | center | width=fill, height=fill, resizable=False, flexible=True |
| `REG-ED-002` | Context bar | Chọn kết nối/database/schema, giới hạn dòng, Run/Stop, tiện ích | top | width=fill, height=toolbar, resizable=False, flexible=True |
| `REG-ED-003` | Tab strip + header actions | Dải tab, nút +, danh sách tab, Format/Split | top | width=fill, height=34px, resizable=False, flexible=True |
| `REG-ED-004` | Connection banner | Báo tab trỏ tới kết nối chưa kết nối | top | width=fill, height=auto, resizable=False, flexible=True |
| `REG-ED-005` | Editor surface | Monaco (WebView) hoặc SqlCodeArea | center | width=fill, height=fill, resizable=True, flexible=True |
| `REG-ED-006` | Empty state | Hiện khi không còn tab nào | center | width=fill, height=fill, resizable=False, flexible=True |
| `REG-ED-007` | Secondary pane (split) | TabPane thứ hai khi chia đôi | right | width=flexible, height=fill, resizable=True, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-CTX-001` | Connection selector | combobox | Chọn kết nối... | unknown | `MainController` | observed |
| `CMP-CTX-002` | Database selector | combobox | Database | unknown | `MainController` | observed |
| `CMP-CTX-003` | Schema selector | combobox | schema | unknown | `MainController` | observed |
| `CMP-CTX-004` | Row limit | combobox | All | unknown | `MainController` | observed |
| `BTN-CTX-001` | Run | button | Run | F5 / Ctrl+Enter | `MainController#handleExecute` | observed |
| `BTN-CTX-002` | Stop | button | Stop | unknown | `MainController#handleStop` | observed |
| `BTN-CTX-003` | Plan | icon_button | unknown | Ctrl+L | `unknown` | observed |
| `BTN-CTX-004` | Format (toolbar) | icon_button | unknown | Ctrl+Alt+L | `unknown` | observed |
| `BTN-CTX-005` | Refresh objects | icon_button | unknown | Ctrl+Shift+R | `handleRefreshObjects` | observed |
| `BTN-CTX-006` | Overflow menu | menu_button | ⋯ | unknown | `MainController` | observed |
| `BTN-ED-001` | New Query Tab (empty state) | button | New Query Tab | unknown | `EditorController#handleNewTab` | code_verified |
| `BTN-ED-002` | Format SQL (tab header) | icon_button | unknown | Ctrl+Alt+L | `EditorController#handleFormatSql` | code_verified |
| `BTN-ED-003` | Split Right | icon_button | unknown | unknown | `EditorController#handleSplitRight` | code_verified |
| `BTN-ED-004` | Split Down | icon_button | unknown | unknown | `EditorController#handleSplitDown` | code_verified |
| `BTN-ED-005` | Close Split | icon_button | unknown | unknown | `EditorController#closeSplit` | code_verified |
| `CMP-ED-006` | Tab chip | tab | tiêu đề tab | unknown | `EditorTabStrip` | code_verified |
| `BTN-ED-007` | Add tab | icon_button | unknown | unknown | `onNewTab` | code_verified |
| `BTN-ED-008` | Tab list | icon_button | unknown | unknown | `EditorTabStrip#showTabList` | code_verified |
| `CMP-ED-009` | Tab context menu | menu | unknown | unknown | `EditorController#handleTabAction` | code_verified |
| `CMP-ED-010` | Connection banner | banner | Chưa kết nối: tab này trỏ tới <kết nối (database)> nhưng chưa được kết nối. | unknown | `SqlEditorTab#updateConnectionBanner` | code_verified |
| `BTN-ED-011` | Banner: Kết nối | button | Kết nối | unknown | `BannerActions#connect` | code_verified |
| `BTN-ED-012` | Banner: Đổi kết nối | button | Đổi kết nối | unknown | `BannerActions#changeConnection` | code_verified |
| `BTN-ED-013` | Banner: Ẩn | icon_button | unknown | unknown | `unknown` | code_verified |
| `CMP-ED-014` | Monaco editor | embedded_editor | unknown | Ctrl+Enter → onExecuteRequested | `MonacoEditorHost (bridge JS)` | code_verified |
| `CMP-ED-015` | Fallback code area | text_editor | unknown | unknown | `unknown` | code_verified |
| `CMP-ED-016` | Secondary tab pane | tab_pane | unknown | unknown | `EditorController#enableSplit` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-ED-001` | `BTN-ED-001` | click | Một tab trống được mở và chọn | no |
| `INT-ED-002` | `CMP-ED-006` | click | Editor chuyển model; status bar cập nhật Ln/Col | no |
| `INT-ED-003` | `CMP-ED-006` | click | Nếu tab chưa lưu thì mở CloseTabDialog (Lưu và đóng / Đóng không lưu / Hủy) | có, khi chưa lưu (CloseTabDialog) |
| `INT-ED-004` | `CMP-ED-009` | right_click | Menu 14 mục; "Đóng" bị tắt khi tab đã ghim | no |
| `INT-ED-005` | `CMP-ED-009` | click | Thực hiện theo loại hành động | no |
| `INT-ED-006` | `CMP-ED-009` | click | Hộp nhập tên; đổi tiêu đề khi đồng ý | no |
| `INT-ED-007` | `BTN-ED-002` | click | Hỏi FormatConfirmDialog (trừ khi đã chọn "Không hỏi lại") rồi gọi Monaco formatDocument | có (DLG-008) |
| `INT-ED-008` | `BTN-ED-003` | click | Hiện secondaryTabPane và nút Đóng split | no |
| `INT-ED-009` | `BTN-ED-004` | click | Chia theo chiều dọc | no |
| `INT-ED-010` | `BTN-ED-005` | click | Chỉ còn primaryTabPane | no |
| `INT-ED-011` | `BTN-ED-011` | click | Mở luồng kết nối (có thể hỏi mật khẩu qua DLG-002) | có thể hỏi mật khẩu |
| `INT-ED-012` | `BTN-ED-012` | click | Mở bộ chọn kết nối | no |
| `INT-ED-013` | `BTN-ED-013` | click | Banner biến mất đến khi kết nối thay đổi | no |
| `INT-ED-014` | `CMP-ED-014` | key | Gọi listener chạy câu lệnh (cùng đường với nút Run) | tùy guard (DLG-007/010 + cảnh báo PROD) |
| `INT-ED-015` | `CMP-ED-014` | selection_changed | Status bar cập nhật Ln/Col (SCR-MAIN-005) | no |
| `INT-ED-016` | `CMP-ED-014` | key | Đánh dấu tab dirty (●) | no |
| `INT-ED-017` | `BTN-ED-008` | click | Chọn một tab để cuộn tới và kích hoạt | no |
| `INT-ED-018` | `BTN-CTX-001` | click | Luồng: marker debug → DLG-010; môi trường PROD → ProductionSafetyDialog; câu nguy hiểm → DLG-007; rồi chạy | có (các guard) |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-ED-001` | empty | Biểu tượng file, "No Editor Tabs Open", gợi ý mở script hoặc tab mới, nút New Query Tab | số tab = 0 |
| `STA-ED-002` | normal | Tiêu đề + chấm màu kết nối + icon loại DB | tab đã lưu, không ghim |
| `STA-ED-003` | selected | Pseudo-class selected trong EditorTabStrip | tab đang được chọn |
| `STA-ED-004` | normal | Dấu ● thay cho nút đóng khi tab chưa lưu (dirtyIndicator) | dirty=true |
| `STA-ED-005` | normal | Tab ghim: có ghim, menu "Đóng" bị tắt | pinned=true |
| `STA-ED-006` | normal | Banner vàng với 3 nút | connected=false, chưa ẩn |
| `STA-ED-007` | collapsed | Ẩn (managed=false) | connected=true/null hoặc đã ẩn |
| `STA-ED-008` | normal | Có secondaryTabPane và nút Đóng split | chia đôi |
| `STA-ED-009` | loading | Thao tác được xếp hàng đợi trong readyCallbacks cho đến onEditorReady | WebView chưa tải xong |
| `STA-ED-010` | disabled | Nút Stop mờ | không có câu đang chạy |
| `STA-ED-011` | loading | Trạng thái đang chạy (Stop bật) | đang thực thi |

## 8. JavaFX design proposal / Đề xuất JavaFX

- Proposed layout: Giữ như đã dựng: thanh ngữ cảnh → dải tab → banner → editor.
- Standard JavaFX controls: ComboBox, Button, MenuButton, TabPane, SplitPane, ContextMenu.
- Custom components: `EditorTabStrip` (dải tab cuộn/nhiều hàng), `SqlEditorTab` (chip tab).
- Retained embedded components: Monaco Editor trong WebView (quyết định phạm vi 2026-11-04).
- Design improvements: Chỉ nhận xét, chưa đề xuất thay đổi: nhãn "New Query Tab"/"No Editor Tabs Open" còn tiếng Anh trong khi phần còn lại tiếng Việt.
- Compatibility/performance considerations: Một Monaco dùng chung cho mọi tab (model theo tab) để tránh nhiều WebView.

## 9. Known gaps and questions / Điểm chưa rõ

| # | Điểm chưa rõ | Ảnh hưởng | Chặn triển khai? |
|---|---|---|---|
| 1 | Ảnh đích cho dải tab, banner, chia đôi chưa có | Chưa so sánh nguồn/đích được | Không |
| 2 | Nhãn trạng thái rỗng bằng tiếng Anh | Thấp, nhất quán ngôn ngữ | Không |
| 3 | Hành vi diff/Debug/Transaction bị hoãn | Mất chức năng so với nguồn nếu người dùng cần | Không (đã duyệt hoãn) |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
