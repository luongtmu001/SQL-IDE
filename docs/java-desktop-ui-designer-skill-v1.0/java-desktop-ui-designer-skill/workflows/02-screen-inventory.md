# Workflow 02 — Screen Inventory
# Quy trình 02 — Kiểm kê màn hình

## Trạng thái

Danh mục đã duyệt ở Gate A: `ui-specifications/00-application/screen-inventory.md` và `.json`.

Hiện có **22 gói đặc tả** trong `01-screens/`: `SCR-SHELL-001`, `SCR-MAIN-001…005`, `SCR-SET-001`, `SCR-DLG-001…014`, `SCR-SPLASH-001`.

`screen-inventory.md` lập ngày 2026-11-04 nên chưa có `SCR-SPLASH-001`, ghi 21 màn hình, và cột đích JavaFX còn cũ. Khi có dịp, cập nhật file này và `03-traceability/`.

## Màn hình mới

Một bề mặt được tính là màn hình khi nó là cửa sổ riêng, dialog, panel công cụ, menu có nhiều thao tác, luồng nhiều bước, hoặc trạng thái làm đổi bố cục.

1. Xác nhận với người dùng rằng màn hình này nằm trong phạm vi. Nhiều tính năng đang bị loại có chủ đích (`rules/01`, mục 2).
2. Tìm trong `screen-inventory.md`, phần ngoài phạm vi, để tránh làm trùng.
3. Cấp ID `SCR-<AREA>-<NNN>`, không dùng lại ID cũ.
4. Thêm vào `screen-inventory.md`, `.json`, `navigation-map.md` với trạng thái `discovered`, `partially_observed`, `observed` hoặc `blocked`.
5. Không xóa mục nào. Muốn loại thì ghi `deprecated_candidate` và chờ xác nhận.

## Điều kiện dừng

Nêu phạm vi và thứ tự đề xuất, chờ người dùng đồng ý rồi mới sang Workflow 03.
