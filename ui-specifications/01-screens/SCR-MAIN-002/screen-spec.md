# Object Explorer — Screen Specification
# Đặc tả: Object Explorer

- Screen ID: `SCR-MAIN-002`
- Source: `templates/index.html:418-468`, `static/js/explorer.js`, `static/js/context-menu.js`, `static/css/explorer.css`
- Target: `sidebar.fxml`, `SidebarController`, `DatabaseTreeCell`, `LazyDatabaseTreeItem`, `DatabaseNodeType`
- Status: `implemented_mock` (Gate B duyệt 2026-11-05; chờ Gate C)
- Evidence: observed (ảnh `main-full`, `ctx-explorer-connection`, `ctx-explorer-group`, `main-sidebar-collapsed-rail`) + code_verified
- Last inspected: 2026-11-05

## 1. So sánh nguồn và đích

| # | Hạng mục | Nguồn | Đích hiện tại | Khoảng cách |
|---|---|---|---|---|
| 1 | Header | "OBJECT EXPLORER" + Kết nối mới, Nhóm mới, Lọc DB, Làm mới, Thu gọn | Icon + "Data…" (bị cắt) + Thêm, Lọc, Làm mới, Collapse All, **Expand All**, Thu gọn | Tiêu đề bị cắt; thiếu Nhóm mới; Expand All mở kết nối thật |
| 2 | Tìm kiếm | Ô lọc + nút xóa | Ô lọc + đếm kết quả + nút xóa | Gần đủ |
| 3 | **Nhóm kết nối** | Thư mục nhiều cấp (`group` = `A/B`), kéo-thả kết nối/nhóm | **Không có** — danh sách phẳng | Thiếu hoàn toàn |
| 4 | Dòng kết nối | Icon theo loại DB + `Tên (Loại)` + chấm trạng thái | Icon chung + tên đậm + host căn phải (bị cắt) | Khác; host gây cuộn ngang |
| 5 | Node danh mục | Nút lọc khi hover + badge đếm (`x of y`) | Không có đếm; lọc qua menu | Thiếu |
| 6 | Hover action | Nút ⋯ trên dòng | Không có | Thiếu |
| 7 | Menu kết nối | Connect/Reconnect, Profiler*, Activity*, Compare*, New Group, Edit, Delete | Connect, Disconnect, Reconnect, Filter DB, Edit, New Console, Refresh, Remove | Thiếu New Group; (*) ngoài phạm vi |
| 8 | Menu nhóm / vùng trống | New Connection, New Group, Rename, Delete, Refresh | Không có | Thiếu |
| 9 | Menu table/view/routine | Script As ▸ (CREATE/ALTER/DROP/SELECT/INSERT/UPDATE/DELETE ▸ Tab / Clipboard), Select Top 1000, Edit Data*, Count, Design*, Dependencies*, Refresh, Drop | Open Data 100, Count, DDL, Generate ▸ SELECT/INSERT/UPDATE/DELETE, Copy name, Refresh, Drop | Khác cấu trúc |
| 10 | Rail thu gọn | Icon kết nối + badge trạng thái | Icon kết nối | Gần đủ |
| 11 | Chevron | ▸ ▾ nhỏ | Tam giác mặc định | Đổi sang chevron |
| 12 | Cuộn ngang | Không | Có (do cột host) | Bỏ, dùng ellipsis + tooltip |

## 2. Đề xuất (Gate B)

**Header 28px:** chữ `OBJECT EXPLORER` (11px, semibold, chữ hoa, màu phụ) + 6 nút 22px: Kết nối mới · Nhóm mới · Lọc database · Làm mới · Thu gọn tất cả · Thu về rail. **Bỏ "Expand All"** (dễ mở kết nối thật).

**Cây (dòng 22px, thụt 14px, chevron Feather):**
- Nhóm: thư mục nhiều cấp theo `profile.group` tách bằng `/`; nhóm đứng trước, rồi kết nối, sắp xếp A→Z.
- Kết nối: chấm màu (màu connection của Phase 1) · icon loại DB (PostgreSQL / SQL Server / SQLite) · **tên** · loại DB chữ mờ. Host/port/user trong tooltip. Trạng thái: chưa kết nối (icon xám), đang kết nối (spinner), đã kết nối (icon màu + tên đậm), lỗi (icon đỏ + tooltip lỗi).
- Danh mục (Tables, Views…): badge số lượng; khi đang lọc hiện `x / y` và icon lọc.
- Dòng hover: nút ⋯ mở cùng menu chuột phải.
- Không cuộn ngang; chữ dài → `…` + tooltip.

**Context menu:** theo loại node như mockup (tiếng Việt, có phím tắt). Mục thuộc tính năng ngoài phạm vi (Profiler, Activity Monitor, Compare, Design, Edit Data, Dependencies) — xem Q3.

**Kéo-thả:** kết nối/nhóm vào nhóm khác (đổi `group`).

**Phím:** F2 đổi tên nhóm, Delete xóa (có xác nhận), Enter = kết nối/mở rộng, F5 làm mới node.

## 3. Câu hỏi Gate B

- **Q1 — Dữ liệu khi mở rộng kết nối trong prototype:** (a) cây mock (`DatabaseMockService`), không kết nối DB thật — đề xuất, đúng quy tắc trước Gate D; (b) giữ kết nối thật như hiện tại.
- **Q2 — Thao tác ghi cấu hình kết nối** (tạo/đổi tên/xóa nhóm, kéo-thả, lưu kết nối): (a) ghi thật vào file cấu hình kết nối cục bộ như dialog kết nối hiện tại đang làm; (b) chỉ trong bộ nhớ, mất khi tắt app.
- **Q3 — Mục ngoài phạm vi trong menu:** (a) ẩn; (b) hiện mờ kèm "(chưa hỗ trợ)" như menu Tools.
- **Q4 — Menu table:** (a) theo cấu trúc nguồn (Script As ▸ … ▸ Tab/Clipboard, Select Top 1000…) — đề xuất; (b) giữ cấu trúc JavaFX hiện tại.

## 4. Quyết định Gate B (2026-11-05)

- Q1 = **a**: mở rộng kết nối dùng dữ liệu mẫu (`MockMetadataProvider`), không kết nối DB. Bật lại dữ liệu thật ở Gate D bằng `-Dsqlide.mockMetadata=false`.
- Q2 = **a**: tạo / đổi tên / xóa nhóm, kéo-thả ghi vào cấu hình kết nối cục bộ (`ConnectionApi.updateConnection`, giữ nguyên mật khẩu). Nhóm rỗng lưu trong `java.util.prefs`.
- Q3 = **b**: mục ngoài phạm vi hiện mờ kèm "(chưa hỗ trợ)".
- Q4 = **a**: menu theo `context-menu.js` của LLTool (Script X as ▸ … ▸ New Query Editor Window / Clipboard).

## 5. Nhật ký triển khai

| Ngày | Thay đổi | File |
|---|---|---|
| 2026-11-05 | Node `GROUP`, cây nhóm nhiều cấp, nhóm trước rồi kết nối A→Z, nhớ nhóm đã thu gọn | `DatabaseNodeType`, `DatabaseNode.groupPath`, `SidebarController` |
| 2026-11-05 | Dịch vụ nhóm: tạo / đổi tên / xóa (kết nối về "chưa phân nhóm") / di chuyển nhóm / di chuyển kết nối | `ExplorerGroupService` |
| 2026-11-05 | Dữ liệu mẫu cho mọi fetch metadata + trạng thái kết nối (chưa / đang / đã / lỗi) | `MockMetadataProvider`, `DesktopMetadataService`, `ExplorerConnectionState` |
| 2026-11-05 | Dòng cây 22px: chấm màu, icon PostgreSQL/SQL Server/SQLite, loại DB mờ, badge số lượng (`x / y` khi lọc), nút lọc + ⋯ khi hover, tooltip chi tiết, không cuộn ngang, chevron | `DatabaseTreeCell`, `components.css` |
| 2026-11-05 | Context menu theo nguồn cho nhóm, vùng trống, kết nối, database, schema, danh mục, table, view, materialized view, procedure, function, column, trigger, sequence, type | `DatabaseTreeCell` |
| 2026-11-05 | Header: OBJECT EXPLORER + Kết nối mới, Nhóm mới, Lọc, Làm mới, Thu gọn tất cả, Thu về rail (bỏ Expand All); empty state "Chưa có kết nối" | `sidebar.fxml` |
| 2026-11-05 | Phím: Enter, F2, Delete, F5; kéo-thả kết nối/nhóm vào nhóm, thả vào vùng trống = bỏ nhóm | `SidebarController`, `DatabaseTreeCell` |
| 2026-11-05 | Double-click table/view và Select Top 1000 / Count: chỉ mở câu lệnh trong tab mới (không chạy thật trước Gate D) | `SidebarController`, `DatabaseTreeCell`, `MainController` |

## 6. Còn lại (Phase 2B — dialog)

- DLG-001 kết nối: chọn sẵn nhóm khi tạo từ menu nhóm.
- DLG-003 / DLG-004: dialog nhóm và xác nhận xóa hiện dùng hộp thoại JavaFX tạm.
- DLG-002, DLG-005, DLG-006: theo thiết kế VS Code.
