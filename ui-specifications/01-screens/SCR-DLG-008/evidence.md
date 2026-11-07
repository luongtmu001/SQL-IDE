# Evidence Log — Format confirmation
# Nhật ký bằng chứng — Xác nhận định dạng code

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG08-C01 | `ui/dialogs/FormatConfirmDialog.java` | `confirm / resetSkip` | Bỏ qua hỏi nếu cờ được đặt | code_verified |
| EVD-DLG08-C02 | `controllers/EditorController.java:600` | `handleFormatSql` | Gọi trước khi định dạng | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG08-001 | Nơi nào gọi resetSkip() (Settings?) | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Không có.
