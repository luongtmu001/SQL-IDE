# Evidence Log — Result panel
# Nhật ký bằng chứng — Bảng kết quả

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| EVD-RES-R01 | Nguồn: tab Results với 12 dòng · 101 ms · 8.6 KB, kết nối, sparkline, nút sao chép JSON | `main-full-1456x816.jpg` | observed |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-RES-C01 | `fxml/results.fxml` | `resultsTabPane` | 5 tab: Results, Messages, Timeline, Execution Plan, Terminal | code_verified |
| EVD-RES-C02 | `controllers/ResultsController.java` | `populateContextMenu` | Menu ngữ cảnh lưới (freeze, copy, visualize, find, aggregates, script, excel, print) | code_verified |
| EVD-RES-C03 | `controllers/ResultsController.java` | `setupTerminal` | Terminal in "PostgreSQL 16.2 Interactive SQL Terminal (Mock)" | code_verified |
| EVD-RES-C04 | `controllers/ResultsController.java` | `handleCopyExecutionStats` | JSON rows/durationMs/columns/timestamp | code_verified |
| EVD-RES-C05 | `ui/results/*` | `FrozenColumnSupport, GridFilterState, ColumnFilterPopup, DesktopGridExportService` | Cố định cột, lọc theo cột/tức thì, xuất file | code_verified |
| EVD-RES-C06 | `ui/components/LoadingOverlay.java` | `attach / show` | Lớp phủ có thời gian trôi và "Hủy" | code_verified |
| EVD-RES-C07 | `ui-specifications/04-reports/phase8-plan.md` | `bảng "Đã làm" (dòng Chạy câu lệnh)` | Chạy theo phiên của tab; một câu lệnh một lúc cho cả app; Hủy theo tab chưa có | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-RES-001 | Nguồn: giao diện thật của tab Messages/Errors/Logs/Plan | Chỉ chụp tab Results (screen-inventory.md: partially_observed) | Cao | Không chặn spec, chặn Gate C |
| UNK-RES-002 | Cách đích hiển thị lỗi truy vấn (nguồn có tab Errors) | Đọc FXML và danh sách handler, chưa lần theo luồng lỗi | Trung bình | Không |
| UNK-RES-003 | Terminal có được duyệt giữ lại không | Không có trong inventory | Thấp | Không |
| UNK-RES-004 | Màu theo tốc độ ở footer, sparkline 5 lần chạy | Chưa kiểm đích | Thấp | Không |
| UNK-RES-005 | Ảnh đích của toàn bộ panel | Chưa chụp | Trung bình | Chặn review |

## Safety notes / Lưu ý an toàn

"Act Plan" và nút Hủy tác động tới DB thật khi backend = real; chỉ thử trên DB thử. Export/Print ghi file hoặc gửi máy in.
