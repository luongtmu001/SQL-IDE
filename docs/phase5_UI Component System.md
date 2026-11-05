# PHASE 05 – UI COMPONENT SYSTEM IMPLEMENTATION PLAN

## 1. MỤC TIÊU

Xây dựng hệ thống UI Component dùng chung cho JavaFX Desktop Application.

Phase 5 tập trung vào:

* Chuẩn hóa Design System.
* Xây dựng Theme System.
* Xây dựng Icon System.
* Chuẩn hóa các UI Controls.
* Xây dựng Layout Components.
* Xây dựng Navigation Components.
* Xây dựng Data Display Components.
* Xây dựng Notification và Dialog System.
* Chuẩn hóa trạng thái Loading, Empty, Error.
* Tạo cơ chế tái sử dụng component giữa các module.

### Kết quả mong muốn

Sau Phase 5, dự án có một hệ thống giao diện thống nhất. Khi phát triển tính năng mới, Developer có thể sử dụng lại các component có sẵn thay vì tự xây dựng Button, Dialog, Tab, Context Menu, Input hoặc Panel cho từng màn hình.

---

# 2. PHẠM VI TRIỂN KHAI

## 2.1. Nội dung thuộc Phase 5

| Nhóm             | Nội dung                            |
| ---------------- | ----------------------------------- |
| Design System    | Design Tokens, Typography, Spacing  |
| Theme System     | Dark Theme, Light Theme             |
| Icon System      | Icon Registry, SVG, Ikonli          |
| Basic Controls   | Button, Input, Checkbox, Select     |
| Navigation       | Tabs, Breadcrumb, Context Menu      |
| Layout           | Split Pane, Dockable Panel, Toolbar |
| Data Display     | Tree View, Table, List              |
| Feedback         | Dialog, Toast, Notification         |
| State Components | Loading, Empty, Error               |
| Component API    | Component Factory, Configuration    |
| Documentation    | Component Catalog, Usage Guide      |

## 2.2. Nội dung chưa triển khai

Không triển khai Business Logic của:

* Database Explorer.
* SQL Editor.
* Query Execution.
* IntelliSense.
* Schema Compare.
* Database Administration.
* Plugin System.
* Python Extension Runtime.

Các component cơ bản có thể được xây dựng ở Phase 5, nhưng logic nghiệp vụ sẽ triển khai ở các Phase tính năng tương ứng.

Ví dụ:

* Xây dựng `DataTable` ở Phase 5.
* Xây dựng `QueryResultGrid` sử dụng `DataTable` ở Phase phát triển Query Execution.

---

# 3. KIẾN TRÚC UI COMPONENT SYSTEM

## 3.1. Kiến trúc đề xuất

```text
┌──────────────────────────────────────────────┐
│                 Feature Modules               │
│                                              │
│  Database Explorer | SQL Editor | Settings   │
│  Schema Compare    | Query Results           │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│               UI Component System            │
│                                              │
│  Basic Controls                              │
│  Navigation Components                       │
│  Layout Components                           │
│  Data Display Components                     │
│  Feedback Components                         │
│  State Components                            │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│                  Design System               │
│                                              │
│  Design Tokens                               │
│  Theme Manager                               │
│  Typography                                  │
│  Icon Registry                               │
│  Component Styles                            │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│                JavaFX Platform               │
│                                              │
│  JavaFX Controls                             │
│  FXML                                        │
│  CSS                                         │
│  AtlantaFX                                  │
│  ControlsFX                                 │
│  Ikonli                                     │
└──────────────────────────────────────────────┘
```

## 3.2. Nguyên tắc thiết kế

1. JavaFX là nền tảng UI chính.
2. FXML dùng để định nghĩa cấu trúc giao diện.
3. CSS được tách riêng.
4. Không viết CSS inline trong Java nếu không thực sự cần.
5. Không tạo component chỉ để bọc một JavaFX Control mà không bổ sung giá trị.
6. Component phải có API rõ ràng.
7. Component không chứa Business Logic.
8. Không phụ thuộc trực tiếp vào Database hoặc Infrastructure.
9. Theme phải được quản lý tập trung.
10. Component phải hỗ trợ Dark/Light Theme.
11. Ưu tiên JavaFX Controls trước khi sử dụng thư viện bên ngoài.
12. Chỉ sử dụng WebView cho những thành phần thực sự cần công nghệ Web.

---

# 4. CẤU TRÚC MODULE

Đề xuất tổ chức:

```text
ui/
│
├── ui-design/
│   └── src/main/
│       ├── java/
│       │   └── com/lltool/ui/design/
│       │       ├── theme/
│       │       ├── tokens/
│       │       ├── typography/
│       │       └── icons/
│       │
│       └── resources/
│           ├── themes/
│           ├── fonts/
│           └── icons/
│
├── ui-components/
│   └── src/main/
│       ├── java/
│       │   └── com/lltool/ui/components/
│       │       ├── controls/
│       │       ├── navigation/
│       │       ├── layout/
│       │       ├── data/
│       │       ├── feedback/
│       │       └── state/
│       │
│       └── resources/
│           ├── fxml/
│           └── css/
│
└── ui-gallery/
    └── src/main/
        ├── java/
        └── resources/
```

### Giải thích

**ui-design**

Quản lý Design System, Theme, Icon và Typography.

**ui-components**

Chứa các component có thể tái sử dụng.

**ui-gallery**

Ứng dụng mẫu để hiển thị, kiểm thử và đánh giá component.

Không bắt buộc phải tạo cả ba Maven module ngay lập tức. Nếu quy mô hiện tại chưa cần tách module, có thể tổ chức thành package trước và tách sau.

---

# 5. CÁC HẠNG MỤC TRIỂN KHAI

## 5.1. Phase 5.1 – Design System

### Mục tiêu

Xây dựng quy chuẩn giao diện thống nhất.

### Nội dung

* Color Palette.
* Typography.
* Font Family.
* Font Size.
* Font Weight.
* Spacing.
* Border Radius.
* Border Width.
* Component Height.
* Icon Size.
* Shadow.
* Focus State.
* Hover State.
* Disabled State.

### Design Tokens

Đề xuất các nhóm token:

```text
Design Tokens
│
├── Colors
│   ├── Background
│   ├── Surface
│   ├── Primary
│   ├── Secondary
│   ├── Text
│   ├── Border
│   ├── Success
│   ├── Warning
│   └── Error
│
├── Typography
│   ├── Font Family
│   ├── Font Size
│   └── Font Weight
│
├── Spacing
│   ├── XS
│   ├── SM
│   ├── MD
│   ├── LG
│   └── XL
│
├── Radius
│
├── Component Sizes
│
└── Effects
```

### Font

Định hướng:

* Inter: UI Text.
* JetBrains Mono: SQL, Code, Technical Text.

Cần kiểm tra khả năng hiển thị trên Windows và macOS trước khi đóng gói font.

### Yêu cầu

* Không hardcode màu sắc ở từng component.
* Không để mỗi module tự định nghĩa spacing riêng.
* Có quy chuẩn kích thước control.
* Có quy chuẩn trạng thái hover, focus, disabled.
* Các token phải có khả năng thay đổi theo Theme.

---

## 5.2. Phase 5.2 – Theme System

### Mục tiêu

Xây dựng hệ thống quản lý giao diện toàn ứng dụng.

### Theme ban đầu

1. Dark Theme.
2. Light Theme.

Có thể bổ sung theme khác trong tương lai.

### Cấu trúc

```text
themes/
│
├── base.css
├── dark.css
├── light.css
│
└── components/
    ├── button.css
    ├── input.css
    ├── dialog.css
    ├── tabs.css
    ├── tree.css
    └── table.css
```

### ThemeManager

Chức năng:

* Load Theme.
* Switch Theme.
* Apply Theme.
* Notify Theme Changes.
* Persist Theme Preference.
* Reload Theme khi cần.

API định hướng:

```java
themeManager.setTheme(Theme.DARK);

themeManager.setTheme(Theme.LIGHT);

Theme currentTheme = themeManager.getCurrentTheme();
```

### Yêu cầu

* Theme được quản lý tập trung.
* Không để từng Controller tự đổi CSS.
* Theme áp dụng nhất quán trên Main Window và cửa sổ phụ.
* Các component phải phản ứng đúng khi đổi Theme.
* Theme Preference được lưu thông qua Configuration/Storage đã xây dựng ở Phase 3.

### Lưu ý kỹ thuật

JavaFX CSS không hỗ trợ đầy đủ CSS Custom Properties giống trình duyệt.

Không nên thiết kế toàn bộ Theme System dựa trên giả định rằng JavaFX CSS hoạt động giống CSS của Chrome.

Có thể sử dụng:

* Các stylesheet riêng theo Theme.
* JavaFX Pseudo-class.
* Style class.
* Centralized ThemeManager.

---

## 5.3. Phase 5.3 – Icon System

### Mục tiêu

Chuẩn hóa icon trên toàn ứng dụng.

### Công nghệ

* Ikonli.
* SVG.
* Icon Font nếu thực sự cần.

### Nguồn icon đề xuất

* Lucide.
* Material Design Icons.
* Font Awesome.

Ưu tiên Lucide vì phong cách line icon gọn, hiện đại và phù hợp với giao diện IDE.

### Icon Registry

Ví dụ:

```text
Icon Registry
│
├── Application
│   ├── app
│   ├── settings
│   └── search
│
├── Navigation
│   ├── home
│   ├── database
│   └── terminal
│
├── Editor
│   ├── file
│   ├── save
│   └── close
│
├── Database
│   ├── server
│   ├── table
│   └── schema
│
└── Actions
    ├── add
    ├── edit
    ├── delete
    └── refresh
```

### Yêu cầu

* Không sử dụng icon Unicode tùy tiện.
* Không hardcode đường dẫn icon trong từng Controller.
* Có quy chuẩn kích thước.
* Icon phải hiển thị tốt trên Dark/Light Theme.
* Ưu tiên SVG khi cần kiểm soát màu sắc và chất lượng hiển thị.

---

## 5.4. Phase 5.4 – Basic Controls

### Mục tiêu

Xây dựng bộ control cơ bản.

### Danh sách

| Component       | Mô tả               |
| --------------- | ------------------- |
| PrimaryButton   | Nút hành động chính |
| SecondaryButton | Nút phụ             |
| IconButton      | Nút icon            |
| ToggleButton    | Nút bật/tắt         |
| TextField       | Nhập văn bản        |
| SearchField     | Ô tìm kiếm          |
| PasswordField   | Nhập mật khẩu       |
| ComboBox        | Danh sách lựa chọn  |
| CheckBox        | Checkbox            |
| RadioButton     | Radio               |
| Switch          | Công tắc            |
| Badge           | Nhãn trạng thái     |
| Separator       | Đường phân cách     |

### Yêu cầu

Mỗi component cần xác định:

* API.
* FXML Usage.
* CSS Style.
* Theme Support.
* Disabled State.
* Focus State.
* Hover State.
* Accessibility.
* Validation nếu phù hợp.

Không cần viết lại tất cả JavaFX Controls.

Chỉ tạo Custom Control khi cần:

* Chuẩn hóa giao diện.
* Bổ sung hành vi.
* Bổ sung khả năng tái sử dụng.
* Kết hợp nhiều control thành một thành phần có ý nghĩa.

---

## 5.5. Phase 5.5 – Navigation Components

### Mục tiêu

Xây dựng component điều hướng dùng chung.

### Danh sách

| Component       | Mô tả               |
| --------------- | ------------------- |
| Tab Component   | Hệ thống Tab        |
| Breadcrumb      | Điều hướng phân cấp |
| Context Menu    | Menu chuột phải     |
| Navigation Item | Mục điều hướng      |
| Sidebar Item    | Mục Sidebar         |
| Toolbar         | Thanh công cụ       |
| Command Menu    | Menu lệnh           |
| Search Box      | Tìm kiếm            |

### Tab System

Đây là component quan trọng đối với ứng dụng IDE.

Yêu cầu:

* Multiple Tabs.
* Active Tab.
* Close Tab.
* Close Others.
* Close All.
* Tab Context Menu.
* Tab Selection.
* Tab Dirty State.
* Tab Icon.
* Tab Title.
* Overflow Handling.

Lưu ý: Phase 5 chỉ xây dựng Tab Component tổng quát. Quản lý nội dung SQL Editor, trạng thái file và lưu file sẽ triển khai trong Feature Module.

---

## 5.6. Phase 5.6 – Layout Components

### Mục tiêu

Xây dựng các thành phần bố cục dùng chung.

### Danh sách

| Component        | Mô tả                     |
| ---------------- | ------------------------- |
| SplitPane        | Chia khu vực              |
| DockablePanel    | Panel có thể Dock         |
| CollapsiblePanel | Panel thu gọn             |
| ResizablePanel   | Panel thay đổi kích thước |
| ToolbarContainer | Container Toolbar         |
| PanelHeader      | Header của Panel          |
| PanelContainer   | Container                 |
| EmptyWorkspace   | Workspace trống           |

### Dockable Panel

Đây là component quan trọng đối với IDE.

Cần hỗ trợ:

* Dock Left.
* Dock Right.
* Dock Bottom.
* Undock.
* Resize.
* Collapse.
* Restore.
* Hide/Show.

### Công nghệ

Ưu tiên JavaFX:

* SplitPane.
* BorderPane.
* AnchorPane.
* StackPane.
* Custom JavaFX Control.

Có thể đánh giá thư viện docking bên ngoài nếu JavaFX thuần không đáp ứng đủ yêu cầu.

Không nên tự xây dựng một hệ thống Docking phức tạp ngay từ đầu.

---

## 5.7. Phase 5.7 – Data Display Components

### Mục tiêu

Xây dựng component hiển thị dữ liệu dùng chung.

### Danh sách

| Component    | Mô tả              |
| ------------ | ------------------ |
| DataTable    | Bảng dữ liệu       |
| DataTree     | Cây dữ liệu        |
| DataList     | Danh sách          |
| PropertyGrid | Bảng thuộc tính    |
| KeyValueView | Hiển thị Key/Value |
| DataCard     | Thẻ dữ liệu        |

### DataTable

Yêu cầu:

* Column Definition.
* Row Data.
* Cell Formatting.
* Column Resize.
* Column Visibility.
* Sorting.
* Selection.
* Empty State.
* Loading State.
* Theme Support.

Không triển khai:

* Query Execution.
* Database Result Mapping.
* SQL Result Pagination.
* Database-specific Data Types.

Các tính năng trên sẽ được triển khai trong module nghiệp vụ tương ứng.

### Hiệu năng

Không tải toàn bộ dữ liệu lớn lên JavaFX TableView cùng lúc.

Cần đánh giá:

* Virtualized Controls.
* ObservableList.
* Lazy Loading.
* Pagination.
* Data Provider API.

Chỉ tối ưu khi có số liệu đo thực tế.

---

## 5.8. Phase 5.8 – Feedback Components

### Mục tiêu

Chuẩn hóa cách ứng dụng phản hồi với người dùng.

### Danh sách

| Component      | Mô tả          |
| -------------- | -------------- |
| Dialog         | Hộp thoại      |
| ConfirmDialog  | Xác nhận       |
| AlertDialog    | Cảnh báo       |
| Toast          | Thông báo ngắn |
| Notification   | Thông báo      |
| ProgressDialog | Tiến trình     |
| Tooltip        | Gợi ý          |
| StatusMessage  | Trạng thái     |

### Yêu cầu

* Có chuẩn về màu sắc.
* Có chuẩn về icon.
* Có chuẩn về tiêu đề và nội dung.
* Hỗ trợ Dark/Light Theme.
* Có thể gọi từ nhiều module.
* Không phụ thuộc vào Business Logic.

---

## 5.9. Phase 5.9 – State Components

### Mục tiêu

Chuẩn hóa các trạng thái giao diện.

### Các trạng thái

**Loading State**

* Loading Indicator.
* Progress Bar.
* Skeleton nếu phù hợp.

**Empty State**

* Không có dữ liệu.
* Chưa có kết nối.
* Chưa có file.
* Chưa có kết quả.

**Error State**

* Lỗi tải dữ liệu.
* Lỗi kết nối.
* Lỗi thao tác.
* Có hành động Retry khi phù hợp.

### Yêu cầu

Các trạng thái này phải được tái sử dụng giữa nhiều module.

Không tạo mỗi màn hình một kiểu Loading hoặc Empty State khác nhau.

---

## 5.10. Phase 5.10 – UI Component Gallery

### Mục tiêu

Xây dựng ứng dụng mẫu để quản lý và kiểm tra component.

Đây là hạng mục tôi khuyến nghị thực hiện bắt buộc.

### Nội dung

Tạo một ứng dụng UI Gallery riêng, có thể mở từng nhóm component.

```text
UI Gallery
│
├── Design System
│   ├── Colors
│   ├── Typography
│   └── Spacing
│
├── Basic Controls
│   ├── Buttons
│   ├── Inputs
│   └── Selection
│
├── Navigation
│   ├── Tabs
│   ├── Context Menu
│   └── Toolbar
│
├── Layout
│   ├── SplitPane
│   ├── Dockable Panel
│   └── Collapsible Panel
│
├── Data Display
│   ├── Table
│   ├── Tree
│   └── List
│
└── Feedback
    ├── Dialog
    ├── Toast
    └── Notification
```

### Yêu cầu

* Hiển thị tất cả component.
* Có dữ liệu mẫu.
* Có thể thay đổi Theme.
* Có thể kiểm tra trạng thái component.
* Có thể kiểm tra kích thước.
* Có thể kiểm tra hành vi.
* Có thể phát hiện lỗi hiển thị trước khi tích hợp vào tính năng thực tế.

UI Gallery không được chứa Business Logic của ứng dụng chính.

---

## 5.11. Phase 5.11 – Testing & Documentation

### Kiểm thử

* Kiểm tra component API.
* Kiểm tra FXML Loading.
* Kiểm tra Theme Switching.
* Kiểm tra trạng thái Hover/Focus/Disabled.
* Kiểm tra Dialog.
* Kiểm tra Tab.
* Kiểm tra Docking.
* Kiểm tra DataTable.
* Kiểm tra khả năng tái sử dụng.
* Kiểm tra hiệu năng với dữ liệu mẫu.

### Tài liệu

```text
docs/
└── ui/
    ├── design-system.md
    ├── theme-system.md
    ├── icon-system.md
    ├── component-guidelines.md
    ├── layout-components.md
    ├── navigation-components.md
    ├── data-components.md
    └── ui-gallery.md
```

---

# 6. THỨ TỰ TRIỂN KHAI

| Sub-phase | Nội dung                | Mức độ   |
| --------- | ----------------------- | -------- |
| 5.1       | Design System           | Bắt buộc |
| 5.2       | Theme System            | Bắt buộc |
| 5.3       | Icon System             | Bắt buộc |
| 5.4       | Basic Controls          | Bắt buộc |
| 5.5       | Navigation Components   | Bắt buộc |
| 5.6       | Layout Components       | Bắt buộc |
| 5.7       | Data Display Components | Cơ bản   |
| 5.8       | Feedback Components     | Bắt buộc |
| 5.9       | State Components        | Bắt buộc |
| 5.10      | UI Component Gallery    | Bắt buộc |
| 5.11      | Testing & Documentation | Bắt buộc |

Không cần triển khai tất cả component ở mức hoàn thiện cao nhất.

Ưu tiên các component có khả năng được sử dụng trong nhiều tính năng.

---

# 7. TIÊU CHÍ NGHIỆM THU

## Design System

* [ ] Có bộ Design Tokens.
* [ ] Có Typography System.
* [ ] Có quy chuẩn Spacing.
* [ ] Có quy chuẩn Component Size.

## Theme

* [ ] Có Dark Theme.
* [ ] Có Light Theme.
* [ ] Chuyển Theme không cần khởi động lại ứng dụng.
* [ ] Theme áp dụng cho Main Window và cửa sổ phụ.
* [ ] Lưu được Theme Preference.

## Icon

* [ ] Có Icon Registry.
* [ ] Có quy chuẩn kích thước.
* [ ] Icon hiển thị đúng trên hai Theme.

## Components

* [ ] Có Basic Controls.
* [ ] Có Tab Component.
* [ ] Có Context Menu.
* [ ] Có SplitPane.
* [ ] Có Dockable Panel cơ bản.
* [ ] Có DataTable.
* [ ] Có DataTree.
* [ ] Có Dialog.
* [ ] Có Notification.
* [ ] Có Loading/Empty/Error State.

## UI Gallery

* [ ] Có thể xem component tập trung.
* [ ] Có dữ liệu mẫu.
* [ ] Có thể chuyển Theme.
* [ ] Có thể kiểm tra trạng thái component.

## Quality

* [ ] Component không chứa Business Logic.
* [ ] Không có dependency vào Infrastructure.
* [ ] Không hardcode Theme trong Controller.
* [ ] FXML và CSS được tách riêng.
* [ ] Có tài liệu sử dụng component.

---

# 8. KẾT QUẢ ĐẦU RA

Sau Phase 5, dự án cần có:

1. Design System.
2. Design Tokens.
3. Theme Manager.
4. Dark Theme.
5. Light Theme.
6. Icon Registry.
7. Basic Controls.
8. Navigation Components.
9. Layout Components.
10. Data Display Components.
11. Feedback Components.
12. State Components.
13. UI Component Gallery.
14. Component Documentation.

---

# 9. NGUYÊN TẮC PHÁT TRIỂN FEATURE Ở CÁC PHASE TIẾP THEO

Sau khi hoàn thành Phase 5, không tiếp tục xây dựng component theo kiểu dự đoán tất cả nhu cầu tương lai.

Áp dụng quy trình:

```text
Feature Requirement
        │
        ▼
Check Existing Components
        │
        ▼
Can Existing Component Be Reused?
        │
    ┌───┴────┐
    │        │
   Yes       No
    │        │
    ▼        ▼
 Reuse    Design New Component
    │        │
    │        ▼
    │    Add to UI Component System
    │        │
    └────┬───┘
         │
         ▼
    Feature UI
         │
         ▼
    Backend Logic
         │
         ▼
    Integration
         │
         ▼
      Testing
```

Mỗi component mới phải được xem xét:

* Có khả năng tái sử dụng không?
* Có thực sự cần Custom Control không?
* JavaFX có component tương đương chưa?
* Có gây ảnh hưởng hiệu năng không?
* Có hỗ trợ Theme không?
* Có làm tăng độ phức tạp kiến trúc không?

---

# 10. PHASE TIẾP THEO

## PHASE 06 – CORE IDE FEATURE DEVELOPMENT

Bắt đầu triển khai các tính năng thực tế của ứng dụng.

Thứ tự đề xuất:

1. Workspace & Project Management.
2. Database Connection Management.
3. Database Explorer.
4. SQL Editor.
5. Query Execution.
6. Results Grid.
7. Query History.
8. Schema Browser.
9. Các tính năng IDE mở rộng.

Mỗi tính năng sẽ được triển khai theo quy trình:

**UI → Application Use Case → Infrastructure Adapter → Integration → Testing.**

Không cần chờ hoàn thiện toàn bộ UI của tất cả tính năng mới bắt đầu Backend.

---

# KẾT LUẬN

Phase 5 tạo ra nền tảng giao diện có thể tái sử dụng cho toàn bộ Desktop Application.

Mục tiêu không phải tạo thật nhiều JavaFX Custom Controls, mà là xây dựng một hệ thống UI thống nhất, có thể mở rộng, dễ bảo trì và giữ được phong cách thiết kế xuyên suốt ứng dụng.
