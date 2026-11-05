# Phân Tích Tính Năng: Query Tabs and Editor Lifecycle (Feature 3)

## 1. Bằng chứng Mã nguồn Python (Source Evidence)
- **Tập tin tham chiếu:**
  - `static/js/tabs.js`: Toàn bộ logic quản lý tab, tính toán overflow, trượt sliding window, pinned tabs, drag-and-drop reordering.
  - `static/js/editor.js`: Quản lý TextModel, dirty state (`*`), lưu nội dung file, đồng bộ context kết nối/database/schema theo từng tab.
  - `templates/partials/editor.html`: Cấu trúc DOM của thanh tab `#ide-tabs-bar`, `#ide-tabs-overflow-btn`, `#ide-new-tab-btn`, empty state.
  - `tests/test_tab_manager.py`: Bộ kiểm thử tự động 302 dòng kiểm tra layout overflow, multi-line, pinned tabs, phím tắt.
  - `docs/archive/bugs/tab_manager.md`: Đặc tả chi tiết hành vi xử lý overflow, menu danh sách tab, hiển thị in đậm (bold) tab đang thấy, tab ghim (pin).
  - `data/session.json`: Lưu trạng thái các tab đang mở và tab active giữa các phiên làm việc.

---

## 2. Quy trình Người dùng Hiện tại (Existing User Workflow)

1. **Mở Tab mới (Tab Creation):**
   - Người dùng bấm nút `+` trên thanh Tab Bar, hoặc phím tắt `Ctrl + N`, hoặc menu *File $\rightarrow$ New Query Tab*, hoặc menu chuột phải trên Object Explorer (*New Query*, *Select Top 1000*).
   - Tab mới được tạo với tiêu đề mặc định dạng `Query 1.sql`, `Query 2.sql`... kèm theo context kết nối và CSDL hiện tại.
2. **Chuyển đổi Tab (Tab Switching):**
   - Click chuột vào tab hoặc chọn từ menu overflow `...`.
   - Nội dung tab tương ứng được hiển thị, cập nhật thanh tiêu đề, con trỏ dòng/cột tại status bar, và đồng bộ dropdown Connection/Database/Schema trên toolbar.
3. **Quản lý Trạng thái Chưa lưu (Dirty State Tracking):**
   - Khi người dùng gõ phím thay đổi nội dung, tab xuất hiện dấu chấm tròn hoặc sao (`*` / `•`).
   - Khi lưu thành công (`Ctrl + S`), dấu dirty biến mất.
4. **Đóng Tab (Tab Closing):**
   - Người dùng bấm nút `×` trên tab, hoặc phím tắt `Ctrl + W`, hoặc menu chuột phải.
   - Nếu tab không bị sửa đổi (clean): Đóng ngay lập tức.
   - Nếu tab có thay đổi chưa lưu (dirty): Hiển thị hộp thoại xác nhận với 3 lựa chọn (*Save*, *Don't Save*, *Cancel*).
   - Nếu đóng hết tab: Hiển thị giao diện Empty State (*"No Editor Tabs Open"*) với nút bấm tạo tab mới.
5. **Ghim Tab (Pin / Unpin Tab):**
   - Người dùng chọn *Pin Tab* từ menu chuột phải. Tab ghim có icon pin, cố định về phía bên trái thanh tab, ẩn nút đóng `×`, và không bị đóng khi chọn *Close Other Tabs* hoặc *Close All Tabs*.
6. **Thao tác Hàng loạt (Batch Tab Actions):**
   - Menu ngữ cảnh trên tab: *Close Tab*, *Close Other Tabs*, *Close All Tabs*, *Close Unmodified*, *Pin/Unpin Tab*, *Split Right*, *Split Down*, *Rename Tab*.
7. **Chia màn hình Soạn thảo (Split View):**
   - Cho phép chia màn hình sang phải (Split Right) hoặc xuống dưới (Split Down) để so sánh hoặc soạn thảo 2 tab đồng thời.

---

## 3. Kiến trúc Giao diện & Ánh xạ sang Java (UI Mapping)

| Thành phần Python | Thành phần Java Desktop (JavaFX) | Trạng thái hiện tại |
| :--- | :--- | :--- |
| `#ide-tabs-bar` container | `TabPane primaryTabPane` (styleClass `editor-tab-pane`) | Đã triển khai |
| Nút `+` New Tab | `Tab addTab` (chứa FontIcon `fth-plus`) ghim ở cuối | Đã triển khai |
| Tab Title & Dirty indicator | `SqlEditorTab` (HBox chứa `fileIcon`, `pinIcon`, `titleLabel`, `dirtyIndicator`) | Đã triển khai |
| Hộp thoại xác nhận lưu | `Alert(AlertType.CONFIRMATION)` với Save, Don't Save, Cancel | Đã triển khai |
| Split Editor (Right / Down) | `SplitPane editorSplitContainer` chứa `primaryTabPane` & `secondaryTabPane` | Đã triển khai |
| Empty Editor State | `VBox emptyEditorStateBox` hiển thị icon, text và nút New Query Tab | Đã triển khai |
| Header Action Buttons | `HBox tabHeaderActions` (Format, Split Right, Split Down, Close Split) | Đã triển khai |
| Context kết nối per-tab | `connectionId`, `databaseName`, `schemaName` trong `SqlEditorTab` | Cần bổ sung vào `SqlEditorTab` |

---

## 4. Các điểm cần Bổ sung & Tối ưu cho Feature 3 trong Java

1. **Gắn Connection & Database Context vào `SqlEditorTab`:**
   - Bổ sung các thuộc tính: `connectionName`, `databaseName`, `schemaName` vào `SqlEditorTab`.
   - Khi chuyển tab (`selectionModel().selectedItemProperty()`), tự động thông báo để đồng bộ lên `connectionComboBox` và `schemaComboBox` trên thanh Toolbar và cập nhật `GlobalDesktopState`.
2. **Hỗ trợ Phím tắt Chuẩn Desktop:**
   - `Ctrl + N`: Tạo tab truy vấn mới.
   - `Ctrl + W`: Đóng tab hiện tại (gọi qua logic kiểm tra dirty state).
   - `Ctrl + Shift + W`: Đóng tất cả tab.
3. **Mở Tab từ Object Explorer với ngữ cảnh chuẩn:**
   - Nâng cấp `MainController.openQueryConsole(title, sql, connName, dbName, schemaName)` và `executeQueryDirectly(...)` để truyền trọn vẹn context vào `SqlEditorTab`.
4. **Viết Bộ kiểm thử Tự động Toàn diện:**
   - Bổ sung unit/integration tests cho: Pin/Unpin, Close Unmodified, Close Others, Dirty confirmation, Context synchronization.

