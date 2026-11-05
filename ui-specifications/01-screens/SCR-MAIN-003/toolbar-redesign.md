# Editor Context Bar — Redesign (approved)
# Thanh ngữ cảnh soạn thảo — phương án đã duyệt

- Part of: `SCR-MAIN-003` (vùng soạn thảo). Bản đặc tả đầy đủ của SCR-MAIN-003 sẽ làm ở bước 4 theo thứ tự đã duyệt.
- Status: `implemented_mock`
- Approval: bạn duyệt ngày 2026-11-04 23:46 ("duyệt phương án, hãy bắt đầu")
- Evidence: nguồn `templates/index.html#ide-editor-pane` (breadcrumb `Local › Bravo10Setup › dbo`, `All`, `Run`, debug, `Stop`, `⋯`), ảnh `00-application/screenshots/source/main-full-1456x816.jpg`, `actionbar-overflow-menu.jpg`

## Bố cục

```
[● server  connection ▾  STAGING] › [db  database ▾] › [layers  schema ▾]  ======  Giới hạn: [All ▾] [▶ Run] [■ Stop] | [plan] [format] [refresh] [⋯]
```

| ID | Control | Nguồn | Đích | Ghi chú |
|---|---|---|---|---|
| CMP-CTX-001 | Connection selector + icon loại DB + env badge | breadcrumb connection | `connectionComboBox`, `connectionTypeIcon`, `toolbarEnvBadge` | env lấy từ profile kết nối |
| CMP-CTX-002 | Database selector | breadcrumb database | `databaseComboBox` (mới) | đồng bộ `GlobalDesktopState.activeCatalog` và database của tab; không truy vấn DB thật |
| CMP-CTX-003 | Schema selector | breadcrumb schema | `schemaComboBox` | giữ nguyên dữ liệu mock |
| CMP-CTX-004 | Giới hạn dòng | `All` | `limitComboBox` | lưu vào `sql.maxRows` như cũ |
| BTN-CTX-001 | Run (nút chính duy nhất) | `#ide-btn-run` | `executeButton` | F5 / Ctrl+Enter |
| BTN-CTX-002 | Stop | `#ide-btn-stop` | `stopButton` | chỉ bật khi đang chạy |
| BTN-CTX-003 | Plan | `#ide-btn-plan` | `planToolbarButton` (icon) | Ctrl+L |
| BTN-CTX-004 | Format | `#ide-btn-format` | icon | Ctrl+Alt+L |
| BTN-CTX-005 | Refresh objects | Refresh | icon → `handleRefreshObjects` | Ctrl+Shift+R |
| BTN-CTX-006 | Menu ⋯ | `#ide-btn-action-overflow` | `toolbarOverflowButton` | New, Open, Save, Connection Manager, BRAVO Tool (BRAVO ẩn khi add-on tắt) |

## Ngoài phạm vi lần này

Nút Debug, Transaction (Commit/Rollback), Parse, Import/Export, Compare, Syntax reference, History, File history, Object search của nguồn sẽ được ghi đầy đủ trong `components.json` của SCR-MAIN-003.

## Cập nhật 2026-11-05 00:05

- Theo yêu cầu: **bỏ badge môi trường trên title bar**, chỉ giữ ở ô kết nối của thanh ngữ cảnh.
- Thanh ngữ cảnh chỉnh theo mockup đã duyệt (`screenshots/approved-mockup.png`):
  - Các ô kết nối / database / schema có nền nhạt, góc bo 5 px.
  - "Giới hạn: All ▾" nằm chung một khung viền.
  - Run có màu nhấn; Stop hiện chữ mờ khi không chạy.
  - Menu ⋯ có phím tắt căn phải.
- Kết quả trên app: `screenshots/target-context-bar.jpg`, `target-context-overflow.jpg`, `target-titlebar-no-badge.jpg`.
