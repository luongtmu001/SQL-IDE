# Evidence Log — Connection create/edit
# Nhật ký bằng chứng — Tạo/sửa kết nối

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| EVD-DLG01-R01 | Nguồn: modal "New Connection" | `00-application/screenshots/source/dialog-new-connection.jpg` | observed |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG01-C01 | `ui/dialogs/ConnectionManagerDialog.java` | `open` | DialogChrome 880×560, phụ đề "Dữ liệu mẫu — chưa kết nối database thật", trả về connectRequestedId | code_verified |
| EVD-DLG01-C02 | `fxml/connection-manager-dialog.fxml` | `cm-form` | Mục CHUNG, MÁY CHỦ, XÁC THỰC, NÂNG CAO; footer Kiểm tra / Đóng / Lưu / Lưu và kết nối | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG01-001 | Ẩn/hiện dòng theo loại DB và Windows Auth chưa đọc kỹ | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Kiểm tra / Tải database/schema / Lưu và kết nối chạm server thật khi backend = real. Chỉ dùng DB thử.
