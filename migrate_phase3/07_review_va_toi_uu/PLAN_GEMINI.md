# Phase 3 — Rà soát và kế hoạch tối ưu (mục lục)

> Ngày rà soát: 2026-11-11 · Phạm vi: 6 tính năng trong `migrate_phase3/01..06`
> Cách kiểm tra: đọc mã UI + service, chạy toàn bộ test (332/332 pass), render offscreen từng tab ở chế độ mock.

Kế hoạch được tách thành 2 file, làm theo thứ tự:

| Thứ tự | File | Nội dung |
|---|---|---|
| 1 | [01_PLAN_GIAO_DIEN.md](01_PLAN_GIAO_DIEN.md) | Sửa giao diện: token màu, component dùng chung, nhãn, canvas đồ thị, từng màn hình, hộp thoại |
| 2 | [02_PLAN_BACKEND.md](02_PLAN_BACKEND.md) | Sửa backend từng tính năng: Data Editor → Table Designer → Object Search → Schema Compare → Dependency Graph → SQL Visualizer |

Tài liệu kèm theo:
- `screenshots/` — ảnh hiện trạng 5 màn hình (Schema Compare chưa chụp được do lỗi icon `fth-table`, sửa ở Bước 0 của File 1).
- `tools/UiSnapshotHarness.java.txt` — harness chụp ảnh offscreen, đưa vào `src/test` ở Bước 0 của File 1.

## Tình trạng thật của 6 tính năng

| # | Tính năng | Báo cáo ghi | Thực tế |
|---|---|---|---|
| 01 | Table Designer | COMPLETE | Dùng được; mất thiết kế sau khi lưu bảng mới, theo dõi thay đổi thiếu, DDL chạy trên UI thread |
| 02 | Table Data Editor | COMPLETE | Chưa an toàn để ghi dữ liệu: INSERT hỏng với bảng có identity, mất dấu tiếng Việt, Delete xoá dòng khi đang gõ, không kiểm tra số dòng bị ảnh hưởng |
| 03 | Object Content Search | COMPLETE | Tốt nhất; nuốt lỗi truy vấn, không huỷ được, không giới hạn kết quả |
| 04 | Schema Compare | COMPLETE / VERIFIED | Chỉ là demo: luôn so sánh 2 snapshot giả; cây kết quả ném exception do icon không tồn tại |
| 05 | Dependency Graph | COMPLETE | Chỉ là demo: luôn vẽ đồ thị mẫu `dbo.orders` |
| 06 | SQL Visualizer | COMPLETE | Phân tích bằng regex; số dòng và "chi phí %" là số cứng trong code; layout bị cắt hai bên |

Lưu ý khi làm File 1 trước: ba tab 04, 05, 06 vẫn chạy dữ liệu mẫu trong suốt đợt giao diện và phải hiện nhãn `DỮ LIỆU MẪU` trên footer cho tới khi xong phần tương ứng ở File 2.
