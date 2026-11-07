# Evidence Log — Group create/rename
# Nhật ký bằng chứng — Tạo/đổi tên nhóm

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| EVD-DLG03-R01 | Nguồn: modal "New Group" | `00-application/screenshots/source/dialog-new-group.jpg` | observed |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG03-C01 | `ui/dialogs/GroupNameDialog.java` | `show` | Tiêu đề đổi theo rename; lỗi hiện nội tuyến | code_verified |
| EVD-DLG03-C02 | `controllers/SidebarController.java:588,601` | `GroupNameDialog.show` | Nơi gọi | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG03-001 | Quy tắc kiểm tra tên (apply) nằm ở ExplorerGroupService | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Không có.
