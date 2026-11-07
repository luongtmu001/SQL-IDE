# Group delete confirmation — Screen Specification
# Đặc tả màn hình: Xác nhận xóa nhóm

- Screen ID: `SCR-DLG-004`
- Source: `groupConfirmModal` (theo screen-inventory.json)
- Target: ui/dialogs/ConfirmDialog.java (dùng chung)
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Xác nhận trước khi xóa nhóm trong Explorer.
- Entry point / Cách mở: Menu ngữ cảnh Delete Group.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-002, DLG-003

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-004/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Xóa nhóm**, icon `fth-alert-triangle`), cỡ 420×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG04-001` | Dialog window | Cửa sổ Group delete confirmation (DialogChrome) | center | width=420px, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG04-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG04-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG04-001` | Message | label | <câu hỏi xóa nhóm> | unknown | `unknown` | code_verified |
| `CMP-DLG04-002` | Detail | label | <chi tiết> | unknown | `unknown` | code_verified |
| `BTN-DLG04-003` | Cancel | button | Hủy | unknown | `unknown` | code_verified |
| `BTN-DLG04-004` | Delete | button | <actionText> | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG04-001` | `BTN-DLG04-004` | click | Trả true; người gọi xóa nhóm | đây là bước xác nhận |
| `INT-DLG04-002` | `BTN-DLG04-003` | click | Trả false | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG04-001` | normal | Nút đỏ (danger) | destructive=true |

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
