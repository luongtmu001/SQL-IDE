# Evidence Log — Check for updates
# Nhật ký bằng chứng — Kiểm tra cập nhật

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG13-C01 | `ui/dialogs/UpdateDialog.java` | `State {CHECKING, LATEST, AVAILABLE, DOWNLOADING, DONE, ERROR}` | Máy trạng thái 6 pha với kịch bản mô phỏng | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG13-001 | Luồng cập nhật thật (URL `updates.updateUrl`, GitHub Releases) | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Cao nếu cần | Không |

## Safety notes / Lưu ý an toàn

Khi nối thật: tải file từ internet, phải xác nhận nguồn và tên file trước khi tải.
