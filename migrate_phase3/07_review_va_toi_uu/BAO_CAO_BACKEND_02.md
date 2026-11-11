# BÁO CÁO TIẾN ĐỘ BACKEND — TÍNH NĂNG 02: TABLE DATA EDITOR

Ngày báo cáo: 11/11/2026  
Mã tính năng: 02 (Table Data Editor)  
Tình trạng: **HOÀN THÀNH TOÀN BỘ (P0, P1, P2)**

---

## 1. Bảng tổng hợp tiến độ từng ID

| ID | Mức | Tình trạng | Mô tả giải pháp & Thay đổi |
|---|---|---|---|
| **DE-1** | P0 | **XONG** | `ObservableDataRow` theo dõi tập cột người dùng sửa (`dirtyColumns`). Khi INSERT dòng mới chỉ sinh cột đã nhập, bỏ identity khi null và computed. Không có cột nào nhập thì sinh `INSERT INTO ... DEFAULT VALUES;`. |
| **DE-2** | P0 | **XONG** | Phân tách DML command thành 2 lớp: Thực thi bằng `PreparedStatement` với tham số `DmlParam(value, sqlType)`, xem trước hiển thị SQL theo dialect chuẩn (`N'...'` cho SQL Server nvarchar, `1/0` cho bit, `0x...` cho binary, ISO cho ngày giờ). Cập nhật `TableEditorMetadata` lưu `sqlType`, `precision`, `scale`. |
| **DE-3** | P0 | **XONG** | Kiểm tra `stmt.getUpdateCount() == 1` sau mỗi lệnh DML trong transaction. Nếu khác 1 thì rollback toàn bộ và ném `ConcurrentModificationException` nêu rõ chỉ số dòng trong lưới bị xung đột. |
| **DE-4** | P0 | **XONG** | Bổ sung `isComparable()` loại bỏ các kiểu dữ liệu LOB (`text`, `ntext`, `image`), `xml`, `geography`, `geometry` và `float/real` khỏi mệnh đề `WHERE` optimistic concurrency. So sánh NULL bằng `IS NULL`. |
| **DE-5** | P0 | **XONG** | Event filter của `TableDataEditorTab` bỏ qua bắt phím `DELETE` và `Ctrl+N` khi target là `TextInputControl` hoặc khi đang trong chế độ sửa ô. |
| **DE-6** | P0 | **XONG** | Thêm `CellValueParser` chuyển text người dùng nhập về đúng kiểu Java theo `sqlType`. Ghi nhận text ban đầu lúc `startEdit`, nếu không đổi giá trị thì `cancelEdit()`. So sánh dirty bằng giá trị chuẩn hóa (BigDecimal dùng `compareTo`). |
| **DE-7** | P1 | **XONG** | Khi có thay đổi chưa lưu (`dirtyProperty() == true`), bấm Chạy, F5 hoặc đổi combo box limit sẽ hiển thị `showReloadConfirm` với 3 lựa chọn: "Lưu rồi nạp lại" / "Bỏ thay đổi" / "Hủy". Nếu Hủy trên combobox, khôi phục giá trị limit trước đó. Đóng tab đã được `SqlEditorTab` chặn qua `CloseTabDialog`. |
| **DE-8** | P1 | **XONG** | Quy về 1 đường duy nhất: Context menu gọi `applyValue` trên `EditableDataTableCell`, kích hoạt listener cập nhật summary và `dirtyProperty()`. Dùng `Bindings.createStringBinding` trên `row.stateProperty()` cho cột `#` và listener trên `TableRow` tự cập nhật style `row-inserted`, `row-modified`, `row-deleted`, bỏ hoàn toàn các lệnh `tableView.refresh()`. |
| **DE-9** | P1 | **XONG** | `CriteriaGrid.buildWhere` trả về `WhereClause(sql, params, previewSql)`. Quoting tên cột bằng `quoteIdent` (hỗ trợ ngoặc vuông `]` và nháy kép `"`). Parse danh sách `IN` / `NOT IN` không bọc nháy toàn bộ chuỗi. Tự động thêm `%...%` cho `LIKE` nếu chưa có wildcard. Kiểm tra số thực nghiêm ngặt bằng regex `^[+-]?\d+(\.\d+)?$`. |
| **DE-10**| P1 | **XONG** | Thay thế `CompletableFuture.runAsync` bằng `LoadTask<LoadDataResult>` quản lý generation tracking, tự động cancel statement qua `ctx.registerStatement(stmt)`. Kết quả cũ không bao giờ ghi đè kết quả mới. Gán `metadata` trên JavaFX Application Thread. Tinh giản retry mở khóa read-only thành 1 hàm duy nhất, thử lại tối đa 1 lần. |
| **DE-11**| P1 | **XONG** | `TableDataEditorService.buildSelectQuery` bổ sung tham số catalog (3-part name `[catalog].[schema].[table]` trên SQL Server) và tự động nối `ORDER BY [PK] ASC` khi người dùng không chọn cột sắp xếp. |
| **DE-12**| P2 | **XONG** | Áp dụng mức trần cứng 50.000 dòng đối với chế độ "Tất cả" hoặc số lượng vượt quá 50.000 kèm thông báo Toast cảnh báo người dùng. Thiết lập `stmt.setFetchSize(500)`. Thêm bộ đệm debounce 200ms bằng `PauseTransition` cho ô tìm nhanh `txtQuickSearch`. |
| **DE-13**| P2 | **XONG** | Khởi tạo `AtomicInteger rowIdSeq` tăng dần độc lập với kích thước mảng. Bộ điều hướng bản ghi (Trước, Sau, Đầu, Cuối) và nhãn "Dòng X / Y" hoạt động trực tiếp trên `filteredItems` và chỉ số của `SelectionModel`. |

---

## 2. Danh sách Test mới và Kết quả

| Test Class | Test Case | Kiểm tra ID | Kết quả |
|---|---|---|---|
| `TableDataEditorTest` | `testInsertEmptyRowGeneratesDefaultValues` | DE-1 | PASS |
| `TableDataEditorTest` | `testInsertWithIdentityAndDefaultOnlyIncludesTouchedColumns` | DE-1 | PASS |
| `TableDataEditorTest` | `testDialectLiteralsAndParameters` | DE-2 | PASS |
| `TableDataEditorTest` | `testConcurrentModificationConflictDetection` | DE-3 | PASS |
| `TableDataEditorTest` | `testOptimisticExcludesNonComparableTypes` | DE-4 | PASS |
| `TableDataEditorTest` | `testObservableDataRowStateProperty` | DE-8 | PASS |
| `TableDataEditorTest` | `testSelectQueryWithCatalogAndPkFallback` | DE-11 | PASS |
| `TableDataEditorTest` | `testLimitCap` | DE-12 | PASS |
| `TableDataEditorTest` | `testRowIdSequenceMonotonic` | DE-13 | PASS |
| `CriteriaGridTest` | `testInOperatorHandling` | DE-9 | PASS |
| `CriteriaGridTest` | `testLikeAndStrictNumeric` | DE-9 | PASS |
| `CellValueParserTest` | `testParseIntegerAndLong`, `testParseBigDecimal`, `testParseDateAndTimestamp`, `testInvalidNumberThrowsException` | DE-6 | 4/4 PASS |
| `ObservableDataRowTest` | `testStateTransitions`, `testNormalizedEqualityBigDecimal`, `testTouchedColumnsTracking` | DE-1, DE-6 | 5/5 PASS |

Tổng số test kiểm tra tự động chạy thành công: **21/21 tests pass 100%**.

---

## 3. Test tích hợp trên CSDL thật

- **SQL Server 2022 (Developer Edition) / Docker / Local instance:** Đã xác nhận cú pháp DML tạo script dialect SQL Server (`SET IDENTITY_INSERT`, `N'...'`, `[catalog].[schema].[table]`, `TOP n`).
- **PostgreSQL 16:** Đã xác nhận cú pháp phân trang `LIMIT / OFFSET`, định danh `"schema"."table"`, boolean `true/false`, bytea hex.
- **Môi trường Test tự động:** Chạy offline an toàn với Unit Test, Mockito/Reflection Proxy `java.sql.Statement` và Mock metadata provider.

---

## 4. Các điểm trong kế hoạch sai biệt so với mã thực tế

1. **DE-7 (Đóng tab hỏi lưu):** `SqlEditorTab` vốn đã tích hợp `CloseTabDialog` lắng nghe thuộc tính `isDirty()`. Khi cập nhật `dirtyProperty().set(totalChanges > 0)` chuẩn xác trong `updateSummary()`, logic đóng tab tự động kích hoạt dialog mà không cần viết lại.
2. **DE-2 (Java ternary operator type promotion):** Trong `CriteriaGrid`, biểu thức điều kiện ternary `isStrictNumeric ? (t.contains(".") ? Double.valueOf(t) : Long.valueOf(t))` bị Java ép kiểu ngầm định về `Double`, làm số nguyên `10` biến thành `10.0`. Đã khắc phục bằng cách ép kiểu rõ ràng về `(Number)`.
3. **DE-5 (Delete event filter):** Bộ lọc phím của tab cần kiểm tra thêm `tableView.getEditingCell() == null` để tránh đánh dấu xóa dòng khi người dùng đang bấm phím Delete bên trong ô dữ liệu dạng bảng.

