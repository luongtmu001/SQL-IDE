# PHASE 06 – CORE IDE FEATURE DEVELOPMENT PLAN

**Project:** LLTool Java Desktop IDE
**Technology:** Java 25 + JavaFX + Maven
**Architecture:** Modular Monolith + Clean Architecture
**Database Support:** SQL Server, PostgreSQL, SQLite
**UI Framework:** JavaFX + FXML + CSS
**Editor:** RichTextFX / JavaFX WebView PoC
**UI Design:** DataGrip-inspired + VS Code-inspired

---

# 1. MỤC TIÊU

Phase 6 tập trung triển khai các tính năng cốt lõi của Database IDE.

Sau Phase 6, ứng dụng cần có khả năng:

1. Quản lý Project và Workspace.
2. Quản lý Database Connection.
3. Kết nối SQL Server, PostgreSQL và SQLite.
4. Hiển thị Database Explorer.
5. Mở và chỉnh sửa SQL File.
6. Chạy SQL Query.
7. Hiển thị kết quả truy vấn.
8. Quản lý nhiều Result Set.
9. Hiển thị Messages và Execution Information.
10. Lưu Query History.
11. Quản lý các Tab làm việc.
12. Lưu trạng thái Workspace.

## 1.1. Mục tiêu sản phẩm

Ứng dụng cần đạt được trải nghiệm sử dụng cơ bản của một Database IDE:

```text
┌──────────────────────────────────────────────────────────┐
│                     Custom Title Bar                     │
├──────────────────────────────────────────────────────────┤
│                       Menu Bar                            │
├──────────────────────────────────────────────────────────┤
│                       Toolbar                             │
├─────────────────────┬────────────────────────────────────┤
│                     │                                    │
│  Database Explorer  │            SQL Editor              │
│                     │                                    │
│  Connections        │  Query Tab 1                        │
│  Databases          │  Query Tab 2                        │
│  Schemas            │                                    │
│  Tables             │                                    │
│  Views              ├────────────────────────────────────┤
│  Functions          │                                    │
│  Procedures         │       Results | Messages            │
│                     │                                    │
│                     │       Result Grid                  │
│                     │                                    │
├─────────────────────┴────────────────────────────────────┤
│                       Status Bar                          │
└──────────────────────────────────────────────────────────┘
```

Đây là bố cục mục tiêu, không bắt buộc tất cả thành phần phải xuất hiện ngay trong lần triển khai đầu tiên.

---

# 2. PHẠM VI PHASE 6

## 2.1. Các nhóm chức năng

| Nhóm       | Chức năng             | Mức độ   |
| ---------- | --------------------- | -------- |
| Workspace  | Project Management    | Bắt buộc |
| Workspace  | Workspace Management  | Bắt buộc |
| Connection | Connection Management | Bắt buộc |
| Connection | Test Connection       | Bắt buộc |
| Explorer   | Database Explorer     | Bắt buộc |
| Explorer   | Schema/Object Browser | Bắt buộc |
| Editor     | SQL Editor            | Bắt buộc |
| Editor     | SQL File Management   | Bắt buộc |
| Execution  | Query Execution       | Bắt buộc |
| Execution  | Query Cancellation    | Bắt buộc |
| Results    | Result Grid           | Bắt buộc |
| Results    | Multi Result Set      | Bắt buộc |
| Results    | Messages              | Bắt buộc |
| History    | Query History         | Bắt buộc |
| Workspace  | Tab State Persistence | Bắt buộc |

## 2.2. Những tính năng chưa triển khai

Không đưa các tính năng sau vào phạm vi Phase 6:

* SQL IntelliSense nâng cao.
* SQL Formatter nâng cao.
* SQL Debugger.
* Execution Plan Visualization.
* Schema Compare.
* Database Migration.
* Data Import/Export nâng cao.
* Database User Management.
* Auto Translation.
* Plugin System.
* Python Extension Runtime.
* AI Assistant.
* REST API.
* Web Application.
* Cloud Synchronization.

Các tính năng này sẽ được triển khai ở những Phase mở rộng sau.

---

# 3. KIẾN TRÚC TỔNG THỂ

## 3.1. Kiến trúc đề xuất

```text
┌─────────────────────────────────────────────────────────┐
│                       JavaFX UI                          │
│                                                         │
│  Main Window                                            │
│  ├── Workspace                                          │
│  ├── Database Explorer                                  │
│  ├── SQL Editor                                         │
│  ├── Results Grid                                       │
│  ├── Messages                                           │
│  └── Query History                                      │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    Desktop Application                  │
│                                                         │
│  Controllers                                             │
│  View Models / UI State                                  │
│  Desktop Task Manager                                    │
│  Navigation / Window Management                          │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                       Core API                           │
│                                                         │
│  Project API                                             │
│  Workspace API                                           │
│  Connection API                                          │
│  Database Explorer API                                   │
│  SQL Editor API                                          │
│  Query Execution API                                     │
│  Query History API                                       │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   Core Application                       │
│                                                         │
│  Project Use Cases                                       │
│  Workspace Use Cases                                     │
│  Connection Use Cases                                    │
│  Metadata Use Cases                                      │
│  Query Execution Use Cases                               │
│  Query History Use Cases                                 │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                    Infrastructure                        │
│                                                         │
│  Project Repository                                      │
│  Workspace Storage                                       │
│  Connection Repository                                   │
│  Secure Credential Storage                               │
│  Database Provider Registry                              │
│  JDBC Providers                                          │
│  Query Execution Adapters                                │
│  Metadata Adapters                                       │
└─────────────────────────────────────────────────────────┘
```

## 3.2. Nguyên tắc kiến trúc

1. JavaFX Controller không chứa Business Logic.
2. UI không truy cập JDBC trực tiếp.
3. UI không nhận JDBC Connection.
4. Domain không phụ thuộc JavaFX.
5. Core không phụ thuộc JDBC implementation.
6. Infrastructure thực hiện các thao tác DB.
7. Application Use Case điều phối nghiệp vụ.
8. Các tác vụ DB phải chạy bất đồng bộ.
9. UI cập nhật trên JavaFX Application Thread.
10. Không tạo Thread tùy tiện.
11. Không lưu Password trực tiếp trong Connection JSON.
12. Không đưa Database-specific Logic vào một lớp dùng chung nếu hành vi thực tế khác nhau.

## 3.3. Phân biệt các lớp dữ liệu

Cần phân biệt rõ:

| Loại                 | Ví dụ                            |
| -------------------- | -------------------------------- |
| Domain Entity        | Project, Workspace               |
| Application DTO      | ConnectionInfo, QueryResult      |
| Infrastructure Model | JdbcConnectionConfig             |
| UI Model             | QueryTabModel, ExplorerNodeModel |

Không sử dụng một Model duy nhất cho tất cả các lớp.

---

# 4. CẤU TRÚC MODULE ĐỊNH HƯỚNG

Không cần tạo toàn bộ module trước khi bắt đầu.

Cấu trúc định hướng:

```text
core/
├── core-common/
├── core-domain/
├── core-application/
└── core-api/

infrastructure/
├── infrastructure-common/
├── configuration/
├── filesystem/
├── storage-api/
├── storage-local/
├── security/
├── database-api/
├── database-jdbc/
├── database-sqlserver/
├── database-postgresql/
└── database-sqlite/

desktop/
└── desktop-app/

features/
├── feature-project/
├── feature-connection/
├── feature-explorer/
├── feature-editor/
├── feature-query/
└── feature-history/
```

### Nguyên tắc

* Feature Module chứa thành phần thuộc tính năng tương ứng.
* UI Controller thuộc Desktop hoặc Feature UI.
* Use Case thuộc Core Application.
* Database Provider thuộc Infrastructure.
* Không để Feature Module phụ thuộc lẫn nhau không cần thiết.
* Không chia nhỏ Maven Module chỉ vì muốn có nhiều package.

Trong giai đoạn đầu, có thể tổ chức các feature bằng package trong module hiện có. Chỉ tách Maven module khi ranh giới phụ thuộc đã rõ ràng.

---

# 5. CHI TIẾT CÁC SUB-PHASE

# PHASE 6.1 – PROJECT & WORKSPACE MANAGEMENT

## Mục tiêu

Hoàn thiện quản lý Project và Workspace để người dùng có môi trường làm việc ổn định.

## Chức năng

### Project Management

* Create Project.
* Open Project.
* Close Project.
* Rename Project.
* Delete Project.
* Get Project List.
* Get Project Information.

### Workspace Management

* Create Workspace.
* Open Workspace.
* Close Workspace.
* Save Workspace.
* Restore Workspace.
* Manage Workspace Settings.

### Workspace State

Lưu các thông tin:

* Project hiện tại.
* Các SQL Tab đang mở.
* File đang active.
* Vị trí và kích thước các panel.
* Connection được chọn.
* Theme Preference.
* Trạng thái Explorer.

Không lưu các dữ liệu nhạy cảm trực tiếp vào Workspace.

## Cấu trúc dữ liệu định hướng

```text
Project
├── ProjectId
├── ProjectName
├── ProjectPath
├── CreatedAt
└── UpdatedAt

Workspace
├── WorkspaceId
├── ProjectId
├── WorkspaceName
├── WorkspacePath
└── WorkspaceSettings
```

## Luồng xử lý

```text
User
 │
 ▼
Project UI
 │
 ▼
Project API
 │
 ▼
Project Use Case
 │
 ▼
Project Repository
 │
 ▼
Local Storage
```

## Tiêu chí nghiệm thu

* Tạo Project.
* Mở và đóng Project.
* Lưu Workspace.
* Khôi phục Workspace.
* Không mất trạng thái khi ứng dụng khởi động lại.
* Xử lý được Project không còn tồn tại trên ổ đĩa.

---

# PHASE 6.2 – DATABASE CONNECTION MANAGEMENT

## Mục tiêu

Xây dựng hệ thống quản lý Database Connection dùng chung cho các Database Provider.

## Chức năng

### Connection Management

* Create Connection.
* Edit Connection.
* Delete Connection.
* Duplicate Connection.
* Test Connection.
* Connect.
* Disconnect.
* Reconnect.
* Set Active Connection.

### Connection Configuration

Các thông tin cơ bản:

```text
Connection
├── ConnectionId
├── ConnectionName
├── DatabaseType
├── Host
├── Port
├── DatabaseName
├── Username
├── AuthenticationType
├── CredentialReference
├── DriverProperties
└── ConnectionOptions
```

### Database Support

| Database   | Provider               |
| ---------- | ---------------------- |
| SQL Server | Microsoft JDBC Driver  |
| PostgreSQL | PostgreSQL JDBC Driver |
| SQLite     | Xerial SQLite JDBC     |

### Connection UI

Xây dựng Connection Manager Dialog:

```text
┌───────────────────────────────────────────────┐
│              New Connection                   │
├───────────────────────────────────────────────┤
│ Database Type                                 │
│ [ PostgreSQL                         ▼ ]      │
│                                               │
│ Connection Name                               │
│ [ Development Database              ]        │
│                                               │
│ Host                                          │
│ [ localhost                         ]        │
│                                               │
│ Port                                          │
│ [ 5432                              ]        │
│                                               │
│ Database                                      │
│ [ sample_db                        ]         │
│                                               │
│ Username                                      │
│ [ user                             ]          │
│                                               │
│ Password                                      │
│ [ ********                         ]          │
│                                               │
│ [ Test Connection ]      [ Save ]             │
└───────────────────────────────────────────────┘
```

## Bảo mật

* Không lưu Password dạng Plain Text.
* Sử dụng SecureStorage đã xây dựng ở Phase 3.
* Connection Configuration chỉ lưu Credential Reference.
* Không ghi Password vào Log.
* Không đưa thông tin xác thực vào Exception Message.

## Connection Lifecycle

```text
DISCONNECTED
     │
     ▼
 CONNECTING
     │
     ├── SUCCESS
     │      │
     │      ▼
     │  CONNECTED
     │
     └── FAILURE
            │
            ▼
         ERROR
```

Cần bổ sung trạng thái:

* DISCONNECTING.
* RECONNECTING.

Không nên coi Connection Profile và Connection Session là một đối tượng.

**Connection Profile** là cấu hình kết nối được lưu.

**Connection Session** là phiên kết nối đang hoạt động.

## Tiêu chí nghiệm thu

* Tạo và sửa Connection.
* Test Connection.
* Kết nối và ngắt kết nối.
* Kết nối được ba Database Provider.
* Lưu Credential an toàn.
* Hiển thị Connection Status.
* Không làm treo UI khi kết nối thất bại hoặc timeout.

---

# PHASE 6.3 – DATABASE EXPLORER

## Mục tiêu

Xây dựng Database Explorer theo phong cách DataGrip.

## Cấu trúc Tree

```text
Database Explorer
│
├── SQL Server Connection
│   ├── Databases
│   │   ├── Database A
│   │   │   ├── Schemas
│   │   │   │   ├── dbo
│   │   │   │   └── sales
│   │   │   │
│   │   │   ├── Tables
│   │   │   ├── Views
│   │   │   ├── Procedures
│   │   │   ├── Functions
│   │   │   └── Triggers
│   │   │
│   │   └── Database B
│   │
│   └── Server Objects
│
├── PostgreSQL Connection
│   ├── Databases
│   │   ├── Database A
│   │   │   ├── Schemas
│   │   │   ├── Tables
│   │   │   ├── Views
│   │   │   ├── Functions
│   │   │   └── Sequences
│   │   │
│   │   └── Database B
│
└── SQLite Connection
    ├── Tables
    ├── Views
    ├── Indexes
    └── Triggers
```

Cấu trúc trên là cách tổ chức giao diện định hướng. Cây metadata thực tế phải phản ánh khả năng và quy ước của từng Database Provider.

## Chức năng

### Explorer Operations

* Expand Node.
* Collapse Node.
* Refresh.
* Search Object.
* Filter Object.
* Select Object.
* Open Object.
* Show Object Properties.

### Context Menu

Dự kiến:

**Connection**

* Connect.
* Disconnect.
* Reconnect.
* Edit Connection.
* New Query.
* Refresh.
* Remove Connection.

**Database**

* Set Active Database.
* New Query.
* Refresh.
* Properties.

**Table**

* Open Data.
* View Structure.
* Generate SELECT.
* Generate INSERT.
* Generate UPDATE.
* Generate DELETE.
* Refresh.

Ở Phase 6 chỉ triển khai các hành động cơ bản. Các chức năng Generate Script nâng cao sẽ phát triển sau.

## Metadata API

Không truy vấn metadata trực tiếp trong Controller.

Thiết kế API:

```text
DatabaseMetadataService
├── getCatalogs()
├── getSchemas()
├── getTables()
├── getViews()
├── getProcedures()
├── getFunctions()
├── getColumns()
└── getIndexes()
```

Đây là API định hướng. Cần xác định các khái niệm metadata thực sự dùng chung và các phần đặc thù theo Provider.

## Hiệu năng

* Load metadata theo yêu cầu.
* Không load toàn bộ Database Tree ngay khi mở Connection.
* Lazy Loading cho các Node lớn.
* Cache metadata ở phạm vi phù hợp.
* Có cơ chế Refresh.
* Tránh gửi nhiều truy vấn metadata trùng lặp.
* Không chặn JavaFX Application Thread.

## Tiêu chí nghiệm thu

* Hiển thị được Connection.
* Hiển thị Database và Schema.
* Hiển thị Table, View, Procedure, Function.
* Expand/Collapse.
* Refresh.
* Search/Filter.
* Mở được thông tin Table.
* Hỗ trợ ba Database Provider theo phạm vi metadata đã xác định.

---

# PHASE 6.4 – SQL EDITOR TECHNOLOGY VALIDATION

## Mục tiêu

Chọn công nghệ Editor chính thức trước khi đầu tư phát triển SQL Editor.

Đây là một Sub-phase quan trọng, không nên bỏ qua.

## Hai phương án

### Phương án A – RichTextFX

Ưu điểm:

* Native JavaFX.
* Không cần WebView.
* Có khả năng tích hợp sâu với JavaFX.
* Có thể xây dựng syntax highlighting.
* Chủ động kiểm soát giao diện và hành vi.

Nhược điểm:

* Phải tự xây dựng nhiều chức năng Editor.
* IntelliSense nâng cao cần thêm nhiều công việc.
* Một số tính năng kiểu VS Code phải tự phát triển.

### Phương án B – Monaco Editor qua JavaFX WebView

Ưu điểm:

* Có hệ sinh thái Editor mạnh.
* Syntax Highlighting.
* Multi-language support.
* Editor API phong phú.
* Có thể tái sử dụng kinh nghiệm phát triển Web.

Nhược điểm:

* Phụ thuộc WebView và JavaScript.
* Có chi phí giao tiếp Java ↔ JavaScript.
* Cần kiểm tra khả năng tương thích với JavaFX WebView.
* Quản lý lifecycle và bộ nhớ phức tạp hơn.
* Không được mặc định rằng JavaFX WebView có hiệu năng tương đương Chromium.

## Các tiêu chí kiểm thử

| Tiêu chí     | Nội dung                      |
| ------------ | ----------------------------- |
| Startup      | Thời gian khởi tạo            |
| Memory       | RAM sử dụng                   |
| Typing       | Độ trễ khi nhập               |
| Highlighting | Hiệu năng syntax highlighting |
| Large File   | Xử lý SQL File lớn            |
| Multi Tab    | Nhiều Editor cùng lúc         |
| Search       | Tìm kiếm trong Editor         |
| Selection    | Bôi đen và thao tác           |
| Clipboard    | Copy/Paste                    |
| Theme        | Dark/Light Theme              |
| Integration  | Java Integration              |
| Stability    | Độ ổn định                    |

## Kịch bản thử nghiệm

* File SQL nhỏ.
* File SQL trung bình.
* File SQL lớn.
* Nhiều Tab.
* Syntax Highlighting.
* Search.
* Replace.
* Undo/Redo.
* Copy/Paste.
* Theme Switching.

Cần thống nhất bộ dữ liệu kiểm thử để so sánh công bằng.

## Quyết định đầu ra

Chọn một trong hai:

* RichTextFX làm Editor chính.
* Monaco Editor làm Editor chính.

Không nên duy trì đồng thời hai Editor Engine trong sản phẩm chỉ để dự phòng.

Nếu cả hai chưa đáp ứng, đánh giá thêm giải pháp khác trước khi triển khai sâu.

**Lưu ý:** Không bắt đầu xây dựng IntelliSense nâng cao trong Sub-phase này.

---

# PHASE 6.5 – SQL EDITOR CORE

## Mục tiêu

Xây dựng SQL Editor cơ bản có khả năng làm việc với nhiều SQL File và nhiều Tab.

## Chức năng

### Editor

* Create New SQL Tab.
* Open SQL File.
* Edit SQL.
* Save.
* Save As.
* Close Tab.
* Close All.
* Close Others.
* Rename Tab.
* Track Dirty State.
* Undo/Redo.
* Find.
* Replace.
* Line Number.
* Syntax Highlighting.
* Current Line Highlight.
* Word Wrap.
* Font Size.
* Tab Size.

### Multi Tab

Mỗi Tab cần quản lý:

```text
EditorTab
├── TabId
├── FilePath
├── Title
├── Content
├── Dirty
├── Active
├── DatabaseType
├── ConnectionId
├── DatabaseContext
└── EditorState
```

Không nên lưu toàn bộ Editor State vào một Global State.

### SQL Context

Một SQL Tab có thể có:

* Database Type.
* Connection Profile.
* Active Connection.
* Active Database.
* Active Schema.

Không nên bắt buộc mỗi Tab phải có một Connection riêng.

### File Management

Hỗ trợ:

* New File.
* Open File.
* Save.
* Save As.
* Detect External Modification.
* Unsaved Changes Warning.

## Lưu trữ

SQL File sử dụng File System.

Workspace lưu:

* Các Tab đang mở.
* Tab đang active.
* Vị trí con trỏ nếu phù hợp.
* Trạng thái Editor cần khôi phục.

Không cần lưu toàn bộ nội dung SQL vào Workspace nếu File System đã quản lý nội dung đó.

## Tiêu chí nghiệm thu

* Tạo và mở SQL File.
* Chỉnh sửa và lưu File.
* Quản lý nhiều Tab.
* Dirty State hoạt động.
* Có cảnh báo khi đóng File chưa lưu.
* Khôi phục Workspace.
* Không làm treo UI khi mở File lớn trong giới hạn đã kiểm thử.

---

# PHASE 6.6 – QUERY EXECUTION ENGINE

## Mục tiêu

Xây dựng hệ thống thực thi SQL Query.

Đây là phần Backend quan trọng nhất của Phase 6.

## Chức năng

* Execute SQL.
* Execute Selected SQL.
* Execute Current Statement.
* Execute Multiple Statements.
* Cancel Query.
* Query Timeout.
* Capture Execution Time.
* Capture Affected Rows.
* Capture Result Sets.
* Capture Execution Messages.
* Handle SQL Exceptions.

## Execution Flow

```text
SQL Editor
    │
    ▼
Query Execution UI
    │
    ▼
ExecuteQuery API
    │
    ▼
ExecuteQueryUseCase
    │
    ▼
Query Execution Service
    │
    ▼
Database Provider
    │
    ▼
JDBC Execution
    │
    ▼
Query Result
    │
    ├── Result Set
    ├── Update Count
    ├── Messages
    ├── Execution Time
    └── Error
    │
    ▼
Application Response
    │
    ▼
JavaFX UI
```

## Query Request

Định hướng:

```text
QueryRequest
├── QueryId
├── ConnectionId
├── DatabaseType
├── DatabaseContext
├── SqlText
├── ExecutionMode
├── Timeout
└── ExecutionOptions
```

Không truyền JDBC Connection từ UI xuống Application API.

## Execution Mode

### Execute All

Thực thi toàn bộ nội dung SQL theo quy tắc của Database Provider.

### Execute Selected

Chỉ thực thi phần SQL được chọn.

### Execute Current Statement

Xác định câu lệnh tại vị trí con trỏ và thực thi.

Cần xây dựng cơ chế phân tách SQL Statement phù hợp với từng Database Type. Không dùng một cách tách SQL đơn giản theo dấu `;` cho mọi trường hợp vì dấu phân cách có thể xuất hiện trong chuỗi, comment hoặc cấu trúc đặc thù.

## JDBC Execution

Cần phân biệt:

* Statement.
* PreparedStatement.
* ResultSet.
* Update Count.
* SQL Warning.
* SQL Exception.

Xử lý đúng chuỗi kết quả của JDBC, đặc biệt khi một lần thực thi có nhiều Result Set và Update Count.

## Cancellation

* Hỗ trợ yêu cầu Cancel.
* Sử dụng JDBC Statement cancellation khi Driver hỗ trợ.
* Giải phóng tài nguyên sau khi thực thi.
* Xử lý trường hợp Driver không phản hồi Cancel ngay lập tức.
* Không coi việc hủy tác vụ UI là đồng nghĩa với việc truy vấn DB đã dừng.

## Transaction

Ở Phase 6:

* Hỗ trợ chế độ Auto Commit.
* Hỗ trợ Transaction cơ bản.
* Xử lý Commit/Rollback khi có lỗi.
* Không tự động Commit các thao tác mà người dùng chưa xác nhận.
* Xác định rõ hành vi khi một Script gồm nhiều Statement.

Cần tránh giả định rằng hành vi Transaction giống nhau giữa SQL Server, PostgreSQL và SQLite.

## Security & Safety

* Không log SQL Parameter nhạy cảm.
* Có Query Timeout.
* Có cơ chế Cancel.
* Không tự động thực thi SQL khi mở File.
* Các thao tác thay đổi dữ liệu phải được thực thi theo yêu cầu rõ ràng của người dùng.
* Không chạy truy vấn DB trên UI Thread.

## Tiêu chí nghiệm thu

* Execute SQL thành công.
* Execute Selected SQL.
* Execute nhiều Statement.
* Hỗ trợ Cancel.
* Có Timeout.
* Xử lý Exception.
* Thu thập Execution Time.
* Giải phóng JDBC Resource.
* Không làm treo UI.

---

# PHASE 6.7 – RESULT GRID

## Mục tiêu

Xây dựng hệ thống hiển thị kết quả SQL Query.

## Chức năng

* Display Result Set.
* Multiple Result Tabs.
* Column Header.
* Row Selection.
* Cell Selection.
* Column Resize.
* Column Sorting.
* Row Count.
* Data Type Display.
* Null Display.
* Copy Cell.
* Copy Row.
* Copy Selected Cells.
* Refresh Result nếu phù hợp.

## Result Data Model

```text
QueryExecutionResult
├── QueryId
├── ExecutionStatus
├── ExecutionTime
├── ResultSets[]
│   ├── ResultSetId
│   ├── Columns[]
│   │   ├── Name
│   │   ├── DataType
│   │   ├── Nullable
│   │   └── Position
│   │
│   ├── Rows
│   └── RowCount
│
├── UpdateCounts[]
├── Messages[]
└── Error
```

## Result Grid Architecture

Không truyền trực tiếp `java.sql.ResultSet` vào JavaFX TableView.

Cần chuyển dữ liệu sang Application Data Model hoặc một cơ chế đọc dữ liệu có kiểm soát.

### Data Loading

Cần đánh giá hai phương án:

**Phương án A – Load vào Memory**

Phù hợp với Result Set nhỏ và vừa.

Ưu điểm:

* Đơn giản.
* Dễ Sorting.
* Dễ Selection.
* Dễ Copy.

Nhược điểm:

* Tốn RAM khi dữ liệu lớn.

**Phương án B – Lazy Loading**

Chỉ tải một phần dữ liệu khi cần.

Ưu điểm:

* Hạn chế tiêu thụ RAM.
* Phù hợp Result Set lớn.

Nhược điểm:

* Phức tạp hơn.
* Phải quản lý Cursor và Connection Lifecycle.
* Khả năng cuộn, Sorting và Navigation cần thiết kế cẩn thận.

Đề xuất:

Bắt đầu với Load into Memory có giới hạn cấu hình. Sau đó benchmark để quyết định khi nào cần Lazy Loading.

Không tải vô hạn dữ liệu vào RAM.

## Multiple Result Sets

Cần hỗ trợ:

```text
Query Result
│
├── Result 1
├── Result 2
├── Result 3
│
├── Messages
└── Execution Information
```

Không giả định mọi Database Provider trả về các Result Set giống nhau.

## Tiêu chí nghiệm thu

* Hiển thị Result Set.
* Hiển thị nhiều Result Set.
* Hiển thị Update Count.
* Copy dữ liệu.
* Xử lý NULL.
* Hiển thị đúng kiểu dữ liệu cơ bản.
* Không làm treo UI với lượng dữ liệu kiểm thử đã thống nhất.

---

# PHASE 6.8 – MESSAGES & EXECUTION INFORMATION

## Mục tiêu

Xây dựng khu vực hiển thị thông tin thực thi.

## Messages

Hiển thị:

* Query Started.
* Query Completed.
* Query Failed.
* Rows Affected.
* SQL Error.
* SQL Warning.
* Database Messages.
* Execution Information.

## Execution Information

```text
Execution Information
├── Query ID
├── Connection
├── Database
├── Start Time
├── End Time
├── Duration
├── Rows Affected
└── Execution Status
```

## Yêu cầu

* Messages được quản lý theo Query Execution.
* Không trộn Messages giữa các Tab.
* Có thể Clear Messages.
* Có thể Copy Messages.
* Phân biệt Error, Warning và Information.
* Không hiển thị thông tin xác thực.

## Tiêu chí nghiệm thu

* Hiển thị thông báo thành công.
* Hiển thị lỗi.
* Hiển thị Warning.
* Hiển thị Execution Time.
* Hiển thị Rows Affected.
* Messages gắn đúng Query Execution.

---

# PHASE 6.9 – QUERY HISTORY

## Mục tiêu

Lưu lại lịch sử các câu SQL đã thực thi.

## Chức năng

* Save Query History.
* View History.
* Search History.
* Filter by Connection.
* Filter by Date.
* Filter by Execution Status.
* Reopen Query.
* Copy SQL.
* Clear History.

## History Model

```text
QueryHistory
├── HistoryId
├── QueryId
├── ConnectionId
├── DatabaseName
├── SqlText
├── ExecutionTime
├── Duration
├── RowsAffected
├── ExecutionStatus
└── CreatedAt
```

## Storage

Có thể sử dụng SQLite làm Local History Database.

Lưu ý:

* Không nhầm SQLite History Database với SQLite Database Provider mà người dùng kết nối.
* Có chính sách giới hạn dung lượng History.
* Có cơ chế xóa History.
* Không lưu Password hoặc dữ liệu xác thực.
* Cần xem xét việc lưu SQL có chứa thông tin nhạy cảm và cung cấp tùy chọn không lưu History.

## Tiêu chí nghiệm thu

* Tự động lưu History khi thực thi thành công hoặc thất bại.
* Hiển thị History.
* Tìm kiếm.
* Lọc.
* Mở lại Query.
* Xóa History.

---

# PHASE 6.10 – WORKSPACE INTEGRATION

## Mục tiêu

Kết nối toàn bộ các tính năng vào Main Window.

## Nội dung

### Main Layout

* Database Explorer bên trái.
* Editor Workspace ở giữa.
* Results Panel bên dưới.
* Messages Panel.
* Status Bar.

### Tab Integration

* SQL Editor Tab.
* Query Result Tab hoặc Result View gắn với Editor Tab.
* Active Tab.
* Dirty State.
* Close Tab.
* Restore Workspace.

### Panel Management

Sử dụng Dockable Panel từ Phase 5.

* Show/Hide Explorer.
* Show/Hide Results.
* Resize Panel.
* Collapse Panel.
* Restore Layout.

### Connection Context

Mỗi Editor Tab có thể lựa chọn Connection và Database Context riêng.

Không mặc định sử dụng Connection đang được chọn trong Explorer cho tất cả Editor Tab.

### Trạng thái UI

Cần hiển thị:

* Active Connection.
* Active Database.
* Query Running.
* Query Completed.
* Query Error.
* Editor Dirty State.

## Tiêu chí nghiệm thu

* Có thể mở nhiều SQL Tab.
* Mỗi Tab có Connection Context riêng.
* Có thể chạy Query từ Tab tương ứng.
* Kết quả hiển thị đúng Tab.
* Có thể đóng và mở lại Workspace.
* Layout được khôi phục.

---

# PHASE 6.11 – PERFORMANCE & STABILITY

## Mục tiêu

Kiểm tra hiệu năng toàn bộ các tính năng cốt lõi.

## Các nhóm cần kiểm thử

### Connection

* Kết nối nhiều Database.
* Timeout.
* Reconnect.
* Disconnect.
* Connection Failure.

### Explorer

* Database có nhiều Schema.
* Schema có nhiều Table.
* Refresh nhiều lần.
* Expand/Collapse liên tục.
* Metadata Cache.

### Editor

* SQL File nhỏ.
* SQL File lớn.
* Nhiều Tab.
* Syntax Highlighting.
* Search/Replace.

### Query Execution

* Query nhanh.
* Query chạy lâu.
* Query trả về nhiều dòng.
* Query nhiều Result Set.
* Query lỗi.
* Query bị Cancel.

### Result Grid

* Dữ liệu nhỏ.
* Dữ liệu trung bình.
* Dữ liệu lớn.
* Nhiều cột.
* Giá trị NULL.
* Dữ liệu dài.

## Quy tắc

* Không đặt giới hạn dữ liệu tùy tiện mà không có cấu hình.
* Không tối ưu dựa trên cảm tính.
* Đo RAM, CPU và thời gian phản hồi.
* Phân biệt thời gian thực thi SQL và thời gian render UI.
* Không giữ ResultSet/Statement sau khi không còn sử dụng.
* Có cơ chế giải phóng tài nguyên khi đóng Tab hoặc ứng dụng.

## Tiêu chí nghiệm thu

* Không treo UI.
* Không rò rỉ JDBC Resource.
* Không tăng RAM vô hạn khi chạy nhiều Query.
* Không tạo Thread không kiểm soát.
* Có số liệu Benchmark cho các kịch bản chính.

---

# PHASE 6.12 – TESTING & DOCUMENTATION

## Unit Test

Sử dụng JUnit 5.

Kiểm thử:

* Project Use Cases.
* Workspace Use Cases.
* Connection Use Cases.
* Query Execution Use Cases.
* Query History Use Cases.
* Metadata Services.
* SQL Statement Selection/Parsing ở phạm vi cơ bản.

## Integration Test

Kiểm thử với:

* SQL Server.
* PostgreSQL.
* SQLite.

Nên sử dụng Database Test Environment riêng, không chạy thử trên Database sản xuất.

## UI Test

Kiểm thử:

* Main Workspace.
* Connection Dialog.
* Explorer.
* Editor.
* Result Grid.
* Messages.
* Query History.

## Architecture Test

Sử dụng ArchUnit để đảm bảo:

* Core không phụ thuộc JavaFX.
* Domain không phụ thuộc JDBC.
* Controller không phụ thuộc Repository.
* Infrastructure không phụ thuộc UI.
* Không có Circular Dependency.

## Tài liệu

```text
docs/
└── features/
    ├── project-workspace.md
    ├── connection-management.md
    ├── database-explorer.md
    ├── sql-editor.md
    ├── query-execution.md
    ├── result-grid.md
    ├── query-history.md
    └── performance-testing.md
```

---

# 6. THỨ TỰ TRIỂN KHAI TỔNG THỂ

| Sub-phase | Nội dung                         | Ưu tiên |
| --------- | -------------------------------- | ------- |
| 6.1       | Project & Workspace              | Cao     |
| 6.2       | Connection Management            | Rất cao |
| 6.3       | Database Explorer                | Rất cao |
| 6.4       | SQL Editor Technology Validation | Rất cao |
| 6.5       | SQL Editor Core                  | Rất cao |
| 6.6       | Query Execution Engine           | Rất cao |
| 6.7       | Result Grid                      | Rất cao |
| 6.8       | Messages & Execution Info        | Cao     |
| 6.9       | Query History                    | Cao     |
| 6.10      | Workspace Integration            | Rất cao |
| 6.11      | Performance & Stability          | Rất cao |
| 6.12      | Testing & Documentation          | Rất cao |

## Trình tự triển khai thực tế

Không nhất thiết phải hoàn thành toàn bộ 6.1 mới bắt đầu 6.2.

Có thể triển khai theo các lát cắt:

**Vertical Slice 1 – Kết nối Database**

```text
Connection UI
    ↓
Connection API
    ↓
Connection Use Case
    ↓
Database Provider
    ↓
Test Connection
    ↓
Connection Status UI
```

**Vertical Slice 2 – Database Explorer**

```text
Explorer UI
    ↓
Metadata API
    ↓
Metadata Use Case
    ↓
Database Provider
    ↓
Metadata Response
    ↓
Explorer Tree
```

**Vertical Slice 3 – SQL Editor**

```text
Editor UI
    ↓
Editor State
    ↓
File Storage
    ↓
Save / Open SQL
    ↓
Workspace Persistence
```

**Vertical Slice 4 – Query Execution**

```text
Editor
    ↓
Execute SQL
    ↓
Query Execution API
    ↓
JDBC Provider
    ↓
Result Model
    ↓
Result Grid + Messages
```

**Vertical Slice 5 – Workspace hoàn chỉnh**

```text
Project
    ↓
Workspace
    ↓
Connection
    ↓
Explorer
    ↓
Editor
    ↓
Query Execution
    ↓
Results
    ↓
History
```

---

# 7. TIÊU CHÍ NGHIỆM THU PHASE 6

## Project & Workspace

* [ ] Tạo Project.
* [ ] Mở Project.
* [ ] Đóng Project.
* [ ] Lưu Workspace.
* [ ] Khôi phục Workspace.

## Connection

* [ ] Tạo Connection.
* [ ] Sửa Connection.
* [ ] Xóa Connection.
* [ ] Test Connection.
* [ ] Connect/Disconnect.
* [ ] Hỗ trợ SQL Server.
* [ ] Hỗ trợ PostgreSQL.
* [ ] Hỗ trợ SQLite.
* [ ] Bảo vệ thông tin xác thực.

## Database Explorer

* [ ] Hiển thị Connection.
* [ ] Hiển thị Database.
* [ ] Hiển thị Schema.
* [ ] Hiển thị Tables.
* [ ] Hiển thị Views.
* [ ] Hiển thị Procedures/Functions theo Provider.
* [ ] Expand/Collapse.
* [ ] Refresh.
* [ ] Search/Filter.

## SQL Editor

* [ ] Chọn được Editor Engine.
* [ ] New SQL Tab.
* [ ] Open SQL File.
* [ ] Save SQL File.
* [ ] Save As.
* [ ] Multi Tab.
* [ ] Dirty State.
* [ ] Undo/Redo.
* [ ] Find/Replace.
* [ ] Syntax Highlighting.

## Query Execution

* [ ] Execute SQL.
* [ ] Execute Selected SQL.
* [ ] Execute Multiple Statements.
* [ ] Cancel Query.
* [ ] Timeout.
* [ ] Error Handling.
* [ ] Execution Time.
* [ ] Update Count.

## Results

* [ ] Hiển thị Result Grid.
* [ ] Multiple Result Sets.
* [ ] Copy dữ liệu.
* [ ] Hiển thị NULL.
* [ ] Hiển thị Data Type.
* [ ] Messages.
* [ ] Execution Information.

## History

* [ ] Lưu Query History.
* [ ] Tìm kiếm History.
* [ ] Lọc History.
* [ ] Mở lại Query.
* [ ] Xóa History.

## Quality

* [ ] Không truy cập DB trên UI Thread.
* [ ] Không rò rỉ JDBC Resource.
* [ ] Không lưu Password Plain Text.
* [ ] Không có Business Logic trong Controller.
* [ ] Không có Circular Dependency.
* [ ] Có Unit Test.
* [ ] Có Integration Test.
* [ ] Có Benchmark cơ bản.

---

# 8. KẾT QUẢ ĐẦU RA

Sau Phase 6, ứng dụng cần có một bộ tính năng Database IDE cơ bản:

1. Project Management.
2. Workspace Management.
3. Database Connection Management.
4. SQL Server Provider.
5. PostgreSQL Provider.
6. SQLite Provider.
7. Database Explorer.
8. SQL Editor.
9. SQL File Management.
10. Query Execution Engine.
11. Query Cancellation.
12. Result Grid.
13. Multi Result Set.
14. Messages Panel.
15. Execution Information.
16. Query History.
17. Workspace Persistence.

Đây là phiên bản IDE có thể sử dụng để làm việc với Database ở mức cơ bản, chưa phải phiên bản hoàn thiện toàn bộ tính năng chuyên sâu.

---

# 9. ĐỊNH HƯỚNG PHASE 7

## PHASE 07 – ADVANCED DATABASE IDE FEATURES

Sau khi Phase 6 hoàn thành, có thể phát triển các tính năng nâng cao:

### SQL Editor Advanced

* SQL IntelliSense.
* Syntax Validation.
* SQL Formatting.
* Code Completion.
* Snippets.
* Parameter Support.

### Database Explorer Advanced

* Object Properties.
* Generate DDL.
* Dependency Browser.
* Object Search.
* Database Object Designer.

### Query Advanced

* Query Plan.
* Query Statistics.
* Query Profiling.
* Result Export.
* Data Editing.

### Database Tools

* Schema Compare.
* Database Compare.
* User Management.
* Permission Viewer.
* Script Generator.

### Extension

* Plugin System.
* Python Extension Runtime.
* External Tool Integration.

Không cần triển khai tất cả ngay sau Phase 6. Nên ưu tiên theo nhu cầu sử dụng thực tế.

---

# 10. NGUYÊN TẮC QUAN TRỌNG

1. Không xây dựng tất cả UI trước rồi mới làm Backend.
2. Không xây dựng Backend cho tất cả tính năng rồi mới tích hợp UI.
3. Mỗi tính năng phải hoàn thành theo Vertical Slice.
4. Không để JavaFX Controller chứa Business Logic.
5. Không để UI truy cập JDBC trực tiếp.
6. Không sử dụng JDBC Connection trực tiếp trong Domain.
7. Không phụ thuộc cứng vào một Database Provider.
8. Không giả định SQL Server, PostgreSQL và SQLite có hành vi giống nhau.
9. Không triển khai IntelliSense nâng cao trước khi chọn Editor Engine.
10. Không tải Result Set không giới hạn vào RAM.
11. Không chạy tác vụ DB trên JavaFX Application Thread.
12. Không lưu thông tin xác thực dạng Plain Text.
13. Không tạo module hoặc class khi chưa có nhu cầu thực tế.
14. Mọi tính năng phải có Unit Test hoặc Integration Test phù hợp.
15. Ưu tiên độ ổn định và khả năng bảo trì trước số lượng tính năng.

---

# KẾT LUẬN

Phase 6 là giai đoạn hình thành sản phẩm Database IDE thực tế.

Thứ tự ưu tiên cao nhất:

**Connection Management → Database Explorer → SQL Editor → Query Execution → Result Grid → Workspace Integration.**

Trong đó, cần kiểm chứng Editor Engine và Result Grid sớm để tránh lựa chọn công nghệ không phù hợp rồi phải thay đổi kiến trúc khi ứng dụng đã phát triển lớn.
