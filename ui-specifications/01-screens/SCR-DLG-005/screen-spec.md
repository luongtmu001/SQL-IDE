# Database filter — Screen Specification
# Đặc tả màn hình: Lọc database

- Screen ID: `SCR-DLG-005`
- Source: `#ide-db-filter-modal (explorer.js)` (theo screen-inventory.json)
- Target: ui/dialogs/DatabaseFilterDialog.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Chọn các database hiển thị dưới một kết nối trong Explorer.
- Entry point / Cách mở: Explorer toolbar Filter DB (SidebarController:695).
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-002, DLG-006

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-005/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Lọc database**, icon `unknown`), cỡ -1×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG05-001` | Dialog window | Cửa sổ Database filter (DialogChrome) | center | width=auto, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG05-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG05-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG05-001` | Search | text_field | Tìm database… | unknown | `unknown` | code_verified |
| `CMP-DLG05-002` | Select all | hyperlink | Chọn tất cả | unknown | `unknown` | code_verified |
| `CMP-DLG05-003` | Deselect all | hyperlink | Bỏ chọn tất cả | unknown | `unknown` | code_verified |
| `CMP-DLG05-004` | Database list | list | (danh sách có ô chọn) | unknown | `unknown` | code_verified |
| `CMP-DLG05-005` | Stats | label | Đã chọn N/M | unknown | `unknown` | code_verified |
| `CMP-DLG05-006` | Reset | hyperlink | Đặt lại | unknown | `unknown` | code_verified |
| `BTN-DLG05-007` | Cancel | button | Hủy | unknown | `unknown` | code_verified |
| `BTN-DLG05-008` | Apply | button | Áp dụng | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG05-001` | `BTN-DLG05-008` | click | Lưu danh sách database đã chọn và gọi onApplied (tải lại Explorer) | no |
| `INT-DLG05-002` | `CMP-DLG05-001` | key | Danh sách thu hẹp | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG05-001` | normal | "Đã chọn N/M" cập nhật khi tích | đổi lựa chọn |

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
