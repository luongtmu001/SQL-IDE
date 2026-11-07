# Reconnect password prompt — Screen Specification
# Đặc tả màn hình: Nhập mật khẩu khi kết nối lại

- Screen ID: `SCR-DLG-002`
- Source: `reconnectPasswordModal` (theo screen-inventory.json)
- Target: ui/dialogs/PasswordPromptDialog.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Xin mật khẩu khi kết nối profile chưa lưu mật khẩu hoặc mật khẩu đã lưu quá 30 ngày.
- Entry point / Cách mở: Kết nối profile (Explorer, banner tab, MainController) khi thiếu mật khẩu.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: DLG-001, SCR-MAIN-002, SCR-MAIN-003 (banner)

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-002/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Kết nối "<tên>"**, icon `fth-lock`), cỡ 400×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG02-001` | Dialog window | Cửa sổ Reconnect password prompt (DialogChrome) | center | width=400px, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG02-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG02-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG02-001` | Target info | label | <target> | unknown | `unknown` | code_verified |
| `CMP-DLG02-002` | Reason | label | Kết nối này chưa lưu mật khẩu. / Mật khẩu đã lưu quá 30 ngày. Nhập lại để tiếp tục. | unknown | `unknown` | code_verified |
| `CMP-DLG02-003` | Password | password_field | Mật khẩu | unknown | `unknown` | code_verified |
| `BTN-DLG02-004` | Show password | toggle_button | Hiện/ẩn | unknown | `unknown` | code_verified |
| `CMP-DLG02-005` | Save password | checkbox | Lưu mật khẩu (mã hóa, 30 ngày) | unknown | `unknown` | code_verified |
| `CMP-DLG02-006` | Hint | label | Không lưu: mật khẩu chỉ giữ trong bộ nhớ đến khi đóng ứng dụng. | unknown | `unknown` | code_verified |
| `BTN-DLG02-007` | Cancel | button | Hủy | unknown | `chrome.close` | code_verified |
| `BTN-DLG02-008` | Connect | button | Kết nối | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG02-001` | `BTN-DLG02-008` | click | Trả Result(password, save) và đóng | no |
| `INT-DLG02-002` | `BTN-DLG02-007` | click | Đóng, không kết nối | no |
| `INT-DLG02-003` | `BTN-DLG02-004` | click | Đổi giữa PasswordField và TextField liên kết hai chiều | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG02-001` | disabled | Nút mờ | mật khẩu rỗng |
| `STA-DLG02-002` | error | Chữ đỏ "Mật khẩu đã lưu quá 30 ngày…" | expired=true |
| `STA-DLG02-003` | normal | Chữ gợi ý "Kết nối này chưa lưu mật khẩu." | expired=false |

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
| 1 | Chưa so với nguồn (inventory: discovered) | Không biết khác biệt văn bản/hành vi | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
