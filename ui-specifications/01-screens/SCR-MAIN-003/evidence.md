# Evidence Log — Editor workspace
# Nhật ký bằng chứng — Vùng soạn thảo

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| EVD-ED-R01 | Nguồn: breadcrumb `Local › Bravo10Setup › dbo`, `All`, Run, Stop, ⋯; tab strip; editor status bar (90 %, PID 449759, Ln 2 Ch 108, TABS, LF, UTF-8) | `main-full-1456x816.jpg` | observed |
| EVD-ED-R02 | Nguồn: banner vàng "Kết nối / Đổi kết nối" khi tab chưa kết nối | `main-disconnected-tab-1456x816.jpg` | observed |
| EVD-ED-R03 | Nguồn: menu overflow của action bar | `actionbar-overflow-menu.jpg` | observed |
| EVD-ED-R04 | Đích: thanh ngữ cảnh theo mockup, menu ⋯, title bar không còn badge | `target-context-bar.jpg`, `target-context-overflow.jpg`, `target-titlebar-no-badge.jpg` | observed |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-ED-C01 | `fxml/editor.fxml` | `primaryTabPane / secondaryTabPane / emptyEditorStateBox / tabHeaderActions` | Cấu trúc đích: SplitPane + 2 TabPane + trạng thái rỗng + cụm nút phải (Format, Split ×2, Đóng split) | code_verified |
| EVD-ED-C02 | `ui/editor/SqlEditorTab.java` | `setupContent / updateConnectionBanner` | Banner "Chưa kết nối" + Kết nối / Đổi kết nối / Ẩn; ẩn khi connected null hoặc true | code_verified |
| EVD-ED-C03 | `ui/editor/SqlEditorTab.java` | `setupContextMenu` | Menu tab 14 mục, 2 mục deferred | code_verified |
| EVD-ED-C04 | `controllers/EditorController.java` | `handleTabAction` | 11 loại hành động tab | code_verified |
| EVD-ED-C05 | `ui/editor/MonacoEditorHost.java` | `attachJavaBridge` | Bridge: onEditorReady, onContentChanged, onCursorPositionChanged, onExecuteRequested; hàng đợi readyCallbacks | code_verified |
| EVD-ED-C06 | `ui/editor/EditorTabStrip.java` | `SHOW_ALL, rebuild, showTabList` | Một hàng cuộn hoặc nhiều hàng (Preferences); danh sách tab | code_verified |
| EVD-ED-C07 | `controllers/MainController.java` | `lines 1246, 1305, 1564` | Run đi qua DebugExtensionDialog → ProductionSafetyDialog → ActionsGuardDialog | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-ED-001 | Control bật/tắt chế độ nhiều hàng (`SHOW_ALL`) nằm ở đâu | Đọc `EditorTabStrip` (chỉ thấy property), chưa tìm nơi gọi | Thấp | Không |
| UNK-ED-002 | Điều kiện dùng `SqlCodeArea` thay Monaco | Chỉ thấy chú thích "fallback" | Thấp | Không |
| UNK-ED-003 | Phím tắt tab mới / đóng tab ngoài Ctrl+W, Ctrl+S | Chưa đọc bảng accelerator của MainController | Thấp | Không |
| UNK-ED-004 | Chế độ diff và các nút action bar nguồn (Debug, Tx, Parse…) | Đã hoãn theo phạm vi | Trung bình | Không |
| UNK-ED-005 | Ảnh đích của dải tab, banner, chia đôi | Chưa chụp | Trung bình (chặn đóng Gate C của màn hình này) | Không chặn spec, chặn review |

## Safety notes / Lưu ý an toàn

Nút Run, phím chạy trong Monaco và "Kết nối" ở banner có thể chạm DB thật khi backend = real. Chỉ thử trên DB thử; mặc định `sqlide.readOnly=true`.
