# Báo cáo kết quả Backend — 03 Object Content Search

> Ngày thực hiện: 2026-11-11
> Trạng thái: Đã hoàn thành toàn bộ các nhiệm vụ P1, P2 theo kế hoạch `02_PLAN_BACKEND.md`.

---

## 1. Bảng tổng hợp công việc theo Task ID

| Task ID | Mức | Mô tả | Trạng thái | Phương án thực hiện |
|---|---|---|---|---|
| **OS-1** | P1 | `executeQuery` nuốt mọi exception; lỗi khiến người dùng tưởng không có kết quả | **Hoàn thành** | Chuyển việc thực thi truy vấn sang `executeQueryWithWarnings`, thu thập lỗi theo từng loại đối tượng (Views/Procedures/Functions/Triggers) vào `warnings`. UI hiện `warnBanner` (vàng cảnh báo) với thông báo cụ thể khi có truy vấn bị lỗi một phần. |
| **OS-2** | P1 | Hủy không hủy: overlay ẩn nhưng tác vụ nền vẫn chạy và kết quả đè UI | **Hoàn thành** | Tích hợp `searchTask = new LoadTask<SearchResultWithWarnings>()`. Nút Hủy gọi `searchTask.cancel()`. Trong vòng lặp so khớp duyệt các đối tượng kiểm tra `Thread.currentThread().isInterrupted()`. |
| **OS-3** | P1 | Regex không hợp lệ hiện toast `PatternSyntaxException`; backtracking nặng gây treo luồng | **Hoàn thành** | (1) Kiểm tra cú pháp regex trước khi tìm: nếu sai cú pháp thì hiện `lblRegexError` màu đỏ ngay dưới thanh tìm kiếm với vị trí và mô tả lỗi; (2) Dùng `TimedCharSequence` bọc chuỗi kiểm tra thời gian thực thi (giới hạn 2000ms / đối tượng); nếu vượt ngưỡng ném `MatchTimeoutException` để bỏ qua đối tượng đó và ghi nhận vào cảnh báo. |
| **OS-4** | P1 | Không giới hạn kết quả: từ khoá phổ biến kéo toàn bộ định nghĩa về bộ nhớ | **Hoàn thành** | Áp dụng trần tối đa 500 đối tượng hoặc 5.000 vị trí khớp; mỗi đối tượng lưu tối đa 200 vị trí khớp. Vượt quá ngưỡng sẽ dừng và báo rõ "Đã cắt bớt...". |
| **OS-5** | P1 | PG so khớp `prosrc ILIKE` bỏ sót chữ ký hàm; SQL Server `LIKE` sai collation | **Hoàn thành** | (1) PostgreSQL: lọc trên biểu thức định nghĩa `COALESCE(pg_catalog.pg_get_functiondef(p.oid), p.prosrc)`, escape `\`, `%`, `_` kèm `ESCAPE '\'`, dùng `LIKE` khi phân biệt hoa thường và `ILIKE` khi không phân biệt; (2) SQL Server: thêm `COLLATE Latin1_General_CS_AS` hoặc `COLLATE Latin1_General_CI_AI` tương ứng; với regex: trích xuất chuỗi literal độ dài >= 3 ký tự để lọc sơ bộ ở tầng SQL Server / PG. |
| **OS-6** | P2 | Đối tượng mã hoá (`WITH ENCRYPTION`) có definition NULL bị bỏ qua im lặng | **Hoàn thành** | Thống kê số lượng đối tượng có `definition == null` (`encryptedCount`) và hiển thị ra footer status bar: `"· N đối tượng mã hóa bị bỏ qua"`. |
| **OS-7** | P2 | Lọc nhanh trong cây làm mất mục đang chọn; mỗi lần gõ dựng lại cây | **Hoàn thành** | Debounce 150ms khi nhập `quickFilterField`; lưu lại khóa `(type + "|" + schema + "|" + name).toLowerCase()` của đối tượng đang chọn để tự động khôi phục selection trên cây sau khi lọc. |

---

## 2. Kết quả kiểm thử Unit Tests

Đã chạy kiểm thử tự động thành công qua Maven Surefire:
- `app.ui.search.ObjectSearchServiceTest` (9 tests - pass 100%):
  - `testCompilePatternDefault`, `testCompilePatternCaseSensitive`, `testCompilePatternWholeWord`, `testCompilePatternRegex`.
  - `testExtractMatches`: Tọa độ dòng/cột 1-based, độ dài và snippet.
  - `testMockSearch`: Sắp xếp đúng thứ tự VIEW -> PROCEDURE -> FUNCTION -> TRIGGER.
  - `testSearchFilterTypes`: Lọc đúng danh mục loại đối tượng.
  - `testRegexTimeoutGuard`: **Test tái hiện OS-3** — chuỗi gây catastrophic backtracking bị ngắt bởi `MatchTimeoutException` trong vòng < 2 giây, không gây treo app.
  - `testSearchWithWarnings`: **Test OS-1, OS-4, OS-6** — kiểm tra kết quả trả về cấu trúc `SearchResultWithWarnings` gồm hits, warnings, encryptedCount, truncated flag.
- `app.ui.search.ObjectContentSearchTabTest` (1 test - pass 100%):
  - Kiểm thử khởi tạo và hiển thị `ObjectContentSearchTab` trên JavaFX Platform thread.

```text
[INFO] Results:
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 3. Nhật ký Git Commit

1. `282a956`: `feat(search): warning banners, async LoadTask cancel, regex timeout guard, limit cap and collation filters (OS-1, OS-2, OS-3, OS-4, OS-5, OS-6, OS-7)`

