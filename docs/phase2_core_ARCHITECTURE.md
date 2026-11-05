# PHASE 02 – CORE ARCHITECTURE

## Kế hoạch xây dựng kiến trúc lõi cho Java Desktop Application

**Công nghệ:** Java 25, Maven
**Kiến trúc:** Modular Monolith + Clean Architecture
**Phạm vi:** Core độc lập với UI và Infrastructure
**Mục tiêu:** Xây dựng nền tảng dùng chung cho Desktop, Web và Extension trong tương lai.

---

# 1. MỤC TIÊU

Xây dựng Core Architecture làm nền tảng cho toàn bộ ứng dụng Java.

Core phải độc lập với giao diện JavaFX, cơ sở dữ liệu, hệ điều hành và các thư viện UI.

Kiến trúc cần đáp ứng:

* Desktop Application sử dụng Core thông qua Application API.
* Web Application trong tương lai có thể tái sử dụng Core.
* Java Plugin có thể sử dụng các chức năng được Core công khai.
* Python Extension có thể giao tiếp với Core thông qua API trung gian trong tương lai.
* Có khả năng mở rộng module mà không phải thay đổi cấu trúc lõi.
* Dễ kiểm thử và bảo trì.
* Có thể tích hợp từng tính năng với UI mà không phải viết lại giao diện.

## 1.1. Nguyên tắc kiến trúc

1. Core không phụ thuộc JavaFX.
2. Core không phụ thuộc Spring Boot.
3. Core không truy cập trực tiếp Database.
4. Core không chứa logic giao diện.
5. Không đưa Business Logic vào JavaFX Controller.
6. Sử dụng Dependency Injection thông qua Constructor Injection.
7. Ưu tiên Java thuần, chỉ sử dụng thư viện thực sự cần thiết.
8. Không triển khai Microservices.
9. Không xây dựng Plugin System trong Phase này.
10. Không tạo module hoặc abstraction chưa có nhu cầu thực tế.

---

# 2. PHẠM VI PHASE 02

## 2.1. Nội dung triển khai

| STT | Hạng mục              | Nội dung                             |
| --- | --------------------- | ------------------------------------ |
| 1   | Core Common           | Các thành phần dùng chung            |
| 2   | Core Domain           | Mô hình dữ liệu và quy tắc nghiệp vụ |
| 3   | Core Application      | Điều phối các Use Case               |
| 4   | Core API              | Hợp đồng giao tiếp với Core          |
| 5   | DTO & Mapping         | Chuẩn hóa dữ liệu trao đổi           |
| 6   | Error Handling        | Chuẩn hóa xử lý lỗi                  |
| 7   | Event System          | Cơ chế phát và nhận sự kiện          |
| 8   | Dependency Management | Quản lý phụ thuộc                    |
| 9   | Testing               | Unit Test và Architecture Test       |
| 10  | Documentation         | Tài liệu kiến trúc                   |

## 2.2. Không triển khai trong Phase 02

* Kết nối SQL Server.
* Kết nối PostgreSQL.
* SQLite Database.
* JDBC và Connection Pool.
* Database Explorer Backend.
* SQL Editor Backend.
* Database Metadata Service.
* Plugin System.
* Python Runtime.
* REST API.
* Web Frontend.
* Cơ chế đồng bộ dữ liệu.
* Packaging và Deployment.

Các nội dung trên được triển khai ở những Phase tiếp theo.

---

# 3. KIẾN TRÚC TỔNG THỂ

## 3.1. Cấu trúc module

```text
JavaPlatform/
│
├── pom.xml
│
├── apps/
│   └── desktop-app/
│
├── core/
│   ├── core-common/
│   ├── core-domain/
│   ├── core-application/
│   └── core-api/
│
├── infrastructure/
│
├── desktop/
│
├── plugin-system/
│
├── integrations/
│
├── plugins/
│
├── tests/
│
└── docs/
```

Trong Phase 02 chỉ tập trung vào thư mục `core/`.

Các module khác chưa cần triển khai đầy đủ.

## 3.2. Quan hệ phụ thuộc

```text
                 Desktop Application
                         |
                         v
                     Core API
                         |
                         v
                  Core Application
                         |
                         v
                    Core Domain
                         |
                         v
                    Core Common
```

Nguyên tắc:

* Domain không phụ thuộc Application.
* Application phụ thuộc Domain.
* API cung cấp hợp đồng giao tiếp ổn định.
* Common chứa các thành phần dùng chung thực sự.
* Infrastructure sẽ triển khai các Adapter cho những Port được Application hoặc Domain định nghĩa.

Lưu ý: Đây là hướng phụ thuộc logic. Không bắt buộc mọi module phải phụ thuộc trực tiếp theo sơ đồ trên nếu điều đó tạo ra vòng phụ thuộc Maven.

---

# 4. THIẾT KẾ CHI TIẾT CÁC MODULE

## 4.1. CORE-COMMON

### Mục đích

Chứa các thành phần nền tảng được nhiều module sử dụng.

### Cấu trúc

```text
core-common/
└── src/main/java/
    └── com/lltool/core/common/
        ├── result/
        ├── error/
        ├── event/
        ├── identity/
        ├── validation/
        └── time/
```

### Các thành phần

| Package    | Chức năng                   |
| ---------- | --------------------------- |
| result     | Chuẩn hóa kết quả xử lý     |
| error      | Định nghĩa lỗi dùng chung   |
| event      | Hợp đồng sự kiện            |
| identity   | ID và định danh             |
| validation | Thành phần kiểm tra dữ liệu |
| time       | Đồng hồ và thời gian        |

### Thiết kế Result

Có thể sử dụng Java Sealed Interface:

```java
public sealed interface Result<T>
    permits Success, Failure {
}
```

```java
public record Success<T>(
    T data
) implements Result<T> {
}
```

```java
public record Failure<T>(
    AppError error
) implements Result<T> {
}
```

Quy ước:

* Success: Xử lý thành công.
* Failure: Lỗi nghiệp vụ hoặc lỗi có thể dự đoán.
* Exception: Lỗi bất thường hoặc lỗi hệ thống không thể xử lý bình thường.

Không sử dụng Result cho mọi method Java. Chỉ áp dụng tại những ranh giới giao tiếp cần biểu diễn kết quả có thể thất bại.

---

## 4.2. CORE-DOMAIN

### Mục đích

Chứa các mô hình và quy tắc nghiệp vụ cốt lõi.

Domain không biết ứng dụng đang chạy trên JavaFX, Web hay môi trường khác.

### Cấu trúc

```text
core-domain/
└── src/main/java/
    └── com/lltool/core/domain/
        ├── project/
        │   ├── model/
        │   ├── valueobject/
        │   ├── service/
        │   └── repository/
        │
        └── workspace/
            ├── model/
            ├── valueobject/
            ├── service/
            └── repository/
```

### Các mô hình ban đầu

**Project**

Đại diện cho một dự án làm việc trong ứng dụng.

Các thuộc tính dự kiến:

* Project ID.
* Project Name.
* Project Path.
* Created At.
* Updated At.

**Workspace**

Đại diện cho môi trường làm việc hiện tại.

Các thuộc tính dự kiến:

* Workspace ID.
* Workspace Name.
* Project List.
* Active Project.

### Value Object

Ví dụ:

* ProjectId.
* WorkspaceId.
* ProjectName.
* FilePath.

### Repository Interface

Chỉ định nghĩa hợp đồng lưu trữ khi thực sự cần.

Ví dụ:

```java
public interface ProjectRepository {

    Optional<Project> findById(ProjectId id);

    List<Project> findAll();

    void save(Project project);

    void delete(ProjectId id);
}
```

Repository Interface thuộc Domain hoặc Application tùy trách nhiệm. Cần thống nhất một vị trí cho từng loại hợp đồng, tránh định nghĩa trùng lặp.

### Nguyên tắc

Không đưa các thư viện sau vào Domain:

* JavaFX.
* JDBC.
* HikariCP.
* Spring.
* Jackson.
* PF4J.

Domain phải có khả năng chạy và kiểm thử độc lập.

---

## 4.3. CORE-APPLICATION

### Mục đích

Điều phối các chức năng thông qua Use Case.

Application không trực tiếp xử lý giao diện hoặc truy cập Database.

### Cấu trúc

```text
core-application/
└── src/main/java/
    └── com/lltool/core/application/
        ├── project/
        │   ├── usecase/
        │   ├── command/
        │   ├── query/
        │   ├── dto/
        │   └── mapper/
        │
        ├── workspace/
        │   ├── usecase/
        │   ├── command/
        │   ├── query/
        │   ├── dto/
        │   └── mapper/
        │
        └── port/
```

### Phân chia trách nhiệm

| Thành phần | Chức năng                             |
| ---------- | ------------------------------------- |
| Use Case   | Thực hiện một chức năng cụ thể        |
| Command    | Đại diện cho yêu cầu thay đổi dữ liệu |
| Query      | Đại diện cho yêu cầu đọc dữ liệu      |
| DTO        | Dữ liệu trao đổi giữa các tầng        |
| Port       | Hợp đồng với thành phần bên ngoài     |
| Mapper     | Chuyển đổi dữ liệu                    |

### Use Case dự kiến

```text
CreateProjectUseCase
OpenProjectUseCase
CloseProjectUseCase
RenameProjectUseCase
DeleteProjectUseCase
GetProjectListUseCase
GetProjectDetailUseCase
```

Ví dụ:

```java
public interface CreateProjectUseCase {

    Result<ProjectDto> execute(
        CreateProjectCommand command
    );
}
```

### Luồng xử lý

```text
JavaFX Controller
       |
       v
CreateProjectUseCase
       |
       v
Project Domain
       |
       v
ProjectRepository
       |
       v
Infrastructure Adapter
```

Trong Phase 02, Repository sử dụng InMemory implementation để kiểm thử.

Chưa cần triển khai lưu trữ thực tế.

---

## 4.4. CORE-API

### Mục đích

Cung cấp giao diện giao tiếp ổn định giữa Core và các thành phần bên ngoài.

Đây là lớp quan trọng để Desktop, Web và Plugin có thể sử dụng chung Core trong tương lai.

### Cấu trúc

```text
core-api/
└── src/main/java/
    └── com/lltool/core/api/
        ├── project/
        ├── workspace/
        ├── application/
        ├── event/
        ├── request/
        └── response/
```

### API dự kiến

```text
ProjectApi
WorkspaceApi
ApplicationApi
EventApi
```

Ví dụ:

```java
public interface ProjectApi {

    Result<ProjectDto> createProject(
        CreateProjectRequest request
    );

    Result<ProjectDto> getProject(
        String projectId
    );

    Result<List<ProjectDto>> getProjects();

    Result<Void> deleteProject(
        String projectId
    );
}
```

### Quy tắc

* Không công khai Entity nội bộ của Domain.
* Chỉ công khai DTO, Request, Response và kiểu dữ liệu cần thiết.
* Không để API phụ thuộc JavaFX.
* Không để API phụ thuộc Infrastructure.
* Không tạo API cho tất cả module khi chưa có nhu cầu.

Lưu ý: Core API là Java API nội bộ, không phải REST API. REST API sẽ được xem xét ở giai đoạn phát triển Web Platform.

---

# 5. EVENT SYSTEM

## 5.1. Mục tiêu

Xây dựng cơ chế giao tiếp bằng sự kiện giữa các thành phần.

Ví dụ:

```text
ProjectCreated
ProjectOpened
ProjectClosed
ProjectDeleted
WorkspaceChanged
ApplicationStarted
ApplicationClosing
```

## 5.2. Cấu trúc

```text
core-common/
└── event/
    ├── AppEvent.java
    ├── EventPublisher.java
    ├── EventSubscriber.java
    └── EventHandler.java
```

## 5.3. Nguyên tắc triển khai

* Event không phụ thuộc JavaFX.
* Event Publisher độc lập với UI.
* Có thể đăng ký và hủy đăng ký Subscriber.
* Xử lý lỗi của Subscriber riêng biệt.
* Có quy định rõ về thứ tự xử lý và Thread.
* Không để một Subscriber lỗi làm dừng toàn bộ Event System.

Giai đoạn này triển khai Event Bus đồng bộ đơn giản.

Chưa cần Kafka, RabbitMQ hoặc Message Broker.

---

# 6. ERROR HANDLING

## 6.1. Phân loại lỗi

```text
AppError
├── ValidationError
├── BusinessError
├── ResourceError
├── ConfigurationError
└── SystemError
```

Ví dụ:

| Mã lỗi                 | Ý nghĩa                   |
| ---------------------- | ------------------------- |
| PROJECT_NOT_FOUND      | Không tìm thấy Project    |
| PROJECT_ALREADY_EXISTS | Project đã tồn tại        |
| INVALID_PROJECT_NAME   | Tên Project không hợp lệ  |
| RESOURCE_NOT_FOUND     | Không tìm thấy tài nguyên |
| INTERNAL_ERROR         | Lỗi hệ thống              |

## 6.2. Quy tắc

* Lỗi nghiệp vụ không bị biến thành lỗi hệ thống.
* Không trả về Stack Trace cho UI.
* Log đầy đủ thông tin kỹ thuật.
* Thông báo người dùng phải dễ hiểu.
* Các tầng sử dụng cấu trúc lỗi thống nhất.

---

# 7. DEPENDENCY MANAGEMENT

## 7.1. Mục tiêu

Kiểm soát phụ thuộc giữa các module.

Không để Core phụ thuộc ngược vào Desktop hoặc Infrastructure.

## 7.2. Maven Dependencies

Dự kiến:

```text
core-common
    |
    +------ core-domain
                |
                +------ core-application
                            |
                            +------ core-api
```

Đây là sơ đồ định hướng, cần kiểm tra dependency thực tế để không tạo vòng phụ thuộc.

## 7.3. Dependency Injection

Phase 02 chưa cần sử dụng Spring.

Sử dụng Constructor Injection và một Composition Root đơn giản.

Ví dụ:

```java
public class ApplicationBootstrap {

    public CoreServices createCore() {

        ProjectRepository repository =
            new InMemoryProjectRepository();

        CreateProjectUseCase createProject =
            new CreateProjectService(repository);

        return new CoreServices(createProject);
    }
}
```

Khi số lượng Service tăng, có thể đánh giá thư viện DI phù hợp.

Không nên đưa DI Framework vào dự án chỉ vì mục đích kiến trúc.

---

# 8. INTEGRATION CONTRACT VỚI UI

Đây là phần quan trọng vì dự án triển khai giao diện và Backend song song.

## 8.1. Nguyên tắc

JavaFX Controller không gọi trực tiếp Domain hoặc Repository.

Controller giao tiếp thông qua Core API hoặc Application Facade được công khai.

## 8.2. Luồng kiến trúc

```text
JavaFX View
     |
     v
Controller / ViewModel
     |
     v
Core API
     |
     v
Application Use Case
     |
     v
Domain
     |
     v
Repository Interface
```

## 8.3. DTO

Ví dụ:

```java
public record ProjectDto(
    String id,
    String name,
    String path,
    String createdAt
) {}
```

DTO cần đáp ứng:

* Không chứa JavaFX Object.
* Không chứa Entity nội bộ.
* Không chứa JDBC Object.
* Có cấu trúc ổn định.
* Có thể sử dụng trong Desktop và Web.

## 8.4. Quy định khi tích hợp UI

* Không để Controller chứa Business Logic.
* Không để UI truy cập Database trực tiếp.
* Không để UI tự quản lý Repository.
* Xử lý tác vụ dài bằng cơ chế bất đồng bộ.
* JavaFX UI update phải chạy trên JavaFX Application Thread.
* Không để Core phụ thuộc JavaFX Application Thread.

Trong Phase 02 chỉ định nghĩa nguyên tắc và kiểm thử hợp đồng. Cơ chế tích hợp bất đồng bộ hoàn chỉnh sẽ triển khai ở Desktop Framework.

---

# 9. TESTING STRATEGY

## 9.1. Unit Test

Sử dụng JUnit 5.

Kiểm thử:

* Entity.
* Value Object.
* Domain Service.
* Use Case.
* Validation.
* Error Handling.
* Event System.

## 9.2. Architecture Test

Sử dụng ArchUnit để kiểm tra:

* Domain không phụ thuộc JavaFX.
* Domain không phụ thuộc Infrastructure.
* Application không phụ thuộc Desktop.
* Core không phụ thuộc Spring.
* Không có circular dependency giữa các module.

## 9.3. Integration Test

Kiểm thử:

* Application gọi Domain.
* Application gọi Repository Interface.
* InMemory Repository hoạt động.
* Event được phát và nhận đúng.
* Core API gọi được Use Case.

---

# 10. KẾ HOẠCH TRIỂN KHAI THEO SUB-PHASE

## Phase 2.1 – Core Module Foundation

Công việc:

* Tạo Maven Modules.
* Thiết lập dependency.
* Thiết lập package naming.
* Cấu hình JUnit.
* Cấu hình ArchUnit.
* Thiết lập coding conventions.
* Kiểm tra khả năng build từng module.

Kết quả:

Bốn module Core có thể build độc lập và không có dependency vòng.

## Phase 2.2 – Core Common

Công việc:

* Result.
* Error.
* Event Contract.
* Identity.
* Validation.
* Time abstraction.

Kết quả:

Có bộ thành phần nền tảng dùng chung.

## Phase 2.3 – Core Domain

Công việc:

* Project Entity.
* Workspace Entity.
* Value Objects.
* Domain Services.
* Repository Interfaces.
* Domain Validation.

Kết quả:

Domain hoạt động độc lập với UI và Infrastructure.

## Phase 2.4 – Core Application

Công việc:

* Command / Query.
* DTO.
* Use Case.
* Application Services.
* Repository Ports.
* Mapper.

Kết quả:

Có thể thực hiện các Use Case mà không cần JavaFX.

## Phase 2.5 – Core API

Công việc:

* Project API.
* Workspace API.
* Application API.
* Event API.
* Request / Response DTO.
* API Error Contract.

Kết quả:

Có giao diện giao tiếp ổn định cho Desktop và các nền tảng tương lai.

## Phase 2.6 – Event System

Công việc:

* Event Publisher.
* Event Subscriber.
* Event Bus.
* Subscription Management.
* Error Isolation.
* Threading Policy.

Kết quả:

Các thành phần có thể giao tiếp thông qua Event System.

## Phase 2.7 – Testing & Architecture Validation

Công việc:

* Unit Test.
* Integration Test.
* Architecture Test.
* Dependency Validation.
* Build Validation.

Kết quả:

Toàn bộ Core đáp ứng các quy tắc kiến trúc đã đặt ra.

## Phase 2.8 – Documentation & Handover

Công việc:

* Tài liệu kiến trúc.
* Tài liệu Module.
* Dependency Diagram.
* API Documentation.
* Coding Convention.
* Hướng dẫn thêm Use Case.

Kết quả:

Core sẵn sàng chuyển sang Phase 03.

---

# 11. CẤU TRÚC THƯ MỤC SAU PHASE 02

```text
core/
│
├── pom.xml
│
├── core-common/
│   └── src/main/java/
│       └── com/lltool/core/common/
│           ├── result/
│           ├── error/
│           ├── event/
│           ├── identity/
│           ├── validation/
│           └── time/
│
├── core-domain/
│   └── src/main/java/
│       └── com/lltool/core/domain/
│           ├── project/
│           └── workspace/
│
├── core-application/
│   └── src/main/java/
│       └── com/lltool/core/application/
│           ├── project/
│           ├── workspace/
│           └── port/
│
└── core-api/
    └── src/main/java/
        └── com/lltool/core/api/
            ├── project/
            ├── workspace/
            ├── application/
            ├── event/
            ├── request/
            └── response/
```

Đây là cấu trúc mục tiêu. Chỉ tạo Package khi có thành phần thực tế cần sử dụng.

---

# 12. CÔNG NGHỆ SỬ DỤNG

| Công nghệ           | Mục đích                    |
| ------------------- | --------------------------- |
| Java 25             | Ngôn ngữ chính              |
| Maven               | Build và quản lý module     |
| JUnit 5             | Unit Test                   |
| ArchUnit            | Kiểm tra kiến trúc          |
| Java Records        | DTO và Value Object phù hợp |
| Java Sealed Classes | Result và Error hierarchy   |
| SLF4J               | Logging API                 |
| Git                 | Version Control             |

Không bổ sung Spring Boot, Hibernate, PF4J hoặc Message Broker trong Phase này.

---

# 13. TIÊU CHÍ NGHIỆM THU

| STT | Tiêu chí            | Yêu cầu                           |
| --- | ------------------- | --------------------------------- |
| 1   | Maven Build         | Build thành công                  |
| 2   | Module Separation   | Các module được tách độc lập      |
| 3   | Domain Independence | Domain không phụ thuộc UI         |
| 4   | Application Layer   | Có Use Case hoạt động             |
| 5   | Core API            | Có API giao tiếp                  |
| 6   | Error Handling      | Có cấu trúc lỗi thống nhất        |
| 7   | Event System        | Event Bus hoạt động               |
| 8   | Unit Test           | Các thành phần quan trọng có Test |
| 9   | Architecture Test   | Không vi phạm Dependency Rules    |
| 10  | UI Contract         | DTO và API không phụ thuộc JavaFX |
| 11  | Documentation       | Có tài liệu kiến trúc             |

## Bài kiểm tra thực tế

Không sử dụng Database.

Thực hiện luồng:

```text
Desktop Application
        |
        v
Core API
        |
        v
CreateProjectUseCase
        |
        v
Project Domain
        |
        v
InMemoryProjectRepository
        |
        v
ProjectCreated Event
```

Thực hiện:

1. Tạo Project.
2. Nhận kết quả ProjectDto.
3. Phát sự kiện ProjectCreated.
4. Một Subscriber nhận sự kiện.
5. Truy vấn lại danh sách Project.
6. Kiểm tra dữ liệu trả về.
7. Chạy toàn bộ Unit Test và Architecture Test.

Nếu luồng trên hoạt động và các bài kiểm thử đạt yêu cầu, Core Architecture đã sẵn sàng cho Phase 03.

---

# 14. QUY TẮC QUẢN LÝ PHẠM VI

Trong quá trình triển khai:

* Không tự ý thay đổi công nghệ của Phase 01.
* Không thay đổi cấu trúc UI nếu không có yêu cầu.
* Không đưa JavaFX vào Core.
* Không đưa Database Implementation vào Core.
* Không triển khai Plugin System sớm.
* Không tạo các module rỗng.
* Không xây dựng abstraction quá mức.
* Mỗi thay đổi kiến trúc phải nêu rõ lý do và phạm vi ảnh hưởng.
* Các tính năng UI hiện có phải tiếp tục build và chạy được.

---

# 15. KẾT QUẢ ĐẦU RA

Sau Phase 02 cần có:

* [ ] Maven Core Modules.
* [ ] Core Common.
* [ ] Core Domain.
* [ ] Core Application.
* [ ] Core API.
* [ ] Event System.
* [ ] Error Handling.
* [ ] Dependency Rules.
* [ ] UI Integration Contract.
* [ ] Unit Tests.
* [ ] Architecture Tests.
* [ ] Architecture Documentation.
* [ ] Demo Use Case chạy độc lập với JavaFX.

**KẾT THÚC PHASE 02**

Ứng dụng có một Core Architecture độc lập, có thể được tái sử dụng bởi Desktop, Web và Extension trong tương lai.

Phase tiếp theo:

**PHASE 03 – INFRASTRUCTURE ARCHITECTURE**

Triển khai Configuration, Logging, Local Storage, Database Connectivity và các Infrastructure Adapter.
