# Object filter — Screen Specification
# Đặc tả màn hình: Lọc object

- Screen ID: `SCR-DLG-006`
- Source: `showFilterModal (explorer.js)` (theo screen-inventory.json)
- Target: ui/dialogs/ObjectFilterDialog.java
- Status: `implemented_mock`
- Evidence confidence: code_verified
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Lọc đối tượng (bảng/view/function/procedure) dưới một thư mục trong Explorer theo tên hoặc schema.
- Entry point / Cách mở: Menu ngữ cảnh trên node thư mục (SidebarController:702).
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-002, DLG-005

## 2. Visual reference / Tham chiếu hình ảnh

Chưa có ảnh nguồn lẫn đích trong `01-screens/SCR-DLG-006/screenshots/`.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Lọc <loại đối tượng>**, icon `unknown`), cỡ -1×tự co theo nội dung.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG06-001` | Dialog window | Cửa sổ Object filter (DialogChrome) | center | width=auto, height=auto (theo nội dung), resizable=False, flexible=True |
| `REG-DLG06-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG06-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-DLG06-001` | Scope | label | <catalog · schema> | unknown | `unknown` | code_verified |
| `CMP-DLG06-002` | Header: Trường | label | Trường | unknown | `unknown` | code_verified |
| `CMP-DLG06-003` | Header: Toán tử | label | Toán tử | unknown | `unknown` | code_verified |
| `CMP-DLG06-004` | Header: Giá trị | label | Giá trị | unknown | `unknown` | code_verified |
| `CMP-DLG06-005` | Field combo | combobox | Name / Schema | unknown | `unknown` | code_verified |
| `CMP-DLG06-006` | Operator combo | combobox | Toán tử (ObjectFilter.Operator) | unknown | `unknown` | code_verified |
| `CMP-DLG06-007` | Value | text_field | Giá trị | unknown | `unknown` | code_verified |
| `BTN-DLG06-008` | Remove condition | icon_button | Xóa điều kiện | unknown | `unknown` | code_verified |
| `BTN-DLG06-009` | Add condition | button | Thêm điều kiện | unknown | `unknown` | code_verified |
| `CMP-DLG06-010` | Logic | combobox | Kết hợp: (AND/OR) | unknown | `unknown` | code_verified |
| `CMP-DLG06-011` | Clear filter | hyperlink | Xóa bộ lọc | unknown | `unknown` | code_verified |
| `BTN-DLG06-012` | Cancel | button | Hủy | unknown | `unknown` | code_verified |
| `BTN-DLG06-013` | Apply | button | Áp dụng | unknown | `unknown` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG06-001` | `BTN-DLG06-009` | click | Hàng mới Name CONTAINS "" | no |
| `INT-DLG06-002` | `BTN-DLG06-013` | click | Lưu ObjectFilter theo kết nối/catalog/schema/loại và gọi onApplied | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG06-001` | normal | Mỗi hàng có nút xóa | nhiều điều kiện |

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
