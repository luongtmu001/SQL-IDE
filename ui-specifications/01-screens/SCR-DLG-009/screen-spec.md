# SQL code style manager — Screen Specification
# Đặc tả màn hình: Quản lý định dạng mã SQL

- Screen ID: `SCR-DLG-009`
- Source: `ide-modal-code-style` (theo screen-inventory.json)
- Target: ui/dialogs/CodeStyleDialog.java + ui/codestyle/{SqlStyleStore,SqlStyleMetadata,SimpleSqlFormatter}
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Tạo/sửa/nhân bản/xóa/đổi tên/đặt mặc định các style định dạng SQL, xem trước bằng Monaco, nhập/xuất file `.sqlstyle.json`.
- Entry point / Cách mở: Menu Format ▸ SQL Style Manager (MainController:842); từ Settings ▸ Editor.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-003, SCR-SET-001, DLG-008

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-009/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Quản lý style**, icon `unknown`), cỡ -1×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG09-001` | Dialog window | Cửa sổ SQL code style manager (DialogChrome) | center | width=auto, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG09-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG09-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `BTN-DLG09-001` | New style | button | Style mới | unknown | `addStyle` | code_verified |
| `BTN-DLG09-002` | Duplicate | button | Nhân bản | unknown | `addStyle(editing())` | code_verified |
| `BTN-DLG09-003` | Delete | icon_button | Xóa style | unknown | `unknown` | code_verified |
| `BTN-DLG09-004` | Rename | button | Đổi tên | unknown | `unknown` | code_verified |
| `BTN-DLG09-005` | Set default | button | Đặt mặc định | unknown | `unknown` | code_verified |
| `CMP-DLG09-006` | Style list | combobox | Style | unknown | `unknown` | code_verified |
| `CMP-DLG09-007` | Read-only hint | label | Style hệ thống chỉ đọc. Bấm Nhân bản để tạo style của bạn rồi chỉnh. | unknown | `unknown` | code_verified |
| `CMP-DLG09-008` | Search options | text_field | Tìm tùy chọn… | unknown | `unknown` | code_verified |
| `BTN-DLG09-009` | Dialect: SQL Server | toggle_button | SQL Server | unknown | `unknown` | code_verified |
| `BTN-DLG09-010` | Dialect: PostgreSQL | toggle_button | PostgreSQL | unknown | `unknown` | code_verified |
| `CMP-DLG09-011` | Option rows | setting_row | (boolean / number / …) | unknown | `unknown` | code_verified |
| `CMP-DLG09-012` | Preview | embedded_editor | Xem trước | unknown | `unknown` | code_verified |
| `CMP-DLG09-013` | Use current SQL | checkbox | Xem trước bằng câu lệnh đang mở | unknown | `unknown` | code_verified |
| `BTN-DLG09-014` | Format | button | Format | unknown | `unknown` | code_verified |
| `BTN-DLG09-015` | Sample SQL | button | SQL mẫu | unknown | `unknown` | code_verified |
| `BTN-DLG09-016` | Import / Export | button | Nhập / Xuất | unknown | `unknown` | code_verified |
| `CMP-DLG09-017` | Status | label | Có thay đổi chưa lưu / Đã lưu | unknown | `unknown` | code_verified |
| `BTN-DLG09-018` | Save | button | Lưu | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG09-001` | `BTN-DLG09-018` | click | Ghi SqlStyleStore | no |
| `INT-DLG09-002` | `BTN-DLG09-003` | click | Xóa sau ConfirmDialog; tab dùng style này chuyển về style hệ thống | có (ConfirmDialog) |
| `INT-DLG09-003` | `BTN-DLG09-014` | click | SQL trong khung được định dạng theo style đang sửa | no |
| `INT-DLG09-004` | `BTN-DLG09-016` | click | Xuất `.sqlstyle.json`; nhập kiểm tra tính hợp lệ | hộp chọn file |
| `INT-DLG09-005` | `CMP-DLG09-006` | selection_changed | Style hệ thống chỉ đọc (hiện gợi ý); style riêng sửa được | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG09-001` | normal | "Đã lưu" | không có thay đổi |
| `STA-DLG09-002` | error | "Có thay đổi chưa lưu" | có sửa |
| `STA-DLG09-003` | disabled | Style hệ thống: các control sửa bị tắt, hiện gợi ý | chọn style hệ thống |

## 8. JavaFX design proposal / Đề xuất JavaFX

- Proposed layout: Giữ như đã dựng (DialogChrome: tiêu đề + nội dung + footer).
- Standard JavaFX controls: Label, TextField, ComboBox, CheckBox, ListView, Button theo bảng component.
- Custom components: `DialogChrome` (khung cửa sổ, R-UI-01).
- Retained embedded components: Không có.
- Design improvements: Chưa đề xuất thay đổi.
- Compatibility/performance considerations: Không đáng kể.

## 9. Known gaps and questions / Điểm chưa rõ

| # | Điểm chưa rõ | Ảnh hưởng | Chặn triển khai? |
|---|---|---|---|
| 1 | Chưa so nguồn | Chưa đối chiếu | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
