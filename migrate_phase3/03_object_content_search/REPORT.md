# Khảo sát & Đánh giá Tính năng: Object Content Search (Tìm kiếm Nội dung Đối tượng)

---

## 1. Khảo sát mã nguồn Python gốc

### 1.1. Các file và vị trí trong Python repository
- **Backend Service:** `app/services/object_search_service.py` (23,178 bytes)
  - Class: `ObjectSearchService`
  - Các hàm cốt lõi:
    - `search(keyword, database, object_types, case_sensitive, whole_word, is_regex, schema)`: Tìm kiếm chuỗi/regex trong mã nguồn DDL của các đối tượng SQL (Views, Procedures, Functions, Triggers).
      - SQL Server: Truy vấn `sys.sql_modules` kết hợp `sys.objects` và `sys.schemas`.
      - PostgreSQL: Truy vấn `pg_proc` (Functions, Stored Procedures), `pg_views` (Views), `pg_trigger` kết hợp `pg_proc`.
      - Trả về danh sách rút gọn: `{schema, name, type, match_count}` để giảm tải băng thông.
    - `get_ddl(object_type, schema, name, database)`: Lấy mã nguồn DDL đầy đủ của đối tượng được chọn.
    - `_compile_pattern(keyword, case_sensitive, whole_word, is_regex)`: Biên dịch regex chuẩn hóa theo tùy chọn tìm kiếm.
- **Frontend Modules:** Thư mục `static/js/object-search/`
  - `object-search-api.js`: Gọi API backend tìm kiếm và lấy DDL.
  - `object-search-store.js`: Lưu trữ trạng thái tìm kiếm (từ khóa, tùy chọn, danh sách kết quả, đối tượng đang chọn, chỉ mục match hiện tại).
  - `match-panel.js`: Panel danh sách kết quả bên trái, gom nhóm theo Schema hoặc Loại đối tượng (View, Proc, Function, Trigger), hiển thị badge đếm số lượng khớp.
  - `ddl-viewer.js`: Tích hợp Monaco Editor xem mã nguồn DDL, tự động đánh dấu (highlight decorations) các từ khóa tìm thấy và cuộn đến dòng khớp tương ứng.
  - `object-search-controller.js`: Điều khiển giao diện, xử lý phím tắt, debounce tìm kiếm, nút mở sang tab SQL mới.
- **Markup & Styling:**
  - Container `#object-search-container` trong `templates/index.html`.
  - `static/css/object-search.css`.
- **Test suite Python:**
  - `tests/test_object_search_all_phases.py`.

---

## 2. Khảo sát hiện trạng mã nguồn Java

Hiện tại tính năng Object Content Search **chưa được triển khai trong Java**.
- Trong thanh menu chính `MenuBar` và `MainToolbar` chưa có nút mở tính năng này.
- Hạ tầng Java đã có sẵn các thành phần lý tưởng để tái sử dụng:
  - `EmbeddedMonaco`: Host Monaco editor trong JavaFX WebView, hỗ trợ nạp text và nhảy dòng (`revealLineInCenter`), đánh dấu syntax SQL.
  - `SearchField`: Ô tìm kiếm chuẩn desktop có nút xóa và icon kính lúp.
  - `Badge`: Hiển thị số lượng match trên từng dòng đối tượng.
  - `DatabaseType`: Phân định PostgreSQL và SQL Server.

---

## 3. Chi tiết danh sách File cần Sửa & File Thêm Mới

### 3.1. Các file cần sửa (Modified Files)

| File Path | Class | Phương thức cần sửa/thêm | Lý do & Nội dung sửa |
|---|---|---|---|
| `src/main/java/app/controllers/MainController.java` | `MainController` | - `openObjectContentSearch()`<br>- Phím tắt `Ctrl+Shift+F` | Mở tab `ObjectContentSearchTab` trong thanh tab chính hoặc kích hoạt từ menu. |
| `src/main/java/app/ui/components/DatabaseTreeCell.java` | `DatabaseTreeCell` | `createContextMenuForDatabase(...)` | Bổ sung mục menu ngữ cảnh: **"Tìm kiếm nội dung đối tượng (Search in Database)..."**. |
| `src/main/java/app/ui/editor/EmbeddedMonaco.java` | `EmbeddedMonaco` | - `highlightRanges(List<SearchMatchLocation> ranges)`<br>- `goToLocation(int line, int column)` | Bổ sung phương thức JavaScript bridge để Monaco bôi màu các từ khóa khớp và di chuyển con trỏ đến vị trí match. |

### 3.2. Các file thêm mới (New Files)

| File Path | Class / Resource | Trách nhiệm & Phương thức chính |
|---|---|---|
| `core/core-domain/src/main/java/com/lltool/core/domain/objectsearch/model/ObjectSearchQuery.java` | `ObjectSearchQuery` | Model chứa tiêu chí tìm kiếm: keyword, database, schema, Set<ObjectType> (VIEW, PROCEDURE, FUNCTION, TRIGGER), caseSensitive, wholeWord, isRegex. |
| `core/core-domain/src/main/java/com/lltool/core/domain/objectsearch/model/SearchMatchLocation.java` | `SearchMatchLocation` | Vị trí khớp trong văn bản: `line`, `column`, `length`, `snippet` (đoạn text trích xuất quanh từ khóa). |
| `core/core-domain/src/main/java/com/lltool/core/domain/objectsearch/model/ObjectSearchHit.java` | `ObjectSearchHit` | Kết quả tìm thấy: catalog, schema, objectName, objectType, matchCount, List<SearchMatchLocation>. |
| `core/core-application/src/main/java/com/lltool/core/application/objectsearch/ObjectSearchService.java` | `ObjectSearchService` | Service thực thi tìm kiếm: chạy truy vấn catalog trên SQL Server / PostgreSQL, biên dịch regex, trích xuất vị trí dòng/cột của các match, lấy DDL toàn vẹn của đối tượng. |
| `src/main/java/app/ui/search/ObjectContentSearchTab.java` | `ObjectContentSearchTab` | Giao diện chính kế thừa `SqlEditorTab`: Thanh tìm kiếm trên (SearchField, checkbox Match Case, Whole Word, Regex, filter chips loại đối tượng), SplitPane chia 2 (trái: danh sách kết quả có Badge, phải: EmbeddedMonaco xem DDL và highlight match), footer thống kê. |
| `src/main/resources/css/object-content-search.css` | CSS Stylesheet | Token giao diện theo `-ide-*` và `-color-*` cho thanh tìm kiếm, filter chip, danh sách kết quả và badge đếm match. |
| `src/test/java/app/ui/search/ObjectSearchServiceTest.java` | Unit Test | Test regex compiling, trích xuất dòng/cột từ DDL, mock catalog queries cho SQL Server và PostgreSQL. |

---

## 4. Thư viện & Phụ thuộc
- Tận dụng `java.util.regex` chuẩn của Java, JavaFX ListView/TreeView và `EmbeddedMonaco`. Không cần thư viện ngoài.

