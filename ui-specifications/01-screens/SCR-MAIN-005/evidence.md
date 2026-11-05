# Evidence — SCR-MAIN-005

| ID | Nhận định | Nhãn | Nguồn |
|---|---|---|---|
| EVD-SB-001 | Status bar editor nguồn: zoom, issues, badge ENV/READ ONLY/TX/SPID, Ln/Ch, SPACES, EOL, UTF-8 | code_verified + observed | `index.html:1107-1171`, ảnh `main-full` (90 %, No issues found, PID 449759, Ln: 2, Ch: 108, TABS, LF, UTF-8) |
| EVD-SB-002 | Zoom nguồn 20–400% | code_verified | `index.html:1114-1124` |
| EVD-SB-003 | Footer results: rows, time badge, size, conn, sparkline, copy JSON | code_verified + observed | `index.html:1442-1465`, ảnh (12dòng · 101 ms · 8.6 KB · #conn…) |
| EVD-SB-004 | Status bar đích 24px, dữ liệu giả cố định, có nhãn theme và Separator | code_verified | `status-bar.fxml`, `StatusBarController.initialize`, `layout.css:173` |
| EVD-SB-005 | Nút chuông đích chưa có handler | code_verified | `status-bar.fxml` (Button không onAction) |
