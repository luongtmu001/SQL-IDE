# BÁO CÁO TRIỂN KHAI PHASE 02 – CORE ARCHITECTURE

**Dự án:** SQL IDE / LLTool Platform  
**Phiên bản:** 1.0.0-SNAPSHOT  
**Kiến trúc:** Modular Monolith + Clean Architecture  
**Công nghệ:** Java 21+ (Java 25 LTS Runtime), Maven, JUnit 5, ArchUnit, SLF4J  
**Ngày hoàn thành:** 03/11/2026  

---

## 1. TỔNG QUAN KẾT QUẢ TRIỂN KHAI

Toàn bộ 8 tiểu mục của Phase 02 đã được triển khai hoàn chỉnh theo đúng kế hoạch tại [`docs/phase2_core_ARCHITECTURE.md`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/phase2_core_ARCHITECTURE.md):

| Sub-Phase | Tên hạng mục | Trạng thái | Số lượng Unit Tests |
| :--- | :--- | :---: | :---: |
| **Phase 2.1** | Core Module Foundation | **Hoàn thành** | Build độc lập (4 modules) |
| **Phase 2.2** | Core Common | **Hoàn thành** | 6 tests |
| **Phase 2.3** | Core Domain | **Hoàn thành** | 4 tests |
| **Phase 2.4** | Core Application | **Hoàn thành** | 5 tests |
| **Phase 2.5** | Core API | **Hoàn thành** | 2 tests |
| **Phase 2.6** | Event System | **Hoàn thành** | (tích hợp trong Common & API) |
| **Phase 2.7** | Testing & ArchUnit Validation | **Hoàn thành** | 4 ArchUnit tests |
| **Phase 2.8** | Documentation & Handover | **Hoàn thành** | Báo cáo chi tiết & hướng dẫn |
| **Tổng cộng** | **Core Platform** | **100% SUCCESS** | **21/21 Tests Pass** |
| **Bảo lưu** | **Desktop UI (Phase 1)** | **100% SUCCESS** | **34/34 Tests Pass** |

---

## 2. CẤU TRÚC MODULES VÀ HƯỚNG PHỤ THUỘC

```text
                  Desktop UI / Web / Extensions
                               |
                               v
                       [ core-api ]
                               |
                               v
                   [ core-application ]
                               |
                               v
                       [ core-domain ]
                               |
                               v
                       [ core-common ]
```

### 2.1. Danh mục module trong thư mục `core/`:
- [`core/pom.xml`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/pom.xml): Parent POM quản lý versions, dependencyManagement và compiler release.
- [`core/core-common`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/pom.xml): Chứa các thành phần dùng chung: Result monad, Error hierarchy, Event contracts, Identity, Validation, Time abstraction.
- [`core/core-domain`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-domain/pom.xml): Mô hình Aggregate Root (`Project`, `Workspace`), Value Objects (`ProjectId`, `ProjectName`, `ProjectPath`, `WorkspaceId`, `WorkspaceName`), Domain Events và Repository interfaces.
- [`core/core-application`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/pom.xml): Điều phối Use Cases (`CreateProjectUseCase`, `RenameProjectUseCase`, `DeleteProjectUseCase`, `GetProjectDetailUseCase`, `GetProjectListUseCase`), Commands, Queries, DTOs, Mappers và In-Memory repository adapters cho testing.
- [`core/core-api`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-api/pom.xml): Cung cấp hợp đồng giao tiếp ổn định cho Client (`ProjectApi`, `EventApi`), Request/Response DTOs, và `CorePlatform` Composition Root bootstrap qua Constructor Injection.

---

## 3. CHI TIẾT CÁC THÀNH PHẦN CỐT LÕI

### 3.1. Result & Error Monad ([`com.lltool.core.common`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/src/main/java/com/lltool/core/common))
- **Sealed Result Interface**: [`Result<T>`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/src/main/java/com/lltool/core/common/result/Result.java) chỉ cho phép hai nhánh [`Success<T>`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/src/main/java/com/lltool/core/common/result/Success.java) và [`Failure<T>`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/src/main/java/com/lltool/core/common/result/Failure.java).
- Hỗ trợ đầy đủ các hàm xử lý functional: `map()`, `flatMap()`, `onSuccess()`, `onFailure()`, `orElse()`, `orElseGet()`, và tương thích 100% với Java Pattern Matching `switch (result)`.
- **Sealed AppError Interface**: [`AppError`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/src/main/java/com/lltool/core/common/error/AppError.java) phân loại rành mạch:
  - `ValidationError`: Lỗi validation đầu vào.
  - `BusinessError`: Lỗi vi phạm quy tắc nghiệp vụ (ví dụ: `PROJECT_ALREADY_EXISTS`).
  - `ResourceError`: Lỗi không tìm thấy tài nguyên (ví dụ: `PROJECT_NOT_FOUND`).
  - `ConfigurationError`: Lỗi cấu hình hệ thống.
  - `SystemError`: Lỗi kỹ thuật không dự tính kèm theo `Throwable cause`.

### 3.2. Event System ([`com.lltool.core.common.event`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/src/main/java/com/lltool/core/common/event))
- [`EventBus`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-common/src/main/java/com/lltool/core/common/event/EventBus.java): Triển khai đồng bộ, an toàn đa luồng (`ConcurrentHashMap`, `CopyOnWriteArrayList`), độc lập 100% với JavaFX.
- **Error Isolation**: Một Subscriber gặp ngoại lệ sẽ được bắt và log lại độc lập qua SLF4J, tuyệt đối không làm gián đoạn các Subscriber khác hoặc phía gọi `publish()`.
- **AutoCloseable Subscription**: Đăng ký sự kiện trả về `AutoCloseable` giúp huỷ đăng ký (unsubscribe) cực kỳ thuận tiện và an toàn.

### 3.3. Pure Domain Layer ([`com.lltool.core.domain`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-domain/src/main/java/com/lltool/core/domain))
- [`Project`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-domain/src/main/java/com/lltool/core/domain/project/model/Project.java): Thực thể Aggregate Root bảo vệ toàn vẹn trạng thái. Các thao tác thay đổi trạng thái (`rename`, `relocate`, `setActive`) luôn cập nhật timestamp `updatedAt`.
- [`ProjectName`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-domain/src/main/java/com/lltool/core/domain/project/valueobject/ProjectName.java): Value Object tự kiểm tra tính hợp lệ: 1–100 ký tự, không chứa ký tự cấm hệ điều hành (`\ / : * ? " < > |`), tự động `trim()`.
- [`Workspace`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-domain/src/main/java/com/lltool/core/domain/workspace/model/Workspace.java): Quản lý tập hợp các `ProjectId` và duy trì `activeProjectId` hợp lệ.

### 3.4. Application Layer & Use Cases ([`com.lltool.core.application`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/src/main/java/com/lltool/core/application))
- [`ProjectApplicationService`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/src/main/java/com/lltool/core/application/project/service/ProjectApplicationService.java): Điều phối luồng nghiệp vụ cho 5 Use Cases (`CreateProjectUseCase`, `RenameProjectUseCase`, `DeleteProjectUseCase`, `GetProjectDetailUseCase`, `GetProjectListUseCase`).
- Tách bạch Input qua Command/Query records và Output qua [`ProjectDto`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/src/main/java/com/lltool/core/application/project/dto/ProjectDto.java).
- Đảm bảo phát sinh Domain Event (`ProjectCreatedDomainEvent`, `ProjectRenamedDomainEvent`, `ProjectDeletedDomainEvent`) ngay sau khi lưu trữ thành công.
- Cung cấp [`InMemoryProjectRepository`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/src/main/java/com/lltool/core/application/port/InMemoryProjectRepository.java) và [`InMemoryWorkspaceRepository`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-application/src/main/java/com/lltool/core/application/port/InMemoryWorkspaceRepository.java) phục vụ kiểm thử và chạy thử nghiệm mà chưa cần Database thực tế.

### 3.5. Core API & Composition Root ([`com.lltool.core.api`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-api/src/main/java/com/lltool/core/api))
- [`ProjectApi`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-api/src/main/java/com/lltool/core/api/project/ProjectApi.java) & [`DefaultProjectApi`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-api/src/main/java/com/lltool/core/api/project/DefaultProjectApi.java): Cung cấp hợp đồng ổn định cho tầng ngoài.
- [`CorePlatform`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-api/src/main/java/com/lltool/core/api/application/CorePlatform.java): Composition Root kết nối toàn bộ Dependencies bằng Constructor Injection thông qua Fluent Builder (`CorePlatform.builder().build()`).

---

## 4. KIỂM CHỨNG KIẾN TRÚC VÀ BÀI TEST THỰC TẾ

### 4.1. ArchUnit Rules ([`CoreArchitectureTest`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-api/src/test/java/com/lltool/core/architecture/CoreArchitectureTest.java))
Đã kiểm chứng tự động bằng ArchUnit 1.3.0 với 4 quy tắc nghiêm ngặt:
1. `testCoreDoesNotDependOnJavaFX`: Không bất kỳ class nào trong `com.lltool.core..` phụ thuộc vào `javafx..`.
2. `testCoreDoesNotDependOnSpring`: Không bất kỳ class nào phụ thuộc vào Spring Framework.
3. `testCoreDoesNotDependOnDesktopUI`: Không phụ thuộc ngược vào package `app..` của Desktop UI.
4. `testDomainIndependence`: Tầng Domain hoàn toàn độc lập, không phụ thuộc vào Application hay API layers.

### 4.2. Bài kiểm tra chấp nhận thực tế (Mục 13) ([`CorePlatformIntegrationTest`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/core/core-api/src/test/java/com/lltool/core/api/application/CorePlatformIntegrationTest.java))
Thực hiện luồng từ đầu đến cuối không cần Database:
1. Khởi tạo `CorePlatform.createDefault()`.
2. Đăng ký Subscriber lắng nghe `ProjectCreatedDomainEvent`.
3. Gọi `ProjectApi.createProject(...)`.
4. Nhận kết quả `ProjectDto` thành công.
5. Subscriber nhận sự kiện chính xác với ID và tên dự án.
6. Gọi `ProjectApi.getProjects()` truy vấn lại danh sách và kiểm tra dữ liệu trả về.
-> **100% Passed**.

---

## 5. HƯỚNG DẪN BỔ SUNG USE CASE TRONG GIAI ĐOẠN TIẾP THEO

Khi cần thêm Use Case mới (ví dụ trong Phase 03 - Infrastructure hoặc Phase 04 - Metadata):
1. **Domain**:
   - Nếu có logic/invariants mới, định nghĩa trong Entity (`com.lltool.core.domain.<module>.model`).
   - Khai báo Repository method nếu cần (`com.lltool.core.domain.<module>.repository`).
2. **Application**:
   - Tạo Command hoặc Query record (`com.lltool.core.application.<module>.command / query`).
   - Tạo Use Case interface (`com.lltool.core.application.<module>.usecase`).
   - Triển khai logic trong Application Service (`com.lltool.core.application.<module>.service`).
3. **Core API**:
   - Thêm method vào API interface (`com.lltool.core.api.<module>.<Module>Api`).
   - Ủy quyền cho Use Case trong `Default<Module>Api`.
4. **Viết Tests**:
   - Viết Unit Test cho Use Case.
   - Chạy `.\mvnw.cmd test -f core/pom.xml` để đảm bảo không vi phạm ArchUnit.

