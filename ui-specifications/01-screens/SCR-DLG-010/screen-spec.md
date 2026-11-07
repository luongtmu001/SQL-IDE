# Debug extension warning — Screen Specification
# Đặc tả màn hình: Cảnh báo extension debug

- Screen ID: `SCR-DLG-010`
- Source: `ide-modal-debug-extension-warning` (theo screen-inventory.json)
- Target: ui/dialogs/DebugExtensionDialog.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Cảnh báo khi câu lệnh chứa hàm debug `bs.*` (chỉ chạy ở chế độ Run Debug).
- Entry point / Cách mở: Run khi có marker debug (MainController:1246).
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-003 (Run)

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-010/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Có hàm debug bs.* trong code**, icon `fth-alert-triangle`), cỡ 520×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG10-001` | Dialog window | Cửa sổ Debug extension warning (DialogChrome) | center | width=520px, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG10-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG10-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG10-001` | Message | label | Các hàm bs.* chỉ chạy ở chế độ Run Debug, không chạy được ở chế độ Run thường. | unknown | `unknown` | code_verified |
| `CMP-DLG10-002` | Occurrence list | list | Dòng N · <text> | unknown | `unknown` | code_verified |
| `BTN-DLG10-003` | Cancel | button | Hủy | unknown | `unknown` | code_verified |
| `BTN-DLG10-004` | Comment and run | button | Comment hết bs.* và chạy | unknown | `unknown` | code_verified |
| `BTN-DLG10-005` | Run Debug | button | Run Debug (chưa hỗ trợ) | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG10-001` | `BTN-DLG10-004` | click | Trả COMMENT_AND_RUN | no |
| `INT-DLG10-002` | `BTN-DLG10-003` | click | Trả CANCEL | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG10-001` | disabled | Nút mờ "(chưa hỗ trợ)" | luôn (hoãn) |

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
| 1 | Run Debug chưa làm | Hoãn theo phạm vi | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
