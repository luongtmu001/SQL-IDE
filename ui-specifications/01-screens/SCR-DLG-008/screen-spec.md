# Format confirmation — Screen Specification
# Đặc tả màn hình: Xác nhận định dạng code

- Screen ID: `SCR-DLG-008`
- Source: `ide-modal-format-confirm` (theo screen-inventory.json)
- Target: ui/dialogs/FormatConfirmDialog.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Xác nhận định dạng lại toàn bộ SQL của tab; có "Không hỏi lại".
- Entry point / Cách mở: Format SQL trong tab (EditorController:600).
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-003, DLG-009

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-008/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Format toàn bộ tài liệu**, icon `fth-align-left`), cỡ 400×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG08-001` | Dialog window | Cửa sổ Format confirmation (DialogChrome) | center | width=400px, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG08-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG08-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG08-001` | Message | label | Toàn bộ SQL trong tab sẽ được định dạng lại. Có thể hoàn tác bằng Ctrl+Z. | unknown | `unknown` | code_verified |
| `CMP-DLG08-002` | Do not ask again | checkbox | Không hỏi lại | unknown | `unknown` | code_verified |
| `BTN-DLG08-003` | Cancel | button | Hủy | unknown | `unknown` | code_verified |
| `BTN-DLG08-004` | Format | button | Format | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG08-001` | `BTN-DLG08-004` | click | Trả true; nếu tích "Không hỏi lại" thì lưu cờ | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG08-001` | normal | Chưa chọn | mặc định |

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
| 1 | Chưa so nguồn; không có chỗ bật lại hỏi (chưa xác nhận) | Thấp | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
