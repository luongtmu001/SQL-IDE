# Evidence Log — Generic message popup
# Nhật ký bằng chứng — Popup thông báo chung

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG11-C01 | `ui/dialogs/MessageDialog.java` | `Kind {INFO, SUCCESS, WARNING, ERROR, CONFIRM}` | Hộp thông báo/xác nhận chung với nút Sao chép | code_verified |
| EVD-DLG11-C02 | `ui/components/AppDialog.java` | `confirm/info/warning/error` | API tiện dụng | code_verified |
| EVD-DLG11-C03 | `ui/dialogs/CloseTabDialog.java` | `Choice {CANCEL, SAVE_AND_CLOSE, CLOSE_WITHOUT_SAVING}` | Hộp thoại đóng tab chưa lưu — chưa có ID trong inventory | code_verified |
| EVD-DLG11-C04 | `ui/dialogs/TextPromptDialog.java` | `show` | Hộp nhập một dòng (đổi tên tab/style/…) — chưa có ID trong inventory | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG11-001 | Danh sách đầy đủ nơi gọi AppDialog | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |

## Safety notes / Lưu ý an toàn

Không có.
