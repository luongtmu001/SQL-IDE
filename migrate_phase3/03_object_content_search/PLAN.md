# Kế hoạch Thực thi: Object Content Search (Phase 3 - Slice 3)

> **Mục tiêu:** Xây dựng tính năng tìm kiếm văn bản và biểu thức chính quy (Regex) bên trong mã nguồn DDL của các đối tượng SQL (Views, Stored Procedures, Functions, Triggers), hiển thị danh sách kết quả trực quan và tích hợp Monaco Editor nhảy dòng, bôi màu từ khóa tìm thấy.

---

## 1. Tuân thủ Quy tắc & Token Thiết kế

### 1.1. Tuân thủ `04-component-rules.md` (Quy tắc Component)
- **Thanh tìm kiếm:** Dùng `SearchField` có nút xóa và icon kính lúp, hỗ trợ debounce 300ms khi người dùng nhập từ khóa.
- **Badge số lượng:** Dùng `Badge` hiển thị số lần xuất hiện từ khóa trên từng đối tượng (`5 matches`).
- **Trạng thái Trống & Đang tải:**
  - Dùng `LoadingOverlay` với animation xoay khi đang thực thi truy vấn tìm kiếm trên server CSDL.
  - Dùng `EmptyState` khi chưa nhập từ khóa ("Nhập từ khóa để tìm kiếm...") hoặc khi không tìm thấy kết quả nào ("Không tìm thấy đối tượng nào khớp với...").
- **Xem trước DDL:** Tái sử dụng `EmbeddedMonaco` có tích hợp highlight decoration và điều hướng vị trí dòng/cột.
- **Nút hành động:** Dùng `AppButton` hoặc `IconButton` cho các nút: Tìm kiếm (`fth-search`), Trước đó (`fth-chevron-up`), Kế tiếp (`fth-chevron-down`), Mở trong tab SQL (`fth-external-link`).

### 1.2. Tuân thủ `02-design-principles.md` (Nguyên tắc Thiết kế Desktop)
- **Bố cục Master-Detail:**
  - `SplitPane` ngang:
    - Panel bên trái (chiếm ~35% độ rộng): Danh sách kết quả tìm thấy, có thanh filter nhanh kết quả và bộ đếm tổng số đối tượng/tổng số match.
    - Panel bên phải (chiếm ~65% độ rộng): `EmbeddedMonaco` hiển thị mã DDL đầy đủ với các highlight màu vàng/cam trên các match.
- **Hỗ trợ 2 chế độ `panelStyle`:** Các trường nhập liệu, filter chip (View, Proc, Function, Trigger) tự động chuyển đổi giữa bo góc `rounded` và vuông góc `flat`.
- **Phím tắt chuẩn Desktop:**
  - `Ctrl+Shift+F`: Mở tab Tìm kiếm nội dung đối tượng từ bất kỳ đâu.
  - `Enter` trong SearchField: Thực thi tìm kiếm ngay lập tức.
  - `F3` / `Shift+F3`: Di chuyển đến kết quả match tiếp theo / trước đó trong Monaco Editor.
  - `Ctrl+Enter`: Mở đối tượng đang chọn vào một Query Editor Tab mới.

### 1.3. Tuân thủ `03-design-tokens.md` (Design Tokens)
- Không hardcode màu hex; dùng các token:
  - Nền panel danh sách: `-ide-sidebar-bg` / `-color-bg-subtle`.
  - Highlight match trong Monaco: `rgba(234, 179, 8, 0.35)` (vàng cảnh báo) cho match thường và `rgba(249, 115, 22, 0.6)` (cam nhấn) cho match đang active.
  - Chip bộ lọc đang chọn: `-color-accent-subtle`, chữ `-color-accent-fg`.

---

## 2. Kiến trúc & Phân rã Nhiệm vụ

### Bước 1: Xây dựng Domain Models & Engine tìm kiếm phía Backend
1. Tạo package `com.lltool.core.domain.objectsearch.model`:
   - `ObjectSearchQuery.java`: Chứa từ khóa, database, schema, tập hợp ObjectType, caseSensitive, wholeWord, isRegex.
   - `SearchMatchLocation.java`: `line`, `column`, `length`, `snippet`.
   - `ObjectSearchHit.java`: Tên đối tượng, schema, loại đối tượng, số lượng match, danh sách `SearchMatchLocation`.
2. Tạo `com.lltool.core.application.objectsearch.ObjectSearchService`:
   - Truy vấn SQL Server:
     ```sql
     SELECT s.name AS schema_name, o.name AS object_name, o.type_desc, m.definition
     FROM sys.sql_modules m
     JOIN sys.objects o ON m.object_id = o.object_id
     JOIN sys.schemas s ON o.schema_id = s.schema_id
     WHERE o.type IN ('V', 'P', 'FN', 'IF', 'TF', 'TR');
     ```
   - Truy vấn PostgreSQL:
     - Views: `SELECT schemaname, viewname, definition FROM pg_views;`
     - Routines: `SELECT n.nspname, p.proname, pg_get_functiondef(p.oid) FROM pg_proc p ...`
     - Triggers: `SELECT tgname, pg_get_triggerdef(oid) FROM pg_trigger ...`
   - Quét regex trên từng definition bằng `java.util.regex.Pattern` / `Matcher`, tính toán số dòng và cột chính xác.
   - Trả về danh sách `ObjectSearchHit` trên background thread qua `CompletableFuture`.

### Bước 2: Nâng cấp `EmbeddedMonaco` hỗ trợ Search Decorations
1. Trong `src/main/resources/monaco/` hoặc JavaScript bridge của `EmbeddedMonaco.java`:
   - Bổ sung hàm JS:
     ```javascript
     window.setSearchDecorations = function(rangesJson) {
         const ranges = JSON.parse(rangesJson);
         // monaco.editor.deltaDecorations(...)
     };
     window.revealSearchMatch = function(line, column) {
         editor.revealPositionInCenter({ lineNumber: line, column: column });
         editor.setPosition({ lineNumber: line, column: column });
     };
     ```
2. Thêm phương thức Java trong `EmbeddedMonaco.java`:
   - `highlightSearchMatches(List<SearchMatchLocation> matches)`
   - `navigateToMatch(SearchMatchLocation match)`

### Bước 3: Hiện thực Giao diện `ObjectContentSearchTab`
1. Tạo `ObjectContentSearchTab.java`:
   - **Header Panel:**
     - HBox chứa `SearchField` (độ rộng co giãn), các nút Toggle: Match Case (`Aa`), Whole Word (`\b`), Regex (`.*`), Database ComboBox, Schema ComboBox.
     - Dòng chip bộ lọc: Checkbox/Toggle chip cho `Views`, `Procedures`, `Functions`, `Triggers`.
     - Nút "Tìm kiếm" (`fth-search`, class `.accent`).
   - **Master SplitPane:**
     - Left: `ListView<ObjectSearchHit>` với custom cell hiển thị icon loại đối tượng (View, Stored Proc, Function, Trigger), tên `[schema].[name]`, đoạn text snippet của match đầu tiên, và `Badge` đếm số lượng match.
     - Right: Toolbar nhỏ (Next Match, Prev Match, Copy DDL, Open in SQL Tab) và `EmbeddedMonaco`.
   - **Footer Status Bar:** Hiển thị tổng số đối tượng tìm thấy, tổng số match, và thời gian thực thi (ms).

### Bước 4: Tích hợp Menu & Phím tắt
1. Thêm mục "Tìm kiếm nội dung đối tượng..." (`Ctrl+Shift+F`) vào menu **Công cụ (Tools)** và menu **Xem (View)** của ứng dụng.
2. Thêm mục vào menu chuột phải của CSDL trong Object Explorer.

---

## 3. Tiêu chí Chấp nhận (Acceptance Criteria)

- [ ] Tìm kiếm không làm đóng băng giao diện (chạy hoàn toàn trên worker thread qua `CompletableFuture`).
- [ ] Tìm kiếm chính xác trên cả SQL Server và PostgreSQL với các tùy chọn: phân biệt hoa/thường, từ nguyên vẹn, biểu thức chính quy.
- [ ] Danh sách kết quả bên trái hiển thị rõ icon, tên đối tượng, schema, và số lượng match.
- [ ] Khi click vào một đối tượng, Monaco editor bên phải nạp mã DDL tương ứng và tự động nhảy đến dòng chứa từ khóa đầu tiên.
- [ ] Từ khóa khớp trong Monaco được bôi màu nổi bật; các nút Next/Prev điều hướng mượt mà qua từng vị trí.
- [ ] Nút "Mở trong tab SQL" mở DDL trong một tab soạn thảo mới với đúng ngữ cảnh kết nối/CSDL.
- [ ] Đạt 100% test cases trong `ObjectSearchServiceTest`.

