# Check for updates — Screen Specification
# Đặc tả màn hình: Kiểm tra cập nhật

- Screen ID: `SCR-DLG-013`
- Source: `update_window.html (+ #ide-update-modal fallback)` (theo screen-inventory.json)
- Target: ui/dialogs/UpdateDialog.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Kiểm tra, tải và cài bản cập nhật (hiện là mô phỏng với 3 kịch bản).
- Entry point / Cách mở: Help ▸ Check updates (MainController:857), nút trong About.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: DLG-012, SCR-SET-001 (updates.*)

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-013/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Kiểm tra cập nhật**, icon `unknown`), cỡ -1×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG13-001` | Dialog window | Cửa sổ Check for updates (DialogChrome) | center | width=auto, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG13-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG13-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG13-001` | Scenario selector | combobox | Kịch bản: Có bản mới / Đã là bản mới nhất / Lỗi mạng | unknown | `unknown` | code_verified |
| `CMP-DLG13-002` | Versions | table | Phiên bản hiện tại / mới nhất | unknown | `unknown` | code_verified |
| `CMP-DLG13-003` | Release notes | list | Ghi chú phát hành | unknown | `unknown` | code_verified |
| `CMP-DLG13-004` | Download via browser | hyperlink | Tải qua trình duyệt | unknown | `unknown` | code_verified |
| `BTN-DLG13-005` | Recheck | button | Kiểm tra lại | unknown | `unknown` | code_verified |
| `BTN-DLG13-006` | Download | button | Tải về | unknown | `unknown` | code_verified |
| `BTN-DLG13-007` | Cancel download | button | Hủy tải | unknown | `unknown` | code_verified |
| `CMP-DLG13-008` | Progress | progress | N.N / M MB | unknown | `unknown` | code_verified |
| `BTN-DLG13-009` | Open folder | button | Mở thư mục | unknown | `unknown` | code_verified |
| `BTN-DLG13-010` | Restart | button | Khởi động lại | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG13-001` | `CMP-DLG13-001` | selection_changed | Trạng thái chuyển CHECKING→AVAILABLE/LATEST/ERROR | no |
| `INT-DLG13-002` | `BTN-DLG13-007` | click | Xác nhận rồi dừng tiến trình giả | có |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG13-001` | loading | Đang kiểm tra / đang tải | State CHECKING, DOWNLOADING |
| `STA-DLG13-002` | normal | LATEST / AVAILABLE / DONE | theo kịch bản |
| `STA-DLG13-003` | error | Lỗi mạng + nút Kiểm tra lại | State ERROR |

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
| 1 | Chưa có logic thật; bộ chọn kịch bản là công cụ phát triển | Cần quyết định có giữ không | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
