# Evidence Log — Debug extension warning
# Nhật ký bằng chứng — Cảnh báo extension debug

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG10-C01 | `ui/dialogs/DebugExtensionDialog.java` | `show` | Lựa chọn CANCEL / COMMENT_AND_RUN / RUN_DEBUG | code_verified |
| EVD-DLG10-C02 | `controllers/MainController.java:1246` | `DebugExtensionDialog.show` | Chỉ COMMENT_AND_RUN tiếp tục | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG10-001 | Cách "comment" sửa SQL (buffer hay chỉ bản chạy) | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Trung bình | Không |

## Safety notes / Lưu ý an toàn

Không có.
