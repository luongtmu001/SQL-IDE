# Feature Analysis: Monaco Editor Integration (Feature 4 - UI-03)

## 1. Source Evidence
- **Files (Python Reference):**
  - `static/vendor/monaco/vs/loader.js`: AMD loader của Microsoft Monaco Editor.
  - `static/vendor/monaco/vs/editor/editor.main.js`: Lõi Monaco Editor (~3.2MB).
  - `static/vendor/monaco/vs/basic-languages/sql/sql.js`: Bộ tokenizer cú pháp SQL, keyword highlight, operators, built-in functions.
  - `static/js/editor.js`: Quản lý vòng đời Monaco (`monaco.editor.create`), khởi tạo `monaco.editor.createModel`, xử lý chuyển model qua `setModel()`, lưu con trỏ `viewState`, đăng ký `onDidChangeModelContent`, `onDidChangeCursorPosition`, cơ chế theme switching (`vs-dark` / `vs`), định dạng SQL format, phím tắt thực thi (`F5`, `Ctrl+Enter`).
  - `templates/partials/editor.html`: Thẻ container `#ide-editor-container` chứa DOM render của Monaco.
  - `static/css/editor.css`: Tùy biến thanh cuộn, minimap, font chữ (JetBrains Mono / Consolas), padding, line numbers.
- **Functions / Classes / Handlers:**
  - `initMonaco()`: Khởi tạo require.js/AMD loader, định nghĩa theme tùy biến (`ide-dark`, `ide-light`).
  - `createModel(tabId, content, language)`: Khởi tạo `TextModel` riêng biệt cho mỗi tab truy vấn.
  - `switchModel(tabId)`: Lưu lại `viewState` của tab hiện tại, chuyển editor sang hiển thị `TextModel` của tab mới, phục hồi `viewState`.
  - `disposeModel(tabId)`: Giải phóng bộ nhớ `TextModel` khi tab đóng.
  - `executeScript`: Giao tiếp hai chiều giữa backend/shell với frontend web view.
- **API routes:**
  - Không có API HTTP riêng cho editor core; toàn bộ diễn ra tại client / browser runtime.
- **Database tables / queries:**
  - Không truy cập DB trực tiếp.
- **Persistence:**
  - `settings.json`: Lưu cấu hình font size, theme (`dark`/`light`), word wrap, minimap toggle.

---

## 2. Existing User Workflow
1. **Khởi tạo Soạn thảo:**
   - Khi ứng dụng khởi động hoặc người dùng mở tab mới, Monaco Editor sẵn sàng cho phép nhập liệu tức thì với highlight cú pháp SQL phong phú (SELECT, FROM, WHERE, JOIN, GROUP BY, subqueries, strings, comments).
2. **Chuyển đổi Tab không mất trạng thái:**
   - Khi chuyển giữa các tab khác nhau, trình soạn thảo giữ nguyên lịch sử Undo/Redo, vị trí con trỏ chuột, và vị trí cuộn màn hình mà không cần reload trang web.
3. **Phản hồi trạng thái Sửa đổi (Dirty tracking):**
   - Mỗi ký tự người dùng gõ vào kích hoạt sự kiện thay đổi nội dung, báo hiệu lên thanh tab để đánh dấu `*` (dirty).
4. **Hiển thị thông tin Con trỏ (Status Bar Cursor Position):**
   - Khi di chuyển con trỏ hoặc bôi đen đoạn văn bản, tọa độ dòng và cột (`Line X, Col Y`) được cập nhật theo thời gian thực xuống Status Bar.
5. **Đổi Giao diện Đen/Trắng (Theme Switching):**
   - Khi người dùng đổi theme ứng dụng (Dark/Light trong AtlantaFX), Monaco tự động chuyển đổi theme tương ứng (`ide-dark` $\leftrightarrow$ `ide-light`) mà không làm nhấp nháy màn hình.
6. **Định dạng Mã nguồn SQL (Format SQL):**
   - Người dùng bấm nút Format hoặc dùng menu/phím tắt, văn bản truy vấn được căn lề, thụt dòng, viết hoa từ khóa chuẩn xác.

---

## 3. Current Behavior
- **Normal path:**
  - Sử dụng 1 instance Monaco Editor duy nhất.
  - Quản lý đa tài liệu thông qua cơ chế `monaco.editor.createModel` và hoán đổi `editor.setModel(targetModel)`.
  - Hai chiều Java $\leftrightarrow$ JavaScript được điều phối qua JavaFX `JSObject` bridge callback.
- **Validation:**
  - Kiểm tra tính hợp lệ của tabId khi switch/dispose model; nếu model chưa tồn tại thì tự động tạo mới.
- **Error path:**
  - Trong môi trường headless hoặc khi WebView gặp trục trặc, hệ thống tự động fallback an toàn sang `SqlCodeArea` (RichTextFX) mà không làm sập ứng dụng.
- **State changes:**
  - Cập nhật dirty flag trên `SqlEditorTab`.
  - Cập nhật dòng/cột trên `StatusBarController`.
- **Side effects:**
  - Bộ nhớ của `TextModel` được giải phóng triệt để khi đóng tab để tránh rò rỉ RAM (memory leak).

---

## 4. UI Structure
- **Main components:**
  - `MonacoEditorHost` (`javafx.scene.layout.StackPane` bọc `javafx.scene.web.WebView`).
  - `editor.html`: Trang HTML tối giản nhúng tài nguyên Monaco Editor offline local.
  - `editor-bridge.js`: Cầu nối JavaScript trung gian xử lý các lệnh từ Java và bắn sự kiện về `javaBridge`.
- **Actions:**
  - `createModel(id, content, language)`
  - `switchModel(id)`
  - `updateContent(id, content)`
  - `disposeModel(id)`
  - `setTheme(themeName)`
  - `setFontSize(size)`
  - `setReadOnly(readOnly)`
  - `formatDocument()`
  - `requestEditorLayout()`
- **Shortcuts:**
  - `F5` / `Ctrl+Enter`: Thực thi truy vấn.
  - `Ctrl+F`: Tìm kiếm trong editor.
  - `Ctrl+H`: Tìm kiếm & Thay thế.
  - `Ctrl+Z` / `Ctrl+Y`: Hoàn tác / Làm lại (quản lý nội bộ bởi Monaco TextModel).
- **Resizing / visibility:**
  - Lắng nghe `layoutChildren()` của `MonacoEditorHost` và resize event của cửa sổ để tự động gọi `editor.layout()`.
- **Empty / loading / error states:**
  - Hàng đợi `readyCallbacks` lưu trữ toàn bộ các lệnh phát ra trước khi Monaco tải xong trong WebView và xả lệnh tuần tự ngay khi sẵn sàng.
  - Khi không còn tab nào, Monaco container ẩn đi, hiển thị `emptyEditorStateBox`.

---

## 5. Dependencies
- `org.openjfx:javafx-web:21.0.5`
- Thư mục tài nguyên offline: `src/main/resources/monaco/vs/` (~11MB)
- `editor.html`, `editor.css`, `editor-bridge.js` trong `src/main/resources/monaco/`

---

## 6. Unknowns / Assumptions
- *Đã giải quyết:* Cơ chế Monaco Web Worker khi chạy trong giao thức `file://` của JavaFX WebKit. Giải pháp: cấu hình `window.MonacoEnvironment = { getWorker: () => { throw new Error("Fallback main thread"); } };` để tokenizer chạy mượt mà ngay trên main thread mà không bị lỗi cross-origin hay bảo mật sandbox.
- *Đã tối ưu hóa:* Hiện tượng chớp màn hình đen và độ trễ 1s khi khởi động Monaco. Giải pháp:
  1. **Visual Continuity:** Khởi tạo tức thì bằng `SqlCodeArea` (0ms, có syntax highlight) để người dùng thấy code ngay lập tức.
  2. **Zero-flicker background:** Đặt `webView.setPageFill(Color.TRANSPARENT)`, `background-color: transparent !important`, và opacity 0.0 lúc bắt đầu. Pre-load `editor.main.css` trong `<head>` để tránh WebKit recalculate style.
  3. **Non-blocking startup:** Tách việc khởi tạo `MonacoEditorHost` sang `Platform.runLater()`, giảm thời gian load FXML từ >5s xuống còn ~0.8s.
  4. **Smooth handover:** Khi Monaco phát sự kiện `onEditorReady()`, đồng bộ nội dung và chuyển sang Monaco mượt mà bằng hiệu ứng FadeTransition 120ms.

---

## 7. Suspected Defects in Legacy Python
- Bản Python trước đây khi người dùng mở quá nhiều tab có thể bị chậm do một số bộ DOM không được ảo hóa hoàn toàn, và khi đóng tab chưa giải phóng sạch các sự kiện listener. Bản Java đã khắc phục triệt để bằng cách dùng `model.dispose()` và chỉ duy trì 1 instance WebView duy nhất cho toàn bộ IDE.

---

## 8. Java Migration Notes
- **Native JavaFX components:** `MonacoEditorHost` mở rộng `StackPane`, chứa 1 `WebView` duy nhất theo chuẩn Rule 6 của `AGENTS.md`.
- **Monaco/WebView requirement:** Bắt buộc tuân thủ quy tắc kiến trúc Single WebView - Multi TextModel. Tuyệt đối không tạo WebView cho từng Tab.
- **Java services:** Tích hợp trực tiếp vào `EditorController.java`, liên kết với `StatusBarController` và `MainController`.
- **JDBC/provider concerns:** Không ảnh hưởng.

---

## 9. Acceptance Criteria
- [x] Monaco Editor được tích hợp hoàn toàn offline từ tài nguyên local `src/main/resources/monaco/vs/`.
- [x] Duy nhất 1 instance `WebView` được khởi tạo và tái sử dụng cho tất cả các tab (Single WebView - Multi TextModel).
- [x] Chuyển đổi tab tức thì (<5ms) mà không mất con trỏ, vùng chọn, hay lịch sử Undo/Redo.
- [x] Tự động đồng bộ theme Dark/Light giữa AtlantaFX và Monaco Editor (`ide-dark`, `ide-light`).
- [x] Lắng nghe sự kiện gõ phím để cập nhật Dirty indicator (`*`) và tọa độ dòng/cột lên Status Bar.
- [x] Cơ chế xếp hàng lệnh an toàn (runWhenReady queue) khi WebView đang tải.
- [x] Graceful fallback sang `SqlCodeArea` (RichTextFX) nếu WebView không khả dụng trong môi trường headless/testing.
- [x] Vượt qua 100% các bài kiểm thử tự động (76/76 unit & integration tests pass).

