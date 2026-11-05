# PHASE 04 – DESKTOP FRAMEWORK IMPLEMENTATION PLAN

## 1. Mục tiêu

Xây dựng nền tảng Desktop cho ứng dụng Java bằng JavaFX.

Phase 4 chịu trách nhiệm:

* Khởi tạo và quản lý ứng dụng JavaFX.
* Xây dựng Main Window.
* Quản lý vòng đời ứng dụng.
* Quản lý cửa sổ chính và cửa sổ phụ.
* Xây dựng hệ thống điều hướng.
* Tích hợp Core Architecture từ Phase 2.
* Tích hợp Infrastructure từ Phase 3.
* Quản lý trạng thái giao diện ở cấp ứng dụng.
* Thiết lập cơ chế xử lý tác vụ bất đồng bộ.
* Chuẩn bị nền tảng để triển khai UI Component System ở Phase 5.

**Kết quả mong muốn:**

Có thể khởi chạy một ứng dụng Desktop hoàn chỉnh, hiển thị giao diện chính, điều hướng giữa các khu vực, mở cửa sổ phụ, quản lý trạng thái và đóng ứng dụng đúng quy trình.

---

# 2. Phạm vi triển khai

## 2.1. Những nội dung thuộc Phase 4

| Hạng mục              | Nội dung                          |
| --------------------- | --------------------------------- |
| Application Bootstrap | Khởi tạo ứng dụng                 |
| JavaFX Runtime        | Quản lý JavaFX Application Thread |
| Main Window           | Quản lý cửa sổ chính              |
| Window Management     | Quản lý cửa sổ phụ                |
| Navigation            | Điều hướng giao diện              |
| Application Lifecycle | Quản lý vòng đời ứng dụng         |
| Dependency Wiring     | Kết nối các module                |
| Async Task Management | Quản lý tác vụ bất đồng bộ        |
| Global State          | Trạng thái ứng dụng               |
| Error Handling        | Xử lý lỗi cấp Desktop             |
| Desktop Integration   | Tích hợp Core và Infrastructure   |

## 2.2. Những nội dung chưa triển khai

Không triển khai trong Phase 4:

* Database Explorer hoàn chỉnh.
* SQL Editor.
* Query Execution.
* IntelliSense.
* Schema Compare.
* Plugin System.
* Python Extension Runtime.
* REST API.
* Web Application.
* Hệ thống UI Component chuyên biệt.

Các nội dung này sẽ được triển khai ở những Phase tiếp theo.

---

# 3. Kiến trúc tổng thể

## 3.1. Kiến trúc đề xuất

Sử dụng JavaFX làm Desktop Framework chính.

Các thành phần:

* JavaFX: giao diện Desktop.
* FXML: định nghĩa cấu trúc giao diện.
* CSS: quản lý giao diện và theme.
* AtlantaFX: nền tảng theme.
* ControlsFX: bổ sung JavaFX Controls.
* Ikonli: hệ thống icon.
* Core API: giao tiếp với Core.
* Infrastructure: cung cấp các dịch vụ hạ tầng.

Luồng kiến trúc:

```text
┌───────────────────────────────────────────┐
│                 Desktop UI                │
│                                           │
│  MainWindow                               │
│  WindowManager                            │
│  NavigationManager                        │
│  Desktop Controllers                      │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│             Desktop Application           │
│                                           │
│  Application Bootstrap                    │
│  Application Lifecycle                    │
│  Desktop Services                         │
│  Async Task Manager                       │
│  Global State                             │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│                 Core API                  │
│                                           │
│  Application Facades                      │
│  Use Cases                                │
│  Commands / Queries                       │
│  DTOs                                     │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│               Infrastructure              │
│                                           │
│  Configuration                            │
│  Logging                                  │
│  File System                              │
│  Storage                                  │
│  Security                                 │
│  Database Connectivity                    │
└───────────────────────────────────────────┘
```

## 3.2. Nguyên tắc kiến trúc

1. Desktop không được truy cập trực tiếp JDBC.
2. JavaFX Controller không được chứa Business Logic.
3. Desktop chỉ giao tiếp với Core thông qua Core API hoặc Application Facade.
4. Core không phụ thuộc JavaFX.
5. Infrastructure không phụ thuộc JavaFX.
6. Không khởi tạo các dịch vụ hạ tầng tùy tiện bên trong Controller.
7. Không sử dụng Singleton toàn cục cho mọi dịch vụ.
8. Các tác vụ mất thời gian không được chạy trực tiếp trên JavaFX Application Thread.
9. Giao diện chỉ cập nhật thông qua JavaFX Application Thread.
10. Các module phải được kết nối thông qua Dependency Injection hoặc Composition Root.

---

# 4. Cấu trúc module đề xuất

Có thể tổ chức theo cấu trúc:

```text
desktop/
│
├── desktop-app/
│   └── src/main/
│       ├── java/
│       │   └── com/lltool/desktop/
│       │       │
│       │       ├── bootstrap/
│       │       │   ├── DesktopApplication.java
│       │       │   ├── ApplicationBootstrap.java
│       │       │   └── ApplicationContext.java
│       │       │
│       │       ├── lifecycle/
│       │       │   ├── ApplicationLifecycle.java
│       │       │   └── ShutdownManager.java
│       │       │
│       │       ├── window/
│       │       │   ├── WindowManager.java
│       │       │   ├── MainWindow.java
│       │       │   ├── WindowFactory.java
│       │       │   └── WindowType.java
│       │       │
│       │       ├── navigation/
│       │       │   ├── NavigationManager.java
│       │       │   ├── NavigationTarget.java
│       │       │   └── NavigationContext.java
│       │       │
│       │       ├── task/
│       │       │   ├── DesktopTaskManager.java
│       │       │   └── UiThreadExecutor.java
│       │       │
│       │       ├── state/
│       │       │   └── DesktopState.java
│       │       │
│       │       ├── error/
│       │       │   └── DesktopErrorHandler.java
│       │       │
│       │       └── controller/
│       │
│       └── resources/
│           ├── fxml/
│           ├── css/
│           ├── icons/
│           └── config/
│
└── desktop-api/
    └── src/main/java/
```

Đây là cấu trúc định hướng, không phải danh sách bắt buộc phải tạo toàn bộ ngay từ đầu.

Chỉ tạo package, class và module khi có nhu cầu thực tế.

Nếu `desktop-api` chưa có chức năng dùng chung độc lập, có thể tạm thời giữ API trong `desktop-app` để tránh chia module quá sớm.

---

# 5. Các hạng mục triển khai

## 5.1. Phase 4.1 – Application Bootstrap

### Mục tiêu

Xây dựng cơ chế khởi tạo ứng dụng JavaFX.

### Nội dung

* Tạo lớp chính kế thừa `javafx.application.Application`.
* Thiết lập Application Bootstrap.
* Khởi tạo Application Context.
* Khởi tạo các dịch vụ Infrastructure.
* Khởi tạo Core Services.
* Đăng ký Dependency Injection.
* Khởi tạo Main Window.
* Hiển thị ứng dụng.

### Luồng khởi động

```text
Application.launch()
        │
        ▼
DesktopApplication.init()
        │
        ▼
ApplicationBootstrap
        │
        ├── Load Configuration
        ├── Initialize Logging
        ├── Initialize Infrastructure
        ├── Initialize Core Services
        ├── Build Dependency Container
        │
        ▼
DesktopApplication.start()
        │
        ▼
Create MainWindow
        │
        ▼
Show Application
```

### Yêu cầu

* Không khởi tạo toàn bộ dịch vụ trong `start()`.
* Không đặt logic khởi tạo trong Controller.
* Có thể phân biệt lỗi khởi động và lỗi trong quá trình chạy.
* Có cơ chế ghi log khi ứng dụng khởi động thất bại.

---

## 5.2. Phase 4.2 – Main Window Management

### Mục tiêu

Xây dựng hệ thống quản lý cửa sổ chính.

### Nội dung

* Tạo MainWindow.
* Quản lý JavaFX Stage.
* Load Main Layout từ FXML.
* Thiết lập kích thước mặc định.
* Thiết lập kích thước tối thiểu.
* Quản lý trạng thái maximize/restore.
* Quản lý vị trí cửa sổ.
* Quản lý trạng thái hiển thị.

### Main Window Layout

```text
┌──────────────────────────────────────────────┐
│                 Title Bar                    │
├──────────────────────────────────────────────┤
│                  Menu Bar                    │
├──────────────────────────────────────────────┤
│                  Toolbar                     │
├───────────────┬──────────────────────────────┤
│               │                              │
│               │                              │
│   Navigation  │        Main Workspace        │
│    / Explorer │                              │
│               │                              │
│               │                              │
├───────────────┴──────────────────────────────┤
│              Bottom Workspace                │
├──────────────────────────────────────────────┤
│                  Status Bar                   │
└──────────────────────────────────────────────┘
```

### Nguyên tắc

* Main Window chỉ có một Stage chính.
* Layout được chia thành các khu vực độc lập.
* Không đặt toàn bộ giao diện vào một FXML quá lớn.
* Các khu vực giao diện có thể được thay thế hoặc mở rộng.
* Các thành phần UI được thiết kế để tái sử dụng ở Phase 5.

### Lưu ý về Custom Title Bar

Tiếp tục sử dụng phương án Custom Title Bar đã thống nhất ở Phase 1.

* `StageStyle.UNDECORATED`.
* Tự xây dựng minimize/maximize/restore/close.
* Hỗ trợ kéo cửa sổ.
* Hỗ trợ double-click để maximize/restore.
* Hỗ trợ resize nếu cần.
* Kiểm tra Windows Snap và DPI Scaling.

Không triển khai lại Title Bar nếu Phase 1 đã hoàn thành. Phase 4 chỉ tích hợp vào Main Window và kiểm tra hoạt động.

---

## 5.3. Phase 4.3 – Window Management

### Mục tiêu

Xây dựng cơ chế quản lý cửa sổ phụ.

### Nội dung

* WindowManager.
* WindowFactory.
* Window Registry.
* Window Lifecycle.
* Window Owner.
* Window Modality.
* Window Positioning.

### Các loại cửa sổ dự kiến

| Loại            | Mục đích           |
| --------------- | ------------------ |
| Main Window     | Cửa sổ chính       |
| Dialog Window   | Hộp thoại          |
| Utility Window  | Cửa sổ công cụ     |
| Settings Window | Cửa sổ cài đặt     |
| About Window    | Thông tin ứng dụng |

### API định hướng

```java
windowManager.open(WindowType.SETTINGS);

windowManager.openDialog(
    WindowType.CONFIRMATION
);

windowManager.close(WindowType.SETTINGS);
```

API trên chỉ là ví dụ định hướng. Thiết kế thực tế cần hỗ trợ tham số, dữ liệu đầu vào và kết quả trả về khi cần.

### Yêu cầu

* Không tạo Stage tùy tiện trong Controller.
* Các cửa sổ được quản lý thông qua WindowManager.
* Dialog phải xác định Owner phù hợp.
* Hỗ trợ cửa sổ modal và non-modal.
* Tránh mở nhiều bản sao của cửa sổ chỉ cho phép tồn tại một instance.
* Cho phép nhiều instance đối với các cửa sổ có nhu cầu đó.

---

## 5.4. Phase 4.4 – Navigation System

### Mục tiêu

Xây dựng hệ thống điều hướng giữa các khu vực giao diện.

### Nội dung

* NavigationManager.
* NavigationTarget.
* NavigationContext.
* Navigation History.
* Navigation State.
* View Loading.

### Ví dụ khu vực điều hướng

```text
Main Window
│
├── Home
│
├── Database
│   ├── Database Explorer
│   └── Connection Manager
│
├── Workspace
│   ├── SQL Editor
│   └── File Explorer
│
├── Tools
│   ├── Schema Compare
│   └── Settings
│
└── Help
    └── About
```

Đây là danh sách định hướng cho tương lai. Phase 4 chỉ cần triển khai một số màn hình mẫu để kiểm tra Navigation System.

### Yêu cầu

* Không điều hướng bằng cách tạo Main Window mới.
* NavigationManager chịu trách nhiệm chuyển đổi nội dung.
* Hỗ trợ điều hướng đến một View cụ thể.
* Có thể truyền NavigationContext.
* Có thể quay lại màn hình trước nếu phù hợp.
* Có cơ chế kiểm tra hoặc xác định trạng thái View.
* Không phụ thuộc trực tiếp vào Business Logic.

---

## 5.5. Phase 4.5 – Application Lifecycle

### Mục tiêu

Quản lý vòng đời ứng dụng từ khi khởi động đến khi đóng.

### Nội dung

* Application Startup.
* Application Running.
* Application Shutdown.
* Shutdown Hooks.
* Resource Cleanup.
* Background Task Cleanup.

### Luồng đóng ứng dụng

```text
User clicks Close
        │
        ▼
ShutdownManager
        │
        ├── Check Pending Operations
        ├── Notify Application Services
        ├── Stop Background Tasks
        ├── Close Secondary Windows
        ├── Release Database Resources
        ├── Flush Logs
        └── Save Application State
        │
        ▼
Exit Application
```

### Yêu cầu

* Không gọi `System.exit()` trực tiếp từ Controller.
* Không đóng ứng dụng khi vẫn còn tác vụ quan trọng chưa được xử lý.
* Có thể hỏi xác nhận khi cần.
* Không để Background Thread tiếp tục chạy sau khi ứng dụng đóng.
* Đóng các tài nguyên theo đúng thứ tự.
* Lỗi khi đóng một dịch vụ không được ngăn toàn bộ quá trình cleanup.

---

## 5.6. Phase 4.6 – Async Task Management

### Mục tiêu

Xây dựng cơ chế chạy tác vụ nền để không làm treo giao diện.

### Công nghệ

* Java ExecutorService.
* JavaFX Task.
* JavaFX Service.
* Platform.runLater().

### Các loại tác vụ

| Tác vụ              | Cách xử lý                |
| ------------------- | ------------------------- |
| Load cấu hình       | Background                |
| Đọc file            | Background                |
| Kiểm tra kết nối DB | Background                |
| Load dữ liệu lớn    | Background                |
| Cập nhật UI         | JavaFX Application Thread |
| Hiển thị thông báo  | JavaFX Application Thread |

### Luồng xử lý

```text
JavaFX Controller
        │
        ▼
Application API
        │
        ▼
DesktopTaskManager
        │
        ▼
Background Executor
        │
        ▼
Core / Infrastructure
        │
        ▼
Task Result
        │
        ▼
JavaFX Application Thread
        │
        ▼
Update UI
```

### Yêu cầu

* Không chạy truy vấn DB trên JavaFX Application Thread.
* Có cơ chế xử lý Success/Failure.
* Có cơ chế Cancel Task khi phù hợp.
* Hỗ trợ Progress khi cần.
* Có thể quản lý các tác vụ đang chạy.
* Không tạo một Thread mới cho mỗi thao tác.
* Có cơ chế shutdown ExecutorService.

Lưu ý: Không biến DesktopTaskManager thành nơi chứa nghiệp vụ. Nó chỉ quản lý thực thi tác vụ.

---

## 5.7. Phase 4.7 – Global Application State

### Mục tiêu

Quản lý trạng thái chung của Desktop Application.

### Trạng thái dự kiến

```text
DesktopState
│
├── Application Status
├── Current Navigation
├── Current Workspace
├── Active Window
├── Current Theme
├── Background Tasks
└── Application Preferences
```

### Yêu cầu

* Phân biệt trạng thái toàn ứng dụng và trạng thái riêng từng View.
* Không lưu toàn bộ dữ liệu nghiệp vụ trong Global State.
* Không sử dụng một Singleton chứa mọi trạng thái.
* Hạn chế phụ thuộc chéo giữa các Controller.
* Sử dụng JavaFX Properties khi cần binding với giao diện.
* Các trạng thái cần lưu lâu dài phải đi qua Configuration hoặc Storage API.

---

## 5.8. Phase 4.8 – Desktop Error Handling

### Mục tiêu

Xây dựng cơ chế xử lý lỗi ở cấp Desktop.

### Phân loại lỗi

| Nhóm                  | Ví dụ                       |
| --------------------- | --------------------------- |
| Startup Error         | Không thể khởi tạo ứng dụng |
| Configuration Error   | Không đọc được cấu hình     |
| Navigation Error      | Không thể tải View          |
| Window Error          | Không thể mở cửa sổ         |
| Background Task Error | Tác vụ nền thất bại         |
| Infrastructure Error  | Lỗi truy cập tài nguyên     |
| Unexpected Error      | Lỗi không xác định          |

### Yêu cầu

* Log lỗi qua Logging Infrastructure.
* Hiển thị thông báo phù hợp với người dùng.
* Không hiển thị Stack Trace trực tiếp trên UI.
* Không nuốt lỗi mà không ghi log.
* Có Correlation ID hoặc Task ID khi cần theo dõi lỗi.
* Không để lỗi ở một Background Task làm sập toàn bộ ứng dụng.

---

## 5.9. Phase 4.9 – Desktop/Core/Infrastructure Integration

### Mục tiêu

Kết nối các thành phần đã xây dựng trong Phase 2 và Phase 3.

### Các tích hợp cần thực hiện

| Desktop               | Module tích hợp       | Mục đích                  |
| --------------------- | --------------------- | ------------------------- |
| Application Bootstrap | Configuration         | Load cấu hình             |
| Application Bootstrap | Logging               | Khởi tạo log              |
| Desktop Services      | Core API              | Gọi Application Use Cases |
| Application Lifecycle | Infrastructure        | Quản lý tài nguyên        |
| Desktop Task Manager  | Core API              | Thực thi tác vụ nền       |
| Desktop State         | Configuration/Storage | Lưu trạng thái cần thiết  |

### Quy tắc

* Không để JavaFX Controller gọi trực tiếp Repository.
* Không để UI sử dụng JDBC Connection.
* Không đưa JavaFX Node, Stage hoặc Control vào Core DTO.
* Không để Core phụ thuộc JavaFX.
* Không để Infrastructure phụ thuộc Controller.

### Demo tích hợp bắt buộc

Xây dựng một luồng mẫu:

```text
User
 │
 ▼
JavaFX Main Window
 │
 ▼
Sample View
 │
 ▼
Core API
 │
 ▼
GetProjectListUseCase
 │
 ▼
ProjectRepository
 │
 ▼
Infrastructure Adapter
 │
 ▼
Local Storage
 │
 ▼
Return DTO
 │
 ▼
Update JavaFX UI
```

Sử dụng dữ liệu mẫu hoặc dữ liệu lưu trữ thực tế đã được triển khai ở Phase 2 và Phase 3.

---

## 5.10. Phase 4.10 – Testing & Documentation

### Kiểm thử

1. Kiểm tra khởi động ứng dụng.
2. Kiểm tra khởi tạo Application Context.
3. Kiểm tra Main Window.
4. Kiểm tra WindowManager.
5. Kiểm tra mở và đóng cửa sổ phụ.
6. Kiểm tra Navigation.
7. Kiểm tra Application Shutdown.
8. Kiểm tra Background Task.
9. Kiểm tra UI Thread.
10. Kiểm tra lỗi khi tải View.
11. Kiểm tra tích hợp Core API.
12. Kiểm tra tích hợp Infrastructure.

### Kiểm tra kiến trúc

Sử dụng ArchUnit để kiểm tra:

* Core không phụ thuộc Desktop.
* Domain không phụ thuộc Infrastructure.
* Infrastructure không phụ thuộc JavaFX.
* Controller không phụ thuộc trực tiếp Repository.
* Các module không phụ thuộc vòng tròn.

### Tài liệu

Tạo các tài liệu:

```text
docs/
└── desktop/
    ├── application-bootstrap.md
    ├── window-management.md
    ├── navigation-system.md
    ├── application-lifecycle.md
    ├── async-task-management.md
    └── desktop-architecture.md
```

---

# 6. Thứ tự triển khai

| Sub-phase | Nội dung                        | Kết quả              |
| --------- | ------------------------------- | -------------------- |
| 4.1       | Application Bootstrap           | Khởi tạo JavaFX      |
| 4.2       | Main Window                     | Cửa sổ chính         |
| 4.3       | Window Management               | Quản lý cửa sổ       |
| 4.4       | Navigation System               | Điều hướng           |
| 4.5       | Application Lifecycle           | Quản lý vòng đời     |
| 4.6       | Async Task Management           | Tác vụ nền           |
| 4.7       | Global Application State        | Trạng thái chung     |
| 4.8       | Error Handling                  | Xử lý lỗi            |
| 4.9       | Core/Infrastructure Integration | Tích hợp kiến trúc   |
| 4.10      | Testing & Documentation         | Kiểm thử và tài liệu |

Không nên triển khai tất cả cùng lúc.

Mỗi Sub-phase cần có:

1. Thiết kế.
2. Implementation.
3. Kiểm thử.
4. Review kiến trúc.
5. Cập nhật tài liệu.

---

# 7. Tiêu chí nghiệm thu Phase 4

Phase 4 được xem là hoàn thành khi đáp ứng các điều kiện:

## Application

* [ ] Ứng dụng khởi động đúng quy trình.
* [ ] Application Context hoạt động.
* [ ] Configuration được khởi tạo.
* [ ] Logging hoạt động.
* [ ] Dependency được quản lý tập trung.

## Window

* [ ] Main Window hoạt động.
* [ ] Custom Title Bar hoạt động.
* [ ] Có thể mở cửa sổ phụ.
* [ ] Có thể đóng cửa sổ phụ.
* [ ] WindowManager quản lý được vòng đời cửa sổ.

## Navigation

* [ ] Điều hướng giữa các View.
* [ ] Không tạo Main Window mới khi điều hướng.
* [ ] Có thể truyền Navigation Context.
* [ ] Không phụ thuộc trực tiếp vào Business Logic.

## Lifecycle

* [ ] Đóng ứng dụng đúng quy trình.
* [ ] Background Tasks được quản lý.
* [ ] Tài nguyên được giải phóng.
* [ ] Không còn Thread do ứng dụng quản lý chạy sau khi đóng.

## Integration

* [ ] Desktop gọi được Core API.
* [ ] Core API gọi được Infrastructure Adapter.
* [ ] Có luồng dữ liệu mẫu hoàn chỉnh.
* [ ] Không vi phạm Dependency Rules.

## Quality

* [ ] Các thành phần chính có kiểm thử.
* [ ] Có tài liệu kiến trúc.
* [ ] Không có dependency cycle.
* [ ] Không chứa Business Logic trong JavaFX Controller.

---

# 8. Kết quả đầu ra

Sau Phase 4, dự án cần có:

1. JavaFX Desktop Application hoàn chỉnh ở cấp nền tảng.
2. Application Bootstrap.
3. Application Context.
4. Main Window.
5. Window Management.
6. Navigation System.
7. Application Lifecycle.
8. Async Task Management.
9. Global Application State.
10. Desktop Error Handling.
11. Core/Infrastructure Integration.
12. Bộ kiểm thử và tài liệu kiến trúc.

---

# 9. Những nguyên tắc cần duy trì cho các Phase tiếp theo

1. JavaFX là nền tảng giao diện chính.
2. FXML và CSS được tách riêng.
3. Không nhúng CSS hoặc JavaScript trực tiếp vào FXML nếu không cần thiết.
4. Không đưa Business Logic vào Controller.
5. Không truy cập Database trực tiếp từ UI.
6. Không tạo Stage tùy tiện.
7. Không tạo Thread tùy tiện.
8. Không tạo Global Singleton cho mọi dịch vụ.
9. Các View cần có khả năng tái sử dụng.
10. Mỗi tính năng được phát triển theo quy trình UI → Backend → Integration → Testing.
11. Không tạo trước hàng loạt module hoặc class chưa có nhu cầu.
12. Không làm thay đổi công nghệ nền tảng đã thống nhất.

---

# 10. Phase tiếp theo

Sau khi hoàn thành Phase 4, chuyển sang:

## PHASE 05 – UI COMPONENT SYSTEM

Tập trung xây dựng hệ thống thành phần giao diện có thể tái sử dụng:

* Design Tokens.
* Theme System.
* Icon System.
* Button.
* Input.
* Dialog.
* Context Menu.
* Tab System.
* Docking Panel.
* Split Pane.
* Tree View.
* Data Grid.
* Notification.
* Loading Indicator.
* Empty State.
* Error State.

Đặc biệt, Phase 5 sẽ chuẩn hóa cách xây dựng các thành phần giao diện để khi phát triển Database Explorer, SQL Editor và Results Panel, không phải viết lại những thành phần cơ bản.

---

**Kết luận:** Phase 4 xây dựng bộ khung vận hành Desktop; Phase 5 xây dựng hệ thống UI Component; từ các Phase phát triển tính năng trở đi, mỗi tính năng sẽ được triển khai đồng thời cả giao diện và Backend theo từng module.
