# Kế hoạch Thực thi: Schema Compare & Sync (Phase 3 - Slice 4)

> **Mục tiêu:** Xây dựng không gian làm việc So sánh & Đồng bộ Lược đồ (Schema Compare Workspace) cho phép so sánh cấu trúc giữa hai CSDL (hoặc hai Schema), hiển thị khác biệt trực quan qua TreeTableView và Monaco Diff Editor, và tự động sinh kịch bản DDL đồng bộ hóa theo thứ tự tô-pô an toàn.

---

## 1. Tuân thủ Quy tắc & Token Thiết kế

### 1.1. Tuân thủ `04-component-rules.md` (Quy tắc Component)
- **Badge Trạng thái:** Dùng `Badge` màu sắc chuẩn cho các trạng thái diff:
  - `Khác biệt (Different)`: Badge viền vàng `-color-warning-emphasis`.
  - `Chỉ có ở Nguồn (Source Only)`: Badge xanh lá `-color-success-emphasis`.
  - `Chỉ có ở Đích (Target Only)`: Badge màu xanh lam hoặc tím `-color-accent-emphasis`.
  - `Đồng nhất (Identical)`: Badge màu xám mờ `-color-fg-muted`.
- **Trạng thái Trống & Đang tải:**
  - Dùng `LoadingOverlay` kèm ProgressBar hiển thị tiến trình (Snapshotting Source... -> Snapshotting Target... -> Computing Diff...).
  - Dùng `EmptyState` khi chưa bắt đầu so sánh ("Chọn CSDL Nguồn và CSDL Đích rồi bấm So sánh...").
- **Monaco Diff Editor:** Tái sử dụng `WebView` để kích hoạt Monaco Diff Editor trực tiếp, hiển thị DDL gốc bên trái và DDL đích bên phải.
- **Nút hành động:** Dùng `AppButton` cho nút So sánh (`fth-play`, class `.accent`), nút Hoán đổi Nguồn/Đích (`fth-repeat`), nút Sinh kịch bản (`fth-file-text`), nút Mở trong tab SQL (`fth-external-link`).

### 1.2. Tuân thủ `02-design-principles.md` (Nguyên tắc Thiết kế Desktop)
- **Bố cục 2 tầng (Top-Bottom Split):**
  - Tầng trên (45% chiều cao): Bảng cây đối tượng (`TreeTableView`) có checkbox chọn đối tượng cần đồng bộ, phân nhóm theo loại đối tượng (Bảng, Views, Stored Procs, Functions, Triggers) hoặc theo trạng thái diff.
  - Tầng dưới (55% chiều cao): `MonacoDiffViewer` hiển thị so sánh DDL chi tiết của đối tượng đang chọn trên cây.
- **Hỗ trợ 2 chế độ `panelStyle`:** Các ComboBox chọn kết nối, nút chuyển đổi, cây đối tượng tuân thủ chặt chẽ bo góc 6px (ở chế độ `rounded`) hoặc vuông góc 0px (ở chế độ `flat`).
- **Phím tắt chuẩn Desktop:**
  - `F5` / `Ctrl+R`: Chạy lại so sánh.
  - `Space` trên TreeTableView: Bật/tắt checkbox đối tượng đang chọn.
  - `Ctrl+Shift+S`: Sinh kịch bản đồng bộ cho các đối tượng đã tích chọn.

### 1.3. Tuân thủ `03-design-tokens.md` (Design Tokens)
- Không hardcode màu hex:
  - Dòng khác biệt: Nền hover `-color-warning-subtle`, chữ `-color-warning-fg`.
  - Nền Monaco Diff: Sử dụng theme VS Code Dark/Light đã nhúng của hệ thống.
  - Viền ngăn cách SplitPane: `-ide-border-color`.

---

## 2. Kiến trúc & Phân rã Nhiệm vụ

### Bước 1: Xây dựng Domain Model & Snapshot Engine
1. Tạo package `com.lltool.core.domain.schemacompare.model`:
   - `SchemaSnapshot.java`: Lưu trữ metadata của toàn bộ bảng, view, proc, func, trigger của một CSDL.
   - `ObjectDiff.java`: Tên đối tượng, loại, `DiffState` (`IDENTICAL`, `DIFFERENT`, `ONLY_IN_SOURCE`, `ONLY_IN_TARGET`), mã DDL nguồn và đích, danh sách thay đổi chi tiết.
   - `SchemaDiffResult.java`: Tổng kết quả so sánh và các số liệu thống kê.
2. Tạo `com.lltool.core.application.schemacompare.SchemaCompareService`:
   - Phương thức `takeSnapshotAsync(connection, catalog, schemas, types)`: Tận dụng `TableDesignReader` để đọc cấu trúc bảng và truy vấn catalog đọc mã DDL của views/procedures/functions.
   - Phương thức `computeDiff(sourceSnapshot, targetSnapshot, options)`: So sánh song song các đối tượng:
     - Với Bảng: Gọi `TableDesignDiffer` so sánh từng cột, kiểu, độ dài, null, default, PK, FK, index, check.
     - Với Code Objects: Chuẩn hóa khoảng trắng (`normalizeWhitespace`), bỏ chú thích header tự sinh, so sánh chuỗi DDL.

### Bước 2: Xây dựng Engine Sinh Kịch bản Đồng bộ (`SchemaSyncScriptGenerator`)
1. Tạo `SchemaSyncScriptGenerator.java`:
   - Sinh script DDL chuyển dịch trạng thái từ Nguồn sang Đích (Forward Sync):
     - Bước 1: Disable/Drop foreign keys phụ thuộc.
     - Bước 2: Drop các view/trigger nếu cần sửa đổi cấu trúc bảng bên dưới.
     - Bước 3: `ALTER TABLE` thêm cột mới, sửa kiểu cột.
     - Bước 4: `CREATE TABLE` các bảng mới chỉ có ở Nguồn.
     - Bước 5: `CREATE OR REPLACE` / `ALTER` các views, procedures, functions.
     - Bước 6: Recreate các Foreign Keys, Unique/Check constraints, và Indexes.
   - Bọc toàn bộ script trong block giao dịch (`BEGIN TRAN ... COMMIT TRAN` cho SQL Server, `BEGIN ... COMMIT` cho PostgreSQL).

### Bước 3: Tích hợp Monaco Diff Editor trong WebView
1. Tạo `MonacoDiffViewer.java`:
   - Tận dụng `WebView` nạp trang web chứa Monaco.
   - Gọi hàm JavaScript:
     ```javascript
     function setDiffModels(originalCode, modifiedCode, originalTitle, modifiedTitle, language) {
         if (diffEditor) {
             const origModel = monaco.editor.createModel(originalCode, language);
             const modModel = monaco.editor.createModel(modifiedCode, language);
             diffEditor.setModel({ original: origModel, modified: modModel });
         }
     }
     ```
2. Đảm bảo hỗ trợ chuyển đổi theme sáng/tối đồng bộ với `ThemeService` của IDE.

### Bước 4: Hiện thực Giao diện `SchemaCompareTab`
1. Tạo `SchemaCompareTab.java`:
   - **Header Bar:**
     - Bên trái: ComboBox chọn Source Connection, Source Database, Source Schema.
     - Giữa: Nút Swap (`fth-repeat`) đảo chiều nguồn và đích.
     - Bên phải: ComboBox chọn Target Connection, Target Database, Target Schema.
     - Nút "So sánh (Compare)" (`fth-play`, class `.accent`).
   - **Options Bar:** Checkbox lọc hiển thị: Chỉ hiện khác biệt (`Show Differences Only`), Bỏ qua khoảng trắng (`Ignore Whitespace`), Bỏ qua Collation.
   - **Center SplitPane:**
     - Top: `TreeTableView<ObjectDiff>` hiển thị phân cấp: Nhóm loại đối tượng -> Đối tượng -> Cột/Ràng buộc bị thay đổi. Cột checkbox cho phép người dùng chọn/bỏ chọn từng đối tượng khi sinh script.
     - Bottom: `MonacoDiffViewer` hiển thị so sánh DDL của đối tượng được chọn.
   - **Footer Bar:** Hiển thị tổng số đối tượng giống/khác; nút "Sinh kịch bản đồng bộ", nút "Mở trong tab SQL".

---

## 3. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] Người dùng có thể chọn 2 kết nối bất kỳ (cùng server hoặc khác server) và thực hiện so sánh lược đồ.
- [ ] Quá trình so sánh chạy nền không làm đơ giao diện; hiển thị tiến trình rõ ràng.
- [ ] Kết quả diff phân loại chính xác: `Identical`, `Different`, `Source Only`, `Target Only`.
- [ ] Monaco Diff Editor render side-by-side chuẩn xác với highlight khác biệt từng dòng và từng ký tự.
- [ ] Kịch bản đồng bộ DDL sinh ra đúng thứ tự tô-pô, có thể mở trực tiếp trong một Query Editor Tab mới.
- [ ] Hỗ trợ cả 2 hệ quản trị CSDL trọng tâm: SQL Server và PostgreSQL.
- [ ] Đạt 100% test cases trong `SchemaCompareServiceTest`.

