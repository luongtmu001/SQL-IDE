# STEP 01 – JAVA DESKTOP APPLICATION UI DEVELOPMENT

**Project:** Java Desktop Application
**Phase:** 01 – UI Foundation
**Framework:** JavaFX
**Build Tool:** Maven
**Architecture:** Modular UI Architecture
**Design Reference:** JetBrains DataGrip + Modern Desktop IDE

---

# 1. MỤC TIÊU

Xây dựng giao diện nền tảng cho ứng dụng Desktop bằng JavaFX.

Giai đoạn này chỉ tập trung vào UI/UX và kiến trúc giao diện. Không triển khai nghiệp vụ hoặc kết nối dữ liệu thực tế.

## 1.1. Mục tiêu chính

* Xây dựng Main Window hiện đại.
* Giao diện có khả năng tùy chỉnh cao.
* Hỗ trợ Dark Theme và Light Theme.
* Các khu vực giao diện có thể thu gọn, mở rộng và thay đổi kích thước.
* Hỗ trợ nhiều tab và nhiều khu vực làm việc.
* Các component có thể tái sử dụng.
* CSS được tách riêng khỏi Java.
* Có khả năng mở rộng bằng module trong tương lai.
* Hạn chế phụ thuộc vào một thư viện UI duy nhất.

## 1.2. Nguyên tắc

1. UI phải độc lập với nghiệp vụ.
2. Không truy cập database trực tiếp từ Controller.
3. Không viết toàn bộ giao diện trong một file Java.
4. Không viết CSS trực tiếp trong FXML.
5. Component dùng chung phải được thiết kế để tái sử dụng.
6. Các khu vực chính phải có khả năng thay thế hoặc mở rộng.
7. Không triển khai những module chưa thuộc phạm vi giai đoạn này.

---

# 2. CÔNG NGHỆ

| Thành phần           | Công nghệ      |
| -------------------- | -------------- |
| Programming Language | Java           |
| Desktop UI           | JavaFX         |
| Build Tool           | Maven          |
| UI Theme             | AtlantaFX      |
| UI Components        | ControlsFX     |
| Icon                 | Ikonli         |
| UI Layout            | JavaFX Layout  |
| UI Definition        | FXML           |
| Styling              | JavaFX CSS     |
| UI Designer          | Scene Builder  |
| Editor thử nghiệm    | RichTextFX     |
| Web Component        | JavaFX WebView |
| Testing              | JUnit 5        |

## 2.1. Quy tắc sử dụng thư viện

### JavaFX

Sử dụng cho:

* Main Window.
* Layout.
* TreeView.
* TabPane.
* SplitPane.
* Menu.
* Toolbar.
* TableView.
* Dialog.

### AtlantaFX

Sử dụng cho:

* Theme.
* Màu sắc nền.
* Typography.
* Styling các control cơ bản.
* Dark/Light mode.

### ControlsFX

Sử dụng cho:

* Notification.
* Popover.
* Searchable controls.
* Dialog nâng cao.

### Ikonli

Sử dụng cho:

* Toolbar icons.
* Sidebar icons.
* Context menu icons.
* Status icons.

### RichTextFX

Dùng thử nghiệm:

* SQL Editor.
* Syntax Highlighting.
* Line Numbers.
* Code Selection.

### JavaFX WebView

Chỉ thử nghiệm khi cần tích hợp thành phần HTML/JavaScript.

Lưu ý: WebView sử dụng WebKit, không phải Chromium. Cần kiểm tra khả năng tương thích với Monaco Editor trước khi quyết định sử dụng.

---

# 3. KIẾN TRÚC THƯ MỤC

Tổ chức project theo hướng phân tách UI, component, controller, theme và tài nguyên.

```text
JavaDesktopApp/
│
├── pom.xml
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── app/
│   │   │       │
│   │   │       ├── Main.java
│   │   │       │
│   │   │       ├── application/
│   │   │       │   ├── AppLauncher.java
│   │   │       │   └── AppContext.java
│   │   │       │
│   │   │       ├── ui/
│   │   │       │   ├── mainwindow/
│   │   │       │   ├── components/
│   │   │       │   ├── layout/
│   │   │       │   ├── panels/
│   │   │       │   ├── tabs/
│   │   │       │   ├── dialogs/
│   │   │       │   └── notifications/
│   │   │       │
│   │   │       ├── controllers/
│   │   │       │   ├── MainController.java
│   │   │       │   ├── SidebarController.java
│   │   │       │   ├── EditorController.java
│   │   │       │   └── ResultsController.java
│   │   │       │
│   │   │       ├── services/
│   │   │       │   ├── ThemeService.java
│   │   │       │   ├── LayoutService.java
│   │   │       │   └── NavigationService.java
│   │   │       │
│   │   │       ├── models/
│   │   │       │
│   │   │       └── utils/
│   │   │
│   │   └── resources/
│   │       │
│   │       ├── fxml/
│   │       │   ├── main-window.fxml
│   │       │   ├── sidebar.fxml
│   │       │   ├── editor.fxml
│   │       │   ├── results.fxml
│   │       │   └── status-bar.fxml
│   │       │
│   │       ├── css/
│   │       │   ├── base.css
│   │       │   ├── layout.css
│   │       │   ├── components.css
│   │       │   ├── editor.css
│   │       │   ├── dark-theme.css
│   │       │   └── light-theme.css
│   │       │
│   │       ├── icons/
│   │       │
│   │       └── images/
│   │
│   └── test/
│       └── java/
│
└── README.md
```

Nguyên tắc: Không tạo sẵn quá nhiều class hoặc module rỗng. Chỉ tạo thành phần khi bắt đầu triển khai chức năng tương ứng.

---

# 4. THIẾT KẾ MAIN WINDOW

## 4.1. Tổng thể giao diện

Thiết kế theo phong cách DataGrip kết hợp các đặc điểm hiện đại của VS Code.

```text
┌──────────────────────────────────────────────────────────────┐
│                         TITLE BAR                             │
├──────────────────────────────────────────────────────────────┤
│ FILE  EDIT  VIEW  NAVIGATE  TOOLS  WINDOW  HELP               │
├──────────────────────────────────────────────────────────────┤
│ TOOLBAR                                                       │
├───────────────┬──────────────────────────────────────────────┤
│               │                                              │
│ DATABASE      │                 EDITOR TABS                  │
│ EXPLORER      ├──────────────────────────────────────────────┤
│               │                                              │
│ Connections   │                 SQL EDITOR                   │
│               │                                              │
│ Database      │                                              │
│ Tables        │                                              │
│ Views         │                                              │
│ Functions     │                                              │
│ Procedures    │                                              │
│               │                                              │
│               ├──────────────────────────────────────────────┤
│               │ RESULTS | MESSAGES | TERMINAL                │
│               ├──────────────────────────────────────────────┤
│               │                                              │
│               │                 RESULTS GRID                 │
│               │                                              │
├───────────────┴──────────────────────────────────────────────┤
│ STATUS BAR                                                    │
└──────────────────────────────────────────────────────────────┘
```

## 4.2. Title Bar

Thành phần:

* Application Icon.
* Application Name.
* Window Controls.
* Window Title.

Yêu cầu:

* Hỗ trợ Windows.
* Có thể tùy chỉnh màu sắc.
* Không ảnh hưởng đến khả năng resize và maximize cửa sổ.

## 4.3. Menu Bar

Các menu:

| Menu     | Nội dung                     |
| -------- | ---------------------------- |
| File     | New, Open, Save, Exit        |
| Edit     | Undo, Redo, Cut, Copy, Paste |
| View     | Theme, Panels, Layout        |
| Navigate | Điều hướng                   |
| Tools    | Công cụ                      |
| Window   | Quản lý cửa sổ               |
| Help     | About, Documentation         |

Giai đoạn này chỉ tạo giao diện và thao tác mẫu.

## 4.4. Toolbar

Các thành phần:

* New Tab.
* Open.
* Save.
* Run.
* Stop.
* Refresh.
* Search.
* Settings.

Yêu cầu:

* Icon thống nhất.
* Tooltip.
* Có thể tùy chỉnh vị trí.
* Hỗ trợ nhóm toolbar.

## 4.5. Database Explorer

Sử dụng JavaFX TreeView kết hợp custom cell.

Cấu trúc dữ liệu mẫu:

```text
Database Explorer
│
├── Connection 01
│   ├── Database
│   │   ├── Tables
│   │   ├── Views
│   │   ├── Functions
│   │   └── Procedures
│   │
│   └── Connection Properties
│
└── Connection 02
    └── Database
        ├── Tables
        ├── Views
        └── Functions
```

Yêu cầu:

* Expand/Collapse.
* Context Menu.
* Search/Filter.
* Custom icons.
* Drag and Drop thử nghiệm.
* Hỗ trợ nhiều connection.
* Có thể thu gọn toàn bộ panel.

Chưa kết nối database thật.

## 4.6. Editor Workspace

Editor Workspace cần hỗ trợ:

* Multiple Tabs.
* Tab Close.
* Tab Rename.
* Tab Context Menu.
* Tab Selection.
* Dirty State Indicator.
* Split Editor thử nghiệm.

Trong giai đoạn đầu sử dụng TextArea hoặc RichTextFX để dựng giao diện.

## 4.7. Results Panel

Sử dụng JavaFX TableView.

Các thành phần:

* Results.
* Messages.
* Terminal.

Results Grid cần có:

* Column Header.
* Row Number.
* Cell Selection.
* Horizontal Scroll.
* Vertical Scroll.
* Context Menu.
* Empty State.
* Loading State.

Sử dụng dữ liệu giả lập.

## 4.8. Status Bar

Thông tin:

* Application Status.
* Current Workspace.
* Current Tab.
* Editor Position.
* Encoding.
* Theme.
* Notification Indicator.

---

# 5. HỆ THỐNG THEME

Xây dựng Theme Service để quản lý giao diện.

## 5.1. Dark Theme

Yêu cầu:

* Background tối.
* Panel phân biệt bằng độ sáng.
* Border nhẹ.
* Text dễ đọc.
* Highlight rõ ràng.
* Màu trạng thái nhất quán.

## 5.2. Light Theme

Yêu cầu:

* Background sáng.
* Panel phân cấp rõ.
* Border nhẹ.
* Không sử dụng màu quá chói.
* Đảm bảo độ tương phản.

## 5.3. Theme Service

Tạo:

```text
ThemeService.java
```

Nhiệm vụ:

* Switch Theme.
* Load Theme.
* Apply CSS.
* Quản lý Theme hiện tại.
* Chuẩn bị cơ chế lưu Theme Preference.

Sử dụng CSS Variables thông qua cơ chế JavaFX CSS phù hợp; không giả định JavaFX CSS hỗ trợ đầy đủ cú pháp CSS Variables như trình duyệt.

---

# 6. HỆ THỐNG COMPONENT

Xây dựng các component dùng chung.

| Component        | Chức năng         |
| ---------------- | ----------------- |
| AppButton        | Button thống nhất |
| IconButton       | Button có icon    |
| SearchField      | Ô tìm kiếm        |
| AppTab           | Tab tùy chỉnh     |
| PanelHeader      | Header cho panel  |
| EmptyState       | Trạng thái rỗng   |
| LoadingView      | Trạng thái tải    |
| NotificationView | Thông báo         |
| ContextMenu      | Menu chuột phải   |
| StatusIndicator  | Trạng thái        |

Mỗi component phải:

* Có thể tái sử dụng.
* Có CSS riêng.
* Có trạng thái hover/focus/disabled.
* Không phụ thuộc vào một màn hình cụ thể.

---

# 7. LAYOUT MANAGEMENT

Đây là phần cần ưu tiên thiết kế kỹ vì ảnh hưởng trực tiếp đến khả năng mở rộng ứng dụng.

## 7.1. Panel Management

Các panel cần hỗ trợ:

* Show/Hide.
* Collapse/Expand.
* Resize.
* Restore Default Layout.

## 7.2. Split Layout

Thử nghiệm:

* Horizontal Split.
* Vertical Split.
* Editor Split.
* Results Resize.

## 7.3. Docking

Nghiên cứu khả năng:

* Dock Panel.
* Undock Panel.
* Floating Window.
* Drag and Drop Panel.
* Save Layout.
* Restore Layout.

Không nên tự xây dựng docking engine ngay từ đầu.

Trước tiên, đánh giá các thư viện docking tương thích JavaFX. Nếu không đáp ứng yêu cầu, mới xem xét xây dựng giải pháp riêng.

---

# 8. CÁC GIAI ĐOẠN TRIỂN KHAI

## Phase 1.1 – Project Foundation

Công việc:

* Khởi tạo Maven Project.
* Cấu hình JavaFX.
* Cấu hình AtlantaFX.
* Cấu hình ControlsFX.
* Cấu hình Ikonli.
* Thiết lập cấu trúc thư mục.
* Thiết lập Application Lifecycle.

Kết quả:

* Project chạy được.
* Hiển thị Main Window.
* Không có lỗi dependency.

## Phase 1.2 – Main Window

Công việc:

* Title Bar.
* Menu Bar.
* Toolbar.
* Main Layout.
* Status Bar.

Kết quả:

* Hoàn thiện khung giao diện chính.

## Phase 1.3 – Database Explorer

Công việc:

* TreeView.
* Custom Tree Cell.
* Context Menu.
* Search.
* Expand/Collapse.

Kết quả:

* Explorer hoạt động với dữ liệu mẫu.

## Phase 1.4 – Editor Workspace

Công việc:

* Editor Tabs.
* Tab Management.
* Editor Area.
* Split Editor.
* RichTextFX PoC.

Kết quả:

* Có thể mở nhiều tab và chuyển đổi giữa các editor.

## Phase 1.5 – Results Panel

Công việc:

* Results Grid.
* Messages.
* Terminal.
* Panel Resize.
* Panel Collapse.

Kết quả:

* Hoàn thiện khu vực kết quả.

## Phase 1.6 – Theme & Components

Công việc:

* Dark Theme.
* Light Theme.
* Theme Switching.
* Shared Components.
* Icon System.
* Tooltip.
* Notification.

Kết quả:

* UI đồng nhất và có thể tùy chỉnh.

## Phase 1.7 – Layout Management

Công việc:

* Panel Resize.
* Show/Hide.
* Split Layout.
* Layout Reset.
* Docking PoC.

Kết quả:

* Có thể thay đổi bố cục giao diện.

## Phase 1.8 – UI Polish

Công việc:

* Hover Effects.
* Focus States.
* Transitions.
* Loading State.
* Empty State.
* Error State.
* Responsive Window Layout.
* UI Performance Testing.

Kết quả:

* Hoàn thiện giao diện Prototype.

---

# 9. KIỂM THỬ

## 9.1. UI Testing

* Mở/đóng tab.
* Chuyển đổi tab.
* Thu gọn Explorer.
* Resize Results Panel.
* Chuyển Theme.
* Reset Layout.
* Mở Context Menu.
* Thử nghiệm Split Editor.

## 9.2. Performance Testing

Kiểm tra:

* Thời gian khởi động.
* RAM khi khởi động.
* RAM khi mở nhiều tab.
* CPU khi thao tác.
* Độ mượt khi resize.
* Độ mượt khi scroll TreeView.
* Hiệu năng TableView với dữ liệu lớn.

Không kết luận hiệu năng chỉ từ một lần chạy. Cần đo trên cùng môi trường và cùng bộ dữ liệu thử nghiệm.

## 9.3. Cross-platform

Kiểm tra:

* Windows 11.
* macOS.

Giai đoạn đầu ưu tiên Windows 11. macOS sẽ được kiểm thử khi có môi trường phù hợp.

---

# 10. TIÊU CHÍ NGHIỆM THU

| Tiêu chí               | Mức độ    |
| ---------------------- | --------- |
| Main Window hoàn chỉnh | Bắt buộc  |
| Dark/Light Theme       | Bắt buộc  |
| Database Explorer      | Bắt buộc  |
| Multiple Editor Tabs   | Bắt buộc  |
| Results Grid           | Bắt buộc  |
| Component tái sử dụng  | Bắt buộc  |
| CSS tách biệt          | Bắt buộc  |
| Panel Resize           | Bắt buộc  |
| Panel Collapse         | Bắt buộc  |
| Split Editor           | Mong muốn |
| Docking                | PoC       |
| Monaco Integration     | PoC       |
| Layout Persistence     | Mong muốn |

---

# 11. NHỮNG VIỆC KHÔNG THUỘC PHẠM VI

Không triển khai trong STEP 01:

* Database Connection.
* SQL Execution.
* Query Engine.
* Database Explorer Backend.
* Authentication.
* User Management.
* Plugin System.
* Python Runtime.
* REST API.
* Web Application.
* Business Logic.
* Data Persistence nghiệp vụ.

Có thể sử dụng dữ liệu giả lập để kiểm thử giao diện.

---

# 12. KẾT QUẢ CUỐI CÙNG

Sau khi hoàn thành STEP 01, ứng dụng phải có:

1. Main Window hoàn chỉnh.
2. Giao diện hiện đại theo phong cách DataGrip.
3. Dark/Light Theme.
4. Database Explorer với dữ liệu mẫu.
5. Multiple Editor Tabs.
6. Results Grid.
7. Messages và Terminal.
8. Component dùng chung.
9. Layout có thể thay đổi.
10. Cấu trúc code đủ rõ ràng để tiếp tục phát triển.

**Nguyên tắc chuyển sang STEP 02:**

Chỉ bắt đầu xây dựng Core Architecture và nghiệp vụ khi STEP 01 đã được nghiệm thu.

Không được tự ý thay đổi công nghệ, kiến trúc hoặc cấu trúc đã thống nhất nếu chưa đánh giá tác động và được phê duyệt.
