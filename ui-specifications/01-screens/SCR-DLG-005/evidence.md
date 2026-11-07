# Evidence Log — Database filter
# Nhật ký bằng chứng — Lọc database

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG05-C01 | `ui/dialogs/DatabaseFilterDialog.java` | `show` | Tham số: owner, connectionId, connectionName, allDatabases, onApplied; phụ đề = tên kết nối | code_verified |
| EVD-DLG05-C02 | `controllers/SidebarController.java:695` | `DatabaseFilterDialog.show` | Nơi gọi | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG05-001 | Icon/kích thước cửa sổ (đọc từ DialogChrome) | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Không có.
