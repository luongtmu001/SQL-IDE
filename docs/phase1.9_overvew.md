# STEP 01.9 – CUSTOM TITLE BAR IMPLEMENTATION PLAN

**Project:** Java Desktop Application
**Phase:** UI Foundation
**Technology:** JavaFX + Maven
**Target OS:** Windows 11, định hướng hỗ trợ macOS
**Scope:** Custom Window Title Bar
**Priority:** High

---

# 1. MỤC TIÊU

Thay thế Title Bar mặc định của Windows bằng một Title Bar do ứng dụng tự thiết kế bằng JavaFX.

Mục tiêu:

* Tùy chỉnh hoàn toàn giao diện Title Bar.
* Đồng bộ màu sắc với Theme hiện tại của ứng dụng.
* Hỗ trợ Dark Theme và Light Theme.
* Hỗ trợ Minimize, Maximize, Restore và Close.
* Hỗ trợ kéo cửa sổ.
* Hỗ trợ thay đổi kích thước cửa sổ.
* Duy trì khả năng tương tác quen thuộc của Windows.
* Có thể mở rộng giao diện Title Bar trong tương lai.

## 1.1. Phạm vi

Chỉ thay đổi:

* Window Decoration.
* Title Bar.
* Window Controls.
* Window Drag.
* Window Resize.
* Theme Integration.

Không thay đổi:

* Navbar.
* Toolbar.
* Database Explorer.
* Editor.
* Results Panel.
* Status Bar.
* Business Logic.
* Database Layer.

---

# 2. THIẾT KẾ GIAO DIỆN

## 2.1. Cấu trúc Title Bar

```text
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  APP ICON   APPLICATION NAME       GLOBAL SEARCH    ─  □  ×  │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                     APPLICATION CONTENT                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

## 2.2. Các thành phần

| Component    | Chức năng                       |
| ------------ | ------------------------------- |
| App Icon     | Biểu tượng ứng dụng             |
| App Name     | Tên ứng dụng                    |
| Window Title | Tiêu đề cửa sổ                  |
| Custom Area  | Khu vực có thể bổ sung nội dung |
| Minimize     | Thu nhỏ cửa sổ                  |
| Maximize     | Phóng to cửa sổ                 |
| Restore      | Khôi phục cửa sổ                |
| Close        | Đóng cửa sổ                     |

Thiết kế phải hỗ trợ thay đổi nội dung Title Bar mà không cần sửa logic quản lý cửa sổ.

---

# 3. CÔNG NGHỆ

| Thành phần      | Công nghệ                |
| --------------- | ------------------------ |
| Window          | JavaFX Stage             |
| Window Style    | StageStyle.UNDECORATED   |
| Layout          | HBox, BorderPane         |
| Window Controls | JavaFX Button            |
| Icons           | Ikonli hoặc SVG          |
| Styling         | JavaFX CSS               |
| Theme           | AtlantaFX + ThemeService |
| Event Handling  | JavaFX Events            |

Không cần bổ sung framework mới chỉ để triển khai Title Bar.

---

# 4. KIẾN TRÚC MODULE

Tách chức năng quản lý cửa sổ khỏi MainController.

```text
src/main/java/app/
│
├── application/
│
├── ui/
│   │
│   ├── window/
│   │   ├── CustomWindow.java
│   │   ├── WindowController.java
│   │   ├── WindowStateManager.java
│   │   ├── WindowDragHandler.java
│   │   └── WindowResizeHandler.java
│   │
│   └── titlebar/
│       ├── TitleBar.java
│       ├── TitleBarController.java
│       ├── WindowControls.java
│       └── WindowTitleModel.java
│
└── services/
    └── ThemeService.java
```

Tài nguyên:

```text
src/main/resources/
│
├── fxml/
│   └── titlebar.fxml
│
├── css/
│   ├── window/
│   │   └── window.css
│   │
│   └── titlebar/
│       ├── titlebar.css
│       └── window-controls.css
│
└── icons/
    └── window/
        ├── minimize.svg
        ├── maximize.svg
        ├── restore.svg
        └── close.svg
```

Không bắt buộc phải tạo tất cả class ngay từ đầu. Chỉ tạo khi bắt đầu triển khai chức năng tương ứng.

---

# 5. QUY TẮC WINDOW MANAGEMENT

## 5.1. Khởi tạo Custom Window

Sử dụng:

```java
stage.initStyle(StageStyle.UNDECORATED);
```

Thực hiện trước khi gọi `show()`.

Không thay đổi cấu trúc Main Window ngoài phần cần thiết để thay thế Title Bar.

## 5.2. Minimize

Khi nhấn Minimize:

```java
stage.setIconified(true);
```

Yêu cầu:

* Thu nhỏ cửa sổ.
* Ứng dụng vẫn chạy.
* Có thể khôi phục từ Windows Taskbar.

## 5.3. Maximize / Restore

Quản lý trạng thái cửa sổ:

```text
NORMAL
  │
  └── MAXIMIZE
         │
         └── RESTORE
```

Yêu cầu:

* Icon thay đổi theo trạng thái.
* Double-click Title Bar để Maximize/Restore.
* Không làm mất trạng thái kích thước trước đó.
* Không để cửa sổ vượt quá vùng hiển thị màn hình.

Ưu tiên sử dụng trạng thái maximize của JavaFX Stage thay vì tự tính kích thước cửa sổ.

## 5.4. Close

Khi nhấn Close:

* Thực hiện quy trình đóng ứng dụng hiện có.
* Không tự ý gọi `System.exit()` từ TitleBarController.
* Cho phép Application Lifecycle xử lý việc đóng ứng dụng.

## 5.5. Drag Window

Yêu cầu:

* Kéo cửa sổ bằng vùng trống của Title Bar.
* Không kéo khi click vào Button hoặc Search Field.
* Double-click để Maximize/Restore.
* Hỗ trợ nhiều màn hình.

Không triển khai drag handler theo cách áp dụng cho toàn bộ Title Bar mà không loại trừ các control tương tác.

## 5.6. Resize Window

Cần hỗ trợ:

* Resize cạnh trái.
* Resize cạnh phải.
* Resize cạnh trên.
* Resize cạnh dưới.
* Resize bốn góc.

Yêu cầu:

* Có giới hạn kích thước tối thiểu.
* Không resize khi cửa sổ đang maximize.
* Không làm mất khả năng tương tác của các control bên trong.

Đánh giá phương án resize bằng API JavaFX trước. Nếu cần hỗ trợ Snap và resize chính xác theo hành vi Windows, có thể triển khai Windows-specific adapter sau khi thử nghiệm.

---

# 6. THEME INTEGRATION

Đây là yêu cầu kiến trúc quan trọng.

Title Bar không được chứa màu sắc cố định trong Java code.

Mọi màu sắc phải được quản lý tập trung thông qua ThemeService và CSS.

## 6.1. Design Tokens

Các token cần có:

| Token                      | Ý nghĩa               |
| -------------------------- | --------------------- |
| titlebar-background        | Màu nền               |
| titlebar-foreground        | Màu chữ               |
| titlebar-border            | Viền dưới             |
| titlebar-icon              | Màu icon              |
| titlebar-button-hover      | Màu hover             |
| titlebar-button-pressed    | Màu khi nhấn          |
| titlebar-close-hover       | Màu hover nút Close   |
| titlebar-search-background | Màu nền Search nếu có |

## 6.2. Dark Theme

Ví dụ:

```css
.title-bar {
    -fx-background-color: #252526;
}

.title-bar-label {
    -fx-text-fill: #CCCCCC;
}

.window-control {
    -fx-background-color: transparent;
}

.window-control:hover {
    -fx-background-color: #383838;
}
```

## 6.3. Light Theme

Ví dụ:

```css
.title-bar {
    -fx-background-color: #F3F3F3;
}

.title-bar-label {
    -fx-text-fill: #333333;
}

.window-control {
    -fx-background-color: transparent;
}

.window-control:hover {
    -fx-background-color: #E5E5E5;
}
```

Đây là màu khởi tạo. Khi triển khai cần đối chiếu với Theme hiện có của ứng dụng.

## 6.4. Quy tắc đồng bộ

Khi ThemeService thay đổi Theme:

1. Title Bar nhận thông báo Theme mới.
2. CSS tương ứng được áp dụng.
3. Background được cập nhật.
4. Text và Icon được cập nhật.
5. Hover và Pressed State sử dụng màu của Theme mới.

Không tạo ThemeService thứ hai chỉ dành cho Title Bar.

---

# 7. CÁC GIAI ĐOẠN TRIỂN KHAI

## Phase 1 – Window Foundation

Công việc:

* Đánh giá cấu trúc Stage hiện tại.
* Kiểm tra cách khởi tạo Main Window.
* Chuyển sang StageStyle.UNDECORATED.
* Giữ nguyên Scene và Application Content.
* Tạo CustomWindow.

Kết quả:

* Ứng dụng chạy với Window không có Title Bar mặc định.

## Phase 2 – Custom Title Bar

Công việc:

* Tạo TitleBar component.
* Hiển thị App Icon.
* Hiển thị Application Name.
* Tạo Window Controls.
* Thiết kế CSS.
* Tích hợp vào Main Window.

Kết quả:

* Có Title Bar riêng.

## Phase 3 – Window Controls

Công việc:

* Minimize.
* Maximize.
* Restore.
* Close.
* Double-click Maximize/Restore.
* Đồng bộ Icon theo trạng thái.

Kết quả:

* Các nút cửa sổ hoạt động.

## Phase 4 – Drag & Resize

Công việc:

* Drag Window.
* Resize Window.
* Minimum Width/Height.
* Multi-monitor testing.
* Windows Snap testing.

Kết quả:

* Có thể thao tác cửa sổ tương tự ứng dụng Windows thông thường.

## Phase 5 – Theme Integration

Công việc:

* Tích hợp ThemeService hiện có.
* Xây dựng Title Bar Design Tokens.
* Dark Theme.
* Light Theme.
* Hover/Pressed States.
* Kiểm tra chuyển Theme khi ứng dụng đang chạy.

Kết quả:

* Title Bar đồng bộ hoàn toàn với Theme ứng dụng.

## Phase 6 – UI Polish & Testing

Công việc:

* Kiểm tra DPI Scaling.
* Kiểm tra màn hình 100%, 125%, 150%.
* Kiểm tra nhiều độ phân giải.
* Kiểm tra cửa sổ maximize/restore.
* Kiểm tra Snap.
* Kiểm tra Minimize/Restore.
* Kiểm tra chuyển Theme.
* Kiểm tra các control trong Title Bar.

Kết quả:

* Title Bar ổn định trên Windows 11.

---

# 8. QUY TẮC KHÔNG ĐƯỢC VI PHẠM

1. Không đặt Window Management Logic trong MainController.
2. Không đặt màu Theme trực tiếp trong Java.
3. Không tạo ThemeService riêng cho Title Bar.
4. Không làm thay đổi Navbar hoặc Toolbar.
5. Không thay đổi kích thước hoặc bố cục các panel hiện tại nếu không cần thiết.
6. Không sử dụng Unicode làm Window Icons.
7. Không gọi System.exit() trực tiếp từ nút Close.
8. Không tự xây dựng cơ chế lưu kích thước cửa sổ phức tạp trong giai đoạn này.
9. Không can thiệp Registry hoặc thay đổi thiết lập giao diện Windows.
10. Không làm mất khả năng mở rộng sang macOS.

---

# 9. TIÊU CHÍ NGHIỆM THU

| STT | Tiêu chí                | Yêu cầu  |
| --- | ----------------------- | -------- |
| 1   | Ẩn Title Bar mặc định   | Pass     |
| 2   | Custom Title Bar        | Pass     |
| 3   | App Icon và Name        | Pass     |
| 4   | Minimize                | Pass     |
| 5   | Maximize                | Pass     |
| 6   | Restore                 | Pass     |
| 7   | Close                   | Pass     |
| 8   | Drag Window             | Pass     |
| 9   | Resize Window           | Pass     |
| 10  | Windows Snap            | Kiểm thử |
| 11  | Dark Theme              | Pass     |
| 12  | Light Theme             | Pass     |
| 13  | Theme Switching         | Pass     |
| 14  | DPI Scaling             | Kiểm thử |
| 15  | Không ảnh hưởng Main UI | Pass     |

---

# 10. KẾT QUẢ BÀN GIAO

Sau khi hoàn thành:

* Custom Title Bar hoạt động.
* Window Controls hoạt động.
* Drag/Resize hoạt động.
* Dark/Light Theme đồng bộ.
* Code được tách thành các module riêng.
* Không ảnh hưởng các khu vực UI khác.
* Có tài liệu mô tả kiến trúc và cách sử dụng Title Bar.

**Kết thúc STEP 01.9.**

Chỉ chuyển sang tính năng tiếp theo sau khi kiểm thử và nghiệm thu đầy đủ.
