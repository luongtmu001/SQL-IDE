# Status Bar — Screen Specification
# Đặc tả: Thanh trạng thái

- Screen ID: `SCR-MAIN-005`
- Source: `#ide-editor-status-bar` (index.html:1107), `#ide-result-footer` (index.html:1442)
- Target: `status-bar.fxml`, `StatusBarController`, `layout.css .status-bar`
- Status: `implemented_mock` (Gate B duyệt 2026-11-05)
- Evidence: observed + code_verified
- Last inspected: 2026-11-05

## 1. Tổng quan

Nguồn **không có status bar toàn cửa sổ**. Có 2 thanh riêng:
- **Status bar của editor** (giữa editor và results): zoom (20–400%), số lỗi cú pháp, badge ENV / READ ONLY / TX OPEN / SPID; bên phải Ln/Ch, SPACES:4 ↔ TABS, CRLF ↔ LF, UTF-8.
- **Footer của results**: số dòng, thời gian (màu theo tốc độ), dung lượng, kết nối/DB, sparkline 5 lần chạy, nút copy JSON metrics.

Đích có status bar toàn cục kiểu VS Code (24px) nhưng toàn **dữ liệu giả cố định** ("Ready", "PostgreSQL 16 (localhost:5432/postgres)", "console_1.sql", "CRLF"), thêm nhãn theme, và thiếu zoom, lỗi cú pháp, badge an toàn.

## 2. Đề xuất (Gate B)

Một status bar toàn cục **22px** (gộp status bar editor; footer results vẫn giữ trong panel Results, làm ở Phase 4).

**Trái → phải:**

| Mục | Nội dung | Click | Nguồn |
|---|---|---|---|
| Kết nối | `●` + tên profile của tab (xanh = đã kết nối, xám = chưa, vàng = đang chạy) | mở Connection Manager | footer conn |
| PID | `PID 449759` | copy | badge SPID |
| Lỗi | `ⓧ 0 ⚠ 0` | mở tab Errors | issues |
| READ ONLY / TX OPEN | chỉ hiện khi có | — | badge an toàn |
| Lần chạy cuối | `✓ 12 rows · 101 ms` / `✗ lỗi` / spinner khi đang chạy | mở tab Results | footer |
| *(spacer)* | | | |
| Con trỏ | `Ln 2, Col 108` (+ `(15 selected)`) | Go to Line (Ctrl+G) | Ln/Ch |
| Thụt lề | `Spaces: 4` / `Tab Size: 4` | đổi Spaces ↔ Tabs | SPACES:4 |
| Encoding | `UTF-8` | — | UTF-8 |
| EOL | `LF` / `CRLF` | đổi LF ↔ CRLF | CRLF |
| Ngôn ngữ | `SQL` | — | — |
| Zoom | `90%` | menu 50–200% + Reset | zoom box |
| Thông báo | chuông + số | mở danh sách thông báo | — |

**Bỏ:** nhãn theme (đã có nút theme trên title bar), tên tab (đã có trên title bar), chuỗi kết nối cố định. Không lặp badge ENV (đã ở toolbar).

**Mỗi mục:** padding 0 8px, hover nền `surface-hover`, không dùng Separator dọc (VS Code không có).

**Q2 — Màu status bar:**
1. Trung tính: nền `surface-2`, chữ `text-secondary`, viền trên hairline.
2. Màu nhấn: nền `accent`, chữ `on-accent` (VS Code cổ điển).
3. **Trung tính, chuyển đỏ (`danger-emphasis`) khi tab đang kết nối môi trường PROD** — đề xuất, vì là cảnh báo an toàn rõ nhất cho thao tác trên production.

Mọi giá trị trong prototype lấy từ mock/state UI, không gọi DB.

## 3. Quyết định đã duyệt (Gate B, 2026-11-05)

- Q2 = **màu theo connection đang chọn, mỗi connection một màu, mặc định xanh** (`#007acc`). Bảng 9 màu trong `ConnectionColorService`; chọn bằng cách bấm mục connection trên status bar → "Màu thanh trạng thái". Lưu trong `java.util.prefs`, theo id profile; không ghi vào profile.
- Status bar bo góc 6px, margin 6px (kiểu Bo góc); kiểu Phẳng: trải ngang, không bo.

## 4. Nhật ký triển khai

| Ngày | Thay đổi |
|---|---|
| 2026-11-05 | `status-bar.fxml` + `StatusBarController` viết lại: kết nối (chấm + tên), PID*, lỗi/cảnh báo, READ ONLY, TX OPEN*, lần chạy cuối; Ln/Col, Spaces/Tab Size, UTF-8, LF/CRLF, SQL, zoom 50–200% (Ctrl+= / Ctrl+- / Ctrl+0), chuông |
| 2026-11-05 | Thông báo ngắn hiện 5 s rồi trả về tóm tắt lần chạy |

\* chưa có nguồn dữ liệu nên đang ẩn.

## 5. Hoãn

- Đổi Spaces/Tabs và LF/CRLF trực tiếp (Phase 3, cần bridge Monaco). Hiện bấm Spaces → mở Settings; LF chỉ hiển thị.
- Danh sách thông báo khi bấm chuông (Phase 6, DLG-011).
- PID / TX OPEN cần dữ liệu phiên (Gate D).
