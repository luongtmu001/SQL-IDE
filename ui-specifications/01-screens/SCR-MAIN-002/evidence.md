# Evidence — SCR-MAIN-002

| ID | Nhận định | Nhãn | Nguồn |
|---|---|---|---|
| EVD-E-001 | Header nguồn: 5 nút (connect, new group, filter DB, refresh, collapse) | code_verified | `index.html:419-441` |
| EVD-E-002 | Nhóm thư mục nhiều cấp, kéo-thả kết nối/nhóm | observed + code_verified | ảnh `main-full`; `explorer.js:2672-2791` |
| EVD-E-003 | Menu kết nối nguồn | observed | `ctx-explorer-connection.jpg` |
| EVD-E-004 | Registry menu theo 20+ loại node | code_verified | `context-menu.js:127-460` |
| EVD-E-005 | Badge đếm và nút lọc trên danh mục | code_verified | `explorer.js:1270-1360` |
| EVD-E-006 | Đích không có node nhóm | code_verified | `DatabaseNodeType.java`, `SidebarController` (không xử lý `group`) |
| EVD-E-007 | Đích hiện host căn phải gây cắt chữ + thanh cuộn ngang | observed | ảnh người dùng 2026-11-05 |
| EVD-E-008 | `ConnectionProfileDto.group()` có sẵn | code_verified | `SidebarController`, `ConnectionManagerController` |
