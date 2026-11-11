# Workflow 01 — Project Discovery
# Quy trình 01 — Khảo sát dự án

## Trạng thái

**Đã hoàn thành ngày 2026-11-04.** Không làm lại toàn bộ. Dùng kết quả có sẵn:

| Cần biết | Xem |
|---|---|
| Cấu trúc code Java | `ui-specifications/00-application/PROJECT-OVERVIEW.md`, cộng phần chỉnh ở `HANDOFF.md`, mục 8 |
| Cấu trúc app Python | `00-application/project-map.md`, `source-index.md`, `application-map.json` |
| Cửa sổ và điều hướng | `00-application/navigation-map.md`, `screen-inventory.md` |
| Ảnh app gốc | `00-application/screenshots/source/` (12 ảnh, 1456×816) |
| Phân tích từng tính năng | `docs/feature_03…08_*.md`, `docs/phase0_source_analysis_inventory.md` |

## Khi nào được khảo sát lại

Khi cần biết hành vi Python của một tính năng cụ thể mà tài liệu chưa có, hoặc tài liệu mâu thuẫn với code.

## Cách làm

1. Nguồn Python: `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`. **Chỉ đọc**, không chạy lệnh ghi vào thư mục này.
2. Tìm đúng điểm vào: template → JS handler → bridge hoặc API → service → câu SQL.
3. Lấy code làm chuẩn, không lấy tài liệu Python. Đã gặp trường hợp tài liệu ghi khác code.
4. Bản đóng gói `dist/LLTool_v1.3.1` chạy được để quan sát. Không dùng thao tác ghi hay kết nối PROD.
5. Menu của WebView2 không chụp được bằng công cụ điều khiển máy: lấy cấu trúc từ `index.html`.
6. Ghi kết quả vào tài liệu của tính năng, không mở thêm một bộ tài liệu khảo sát song song.
