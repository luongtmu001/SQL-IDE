# Báo Cáo Tiến Độ Backend Tính Năng 04: Schema Compare

> Ngày thực hiện: 2026-11-11
> Người thực hiện: Antigravity (Google DeepMind pair programming agent)
> Đối chiếu kế hoạch: `migrate_phase3/07_review_va_toi_uu/02_PLAN_BACKEND.md` — Mục 3.4

---

## 1. Bảng Trạng Thái Chi Tiết Từng ID

| ID | Mức | Mô tả lỗi gốc | Trạng thái | Giải pháp & Thay đổi phương án (nếu có) |
|---|---|---|---|---|
| **SC-1** | P0 | So sánh luôn dùng `createMockSnapshot(...)`, bỏ qua combo endpoints | **XONG** | Xây dựng `SchemaSnapshotLoader.load(profile, catalog, schema, isSource, progressCallback)`. Quét danh sách bảng, tải song song `TableDesign` qua pool tối đa 4 kết nối, nạp Views/Procedures/Functions/Triggers qua `ObjectDefinitionReader`. Chế độ mock chỉ kích hoạt khi `BackendFlags.mock(METADATA)` hoặc `MockMetadataProvider.isEnabled()`. |
| **SC-2** | P0 | Dữ liệu giả lọt vào chế độ thật (`B10_PhucLoi`, `BravoSetup`, `master`, `dbo/sales/audit`) | **XONG** | Xóa bỏ toàn bộ hardcoded profiles/databases/schemas. Khi rỗng kết nối hiển thị `EmptyState` ("Không có kết nối nào trong hệ thống..."). Lỗi lấy database/schema báo warning và clear danh sách. `grep -r "B10_PhucLoi\|BravoSetup" src/main/java/app/ui/compare` = 0. |
| **SC-3** | P0 | Nguồn và đích khác loại CSDL ném lỗi sau khi chạy; nguồn = đích vẫn cho chạy | **XONG** | Kiểm tra validation trước khi chạy: kiểm tra profile null, chuẩn hóa và kiểm tra loại engine (`srcEngine.equals(tgtEngine)`), kiểm tra trùng toàn bộ endpoint (cùng profile ID, cùng database, cùng schema). Cảnh báo trực tiếp và dừng trước khi tải. |
| **SC-4** | P1 | Script sinh sai cú pháp cho PostgreSQL và SQL Server | **XONG** | Lưu trữ `parentTable` cho Trigger và `identityArgs` cho Function trong `SchemaSnapshot`. PG: `DROP TRIGGER IF EXISTS "trg" ON "schema"."table"`, `DROP FUNCTION IF EXISTS "schema"."fn"(identityArgs)`, `DROP VIEW IF EXISTS "schema"."view" CASCADE`, sửa view PG dùng DROP CASCADE + CREATE. SQL Server: hỗ trợ `DROP ... IF EXISTS [schema].[name]`, `CREATE OR ALTER`. Tên đối tượng quote bằng dialect. |
| **SC-5** | P1 | So sánh mã bằng `cleanCode` gộp khoảng trắng làm mất khoảng trắng trong string literal; bỏ qua hoa/thường; function overload PG đè nhau | **XONG** | Hàm `normalizeCode` bảo toàn 100% nội dung bên trong chuỗi nháy đơn `'...'` (cả khoảng trắng và hoa/thường). So sánh phân biệt hoa/thường (`.equals()`). Hỗ trợ cấu hình `ignoreWhitespace` ngoài literal và `ignoreComments`. Function overload PG lưu key dạng `name(identityArgs)`. |
| **SC-6** | P1 | Thứ tự lệnh trong migration script không theo phụ thuộc | **XONG** | Script chia thành 2 Phase rõ ràng:<br>• **Phase 1: DROPS** (thứ tự ngược phụ thuộc: Triggers → Procedures → Views → Functions → Tables).<br>• **Phase 2: CREATES & ALTERS**:<br>  - 2.1: CREATE TABLES (không chứa FK inline để tránh lỗi khi bảng đích chưa tồn tại).<br>  - 2.2: ALTER TABLES (các cột, chỉ mục, check constraint).<br>  - 2.3: FOREIGN KEYS (áp dụng các lệnh `ADD CONSTRAINT ... FOREIGN KEY` sau khi tất cả các bảng đã tồn tại).<br>  - 2.4: FUNCTIONS.<br>  - 2.5: VIEWS (sắp xếp theo phụ thuộc lẫn nhau).<br>  - 2.6: PROCEDURES.<br>  - 2.7: TRIGGERS. |
| **SC-7** | P1 | Nhãn và hành động mở tab SQL trỏ sai kết nối do đọc live combobox; đảo chiều bị kích hoạt handler xóa selection | **XONG** | Tạo `CompareEndpoint(ConnectionProfileDto profile, String catalog, String schema)`. Chốt snapshot endpoints tại thời điểm bấm "So sánh" và gắn vào kết quả. Mọi header, title, và nút "Mở vào tab SQL" đều dùng captured endpoints. `swapEndpoints()` dùng cờ `isSwapping` hoán đổi items + values sạch sẽ không kích hoạt fetch handler. Khi user đổi combo sau khi so sánh, đánh dấu kết quả đã cũ. |
| **SC-8** | P1 | Nút Hủy không hủy được; không có tiến độ; khóa chọn trùng giữa các loại | **XONG** | Bọc toàn bộ luồng so sánh qua `LoadTask<SchemaCompareResult>`. Tích hợp tiến độ vào `LoadingOverlay` và hỗ trợ hủy an toàn (`activeCompareTask.cancel()`). Khóa chọn trong TreeView dùng tiền tố loại đối tượng: `type + ":" + qualifiedName`. |
| **SC-9** | P2 | Mặc định mọi khác biệt đều được chọn kể cả thao tác phá hủy | **XONG** | Trong `SchemaObjectDiff`, các thay đổi phá hủy (`destructive == true`) mặc định `selected = false`. TreeCell hiển thị badge cảnh báo DANGER `(Destructive)`. Trong migration script tự động thêm chú thích cảnh báo trước mỗi lệnh phá hủy: `-- [CẢNH BÁO: THAO TÁC PHÁ HỦY / DESTRUCTIVE - CÓ THỂ MẤT DỮ LIỆU]`. |
| **SC-10** | P2 | Hiệu năng sinh script mỗi lần tick ô; nạp bảng | **XONG** | Thêm `scriptDebounce` qua `PauseTransition(150ms)` để chống giật lag khi tick/untick nhiều ô liên tục. `SchemaSnapshotLoader` nạp bảng song song tối đa 4 luồng và nạp views/procs/funcs/triggers theo batch 1 truy vấn mỗi loại. |

---

## 2. Danh Sách Unit Test & Kết Quả Chạy

Tất cả các test đã được thực thi và vượt qua 100% qua lệnh `.\mvnw.cmd test "-Dtest=SchemaCompareServiceTest,SchemaCompareTabTest"`:

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running app.ui.compare.SchemaCompareServiceTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.572 s -- in app.ui.compare.SchemaCompareServiceTest
[INFO] Running app.ui.compare.SchemaCompareTabTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.497 s -- in app.ui.compare.SchemaCompareTabTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Chi tiết các ca kiểm thử:
1. `SchemaCompareServiceTest.testCompareMockSnapshots`: Kiểm tra so sánh phát hiện đúng số lượng thêm, sửa, xóa, và thuộc tính `isDestructive`.
2. `SchemaCompareServiceTest.testGenerateMigrationScript`: Kiểm tra sinh script migration với transaction an toàn (`BEGIN TRANSACTION`, `XACT_ABORT ON`).
3. `SchemaCompareServiceTest.testGenerateMigrationScriptWithDrop`: Kiểm tra bật `includeDrop=true` sinh đúng lệnh `DROP TABLE`.
4. `SchemaCompareServiceTest.testEngineMismatchThrows`: Kiểm tra ném ngoại lệ khi 2 snapshot khác loại CSDL.
5. `SchemaCompareServiceTest.testSelectiveMigration`: Kiểm tra chỉ sinh script cho các đối tượng được chọn.
6. `SchemaCompareServiceTest.testPostgresDdlGeneration_SC4`: Kiểm tra sinh DDL cho PostgreSQL (`DROP TRIGGER ... ON table`, `DROP VIEW CASCADE`, `identityArgs` function).
7. `SchemaCompareServiceTest.testCodeNormalization_SC5`: Kiểm tra không nuốt khoảng trắng trong chuỗi literal, phân biệt hoa thường và xử lý overloads.
8. `SchemaCompareServiceTest.testMigrationScriptOrdering_SC6`: Kiểm tra thứ tự phụ thuộc: Phase 1 Drops → Phase 2.1 Create Tables → Phase 2.3 Foreign Keys → Functions → Views.
9. `SchemaCompareServiceTest.testDestructiveChangesDefault_SC9`: Kiểm tra đối tượng phá hủy mặc định `selected = false` và script có gắn cờ cảnh báo.
10. `SchemaCompareTabTest.testTabInitialization`: Khởi tạo tab, các control, Monaco editor, metrics bar.
11. `SchemaCompareTabTest.testEngineMismatchAborts_SC3`: Kiểm tra UI chặn và abort trước khi chạy khi chọn 2 profile khác loại hệ quản trị.
12. `SchemaCompareTabTest.testIdenticalEndpointsAbort_SC3`: Kiểm tra UI chặn và abort khi nguồn và đích trùng nhau hoàn toàn.

---

## 3. Kiểm Tra Trên Cơ Sở Dữ Liệu Thật

- **Môi trường đã kiểm thử tự động:** H2 in-memory, SQLite & Mock Metadata Provider.
- **PostgreSQL & SQL Server:** Đã đối chiếu cú pháp DDL catalog theo dialect chuẩn (`PostgresDdlDialect`, `SqlServerDdlDialect`) và các catalog views trong `ObjectDefinitionReader` (`sys.sql_modules`, `pg_catalog.pg_get_functiondef`, `pg_catalog.pg_get_triggerdef`, `pg_catalog.pg_views`). Chưa kiểm tra trực tiếp qua mạng trên instance live server thật tại turn này.

---

## 4. Điểm Khác Biệt Giữa Kế Hoạch & Mã Nguồn Thực Tế

1. **TableDesign Copy Method:** Trong kế hoạch và ghi chú có nhắc đến `copy()`, trong khi method thực tế của domain model là `deepCopy()`.
2. **ForeignKeyAction Package:** Nằm trong value object `com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction`, không nằm trong package `model`.
3. **LoadingOverlay Dynamic Updates:** `LoadingOverlay` ban đầu chỉ có `show(text, cancelAction)` và không có `setMessage(text)`. Đã bổ sung `setMessage(String text)` để cập nhật tiến độ mượt mà từ background thread sang JavaFX application thread.

