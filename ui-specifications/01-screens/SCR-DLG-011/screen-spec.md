# Generic message popup — Screen Specification
# Đặc tả màn hình: Popup thông báo chung

- Screen ID: `SCR-DLG-011`
- Source: `settingsPopupBackdrop` (theo screen-inventory.json)
- Target: ui/dialogs/MessageDialog.java; ui/components/AppDialog.java; ui/dialogs/ConfirmDialog.java; ui/dialogs/CloseTabDialog.java; ui/dialogs/TextPromptDialog.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Thông báo/xác nhận dùng chung: info, success, warning, error, confirm; kèm các hộp xác nhận chuyên dụng.
- Entry point / Cách mở: Nhiều nơi (NotificationService "Chi tiết", Settings, Explorer, EditorController…).
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: Toàn ứng dụng

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-011/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Thông báo**, icon `theo loại`), cỡ -1×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG11-001` | Dialog window | Cửa sổ Generic message popup (DialogChrome) | center | width=auto, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG11-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG11-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG11-001` | Message icon | icon | theo Kind | unknown | `unknown` | code_verified |
| `CMP-DLG11-002` | Header | label | header | unknown | `unknown` | code_verified |
| `CMP-DLG11-003` | Content | label | content | unknown | `unknown` | code_verified |
| `CMP-DLG11-004` | Copy | hyperlink | Sao chép → Đã sao chép | unknown | `unknown` | code_verified |
| `BTN-DLG11-005` | Cancel | button | Hủy | unknown | `unknown` | code_verified |
| `BTN-DLG11-006` | OK | button | Đồng ý (tùy chỉnh) | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG11-001` | `CMP-DLG11-004` | click | Clipboard; đổi nhãn "Đã sao chép" | no |
| `INT-DLG11-002` | `BTN-DLG11-006` | click | Trả true (confirm) hoặc đóng | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG11-001` | normal | Màu theo tone: success/info bình thường, warning/danger có iconStyle | theo Kind |

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
| 1 | CloseTabDialog và TextPromptDialog chưa có ID trong inventory | Đề xuất DLG-015 (đóng tab chưa lưu), DLG-016 (nhập tên một dòng) | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
