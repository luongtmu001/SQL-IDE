# Feature Analysis: Query Execution (Feature 5)

## 1. Source Evidence
- **Files (Python Reference):**
  - `app/services/query_service.py`: Lõi thực thi truy vấn `QueryService.execute()` và hủy truy vấn `cancel()` thông qua adapter database session.
  - `app/api/query_api.py`: `QueryApiMixin.execute_query()`, kiểm tra an toàn `check_query_safety` (chặn DDL/DML nguy hiểm trên PROD không có WHERE), truyền tiến trình qua `on_progress`, xử lý hủy truy vấn `cancel_query()`.
  - `static/js/query.js`: Quản lý giao diện thực thi (`handleRunAction`), trạng thái chạy (`resultPanelState.isRunning`), nút Run/Stop, phím tắt `F5`, `Ctrl+Enter`, `Ctrl+E`, `Escape` để hủy, quản lý bảng kết quả và tab Messages.
  - `tests/test_context_menu_new_query.py` & `tests/test_phase3_bug2_explain_query.py`: Kiểm thử luồng thực thi và ngữ cảnh kết nối.
- **Functions / Classes / Handlers:**
  - `QueryService.execute(sql, params, limit, database, schema, progress_callback, stop_on_error)`
  - `QueryService.cancel()`
  - `QueryApiMixin.execute_query(...)` & `cancel_query(...)`
  - `handleRunAction()` trong `query.js`
- **API routes:**
  - `execute_query`, `cancel_query`
- **Database tables / queries:**
  - Truy vấn tùy ý do người dùng nhập trên kết nối đang hoạt động (SELECT, DDL, DML).
- **Persistence:**
  - Kết quả tạm thời gắn với session của tab truy vấn.

---

## 2. Existing User Workflow
1. **Khởi chạy Truy vấn (Query Execution):**
   - Người dùng bấm nút `Run` trên toolbar hoặc phím tắt `F5` / `Ctrl+Enter`.
   - Nếu có đoạn bôi đen (selection): Chỉ thực thi đoạn mã được chọn.
   - Nếu không có selection: Thực thi toàn bộ nội dung trong tab active.
2. **Hiển thị Bảng Kết quả (Results Panel Behavior):**
   - Mặc định khi mở tab mới, bảng kết quả được ẩn để tối đa không gian soạn thảo.
   - Khi bấm chạy truy vấn hoặc bấm phím tắt `Ctrl + R`, bảng kết quả tự động hiển thị và khôi phục chính xác vị trí kích thước (divider position) trước đó.
3. **Phản hồi Trạng thái Đang chạy (Running State):**
   - Nút `Execute` bị vô hiệu hóa hoặc chuyển sang icon spinner ("Running...").
   - Nút `Stop` bật sáng cho phép người dùng dừng câu lệnh đang chạy.
   - StatusBar hiển thị thông báo "Executing query on [Connection]...".
4. **Hủy Truy vấn (Query Cancellation):**
   - Người dùng bấm `Stop` hoặc phím tắt `Ctrl+F2` / `Escape`.
   - Lệnh `Statement.cancel()` được gửi tới database driver để ngừng xử lý lập tức.
   - StatusBar cập nhật "Query execution cancelled by user".
5. **Hiển thị Kết quả hoặc Thông báo Lỗi:**
   - Nếu câu lệnh trả về ResultSet (SELECT): Bảng TableView hiển thị các cột, số dòng, thời gian thực thi (ms) và tab Results được chọn.
   - Nếu câu lệnh là DDL/DML (INSERT, UPDATE, CREATE...): Hiển thị số dòng bị ảnh hưởng (rows affected) trong tab Messages.
   - Nếu có lỗi cú pháp hoặc lỗi DB: Chuyển sang tab Messages, bôi đỏ thông báo lỗi chi tiết, cập nhật StatusBar.

---

## 3. Current Behavior
- **Normal path:**
  - Chạy bất đồng bộ qua Java 21 Virtual Threads, đảm bảo UI không bao giờ bị đơ (non-blocking).
  - Tự động mở Results Panel và giữ đúng vị trí divider.
- **Validation:**
  - Kiểm tra kết nối hợp lệ; nếu chưa kết nối thì tự động kết nối theo profile đã chọn.
  - Kiểm tra nội dung SQL rỗng.
- **Error path:**
  - Bắt `SQLException`, hiển thị mã lỗi SQLState, ErrorCode và thông báo rõ ràng trong tab Messages.
- **State changes:**
  - `LayoutService.resultsVisible` chuyển sang `true` khi chạy query.
  - Cập nhật số dòng và thời gian thực thi lên `StatusBarController` và `resultsStatsLabel`.

---

## 4. UI Structure
- **Main components:**
  - ToolBar: Nút `Execute` (`fth-play`), nút `Stop` (`fth-square`).
  - Phím tắt: `F5` / `Ctrl+Enter` (Run), `Ctrl+F2` (Stop), `Ctrl+R` (Toggle Results Panel).
  - `centerVerticalSplit`: SplitPane chứa Editor ở trên và Results ở dưới.
  - `ResultsController`: Chứa `resultsTable` (Grid dữ liệu) và `messagesTable` (Log thông báo).
- **Resizing / visibility:**
  - Ghi nhớ biến `resultsDividerPosition` khi ẩn / hiện Results panel.

---

## 5. Dependencies
- `ConnectionSessionManager`
- `DatabaseProviderRegistry`
- `ResultsController`
- `StatusBarController`
- `LayoutService`
- Java 21 Virtual Threads (`Thread.ofVirtual()`)

---

## 6. Unknowns / Assumptions
- Không có; cơ chế JDBC `Statement.execute()`, `Statement.cancel()` và `ResultSetMetaData` là tiêu chuẩn của Java.

---

## 7. Suspected Defects in Legacy Python
- Trong Python cũ, một số query chạy lâu có thể làm đơ giao diện PyWebView nếu luồng callback IPC bị quá tải. Trong Java 21, Virtual Threads tách biệt hoàn toàn luồng I/O với JavaFX Application Thread.

---

## 8. Java Migration Notes
- **Native JavaFX components:** `TableView<QueryResultRow>` kết hợp `centerVerticalSplit` Divider persistence.
- **Java services:** `DesktopQueryExecutionService` quản lý vòng đời luồng thực thi, timeout và hủy lệnh.
- **JDBC/provider concerns:** Hỗ trợ chuẩn xác cho PostgreSQL, SQL Server và SQLite.

---

## 9. Acceptance Criteria
- [ ] Mặc định khi mở tab mới, bảng kết quả (Results panel) không hiển thị.
- [ ] Khi bấm `Execute` / `F5` / `Ctrl+Enter`, bảng kết quả tự động hiển thị và khôi phục đúng vị trí divider trước đó.
- [ ] Phím tắt `Ctrl+R` bật/tắt (toggle) Results panel và nhớ vị trí divider.
- [ ] Thực thi câu lệnh SQL qua JDBC trên Virtual Thread mà không làm đơ giao diện.
- [ ] Nếu có vùng chọn (selection) trong Monaco, chỉ thực thi đoạn được chọn.
- [ ] Nút `Stop` cho phép hủy câu lệnh đang chạy thông qua `Statement.cancel()`.
- [ ] Hiển thị dữ liệu lên `TableView` (kèm số dòng và thời gian ms) hoặc thông báo lỗi trong tab `Messages`.
- [ ] Bộ test suite tự động vượt qua 100%.

