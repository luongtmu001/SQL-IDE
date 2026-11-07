# Evidence Log — App loading overlay
# Nhật ký bằng chứng — Lớp phủ đang tải

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| — | Chưa có quan sát chạy thật cho màn hình này | — | unknown |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-DLG14-C01 | `ui/components/LoadingOverlay.java` | `attach / over / show` | Overlay làm mờ vùng chủ, thẻ giữa có spinner, thời gian, Hủy | code_verified |
| EVD-DLG14-C02 | `controllers/ResultsController.java:160,1532` | `LoadingOverlay.attach` | Nơi dùng hiện tại | code_verified |
| EVD-DLG14-C03 | `phase8-plan.md D2` | `LoadingOverlay cho Explorer` | Chưa nối cho Explorer (hiện dòng "Đang tải…") | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-DLG14-001 | Hành vi hủy thật (D3: Statement.cancel) | Đọc mã dialog đích; chưa đối chiếu mã nguồn Python | Cao | Không |

## Safety notes / Lưu ý an toàn

Hủy thao tác giữa chừng có thể để phiên trong trạng thái chưa rõ; chỉ thử trên DB thử.
