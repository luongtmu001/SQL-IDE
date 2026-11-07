# Traceability Matrix — SQL-IDE
# Ma trận truy vết nguồn → đích

- Cập nhật: 2026-11-05
- Phạm vi: toàn bộ 21 màn hình/bề mặt trong `00-application/screen-inventory.md`.
- Dữ liệu máy: `traceability-matrix.json` (chỉ chứa các mapping mới; 4 màn hình cũ giữ nguyên `mapping.json` của chúng).
- Phía nguồn Python chỉ dựa vào inventory; **không** có bước đối chiếu lại mã Python trong lượt tạo file này (xem cột parity).

## 1. Độ phủ đặc tả / Spec coverage

| Screen | Tên | Inventory | Gói đặc tả (7 file) | Trạng thái |
|---|---|---|---|---|
| `SCR-SHELL-001` | Khung cửa sổ & title bar + menu | partially_observed | đã có | `(xem gói cũ)` |
| `SCR-MAIN-001` | Bố cục workspace chính | observed | đã có | `(xem gói cũ)` |
| `SCR-MAIN-002` | Object Explorer | observed | đã có | `(xem gói cũ)` |
| `SCR-MAIN-003` | Vùng soạn thảo | observed | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-MAIN-004` | Bảng kết quả | partially_observed | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-MAIN-005` | Thanh trạng thái | observed | đã có | `(xem gói cũ)` |
| `SCR-SET-001` | Cửa sổ Cài đặt | observed | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-001` | Tạo/sửa kết nối | observed | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-002` | Nhập mật khẩu khi kết nối lại | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-003` | Tạo / đổi tên nhóm | observed | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-004` | Xác nhận xóa nhóm | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-005` | Lọc database | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-006` | Lọc object | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-007` | Cảnh báo câu lệnh nguy hiểm | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-008` | Xác nhận format code | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-009` | Quản lý định dạng mã SQL | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-010` | Cảnh báo extension debug | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-011` | Popup thông báo chung | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-012` | About | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-013` | Kiểm tra cập nhật | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |
| `SCR-DLG-014` | Lớp phủ đang tải | discovered | mới (2026-11-05, hồi tố) | `implemented_mock` |

21/21 màn hình/bề mặt đã có gói đặc tả. 17 gói mới ở lượt này (đặc tả hồi tố, **chưa Gate B**); MAIN-003 còn giữ `toolbar-redesign.md` đã duyệt làm phụ lục.

## 2. Tổng hợp mapping của 17 gói mới

| mapping_status | Số lượng |
|---|---|
| `deferred` | 2 |
| `implemented_mock` | 39 |
| `not_started` | 2 |

| behavior_parity | Số lượng |
|---|---|
| `changed_with_approval` | 5 |
| `not_checked` | 30 |
| `partial` | 8 |

`not_checked` chiếm phần lớn: đặc tả hồi tố đọc mã đích, **chưa so hành vi** với nguồn Python. Không được coi `implemented_mock` là `reviewed`.

## 3. Mapping của 4 gói cũ (chỉ kiểm từ vựng)

| Screen | Số mapping | mapping_status | Ghi chú từ vựng so với skill |
|---|---|---|---|
| `SCR-SHELL-001` | 45 | implemented_mock×29, deferred×11, not_applicable_approved×5 | đúng chuẩn skill |
| `SCR-MAIN-001` | 4 | spec_draft×4 | `spec_draft` và schema rút gọn (thiếu source_type/target_id/approval_reference) |
| `SCR-MAIN-002` | 6 | spec_draft×5, implemented_mock×1 | `spec_draft`; parity `missing`/`changed` ngoài danh sách của skill |
| `SCR-MAIN-005` | 8 | spec_draft×7, implemented_mock×1 | `spec_draft`; parity `full`/`changed`/`split`/`moved_with_approval` ngoài danh sách của skill |

Đề xuất (chưa làm, cần bạn đồng ý): chuẩn hóa `spec_draft` → `designed`/`implemented_mock` và các giá trị parity về `not_checked | matched | partial | changed_with_approval` cho MAIN-001/002/005.

## 4. Chỉ có ở đích (không có trong inventory nguồn) / Target-only

| Mục | Nơi | Đề xuất |
|---|---|---|
| Tab Terminal (mô phỏng) | SCR-MAIN-004 | Hỏi bạn: giữ, hoãn hay bỏ |
| ProductionSafetyDialog | DLG-007 | Gộp vào DLG-007 hoặc thêm `SCR-DLG-015` |
| CloseTabDialog (đóng tab chưa lưu) | DLG-011 | Thêm `SCR-DLG-016` (đề xuất) |
| TextPromptDialog (nhập một dòng) | DLG-011 | Thêm `SCR-DLG-017` (đề xuất) |
| Bộ chọn kịch bản trong UpdateDialog | DLG-013 | Công cụ dev, bỏ khi nối thật |
| `LoadingView` cũ song song `LoadingOverlay` | DLG-014 | Dọn trùng lặp |

## 5. Chỉ có ở nguồn, chưa có tương đương / Source-only

| Mục nguồn | Màn hình | Tình trạng |
|---|---|---|
| Tab Errors (và Logs) của result panel | SCR-MAIN-004 | `not_started` — cần xác nhận lỗi truy vấn hiện ở đâu |
| Xem trước phông chữ trực tiếp trong Settings | SCR-SET-001 | `not_started` |
| Chế độ diff của editor | SCR-MAIN-003 | `deferred` theo phạm vi |
| Nút Debug/Transaction/Parse/Import-Export/Compare/Syntax/History/Object search | SCR-MAIN-003 | `deferred` theo phạm vi |
| Sparkline 5 lần chạy ở footer result | SCR-MAIN-004 | thiếu (đích chỉ có biểu tượng tĩnh) |

## 6. Việc còn lại để đóng review

1. Bạn duyệt (Gate B) 17 gói đặc tả hồi tố, hoặc nêu phần cần sửa.
2. Chụp ảnh đích cho MAIN-003 (tab/banner/split), MAIN-004, SET-001, các dialog; chép ảnh nguồn vào `screenshots/` từng màn hình.
3. Đối chiếu hành vi với mã Python để chuyển `not_checked` → `matched`/`partial`/`changed_with_approval`.
4. Quyết định các mục ở mục 4 và 5.
