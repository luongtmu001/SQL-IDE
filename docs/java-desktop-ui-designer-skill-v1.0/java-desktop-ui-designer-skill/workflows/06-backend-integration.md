# Workflow 06 — Backend Integration (Gate D)
# Quy trình 06 — Nối logic thật

Áp dụng cho các mốc D3–D9 và phần còn lại của D1, D2. Trạng thái từng mốc: `HANDOFF.md`, mục 5. Tiêu chí gốc: `ui-specifications/04-reports/phase8-plan.md`, mục 3.

## Bước 1 — Đọc nguồn

1. Đọc hàm Python liên quan: `app/services`, `app/api`, `lltool/database`, `utils/sql_safety.py`, `models/`.
2. Ghi hành vi: đầu vào, đầu ra, mặc định, lỗi, khác biệt theo DB.
3. Đọc code Java hiện có. Nhiều phần đã được làm mà `phase8-plan.md` chưa ghi, nên phải kiểm trước để không làm lại.

## Bước 2 — Nêu trước khi làm

Báo: phạm vi, file sẽ tạo hoặc sửa, chức năng có thể bị ảnh hưởng, số kết nối hoặc truy vấn phát sinh, tiêu chí nghiệm thu, câu hỏi còn mở. Chờ duyệt nếu đổi quy trình hoặc hành vi người dùng đã quen.

## Bước 3 — Làm một lát dọc nhỏ

- Từ UI đến DB, cho đúng một mốc.
- Thứ tự DB đã chốt: SQL Server, rồi PostgreSQL, rồi SQLite.
- Chạy nền, có timeout, hủy được, lỗi hiển thị rõ.
- Giữ `rules/06-backend-and-performance.md`.
- Sửa `core/` hoặc `infrastructure/` thì cài lại module.

## Bước 4 — Kiểm chứng theo 3 mức

| Mức | Ví dụ | Ai làm |
|---|---|---|
| Unit với mock hoặc SQLite | Parser, classifier, splitter | Agent |
| IT với server thử, chỉ đọc | Thêm vào `QueryDbIT`, chạy `run-db-tests.cmd` | Agent, sau khi người dùng đồng ý |
| Thử trên giao diện | Danh sách K1–K10 | **Người dùng** |

Với IT, kết quả nằm ở `target/db-test-report.txt` và `target/grid-perf.txt`. Không ghi mật khẩu vào báo cáo.

## Bước 5 — Cập nhật và dừng

Cập nhật `phase8-plan.md`, `HANDOFF.md`. Thêm các mục cần kiểm trên máy vào danh sách K. Báo cáo, chờ duyệt mốc.
