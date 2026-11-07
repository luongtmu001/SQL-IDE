# Dangerous SQL guard — Screen Specification
# Đặc tả màn hình: Cảnh báo câu lệnh nguy hiểm

- Screen ID: `SCR-DLG-007`
- Source: `fatalActionsGuardBackdrop` (theo screen-inventory.json)
- Target: ui/dialogs/ActionsGuardDialog.java (+ ProductionSafetyDialog.java); logic framework/query/SqlProtectionService.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Cảnh báo trước khi chạy câu lệnh có thể mất dữ liệu (DELETE/UPDATE không WHERE, TRUNCATE) và trước khi chạy câu lệnh ghi trên môi trường PRODUCTION.
- Entry point / Cách mở: Run (MainController: ActionsGuardDialog:1564, ProductionSafetyDialog:1305). Chỉ khi bật `sql.sqlprotection`.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-003 (Run), SCR-SET-001 (sql.sqlprotection), D3

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-007/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Actions Guard / Cảnh báo môi trường PRODUCTION**, icon `fth-shield / fth-alert-octagon`), cỡ 520×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG07-001` | Dialog window | Cửa sổ Dangerous SQL guard (DialogChrome) | center | width=520px, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG07-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG07-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG07-001` | Issue list | list | Vấn đề · Dòng | unknown | `unknown` | code_verified |
| `CMP-DLG07-002` | Hint | label | Nhấp đúp một dòng để hủy chạy và nhảy tới dòng code đó. | unknown | `unknown` | code_verified |
| `CMP-DLG07-003` | Question | label | Vẫn tiếp tục thực thi? | unknown | `unknown` | code_verified |
| `BTN-DLG07-004` | Cancel (Esc) | button | Hủy (Esc) | unknown | `unknown` | code_verified |
| `BTN-DLG07-005` | Continue | button | Tiếp tục thực thi | unknown | `unknown` | code_verified |
| `CMP-DLG07-006` | PROD badge | label | PRODUCTION | unknown | `unknown` | code_verified |
| `CMP-DLG07-007` | Prod info | label | Kết nối / Database | unknown | `unknown` | code_verified |
| `BTN-DLG07-008` | Prod confirm | button | Tôi hiểu rủi ro, thực thi | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG07-001` | `CMP-DLG07-001` | double_click | Trả Result(false, line): hủy chạy và đưa con trỏ tới dòng | no |
| `INT-DLG07-002` | `BTN-DLG07-005` | click | Trả Result(true, 0) | đây là bước xác nhận |
| `INT-DLG07-003` | `BTN-DLG07-008` | click | Trả true | đây là bước xác nhận |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG07-001` | focused | Nút Hủy mang focus khi mở | mở hộp thoại |
| `STA-DLG07-002` | normal | Chiều cao tối đa 220 px | có vấn đề |

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
| 1 | ProductionSafetyDialog chưa có ID trong inventory | Cần quyết định gộp vào DLG-007 hay tạo DLG-015 | Không |
| 2 | Chưa so nguồn | Chưa đối chiếu | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
