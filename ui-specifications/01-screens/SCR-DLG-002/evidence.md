# Evidence Log — Reconnect password prompt
# Nhật ký bằng chứng — Nhập mật khẩu khi kết nối lại

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG02-C01 | `ui/dialogs/PasswordPromptDialog.java` | `show` | Bố cục, văn bản, ràng buộc nút Kết nối với ô mật khẩu | code_verified |
| EVD-DLG02-C02 | `controllers/SidebarController.java:151, MainController.java:422,448` | `PasswordPromptDialog.show` | Nơi gọi: Explorer và MainController | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG02-001 | Nguồn: có hạn 30 ngày như đích không | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Mật khẩu được nhập ở đây: không ghi log; không gõ mật khẩu thật khi thử.
