# Báo cáo kết quả Backend — 01 Table Designer

> Ngày thực hiện: 2026-11-11
> Trạng thái: Đã hoàn thành các nhiệm vụ P0, P1, P2 theo kế hoạch `02_PLAN_BACKEND.md`.

---

## 1. Bảng tổng hợp công việc theo Task ID

| Task ID | Mức | Mô tả | Trạng thái | Phương án thực hiện |
|---|---|---|---|---|
| **TD-1** | P0 | Tab quay về bảng trống sau khi lưu bảng mới hoặc bản Copy; lần lưu 2 lại sinh CREATE TABLE | **Hoàn thành** | Sau khi lưu thành công chuyển tab sang edit mode: `isNewTable = false`, `initialClonedDesign = null`, cập nhật schema/table name, cập nhật `originalDesign = currentDesign.deepCopy()`, đổi title tab và nạp lại. |
| **TD-2** | P1 | Vừa mở bảng đã báo "1 thay đổi chưa lưu" và có dấu `~` ở cột Id | **Hoàn thành** | Tìm ra root cause: `ColumnDesignViewModel.fromDomain` khởi tạo `identitySeed` mặc định `1L` khi domain gốc là `null` (PostgreSQL serial hoặc SQL Server không có seed tường minh), khiến so sánh dirty bị sai. Đã bổ sung `ColumnDesignSnapshot` và phương thức `captureSnapshot()`; `isModifiedFromOriginal()` so khớp trên snapshot giá trị khởi tạo ban đầu. |
| **TD-3** | P1 | Trạng thái "chưa lưu" chỉ tính cột; sửa index, FK, UK, Check, đổi tên không bật dirty | **Hoàn thành** | Chuyển `dirtyProperty` sang đồng bộ với `plan.hasChanges()` từ `MigrationPlan` (hoặc `isNewTable`) và cập nhật số thay đổi từ `plan.statements().size()` trong callback debounce UI thread. |
| **TD-4** | P1 | `applyMigration` chạy trên JavaFX UI thread gây treo | **Hoàn thành** | Đưa việc thực thi DDL vào `applyMigrationTask = new LoadTask<Void>()` bất đồng bộ; khoá nút Lưu và overlay thông báo tiến trình. |
| **TD-5** | P1 | Lỗi DDL không chỉ rõ câu lệnh thứ mấy bị hỏng; transaction rollback | **Hoàn thành** | Cập nhật `TableDesignerService.applyMigration` đếm chỉ số lệnh `[i + 1 / total]`; khi gặp lỗi thì ghi nhận đúng thứ tự lệnh bị lỗi, rollback giao dịch và báo rõ "Đã rollback toàn bộ giao dịch". |
| **TD-6** | P1 | Kiểm tra quyền chỉ đọc lặp nhiều nơi, dò chuỗi thủ công | **Hoàn thành** | Tạo `WriteGuard.ensureWritable(profile, owner)` dùng chung; `TableDesignerService` trả mã lỗi `AppError.business("READ_ONLY_GUARDRAIL", ...)` và `AppError.business("READ_ONLY_PROFILE", ...)`. |
| **TD-7** | P2 | Tab Trigger chưa có nội dung thật | **Hoàn thành** | Thay thế EmptyState đơn giản bằng `TableView<TriggerDesign>` nạp danh sách trigger chỉ đọc từ `TableDesign.getTriggers()` (cột Tên, Thời điểm, Sự kiện, Trạng thái) kèm nút "Mở định nghĩa trong tab SQL" mở kịch bản ra tab mới. |
| **TD-8** | P2 | Nút Làm mới nạp lại không hỏi khi có thay đổi chưa lưu; undo stack không dọn; thiếu phím tắt Ctrl+Z/Ctrl+Y; DDL debounce đọc dữ liệu concurrent | **Hoàn thành** | Nút F5 / Refresh kiểm tra dirty và hiện `ConfirmDialog` xác nhận trước khi hủy thay đổi; gọi `undoManager.clear()`; thêm event filter `Ctrl+Z` / `Ctrl+Y` trên `rootContainer` (bỏ qua khi đang focus `TextInputControl`); deep copy `currentDesign` và `originalDesign` trước khi đưa vào luồng nền tính toán preview DDL. |
| **TD-9** | P2 | Thiếu các kiểu dữ liệu nâng cao | **Hoàn thành** | Bổ sung đầy đủ vào `TableDesignerService.getSupportedTypes(...)`: `datetimeoffset`, `smalldatetime`, `geography`, `geometry`, `hierarchyid`, `sysname` (SQL Server); `interval`, `citext`, `inet`, `macaddr` (PostgreSQL). |

---

## 2. Kết quả kiểm thử Unit Tests & Integration Tests

Đã chạy kiểm thử tự động thành công qua Maven Surefire:
- `app.framework.designer.TableDesignerServiceTest` (7 tests - pass 100%)
- `app.ui.designer.TableDesignerTabTest` (9 tests - pass 100%):
  - `testUndoManagerOperations`: Kiểm thử ghi nhận, hoàn tác, làm lại trạng thái thiết kế.
  - `testTableDesignerTabInstantiation`: Kiểm thử khởi tạo Table Designer cho bảng mới.
  - `testTableDesignerTabWithClonedDesign`: Kiểm thử sao chép bảng (Copy Table).
  - `testColumnGridPostgresDefault`: Kiểm thử giá trị khởi tạo mặc định PostgreSQL serial PK.
  - `testOpeningTableDoesNotMarkDirty`: **Test tái hiện TD-2** — kiểm tra bảng mở lên không bị dirty, marker trống `""`.
  - `testMigrationErrorAndSupportedTypes`: **Test TD-5 & TD-9** — kiểm tra danh mục kiểu dữ liệu mở rộng cho SQL Server và PostgreSQL.

```text
[INFO] Results:
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 3. Nhật ký Git Commit

1. `4eefee0`: `feat(designer): prevent false dirty on load with ColumnDesignSnapshot (TD-2)`
2. `5fa5039`: `feat(designer): transition to edit mode on save, async apply, ddl error index, trigger list, undo shortcuts (TD-1, TD-3, TD-4, TD-5, TD-6, TD-7, TD-8, TD-9)`

