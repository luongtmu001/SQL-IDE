# Evidence Log — Group delete confirmation
# Nhật ký bằng chứng — Xác nhận xóa nhóm

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG04-C01 | `ui/dialogs/ConfirmDialog.java` | `show` | Hộp xác nhận chung, nút danger khi destructive | code_verified |
| EVD-DLG04-C02 | `controllers/SidebarController.java:614` | `ConfirmDialog.show("Xóa nhóm", …)` | Nơi gọi xóa nhóm | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG04-001 | Văn bản chính xác và hành vi với kết nối trong nhóm | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Hành động xóa trong Explorer sửa dữ liệu thật của người dùng (profile). Dùng dữ liệu thử.
