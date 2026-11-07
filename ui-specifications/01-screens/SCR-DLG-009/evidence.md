# Evidence Log — SQL code style manager
# Nhật ký bằng chứng — Quản lý định dạng mã SQL

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG09-C01 | `ui/dialogs/CodeStyleDialog.java` | `show` | Cửa sổ quản lý style, có hộp xác nhận khi đóng với thay đổi chưa lưu | code_verified |
| EVD-DLG09-C02 | `ui/codestyle/*` | `SqlStyleStore, SqlStyleMetadata, SimpleSqlFormatter` | Lưu style, metadata tùy chọn, bộ định dạng đơn giản | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG09-001 | Kích thước cửa sổ và icon (chưa đọc) | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |
| UNK-DLG09-002 | Danh sách tùy chọn style theo metadata | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Không có. Nhập file style từ nguồn không tin cậy chỉ được đọc như JSON (kiểm tra hợp lệ).
