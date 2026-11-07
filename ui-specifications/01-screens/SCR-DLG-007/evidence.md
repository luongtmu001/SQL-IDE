# Evidence Log — Dangerous SQL guard
# Nhật ký bằng chứng — Cảnh báo câu lệnh nguy hiểm

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG07-C01 | `ui/dialogs/ActionsGuardDialog.java` | `show` | ListView DangerousActionIssue, nhấp đúp để nhảy dòng, Hủy là mặc định | code_verified |
| EVD-DLG07-C02 | `ui/dialogs/ProductionSafetyDialog.java` | `show` | Badge PRODUCTION + loại câu lệnh, thông tin Kết nối/Database, nút "Tôi hiểu rủi ro, thực thi" | code_verified |
| EVD-DLG07-C03 | `framework/query/SqlProtectionService.java` | `detectDangerousActions` | Tokenize bỏ comment; phát hiện TRUNCATE, DELETE/UPDATE thiếu WHERE… (không áp dụng cho bảng tạm/biến bảng) | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG07-001 | Danh sách đầy đủ các loại vấn đề SqlProtectionService phát hiện | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Thấp | Không |
| UNK-DLG07-002 | Có đủ 3 lớp guard (PROD + Actions Guard + read-only) khi nối D3 không | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Cao | Không |

## Safety notes / Lưu ý an toàn

Đây là lớp bảo vệ chính chống chạy câu lệnh phá hủy. Kiểm bằng DB thử và câu lệnh DELETE không WHERE trên bảng thử.
