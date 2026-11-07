# Settings window — Screen Specification
# Đặc tả màn hình: Cửa sổ Cài đặt

- Screen ID: `SCR-SET-001`
- Source: `templates/settings_window.html`, `static/js/settings.js`, `app/desktop/windows.py:1178` (theo screen-inventory.json)
- Target: `fxml/settings-dialog.fxml`, `SettingsDialogController`, `ui/dialogs/SettingsDialog` (DialogChrome), `models/IdeSettings`, `services/SettingsService`
- Status: `implemented_mock`
- Evidence confidence: observed (nguồn) + code_verified (đích)
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Xem và sửa 47 thiết lập theo 10 nhóm (giao diện Visual) hoặc sửa trực tiếp `settings.json`.
- Entry point / Cách mở: Menu File ▸ Settings hoặc nút bánh răng (theo nguồn); đích: `SettingsDialog.show(owner)`.
- Parent screen / Màn hình cha: `SCR-SHELL-001`
- User roles or context / Ngữ cảnh: Cửa sổ modal (DialogChrome) mở từ cửa sổ chính; lưu vào `settings.json` trong thư mục cấu hình của ứng dụng.
- Related screens / Liên quan: SCR-SHELL-001 (menu), SCR-MAIN-003/004 (font, tab, grid), DLG-009 (code style), DLG-011 (xác nhận đóng khi có thay đổi chưa lưu)

## 2. Visual reference / Tham chiếu hình ảnh

Nguồn: `00-application/screenshots/source/settings-editor.jpg`, `settings-grid.jpg`, `settings-appearance.jpg`, `settings-sql.jpg` (9 nhóm theo inventory). Đích: chưa có ảnh trong `01-screens/SCR-SET-001/screenshots/`.

## 3. Layout / Bố cục

Cửa sổ `DialogChrome` 900×640 (min 760×480 của nội dung):

1. **Hàng tìm kiếm** (còn lại của title bar cũ): ô "Tìm kiếm cài đặt" + nút xóa.
2. **Thanh tab**: [Cài đặt (Visual)] [Cấu hình JSON (settings.json)] ‖ Copy JSON · Khôi phục mặc định.
3. **Vùng chính** (`StackPane`): *Visual* = `HBox` gồm sidebar 230 px (10 mục điều hướng) + `ScrollPane` các nhóm; *JSON* = hộp thông tin + badge "JSON Hợp lệ" + `TextArea`.
4. **Footer** của DialogChrome: bộ đếm "N thay đổi chưa lưu" và nút **Lưu** (Save chuyển từ thanh tab xuống footer).

Mỗi nhóm là một `VBox` (`sectionEditor`, `sectionGrid`, …) chỉ một nhóm hiện (`managed=false` với nhóm khác); mỗi dòng gồm nhãn, mô tả `khóa — giải thích` và một control.

## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-SET-001` | Settings window | Cửa sổ modal (DialogChrome) | center | width=900px, height=640px, min_width=760px, min_height=480px, resizable=True, flexible=True |
| `REG-SET-002` | Search row | Tìm thiết lập | top | width=fill, height=auto, resizable=False, flexible=True |
| `REG-SET-003` | Tabs + global actions | Chuyển Visual/JSON, Copy JSON, Khôi phục mặc định | top | width=fill, height=auto, resizable=False, flexible=True |
| `REG-SET-004` | Navigation sidebar | 10 nhóm thiết lập | left | width=230px, height=fill, min_width=200px, resizable=False, flexible=True |
| `REG-SET-005` | Settings sections | 10 nhóm dạng danh sách dòng | center | width=fill, height=fill, resizable=False, flexible=True |
| `REG-SET-006` | JSON editor area | Sửa trực tiếp settings.json | center | width=fill, height=fill, resizable=False, flexible=True |
| `REG-SET-007` | Footer | Bộ đếm thay đổi + nút Lưu | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-SET-001` | Search settings | text_field | unknown | unknown | `SettingsDialogController` | code_verified |
| `BTN-SET-002` | Clear search | icon_button | unknown | unknown | `handleClearSearch` | code_verified |
| `BTN-SET-003` | Tab: Cài đặt (Visual) | toggle_button | Cài đặt (Visual) | unknown | `handleSwitchToVisualTab` | code_verified |
| `BTN-SET-004` | Tab: Cấu hình JSON | toggle_button | Cấu hình JSON (settings.json) | unknown | `handleSwitchToJsonTab` | code_verified |
| `BTN-SET-005` | Save | button | Lưu | Ctrl+S | `handleSaveAndApply` | code_verified |
| `CMP-SET-006` | Unsaved counter | label | N thay đổi chưa lưu | unknown | `SettingsDialogController` | code_verified |
| `BTN-SET-007` | Copy JSON | button | Copy JSON | unknown | `handleCopyJson` | code_verified |
| `BTN-SET-008` | Restore defaults | button | Khôi phục mặc định | unknown | `handleRestoreDefaults` | code_verified |
| `BTN-SET-009` | Window close × | icon_button | unknown | Esc | `handleClose / canClose` | code_verified |
| `BTN-SET-010` | Nav: Editor | button | Editor (Trình soạn thảo SQL) | unknown | `handleNavEditor` | code_verified |
| `BTN-SET-011` | Nav: Grid | button | Grid (Bảng kết quả) | unknown | `handleNavGrid` | code_verified |
| `BTN-SET-012` | Nav: Messages | button | Messages (Tab thông báo) | unknown | `handleNavMessages` | code_verified |
| `BTN-SET-013` | Nav: Appearance | button | Appearance (Giao diện & Chủ đề) | unknown | `handleNavAppearance` | code_verified |
| `BTN-SET-014` | Nav: SQL | button | SQL (Truy vấn SQL) | unknown | `handleNavSql` | code_verified |
| `BTN-SET-015` | Nav: Session | button | Session (Phiên làm việc) | unknown | `handleNavSession` | code_verified |
| `BTN-SET-016` | Nav: Addons | button | Addons (Tiện ích mở rộng) | unknown | `handleNavAddons` | code_verified |
| `BTN-SET-017` | Nav: History | button | History (Lịch sử truy vấn) | unknown | `handleNavHistory` | code_verified |
| `BTN-SET-018` | Nav: Logging | button | Logging (Nhật ký hệ thống) | unknown | `handleNavLogging` | code_verified |
| `BTN-SET-019` | Nav: Updates | button | Updates (Thông tin & Cập nhật) | unknown | `handleNavUpdates` | code_verified |
| `CMP-SET-020` | Phông chữ soạn thảo | setting_row | Phông chữ soạn thảo | unknown | `key: editor.fontFamily` | code_verified |
| `CMP-SET-021` | Cỡ chữ soạn thảo | setting_row | Cỡ chữ soạn thảo | unknown | `key: editor.fontSize` | code_verified |
| `CMP-SET-022` | Chiều cao dòng | setting_row | Chiều cao dòng | unknown | `key: editor.lineHeight` | code_verified |
| `CMP-SET-023` | Khoảng cách ký tự | setting_row | Khoảng cách ký tự | unknown | `key: editor.letterSpacing` | code_verified |
| `CMP-SET-024` | Kích thước thụt dòng (Tab Size) | setting_row | Kích thước thụt dòng (Tab Size) | unknown | `key: editor.tabSize` | code_verified |
| `CMP-SET-025` | Chèn khoảng trắng thay vì Tab | setting_row | Chèn khoảng trắng thay vì Tab | unknown | `key: editor.insertSpaces` | code_verified |
| `CMP-SET-026` | Tự động xuống dòng | setting_row | Tự động xuống dòng | unknown | `key: editor.wordWrap` | code_verified |
| `CMP-SET-027` | Bản đồ mã nguồn thu nhỏ (Minimap) | setting_row | Bản đồ mã nguồn thu nhỏ (Minimap) | unknown | `key: editor.minimap` | code_verified |
| `CMP-SET-028` | Định dạng từ khóa SQL | setting_row | Định dạng từ khóa SQL | unknown | `key: editor.keywordCase` | code_verified |
| `CMP-SET-029` | Kiểm tra cú pháp SQL | setting_row | Kiểm tra cú pháp SQL | unknown | `key: editor.syntaxCheck` | code_verified |
| `CMP-SET-030` | Gợi ý mã thông minh (IntelliSense) | setting_row | Gợi ý mã thông minh (IntelliSense) | unknown | `key: editor.intellisense` | code_verified |
| `CMP-SET-031` | Ưu tiên gợi ý theo lịch sử dùng | setting_row | Ưu tiên gợi ý theo lịch sử dùng | unknown | `key: editor.intellisenseMru` | code_verified |
| `CMP-SET-032` | Phông chữ bảng kết quả | setting_row | Phông chữ bảng kết quả | unknown | `key: grid.fontFamily` | code_verified |
| `CMP-SET-033` | Cỡ chữ bảng kết quả | setting_row | Cỡ chữ bảng kết quả | unknown | `key: grid.fontSize` | code_verified |
| `CMP-SET-034` | Cách hiển thị nhiều bảng kết quả | setting_row | Cách hiển thị nhiều bảng kết quả | unknown | `key: grid.resultLayout` | code_verified |
| `CMP-SET-035` | Hiển thị cột số thứ tự dòng | setting_row | Hiển thị cột số thứ tự dòng | unknown | `key: grid.showRowNumbers` | code_verified |
| `CMP-SET-036` | Chuỗi hiển thị giá trị NULL | setting_row | Chuỗi hiển thị giá trị NULL | unknown | `key: grid.nullDisplayString` | code_verified |
| `CMP-SET-037` | Phông chữ tab thông báo | setting_row | Phông chữ tab thông báo | unknown | `key: messages.fontFamily` | code_verified |
| `CMP-SET-038` | Cỡ chữ tab thông báo | setting_row | Cỡ chữ tab thông báo | unknown | `key: messages.fontSize` | code_verified |
| `CMP-SET-039` | Chế độ giao diện (Color Theme) | setting_row | Chế độ giao diện (Color Theme) | unknown | `key: theme` | code_verified |
| `CMP-SET-040` | Kích thước biểu tượng | setting_row | Kích thước biểu tượng | unknown | `key: appearance.iconSize` | code_verified |
| `CMP-SET-041` | Phông chữ giao diện IDE | setting_row | Phông chữ giao diện IDE | unknown | `key: appearance.uiFontFamily` | code_verified |
| `CMP-SET-042` | Cỡ chữ giao diện IDE | setting_row | Cỡ chữ giao diện IDE | unknown | `key: appearance.uiFontSize` | code_verified |
| `CMP-SET-043` | Tự động ẩn thanh Menu / Navbar | setting_row | Tự động ẩn thanh Menu / Navbar | unknown | `key: appearance.autoHideNavbar` | code_verified |
| `CMP-SET-044` | Kiểu khung giao diện (Panel Style) | setting_row | Kiểu khung giao diện (Panel Style) | unknown | `key: appearance.panelStyle` | code_verified |
| `CMP-SET-045` | Giới hạn số dòng kết quả (Max Rows) | setting_row | Giới hạn số dòng kết quả (Max Rows) | unknown | `key: sql.maxRows` | code_verified |
| `CMP-SET-046` | Thời gian chờ truy vấn | setting_row | Thời gian chờ truy vấn | unknown | `key: sql.timeoutSeconds` | code_verified |
| `CMP-SET-047` | Bảo vệ câu lệnh SQL nguy hiểm | setting_row | Bảo vệ câu lệnh SQL nguy hiểm | unknown | `key: sql.sqlprotection` | code_verified |
| `CMP-SET-048` | Dừng khi gặp lỗi | setting_row | Dừng khi gặp lỗi | unknown | `key: sql.stopOnError` | code_verified |
| `CMP-SET-049` | Mỗi tab một phiên kết nối riêng | setting_row | Mỗi tab một phiên kết nối riêng | unknown | `key: sql.perTabSession` | code_verified |
| `CMP-SET-050` | Tự động mở bảng kết quả | setting_row | Tự động mở bảng kết quả | unknown | `key: unknown (không có nhãn khóa trong FXML)` | code_verified |
| `CMP-SET-051` | Khôi phục tab khi mở lại IDE | setting_row | Khôi phục tab khi mở lại IDE | unknown | `key: session.restoreOnStartup` | code_verified |
| `CMP-SET-052` | Tự động lưu nội dung SQL chưa đặt tên | setting_row | Tự động lưu nội dung SQL chưa đặt tên | unknown | `key: session.saveSqlContent` | code_verified |
| `CMP-SET-053` | Chu kỳ tự động lưu (giây) | setting_row | Chu kỳ tự động lưu (giây) | unknown | `key: session.autoSaveIntervalSeconds` | code_verified |
| `CMP-SET-054` | Dung lượng tối đa mỗi tab (bytes) | setting_row | Dung lượng tối đa mỗi tab (bytes) | unknown | `key: session.maxTabSizeBytes` | code_verified |
| `CMP-SET-055` | Kích hoạt BRAVO Tool Extension | setting_row | Kích hoạt BRAVO Tool Extension | unknown | `key: addons.bravo_tool.enabled` | code_verified |
| `CMP-SET-056` | Ghi nhật ký lịch sử truy vấn | setting_row | Ghi nhật ký lịch sử truy vấn | unknown | `key: history.enabled` | code_verified |
| `CMP-SET-057` | Số bản ghi tối đa mỗi tài liệu | setting_row | Số bản ghi tối đa mỗi tài liệu | unknown | `key: history.maxEntriesPerDoc` | code_verified |
| `CMP-SET-058` | Thời gian lưu trữ tối đa (ngày) | setting_row | Thời gian lưu trữ tối đa (ngày) | unknown | `key: history.maxAgeDays` | code_verified |
| `CMP-SET-059` | Dung lượng tối đa DB lịch sử (MB) | setting_row | Dung lượng tối đa DB lịch sử (MB) | unknown | `key: history.maxDbSizeMb` | code_verified |
| `CMP-SET-060` | Kích thước tối đa 1 câu truy vấn (KB) | setting_row | Kích thước tối đa 1 câu truy vấn (KB) | unknown | `key: history.maxEntrySizeKb` | code_verified |
| `CMP-SET-061` | Ghi nhật ký ra file | setting_row | Ghi nhật ký ra file | unknown | `key: logging.enabled` | code_verified |
| `CMP-SET-062` | Mức độ chi tiết nhật ký | setting_row | Mức độ chi tiết nhật ký | unknown | `key: logging.level` | code_verified |
| `CMP-SET-063` | Dung lượng tối đa mỗi file log (MB) | setting_row | Dung lượng tối đa mỗi file log (MB) | unknown | `key: logging.maxFileSizeMb` | code_verified |
| `CMP-SET-064` | Số file log xoay vòng tối đa | setting_row | Số file log xoay vòng tối đa | unknown | `key: logging.maxFiles` | code_verified |
| `CMP-SET-065` | Tự động kiểm tra cập nhật khi khởi động | setting_row | Tự động kiểm tra cập nhật khi khởi động | unknown | `key: updates.checkOnStartup` | code_verified |
| `CMP-SET-066` | URL máy chủ cập nhật tùy chỉnh | setting_row | URL máy chủ cập nhật tùy chỉnh | unknown | `key: updates.updateUrl` | code_verified |
| `CMP-SET-067` | Installed version | label | 1.2.6 | unknown | `unknown` | code_verified |
| `CMP-SET-068` | Raw JSON editor | text_area | unknown | Ctrl+S (lưu) | `SettingsDialogController` | code_verified |
| `CMP-SET-069` | JSON status badge | label | JSON Hợp lệ | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-SET-001` | `CMP-SET-001` | key | Lọc/làm nổi dòng khớp (cách lọc chưa kiểm chi tiết) | no |
| `INT-SET-002` | `BTN-SET-002` | click | Ô tìm trống, hiện lại tất cả dòng | no |
| `INT-SET-003` | `BTN-SET-004` | click | Hiện txtRawJson với nội dung settings hiện tại; kiểm tra hợp lệ | no |
| `INT-SET-004` | `BTN-SET-003` | click | Hiện sidebar + nhóm | no |
| `INT-SET-005` | `BTN-SET-010` | click | Hiện nhóm Editor, ẩn nhóm khác | no |
| `INT-SET-006` | `BTN-SET-005` | click | Ghi settings.json, áp theme/font/layout ngay; xóa bộ đếm thay đổi | no |
| `INT-SET-007` | `BTN-SET-007` | click | Clipboard chứa settings hiện tại | no |
| `INT-SET-008` | `BTN-SET-008` | click | Đặt lại các control về mặc định (chưa kiểm có xác nhận/ghi ngay) | unknown |
| `INT-SET-009` | `BTN-SET-009` | click | Không có thay đổi: đóng. Có thay đổi chưa lưu: ConfirmDialog "Đóng cài đặt" (Đóng không lưu) | có, khi có thay đổi chưa lưu |
| `INT-SET-010` | `CMP-SET-068` | key | Badge đổi giữa "JSON Hợp lệ" và lỗi | no |
| `INT-SET-011` | `CMP-SET-068` | key | Giống nút Lưu (theo mô tả trong FXML) | no |
| `INT-SET-012` | `CMP-SET-044` | selection_changed | Áp dụng khi Lưu (R-UI-01: shape-flat.css) | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-SET-001` | selected | Nút có styleClass active | chế độ Visual |
| `STA-SET-002` | selected | Nút active; hộp JSON hiện | chế độ JSON |
| `STA-SET-003` | selected | Mục điều hướng active | đang ở nhóm đó |
| `STA-SET-004` | normal | "N thay đổi chưa lưu" (rỗng nếu 0) | có control đổi giá trị |
| `STA-SET-005` | normal | Nút ✕ hiện khi có chữ | ô tìm không rỗng |
| `STA-SET-006` | normal | "JSON Hợp lệ" (settings-json-badge-valid) | JSON parse được |
| `STA-SET-007` | error | Badge lỗi (tên/chữ chính xác chưa kiểm) | JSON không hợp lệ |
| `STA-SET-008` | disabled | Chưa xác định có tắt khi không có thay đổi | unknown |

## 8. JavaFX design proposal / Đề xuất JavaFX

- Proposed layout: Giữ như đã dựng: tìm kiếm trên cùng, tab Visual/JSON, sidebar 10 nhóm, footer Lưu.
- Standard JavaFX controls: ComboBox, Spinner, CheckBox, TextField, TextArea, ScrollPane.
- Custom components: `SettingsRowStyler` (kiểu dòng giống VS Code, theo dõi thay đổi chưa lưu).
- Retained embedded components: Không có.
- Design improvements: Chỉ nhận xét: nhóm Addons chỉ có 1 dòng; chuỗi "Phiên bản 1.2.6" cố định.
- Compatibility/performance considerations: Không đáng kể.

## 9. Known gaps and questions / Điểm chưa rõ

| # | Điểm chưa rõ | Ảnh hưởng | Chặn triển khai? |
|---|---|---|---|
| 1 | Số nhóm đích (10) khác inventory (9) | Không rõ nhóm nào được thêm | Không |
| 2 | Xem trước phông chữ trực tiếp của nguồn không thấy ở đích | Có thể mất tính năng | Không |
| 3 | Ảnh đích chưa có | Chưa so sánh nguồn/đích | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
