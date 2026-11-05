# Giai đoạn 7 — Review tổng (code review + đối chiếu danh mục)
# Phase 7 — Overall review

- Ngày: 2026-11-05
- Phạm vi: 21 màn hình / bề mặt trong `00-application/screen-inventory.md` (màn hình chính, Settings, dialog).
- Cách làm (bạn chọn ngày 2026-11-05): **review code + đối chiếu danh mục**, không tự điều khiển máy để chụp. Phần so ảnh nguồn / đích làm khi bạn gửi ảnh.
- Kết luận tạm: **PASS_WITH_KNOWN_GAPS**. Chưa ghi `PASS`, vì chưa so ảnh và chưa có đặc tả cho một số màn hình (xem mục 4).

## 1. Độ phủ màn hình / Coverage

| ID | Màn hình | Trạng thái | Đích JavaFX chính | Ghi chú |
|---|---|---|---|---|
| SCR-SHELL-001 | Title bar + menu | approved (Gate C tạm) | `titlebar.fxml`, `TitleBarController`, `WindowsWindowAdapter` | |
| SCR-MAIN-001 | Bố cục workspace | approved | `main-window.fxml`, `MainController`, `RoundedCard` | còn T7 |
| SCR-MAIN-002 | Object Explorer | approved | `sidebar.fxml`, `SidebarController`, `DatabaseTreeCell` | còn T8 |
| SCR-MAIN-003 | Vùng soạn thảo | approved | `EditorTabStrip`, `SqlEditorTab`, `EditorController`, `MonacoEditorHost` | |
| SCR-MAIN-004 | Bảng kết quả | approved | `results.fxml`, `ResultsController`, `GridFilterState`, `ColumnFilterPopup` | còn T11; T10 để Gate D |
| SCR-MAIN-005 | Thanh trạng thái | approved | `status-bar.fxml`, `StatusBarController` | |
| SCR-SET-001 | Cài đặt | approved | `SettingsDialog`, `SettingsDialogController`, `SettingsRowStyler`, `EmbeddedMonaco` | |
| DLG-001 | Quản lý kết nối | approved | `ConnectionManagerDialog`, `ConnectionManagerController` | còn T9 |
| DLG-002 | Hỏi mật khẩu | approved | `PasswordPromptDialog` | |
| DLG-003 | Tên nhóm | approved | `GroupNameDialog` | |
| DLG-004 | Xác nhận xóa | approved | `ConfirmDialog` | |
| DLG-005 | Lọc database | approved | `DatabaseFilterDialog` | |
| DLG-006 | Lọc đối tượng | approved | `ObjectFilterDialog` | |
| DLG-007a/b | Actions Guard / cảnh báo PRODUCTION | approved | `ActionsGuardDialog`, `ProductionSafetyDialog` | |
| DLG-008 | Xác nhận format | approved | `FormatConfirmDialog` | |
| DLG-009 | Quản lý code style | approved | `CodeStyleDialog`, `SqlStyleStore`, `SimpleSqlFormatter` | bộ format thật: Gate D |
| DLG-010 | Debug extension | approved | `DebugExtensionDialog` | Run Debug hiện mờ |
| DLG-011 | Popup thông báo | approved | `ToastHost`, `NotificationService` | |
| DLG-012 | About | approved | `AboutDialog`, `AppInfo` | |
| DLG-013 | Kiểm tra cập nhật | approved (mock) | `UpdateDialog` | không gọi mạng |
| DLG-014 | Lớp phủ đang tải | approved | `LoadingOverlay` | mới gắn vào Results |

**Tổng:** 21/21 màn hình có đích JavaFX và đã qua Gate C. Không còn `Alert` / `Dialog<>` / `TextInputDialog` hệ thống nào trong code.

## 2. Lỗi tồn đọng đã thử sửa trong GĐ 7 — **chưa đạt** (bạn kiểm 2026-11-05)

| # | Giả thuyết nguyên nhân | Cách đã thử |
|---|---|---|
| T7 | Divider của SplitPane chỉ cao 2px, khe 4px phía trên là padding của editor, không bắt chuột | Khe dưới editor thành thanh kéo thật (`MainController.installEditorSash`, `.editor-sash`), kéo được, sáng màu nhấn khi hover / kéo |
| T8 | `TreeCellSkin` chừa 18px cho dòng lá (không có mũi tên) nhưng lấy đúng bề rộng của mũi tên (16px) cho dòng có con, nên icon của dòng lá lệch phải 2px so với thư mục | Ô mũi tên rộng đúng 18px trên mọi dòng (`components.css`) |
| T9 | AtlantaFX vẽ viền bằng lớp nền ngoài và lớp nền trong lùi vào 1px (bán kính 4px, 3px). `shape.css` đặt một bán kính chung 6 cho cả hai lớp, nên góc trong quá lớn và viền trông dày ở góc | Bán kính 2 lớp `6, 5` cho ô nhập, ô chọn, nút; checkbox `4, 3`; ô hiển thị giá trị trong ô chọn không tự vẽ nền; ô chọn có viền trên toolbar ngữ cảnh đổi 4 → 6px và vuông khi phẳng |
| T11 | Lưới xếp chồng dùng `setFixedCellSize(24)`. Khi có fixed cell size, `TableRowSkin` gỡ các ô của cột đã cuộn khỏi tầm nhìn, nên ô # bị gỡ dù đã dịch theo thanh cuộn. Lưới đơn không dùng fixed cell size nên không lỗi | Bỏ `setFixedCellSize`, chiều cao dòng 24px chuyển sang CSS (`.stacked-grid .table-row-cell`) |

Kết quả: cả 4 lỗi **vẫn còn** khi bạn chạy thử (2026-11-05). Các bản sửa dựa trên đọc code và CSS của AtlantaFX / JavaFX, không có bằng chứng chạy thật, nên giả thuyết chưa đúng hoặc chưa đủ. Chuyển sang D0.2 của `phase8-plan.md`: sửa lại với log gỡ lỗi giao diện hoặc ảnh chụp.

## 3. An toàn prototype (chỉ dữ liệu mẫu)

| Kiểm tra | Kết quả |
|---|---|
| Metadata (Explorer, Connection Manager) | ✅ 12/12 hàm `fetch*` trả dữ liệu mẫu khi `MockMetadataProvider.isEnabled()` (mặc định bật, `-Dsqlide.mockMetadata=false` để tắt) |
| Kiểm tra kết nối | ✅ mock |
| **Chạy câu lệnh (Run)** | ⚠️ **Phát hiện và đã chặn.** `DesktopQueryExecutionService` tự mở kết nối theo tên profile của tab và chạy SQL thật trên server, kể cả profile PROD. Nay khi đang mock, mọi câu lệnh chỉ chạy trên SQLite trong bộ nhớ (sandbox). Chạy thật: Gate D |
| **Execution plan** | ⚠️ **Phát hiện và đã chặn.** Plan "actual" chạy câu lệnh trên kết nối đang mở. Nay khi đang mock luôn dùng plan mô phỏng |
| Kiểm tra cập nhật | ✅ không gọi mạng |
| Gọi HTTP khác trong code app | ✅ không có |

## 4. Khoảng trống còn lại (known gaps)

| # | Khoảng trống | Mức | Đề xuất |
|---|---|---|---|
| G1 | Chưa so ảnh nguồn / đích cạnh nhau (Gate 4 của skill) | Trung bình | Bạn chụp LLTool và SQL-IDE cùng cỡ cửa sổ (1456×816) cho: màn hình chính, Explorer + menu chuột phải, Quản lý kết nối, Settings, Code style, lưới nhiều bảng, About / cập nhật. Tôi so và ghi chênh lệch |
| G2 | Thiếu bộ đặc tả `01-screens/<ID>/` cho SCR-MAIN-004, SCR-SET-001 và DLG-001…014 (hiện quyết định nằm trong `migration-plan.md`) | Trung bình | Sinh `screen-spec.md` + `mapping.json` từ code và quyết định Gate B đã chốt |
| G3 | `LoadingOverlay` mới gắn cho Results; tải metadata và kết nối chưa dùng | Thấp | Gắn khi làm Gate D (thời gian chờ thật) |
| G4 | T10 nguồn bảng kết quả | Thấp | Gate D |
| G5 | `ResultsController` (~90 KB) và `MainController` (~67 KB) quá lớn | Thấp | Tách menu lưới, multi-grid, plan thành lớp riêng trước Gate D |
| G6 | `FrozenColumnSupport.install` gắn lại listener mỗi lần đổ dữ liệu vào cùng bảng | Thấp | Gắn một lần cho mỗi bảng |
| G7 | T6 commit Git | — | Chờ bạn đồng ý |

## 5. R-UI-01

Đã quét toàn bộ CSS: 35 luật còn bán kính cố định mà không có luật phẳng tương ứng (ô tìm kiếm, badge, nút toolbar ngữ cảnh, đáy bảng kết quả, nút đóng dialog, ô nhập lỗi…). Đã thêm 33 luật vào `shape-flat.css`; 2 chấm trạng thái hình tròn giữ nguyên.

## 6. Xác nhận

- Không tích hợp backend / logic nghiệp vụ thật. Hai chỗ có thể chạm DB thật (Run, plan) đã được chặn trong chế độ mock.
- Không có trạng thái `approved` nào được ghi mà không có xác nhận Gate C của bạn.
