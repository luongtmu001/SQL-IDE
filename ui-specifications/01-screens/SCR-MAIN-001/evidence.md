# Evidence — SCR-MAIN-001

| ID | Nhận định | Nhãn | Nguồn |
|---|---|---|---|
| EVD-M1-001 | Nguồn dùng panel phẳng, không có khe giữa panel | observed | `00-application/screenshots/source/main-full-1456x816.jpg` |
| EVD-M1-002 | Sidebar nguồn rộng 260px (style inline) | code_verified | `templates/index.html:418` |
| EVD-M1-003 | Rail thu gọn là phần tử riêng | code_verified | `index.html:457`, ảnh `main-sidebar-collapsed-rail.jpg` |
| EVD-M1-004 | Sash nguồn 6px, đổi màu accent khi hover/kéo | code_verified | `app.css:157-173`, `explorer.css:48-67` |
| EVD-M1-005 | Đích dùng card bo 8px, khe 4px | code_verified | `layout.css` (.main-split-pane, .sidebar-panel…) |
| EVD-M1-006 | Divider đích theo tỉ lệ 0.22 / 0.038 / 0.65 | code_verified | `LayoutService.java:59-63`, `main-window.fxml:287-292` |
| EVD-M1-007 | Phím tắt hiện có: Alt+1, Ctrl+R, Ctrl+Alt+R, Ctrl+Shift+U, Ctrl+Shift+F12, Ctrl+Shift+F10 | code_verified | `main-window.fxml:129-193` |
| EVD-M1-008 | Ảnh đích mới nhất (sau đổi toolbar) chưa chụp | unknown | cần chụp lại ở Gate C |
