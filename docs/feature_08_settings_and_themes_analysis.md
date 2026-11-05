# Feature Analysis: Feature 08 - Settings and Themes (Cài đặt & Quản lý giao diện)

## 1. Feature Overview
Feature 8 cung cấp cơ chế quản lý toàn diện cấu hình hệ thống (Settings) và giao diện người dùng (Themes) cho SQL IDE:
- **Quản lý Chủ đề (Theme Management)**:
  - Hỗ trợ đầy đủ các bộ theme hiện đại từ AtlantaFX: Primer Dark, Primer Light, Dracula, Nord Dark, Nord Light, Cupertino Dark, Cupertino Light.
  - Chuyển đổi nhanh Sáng / Tối (Dark / Light toggle) bằng 1 click trên thanh tiêu đề (Title Bar) và đồng bộ tức thì sang Monaco Editor (`vs-dark` $\leftrightarrow$ `vs`).
- **Trung tâm Cài đặt (Settings Center Dialog)**:
  - Bố cục 2 cột Master-Detail theo quy chuẩn `.SKILL/setting_form.md` và `_concept_/settings_template`:
    - **Cột trái (Categories Menu)**: Phân nhóm trực quan (General, Appearance & Themes, SQL Editor, Data Grid, Query Execution).
    - **Cột phải (Detail Panel)**: Các trường cấu hình chi tiết với giao diện trực quan (Toggle switches, Dropdown comboboxes, Number spinners, Text inputs).
- **Cơ chế Hot-Apply (Áp dụng tức thì)**:
  - Mọi thay đổi về giao diện (Theme, Font Size, UI Density) và biên tập (Word Wrap, Line Numbers, Minimap) có hiệu lực ngay lập tức trong runtime mà không yêu cầu khởi động lại ứng dụng.
- **Lưu trữ cấu hình bền vững (Persistence)**:
  - Cấu hình được lưu trữ dưới định dạng JSON nguyên tử tại `~/.lltool/config/settings.json`, tương thích với cấu trúc của Python `settings.json` và tầng `ConfigurationService`.

---

## 2. Python Source Reference
- `.SKILL/setting_form.md`:
  - Mục 1 & 2: Vị trí nút Settings trên taskbar ngay sau icon Dark/Light mode; icon bánh răng (gear/settings); bố cục 2 cột Master-Detail.
  - Mục 3 & 4: Quy tắc nạp schema và cơ chế phản hồi Hot-Apply không cần reload.
- `app/services/settings_store.py` (273 dòng):
  - Hàm `load_settings_file()`, `write_json()`, `read_json()`.
  - Schema mặc định:
    - `theme`: "dark" / "light"
    - `editor`: `fontFamily`, `fontSize`, `lineHeight`, `tabSize`, `insertSpaces`, `wordWrap`, `minimap`, `keywordCase`, `syntaxCheck`, `intellisense`.
    - `appearance`: `theme`, `iconSize`, `uiFontFamily`, `uiFontSize`.
    - `grid`: `fontFamily`, `fontSize`, `maxRows`.
    - `sql`: `maxRows`, `timeoutSeconds`, `stopOnError`.
    - `session`: `restoreOnStartup`, `autoSaveIntervalSeconds`.
- `app/desktop/themes.py` (455 dòng):
  - Cơ chế nhận diện mã màu hex, tính toán độ sáng `is_dark_color`, sinh biến CSS.
- `static/js/settings.js` (1,200+ dòng):
  - Giao diện modal cài đặt, điều hướng các tab (General, Appearance, Editor, Grid, SQL, Advanced), xử lý sự kiện `change` và `hot-apply`.
- `static/js/theme.js` & `static/js/theme-registry.js`:
  - Đăng ký danh sách themes, hàm `switchTheme(themeId)`, đồng bộ theme Monaco.
- `settings/settings.json`:
  - File dữ liệu cấu hình thực tế lưu trữ các giá trị của người dùng.

---

## 3. UI and Layout Analysis
1. **Settings Trigger Points**:
   - **Title Bar**: Nút bánh răng `fth-settings` nằm ngay cạnh nút chuyển Dark/Light `themeToggleButton` trên thanh `customArea`.
   - **Menu Bar**: `File -> Preferences -> Settings...` (Phím tắt `Ctrl+,`).
   - **Phím tắt toàn cục**: `Ctrl+,` (Command + , trên macOS).
2. **Settings Dialog (`settings-dialog.fxml`)**:
   - Kích thước: `840 x 560 px`, hiển thị ở dạng modal undecorated hoặc custom chrome.
   - **Header Bar**:
     - Tiêu đề: "Cài đặt hệ thống (Settings)".
     - Ô tìm kiếm thiết lập (Filter Settings Field): Lọc tức thì các tùy chọn cấu hình.
     - Nút đóng ($\times$).
   - **Center Content Area (Master-Detail HBox/SplitPane)**:
     - **Cột trái (220px)**:
       - `ListView` hoặc danh sách nút danh mục với icon Ikonli:
         1. *General (Cài đặt chung)* — `fth-sliders`
         2. *Appearance & Themes (Giao diện & Chủ đề)* — `fth-sun`
         3. *SQL Editor (Trình soạn thảo)* — `fth-edit-3`
         4. *Data Grid (Lưới kết quả)* — `fth-grid`
         5. *Query Execution (Thực thi truy vấn)* — `fth-play-circle`
     - **Cột phải (VBox có ScrollPane)**:
       - Hiển thị các khối tùy chọn tương ứng mục đang chọn:
         - Mỗi tùy chọn gồm: Tên thiết lập (Bold), Dòng mô tả hướng dẫn (Subtext mờ), và Điều khiển tương tác bên phải.
   - **Footer Action Bar**:
     - Nút `Restore Defaults` (Khôi phục mặc định).
     - Vùng đệm co giãn (`Pane HBox.hgrow="ALWAYS"`).
     - Nút `Cancel` (Hủy bỏ).
     - Nút `Save & Apply` (Lưu & Áp dụng ngay) — nút nổi bật (Accent).

---

## 4. Configuration Schema and Defaults
```json
{
  "general": {
    "appName": "SQL IDE",
    "environment": "Production",
    "restoreSessionOnStartup": true,
    "autoSave": true,
    "autoSaveIntervalSeconds": 30
  },
  "appearance": {
    "theme": "Primer Dark",
    "density": "compact",
    "uiFontFamily": "Inter",
    "uiFontSize": 13
  },
  "editor": {
    "fontFamily": "Consolas",
    "fontSize": 13,
    "tabSize": 4,
    "insertSpaces": true,
    "wordWrap": false,
    "showLineNumbers": true,
    "minimap": false,
    "intellisense": true,
    "keywordCase": "upper"
  },
  "grid": {
    "maxRows": 5000,
    "fontSize": 12,
    "nullDisplayString": "NULL",
    "showRowNumbers": true
  },
  "query": {
    "timeoutSeconds": 60,
    "stopOnError": true,
    "autoShowResultsPanel": true
  }
}
```

---

## 5. Scope Definition
- **In Scope**:
  - Model `IdeSettings`: Lưu trữ toàn bộ các thuộc tính cấu hình.
  - Service `SettingsService`: Đọc/ghi cấu hình nguyên tử từ file `~/.lltool/config/settings.json`, đồng bộ với `ConfigurationService`.
  - Dialog `SettingsDialog`: FXML, CSS, Controller 2 cột theo đúng layout của Python.
  - Tích hợp Hot-Apply: Thay đổi theme, cỡ chữ, word wrap cập nhật ngay trên giao diện và Monaco Editor.
  - Phím tắt `Ctrl+,` và nút bánh răng trên Title Bar.
  - Bộ kiểm thử tự động `SettingsAndThemesTest`.
- **Out of Scope**:
  - Import trực tiếp theme từ file `.vsix` của bên thứ ba (chức năng mở rộng trong tương lai).

---

## 6. Acceptance Criteria
- [ ] Nút Cài đặt xuất hiện đúng vị trí ngay sau icon chuyển đổi Sáng/Tối trên Title Bar.
- [ ] Bấm phím tắt `Ctrl+,` hoặc nút Cài đặt mở hộp thoại Settings.
- [ ] Hộp thoại Cài đặt có 2 cột Master-Detail hiển thị 5 danh mục rõ ràng.
- [ ] Hỗ trợ chuyển đổi đầy đủ 7 theme AtlantaFX và đồng bộ sang Monaco Editor.
- [ ] Thay đổi thiết lập có hiệu lực ngay lập tức (Hot-Apply) mà không cần khởi động lại.
- [ ] Thiết lập được lưu bền vững vào `settings.json` và tự động nạp lại khi mở app.
- [ ] Tất cả unit tests và regression suite đều PASS 100%.

