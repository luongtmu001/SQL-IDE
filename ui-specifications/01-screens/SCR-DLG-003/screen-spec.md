# Group create/rename — Screen Specification
# Đặc tả màn hình: Tạo/đổi tên nhóm

- Screen ID: `SCR-DLG-003`
- Source: `groupInputModal` (theo screen-inventory.json)
- Target: ui/dialogs/GroupNameDialog.java
- Status: `implemented_mock`
- Evidence confidence: observed (nguồn) + code_verified (đích)
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Tạo nhóm mới hoặc đổi tên nhóm trong Explorer; hỗ trợ phân cấp (BRAVO-SERVER ▸ POSTGRE).
- Entry point / Cách mở: Explorer toolbar New group; menu ngữ cảnh New/Rename Group.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-002, DLG-004

## 2. Visual reference / Tham chiếu hình ảnh

Nguồn: `00-application/screenshots/source/dialog-new-group.jpg`. Đích: chưa có ảnh.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Nhóm mới / Đổi tên nhóm**, icon `fth-folder-plus / fth-edit-2`), cỡ 380×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG03-001` | Dialog window | Cửa sổ Group create/rename (DialogChrome) | center | width=380px, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG03-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG03-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG03-001` | Parent path | label | Trong: (gốc) / Trong: <path> | unknown | `unknown` | code_verified |
| `CMP-DLG03-002` | Group name | text_field | Tên nhóm | unknown | `unknown` | code_verified |
| `CMP-DLG03-003` | Error | label | <thông báo lỗi> | unknown | `unknown` | code_verified |
| `CMP-DLG03-004` | Hint | label | Dùng nhóm con để phân cấp, ví dụ BRAVO-SERVER ▸ POSTGRE. | unknown | `unknown` | code_verified |
| `BTN-DLG03-005` | Cancel | button | Hủy | unknown | `unknown` | code_verified |
| `BTN-DLG03-006` | OK | button | Tạo nhóm / Đổi tên | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG03-001` | `BTN-DLG03-006` | click | Gọi apply(name); nếu lỗi hiện thông báo và giữ hộp thoại, nếu OK đóng | no |
| `INT-DLG03-002` | `CMP-DLG03-002` | key | Xóa trạng thái lỗi | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG03-001` | error | Thông báo đỏ + ô nhập viền invalid | apply trả lỗi |
| `STA-DLG03-002` | collapsed | Ẩn (managed=false) | mặc định |

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
| 1 | Nguồn ảnh `dialog-new-group.jpg` chưa so với đích | Chưa đối chiếu | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
