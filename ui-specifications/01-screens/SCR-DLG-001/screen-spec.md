# Connection create/edit — Screen Specification
# Đặc tả màn hình: Tạo/sửa kết nối

- Screen ID: `SCR-DLG-001`
- Source: `connectionModal` (theo screen-inventory.json)
- Target: ui/dialogs/ConnectionManagerDialog.java + fxml/connection-manager-dialog.fxml + controllers/ConnectionManagerController.java
- Status: `implemented_mock`
- Evidence confidence: observed (nguồn) + code_verified (đích)
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Quản lý danh sách kết nối đã lưu (cây), tạo/sửa/nhân bản/xóa và kiểm tra/kết nối ngay.
- Entry point / Cách mở: Explorer: nút Connect, menu ngữ cảnh Edit Connection / New connection; Connection Manager từ status bar hoặc menu ⋯.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Hộp thoại modal, `showAndWait`.
- Related screens / Liên quan: SCR-MAIN-002 (Explorer), SCR-MAIN-005 (status bar), DLG-002 (mật khẩu)

## 2. Visual reference / Tham chiếu hình ảnh

Nguồn: `00-application/screenshots/source/dialog-new-connection.jpg`. Đích: chưa có ảnh.

## 3. Layout / Bố cục

`DialogChrome` (tiêu đề **Quản lý kết nối**, phụ đề *Dữ liệu mẫu — chưa kết nối database thật*, icon `fth-database`), cỡ 880×560.



## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-DLG01-001` | Dialog window | Cửa sổ Connection create/edit (DialogChrome) | center | width=880px, height=560px, resizable=False, flexible=True |
| `REG-DLG01-002` | Content | Nội dung | center | width=fill, height=auto, resizable=False, flexible=True |
| `REG-DLG01-003` | Footer | Nút hành động | bottom | width=fill, height=auto, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `BTN-DLG01-001` | New connection | icon_button | Kết nối mới | unknown | `handleNewConnection` | code_verified |
| `BTN-DLG01-002` | Duplicate | icon_button | Nhân bản | unknown | `handleDuplicateConnection` | code_verified |
| `BTN-DLG01-003` | Delete | icon_button | Xóa | unknown | `handleDeleteConnection` | code_verified |
| `CMP-DLG01-004` | Search connections | text_field | Tìm kết nối… | unknown | `filter` | code_verified |
| `CMP-DLG01-005` | Saved connections tree | tree | KẾT NỐI ĐÃ LƯU | unknown | `selection` | code_verified |
| `CMP-DLG01-006` | DB type | combobox | Loại DB | unknown | `unknown` | code_verified |
| `CMP-DLG01-007` | Environment | combobox | Môi trường | unknown | `unknown` | code_verified |
| `CMP-DLG01-008` | Name | text_field | Tên | unknown | `unknown` | code_verified |
| `CMP-DLG01-009` | Group | combobox | Nhóm | unknown | `unknown` | code_verified |
| `CMP-DLG01-010` | Host | text_field | Host | unknown | `unknown` | code_verified |
| `CMP-DLG01-011` | Port | spinner | Port | unknown | `unknown` | code_verified |
| `CMP-DLG01-012` | Database | combobox | Database | unknown | `unknown` | code_verified |
| `BTN-DLG01-013` | Fetch databases | icon_button | Tải danh sách database | unknown | `handleFetchDatabases` | code_verified |
| `CMP-DLG01-014` | Schema | combobox | Schema | unknown | `unknown` | code_verified |
| `BTN-DLG01-015` | Fetch schemas | icon_button | Tải danh sách schema | unknown | `handleFetchSchemas` | code_verified |
| `CMP-DLG01-016` | SQLite file | text_field | Tệp SQLite | unknown | `unknown` | code_verified |
| `BTN-DLG01-017` | Choose file | icon_button | Chọn tệp | unknown | `handleChooseSqliteFile` | code_verified |
| `CMP-DLG01-018` | Windows Authentication | checkbox | Windows Authentication | unknown | `unknown` | code_verified |
| `CMP-DLG01-019` | Username | text_field | Username | unknown | `unknown` | code_verified |
| `CMP-DLG01-020` | Password | password_field | Mật khẩu | unknown | `unknown` | code_verified |
| `BTN-DLG01-021` | Show password | toggle_button | Hiện / ẩn mật khẩu | unknown | `unknown` | code_verified |
| `CMP-DLG01-022` | Save password | checkbox | Lưu mật khẩu (mã hóa) | unknown | `unknown` | code_verified |
| `BTN-DLG01-023` | Advanced toggle | button | NÂNG CAO | unknown | `handleToggleAdvanced` | code_verified |
| `CMP-DLG01-024` | Timeout | spinner | Timeout (giây) | unknown | `unknown` | code_verified |
| `CMP-DLG01-025` | Read only | checkbox | Chỉ đọc | unknown | `unknown` | code_verified |
| `CMP-DLG01-026` | Trust server certificate | checkbox | Tin cậy chứng chỉ máy chủ (TrustServerCertificate) | unknown | `unknown` | code_verified |
| `BTN-DLG01-027` | Test | button | Kiểm tra | unknown | `handleTestConnection` | code_verified |
| `BTN-DLG01-028` | Close | button | Đóng | unknown | `handleClose` | code_verified |
| `BTN-DLG01-029` | Save | button | Lưu | unknown | `handleSaveConnection` | code_verified |
| `BTN-DLG01-030` | Save and connect | button | Lưu và kết nối | unknown | `handleConnectNow` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-DLG01-001` | `BTN-DLG01-001` | click | Form rỗng (mặc định theo loại DB) | no |
| `INT-DLG01-002` | `CMP-DLG01-005` | selection_changed | Form điền sẵn giá trị của kết nối | no |
| `INT-DLG01-003` | `BTN-DLG01-027` | click | ProgressIndicator rồi thông báo thành công/lỗi trong statusLabel | no |
| `INT-DLG01-004` | `BTN-DLG01-029` | click | Ghi kết nối; mật khẩu lưu mã hóa nếu bật | no |
| `INT-DLG01-005` | `BTN-DLG01-030` | click | Đóng hộp thoại và trả `connectRequestedId` cho người gọi | no |
| `INT-DLG01-006` | `BTN-DLG01-003` | click | ConfirmDialog "Xóa kết nối" rồi xóa | có (ConfirmDialog, nút danger) |
| `INT-DLG01-007` | `BTN-DLG01-013` | click | Điền ComboBox database | no |
| `INT-DLG01-008` | `BTN-DLG01-023` | click | Hiện timeout, chỉ đọc, trust cert | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-DLG01-001` | collapsed | Phần nâng cao ẩn, hiện tóm tắt | mặc định |
| `STA-DLG01-002` | expanded | Hiện timeout/chỉ đọc/trust cert | bấm NÂNG CAO |
| `STA-DLG01-003` | loading | ProgressIndicator hiện, nút tắt (chưa kiểm) | đang kiểm tra |
| `STA-DLG01-004` | selected | Ẩn dòng username/mật khẩu (suy luận) | bật |
| `STA-DLG01-005` | normal | Hiện dòng SQLite thay cho host/port | loại DB = SQLite |

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
| 1 | Phụ đề cố định "Dữ liệu mẫu — chưa kết nối database thật" sai sự thật từ D1 | Gây hiểu nhầm; nên bỏ hoặc đổi theo backend | Không |
| 2 | Nguồn là modal một kết nối, đích mở rộng thành quản lý nhiều kết nối | Khác phạm vi so với nguồn | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
