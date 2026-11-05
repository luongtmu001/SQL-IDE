# PHASE 03 – INFRASTRUCTURE ARCHITECTURE

## Kế hoạch xây dựng lớp hạ tầng cho Java Desktop Application

**Công nghệ:** Java 25, Maven
**Kiến trúc:** Modular Monolith + Clean Architecture
**Phạm vi:** Configuration, Logging, Local Storage, File System, Database Connectivity
**Mục tiêu:** Xây dựng Infrastructure Layer độc lập với UI, có thể tích hợp dần với từng tính năng của ứng dụng.

---

# 1. MỤC TIÊU

Xây dựng Infrastructure Layer chịu trách nhiệm giao tiếp với các tài nguyên bên ngoài và cung cấp dịch vụ hạ tầng cho Core Application.

Infrastructure là lớp triển khai các Port/Interface được định nghĩa ở Core, không chứa Business Logic của ứng dụng.

Các mục tiêu:

* Quản lý cấu hình ứng dụng.
* Quản lý Logging.
* Quản lý dữ liệu và tệp tin cục bộ.
* Quản lý đường dẫn và thư mục ứng dụng.
* Xây dựng cơ chế Database Connectivity.
* Hỗ trợ nhiều loại Database Provider.
* Quản lý Connection Pool khi phù hợp.
* Cung cấp các Adapter để Core Application sử dụng.
* Có khả năng mở rộng thêm Database Provider.
* Không phụ thuộc JavaFX.
* Có thể sử dụng lại trong Desktop và Web Platform tương lai.

## 1.1. Nguyên tắc kiến trúc

1. Infrastructure không chứa UI Logic.
2. Infrastructure không chứa Business Logic.
3. Infrastructure không phụ thuộc JavaFX.
4. Infrastructure triển khai các Port từ Core.
5. Không để Core phụ thuộc vào JDBC Implementation.
6. Không để Database Driver xuất hiện trong Domain.
7. Tách biệt dữ liệu ứng dụng và dữ liệu người dùng.
8. Không lưu mật khẩu Database dưới dạng Plain Text.
9. Không tạo Connection Pool cho mọi kết nối một cách mặc định.
10. Chỉ triển khai những thành phần cần thiết cho giai đoạn hiện tại.

---

# 2. PHẠM VI PHASE 03

## 2.1. Nội dung triển khai

| STT | Hạng mục               | Nội dung                        |
| --- | ---------------------- | ------------------------------- |
| 1   | Configuration          | Quản lý cấu hình ứng dụng       |
| 2   | Logging                | Logging và quản lý log file     |
| 3   | File System            | Quản lý tệp và thư mục          |
| 4   | Local Storage          | Lưu trữ dữ liệu cục bộ          |
| 5   | Security               | Bảo vệ thông tin nhạy cảm       |
| 6   | Database API           | Hợp đồng kết nối Database       |
| 7   | Database JDBC          | JDBC Infrastructure             |
| 8   | Database Providers     | SQL Server, PostgreSQL, SQLite  |
| 9   | Connection Management  | Quản lý vòng đời Connection     |
| 10  | Infrastructure Adapter | Kết nối Infrastructure với Core |
| 11  | Testing                | Unit Test và Integration Test   |

## 2.2. Chưa triển khai trong Phase 03

* Database Explorer UI.
* SQL Editor UI.
* SQL IntelliSense.
* Query Execution UI.
* Result Grid.
* Schema Compare.
* Database Object Designer.
* Database User Management UI.
* Plugin System.
* Python Runtime.
* REST API.
* Web Frontend.
* Google Drive Integration.
* Cloud Synchronization.
* Business Logic phức tạp.

Phase này chỉ xây dựng hạ tầng cần thiết để các tính năng trên có thể sử dụng trong tương lai.

---

# 3. KIẾN TRÚC TỔNG THỂ

## 3.1. Cấu trúc thư mục

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
│   ├── infrastructure-common/
│   ├── configuration/
│   ├── logging/
│   ├── filesystem/
│   ├── storage-api/
│   ├── storage-local/
│   ├── security/
│   │
│   ├── database-api/
│   ├── database-jdbc/
│   ├── database-sqlserver/
│   ├── database-postgresql/
│   └── database-sqlite/
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

Không bắt buộc tạo toàn bộ module ngay từ đầu.

Chỉ tạo module khi bắt đầu triển khai Sub-phase tương ứng.

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
              Repository / Ports
                      ^
                      |
              Infrastructure
                      |
          +-----------+-----------+
          |           |           |
          v           v           v
     Local Storage   Logging   Database
                                  |
                                  v
                               JDBC
                                  |
                 +----------------+----------------+
                 |                |                |
                 v                v                v
             SQL Server       PostgreSQL         SQLite
```

### Nguyên tắc Dependency Inversion

Core định nghĩa các Interface cần thiết.

Infrastructure triển khai các Interface đó.

Ví dụ:

```text
Core Application
       |
       v
ProjectRepository (Interface)
       ^
       |
       |
LocalProjectRepository
       |
       v
File System
```

Core không biết dữ liệu được lưu bằng JSON, SQLite hay Database khác.

## 3.3. Quy định dependency giữa Maven Modules

* `database-jdbc` phụ thuộc `database-api`.
* `database-sqlserver` phụ thuộc `database-jdbc` và SQL Server JDBC Driver.
* `database-postgresql` phụ thuộc `database-jdbc` và PostgreSQL JDBC Driver.
* `database-sqlite` phụ thuộc `database-jdbc` và SQLite JDBC Driver.
* `storage-local` triển khai các Interface lưu trữ.
* `configuration` cung cấp cấu hình cho các module cần thiết.
* `logging` cung cấp Logging Configuration.
* Infrastructure Adapter được phép phụ thuộc Core API hoặc Application Port theo thiết kế cụ thể.

Không tạo dependency ngược từ Core sang Infrastructure.

---

# 4. CONFIGURATION MANAGEMENT

## 4.1. Mục tiêu

Xây dựng hệ thống quản lý cấu hình tập trung.

Cấu hình cần được phân biệt theo nhóm:

1. Application Configuration.
2. UI Configuration.
3. Database Connection Configuration.
4. Logging Configuration.
5. Extension Configuration trong tương lai.

## 4.2. Cấu trúc

```text
configuration/
└── src/main/java/
    └── com/lltool/infrastructure/configuration/
        ├── api/
        ├── model/
        ├── loader/
        ├── provider/
        ├── validator/
        └── service/
```

## 4.3. Các thành phần

| Thành phần             | Chức năng                   |
| ---------------------- | --------------------------- |
| ConfigurationProvider  | Cung cấp cấu hình           |
| ConfigurationLoader    | Đọc cấu hình                |
| ConfigurationValidator | Kiểm tra cấu hình           |
| ConfigurationService   | Điều phối truy cập cấu hình |
| ConfigurationModel     | Mô hình cấu hình            |

## 4.4. Phân loại cấu hình

### Application Settings

Ví dụ:

```json
{
  "application": {
    "language": "vi",
    "theme": "dark",
    "autoSave": true,
    "checkUpdates": true
  }
}
```

### Database Connection Settings

Ví dụ:

```json
{
  "connections": [
    {
      "id": "connection-001",
      "name": "Development",
      "provider": "postgresql",
      "host": "localhost",
      "port": 5432,
      "database": "sample_db",
      "username": "developer"
    }
  ]
}
```

Không lưu Password trực tiếp trong JSON.

### UI Settings

Ví dụ:

```json
{
  "window": {
    "width": 1400,
    "height": 900,
    "maximized": false
  },
  "panels": {
    "databaseExplorer": true,
    "terminal": true,
    "results": true
  }
}
```

## 4.5. Quy tắc

* Có Default Configuration.
* Có kiểm tra cấu hình trước khi sử dụng.
* Có xử lý khi file cấu hình bị lỗi.
* Có cơ chế Backup trước khi ghi cấu hình quan trọng.
* Có phân biệt cấu hình mặc định và cấu hình người dùng.
* Không để các module tự đọc JSON tùy ý.
* Có phiên bản cấu trúc cấu hình để hỗ trợ Migration trong tương lai.

## 4.6. Đầu ra

* Configuration API.
* Configuration Loader.
* Configuration Validator.
* Configuration Service.
* Default Configuration.
* Configuration Documentation.

---

# 5. LOGGING SYSTEM

## 5.1. Mục tiêu

Xây dựng hệ thống Logging thống nhất cho toàn bộ ứng dụng.

Logging phải hỗ trợ:

* Application Log.
* Error Log.
* Database Connection Log.
* Query Execution Log trong tương lai.
* Extension Log trong tương lai.

## 5.2. Công nghệ

* SLF4J: Logging API.
* Logback: Logging Implementation.

Không để các module phụ thuộc trực tiếp vào Logback.

## 5.3. Cấu trúc

```text
logging/
└── src/main/
    ├── java/
    │   └── com/lltool/infrastructure/logging/
    │       ├── configuration/
    │       ├── service/
    │       └── appender/
    │
    └── resources/
        └── logback.xml
```

## 5.4. Log Levels

| Level | Mục đích                     |
| ----- | ---------------------------- |
| TRACE | Thông tin chi tiết khi Debug |
| DEBUG | Thông tin phục vụ phát triển |
| INFO  | Hoạt động bình thường        |
| WARN  | Cảnh báo                     |
| ERROR | Lỗi xử lý                    |

## 5.5. Log Rotation

Cấu hình:

* Giới hạn dung lượng mỗi Log File.
* Giới hạn số ngày lưu Log.
* Tự động tạo Log File mới.
* Có thể thay đổi Log Level.
* Không ghi Password hoặc Secret vào Log.

## 5.6. Đầu ra

* Logging Configuration.
* Log File Management.
* Log Rotation.
* Logging Convention.
* Error Logging.

---

# 6. FILE SYSTEM & LOCAL STORAGE

## 6.1. Mục tiêu

Xây dựng cơ chế quản lý tệp và lưu trữ cục bộ.

Ứng dụng cần hoạt động Local-first, không yêu cầu Server để chạy các chức năng cơ bản.

## 6.2. Phân loại dữ liệu

Phân biệt rõ:

| Nhóm dữ liệu       | Ví dụ                       | Vị trí                                   |
| ------------------ | --------------------------- | ---------------------------------------- |
| Application Data   | Cache, log, runtime data    | App Data                                 |
| User Configuration | Theme, Settings             | User Data                                |
| User Data          | Project, Snippet, Workspace | User Data hoặc vị trí do người dùng chọn |
| Sensitive Data     | Database Password, Secret   | Secure Storage                           |
| Temporary Data     | File tạm                    | Temp Directory                           |

## 6.3. Directory Structure

Đề xuất:

```text
ApplicationData/
│
├── config/
│   ├── settings.json
│   └── connections.json
│
├── data/
│   ├── projects/
│   ├── workspaces/
│   └── snippets/
│
├── logs/
│
├── cache/
│
├── temp/
│
└── security/
```

Đây là cấu trúc logic. Vị trí thực tế cần sử dụng thư mục phù hợp với từng hệ điều hành.

Không mặc định lưu dữ liệu người dùng bên cạnh file EXE hoặc JAR.

## 6.4. File System API

Các chức năng:

```text
FileService
├── readFile()
├── writeFile()
├── deleteFile()
├── copyFile()
├── moveFile()
├── exists()
└── createDirectory()
```

Yêu cầu:

* Sử dụng Java NIO.
* Xử lý UTF-8 mặc định cho dữ liệu văn bản.
* Có xử lý lỗi I/O.
* Hỗ trợ Path trên Windows và macOS.
* Tránh hardcode đường dẫn.
* Hỗ trợ thao tác ghi file an toàn.

## 6.5. Atomic Write

Khi ghi các file cấu hình quan trọng:

1. Ghi vào file tạm.
2. Kiểm tra ghi thành công.
3. Thay thế file cũ bằng file mới.
4. Giữ bản Backup nếu cần.

Mục tiêu: Hạn chế tình trạng file cấu hình bị hỏng khi ứng dụng đóng đột ngột.

## 6.6. Local Storage API

Định nghĩa các hợp đồng lưu trữ độc lập với công nghệ.

Ví dụ:

```java
public interface StorageProvider {

    <T> Optional<T> read(
        String key,
        Class<T> type
    );

    <T> void write(
        String key,
        T value
    );

    void delete(String key);

    boolean exists(String key);
}
```

Có thể triển khai:

* JsonStorageProvider.
* FileStorageProvider.
* SqliteStorageProvider trong tương lai.

Không nên dùng một Storage API quá tổng quát cho mọi loại dữ liệu. Nếu một loại dữ liệu cần truy vấn phức tạp, nên sử dụng Repository riêng.

## 6.7. Đầu ra

* Application Directory Manager.
* File System Service.
* Storage API.
* Local Storage Implementation.
* Atomic File Writer.
* Backup Strategy.

---

# 7. SECURITY INFRASTRUCTURE

## 7.1. Mục tiêu

Bảo vệ thông tin nhạy cảm được ứng dụng lưu trữ.

Đặc biệt là:

* Database Password.
* API Key trong tương lai.
* License Key nếu cần lưu trữ.
* Thông tin xác thực của Extension.

## 7.2. Secure Storage

Không lưu Password dưới dạng Plain Text trong:

* JSON.
* XML.
* Log File.
* Database Connection String.
* Source Code.

## 7.3. Chiến lược lưu trữ

Đối với Windows:

* Đánh giá Windows Credential Manager hoặc Windows DPAPI.
* Có thể sử dụng thư viện Java phù hợp để tích hợp.

Đối với macOS:

* Đánh giá macOS Keychain.

Thiết kế một Interface chung:

```java
public interface SecureStorage {

    void store(
        String key,
        char[] secret
    );

    Optional<char[]> retrieve(String key);

    void delete(String key);
}
```

Có thể triển khai theo từng hệ điều hành:

```text
SecureStorage
    |
    +── WindowsSecureStorage
    |
    +── MacOSSecureStorage
    |
    +── UnsupportedSecureStorage
```

Không tự xây dựng thuật toán mã hóa riêng.

Không lưu Master Password trong cùng vị trí với dữ liệu được mã hóa.

## 7.4. Quy tắc bảo mật

* Không ghi Secret vào Log.
* Hạn chế thời gian giữ Password trong bộ nhớ.
* Xóa hoặc giải phóng mảng ký tự sau khi sử dụng khi có thể.
* Không đưa Password vào DTO hiển thị trên UI.
* Không lưu Password trong Connection Profile JSON.
* Kiểm tra quyền truy cập file.

---

# 8. DATABASE INFRASTRUCTURE

Đây là một trong những phần quan trọng nhất của Phase 03.

Mục tiêu là xây dựng nền tảng Database Connectivity để sau này phát triển Database Explorer, SQL Editor và Query Execution.

## 8.1. Database Providers

Trong giai đoạn đầu hỗ trợ:

| Provider   | Công nghệ | Driver                    |
| ---------- | --------- | ------------------------- |
| SQL Server | JDBC      | Microsoft JDBC Driver     |
| PostgreSQL | JDBC      | PostgreSQL JDBC Driver    |
| SQLite     | JDBC      | Xerial SQLite JDBC Driver |

Các Provider khác có thể bổ sung sau mà không phải thay đổi toàn bộ Core.

## 8.2. Cấu trúc module

```text
infrastructure/
│
├── database-api/
│   └── src/main/java/
│       └── com/lltool/database/api/
│           ├── provider/
│           ├── connection/
│           ├── configuration/
│           ├── metadata/
│           └── exception/
│
├── database-jdbc/
│   └── src/main/java/
│       └── com/lltool/database/jdbc/
│           ├── connection/
│           ├── datasource/
│           ├── factory/
│           ├── transaction/
│           └── exception/
│
├── database-sqlserver/
│
├── database-postgresql/
│
└── database-sqlite/
```

## 8.3. Database API

Định nghĩa hợp đồng chung cho các Database Provider.

Ví dụ:

```java
public interface DatabaseProvider {

    String getProviderId();

    String getDisplayName();

    DatabaseConnection createConnection(
        DatabaseConnectionConfig config
    );

    boolean supports(DatabaseType type);
}
```

Connection:

```java
public interface DatabaseConnection {

    void connect();

    void disconnect();

    boolean isConnected();

    ConnectionStatus getStatus();
}
```

Lưu ý:

Không công khai JDBC Connection trực tiếp cho Core Domain.

Nếu cần thực thi SQL, hãy thiết kế một API chuyên biệt ở Application/Infrastructure Boundary thay vì để các module UI thao tác JDBC Connection tùy ý.

## 8.4. Connection Configuration

Mô hình cấu hình:

```java
public record DatabaseConnectionConfig(
    String id,
    String name,
    DatabaseType databaseType,
    String host,
    int port,
    String database,
    String username,
    String credentialReference
) {}
```

Password được lấy từ Secure Storage thông qua `credentialReference`.

Không đưa Password vào Connection Configuration DTO thông thường.

## 8.5. JDBC Infrastructure

Sử dụng:

* JDBC API.
* HikariCP khi cần Connection Pool.
* PreparedStatement cho truy vấn có tham số.
* try-with-resources để quản lý tài nguyên.

Các thành phần:

```text
JdbcConnectionFactory
JdbcDataSourceFactory
JdbcTransactionManager
JdbcExceptionTranslator
```

## 8.6. Provider Implementation

### SQL Server

Triển khai:

* SQL Server JDBC Driver.
* Connection URL Builder.
* Connection Validation.
* Authentication Configuration.
* SSL Configuration.
* Exception Mapping.

### PostgreSQL

Triển khai:

* PostgreSQL JDBC Driver.
* Connection URL Builder.
* Connection Validation.
* Authentication Configuration.
* SSL Configuration.
* Exception Mapping.

### SQLite

Triển khai:

* SQLite JDBC Driver.
* Local Database File.
* Connection Validation.
* SQLite Configuration.
* Transaction Handling.

SQLite không cần sử dụng Connection Pool theo cách giống SQL Server hoặc PostgreSQL.

## 8.7. Connection Lifecycle

Luồng kết nối:

```text
Connection Request
        |
        v
Database Provider
        |
        v
Validate Configuration
        |
        v
Resolve Credentials
        |
        v
Create JDBC Connection
        |
        v
Test Connection
        |
        v
Connected / Failed
```

Yêu cầu:

* Có Connection Timeout.
* Có Connection Validation.
* Có xử lý Connection Failure.
* Có Disconnect.
* Có quản lý vòng đời tài nguyên.
* Không tạo kết nối lặp không cần thiết.
* Không giữ Connection vô thời hạn.

## 8.8. Connection Pool

Đánh giá HikariCP cho các Database Server.

Không mặc định tạo một Pool cho mọi Connection Profile.

Cần xác định:

* Khi nào tạo Pool.
* Khi nào đóng Pool.
* Số lượng Connection tối đa.
* Connection Timeout.
* Idle Timeout.
* Max Lifetime.

Đối với ứng dụng Desktop, cần cân nhắc số lượng kết nối và RAM sử dụng.

Mục tiêu là không làm ứng dụng nặng hơn mức cần thiết.

---

# 9. DATABASE PROVIDER REGISTRY

## 9.1. Mục tiêu

Tạo cơ chế đăng ký và tìm kiếm Database Provider.

Không hardcode điều kiện SQL Server/PostgreSQL ở nhiều vị trí.

## 9.2. Cấu trúc

```text
DatabaseProviderRegistry
├── registerProvider()
├── unregisterProvider()
├── getProvider()
└── getAllProviders()
```

Ví dụ:

```java
DatabaseProviderRegistry registry =
    new DefaultDatabaseProviderRegistry();

registry.registerProvider(
    new SqlServerDatabaseProvider()
);

registry.registerProvider(
    new PostgreSqlDatabaseProvider()
);

registry.registerProvider(
    new SqliteDatabaseProvider()
);
```

## 9.3. Quy tắc

* Provider có ID duy nhất.
* Không đăng ký Provider trùng ID.
* Có thể tìm Provider theo ID.
* Có thể lấy danh sách Provider.
* Có thể mở rộng Provider trong tương lai.

Phase này chỉ xây dựng Registry nội bộ.

Chưa tích hợp PF4J hoặc Plugin System.

---

# 10. INFRASTRUCTURE ADAPTER

## 10.1. Mục tiêu

Kết nối Core Application với các Infrastructure Implementation.

Ví dụ:

```text
Core Application
       |
       v
ProjectRepository
       ^
       |
       |
LocalProjectRepository
       |
       v
JsonStorageProvider
       |
       v
File System
```

## 10.2. Adapter dự kiến

```text
infrastructure/
└── adapters/
    ├── project/
    ├── workspace/
    └── configuration/
```

Không nhất thiết phải tạo một module Adapter riêng nếu số lượng Adapter còn ít.

## 10.3. Quy tắc

* Adapter triển khai Interface từ Core.
* Adapter không đưa công nghệ lưu trữ vào Domain.
* Có thể thay JsonStorage bằng SQLite mà không thay đổi Use Case.
* Có thể thay Infrastructure Implementation mà không phải viết lại JavaFX UI.

---

# 11. TESTING STRATEGY

## 11.1. Unit Test

Sử dụng JUnit 5.

Kiểm thử:

* Configuration Loader.
* Configuration Validator.
* File System Service.
* Storage Provider.
* Error Mapping.
* Database URL Builder.
* Provider Registry.
* Connection Configuration Validator.

## 11.2. Integration Test

Kiểm thử thực tế:

### Local Storage

* Đọc file.
* Ghi file.
* Xóa file.
* Ghi đè file.
* Backup.
* Xử lý file lỗi.
* Xử lý quyền truy cập.

### Database

* Kết nối SQL Server.
* Kết nối PostgreSQL.
* Kết nối SQLite.
* Disconnect.
* Connection Failure.
* Timeout.
* Authentication Failure.
* Transaction.

Các bài kiểm thử SQL Server và PostgreSQL cần môi trường Database tương ứng.

Không bắt buộc tất cả Developer phải cài cả hai Database trên máy cá nhân; có thể sử dụng môi trường test riêng.

## 11.3. Architecture Test

Sử dụng ArchUnit.

Kiểm tra:

* Infrastructure không phụ thuộc JavaFX.
* Core không phụ thuộc Infrastructure Implementation.
* Domain không phụ thuộc JDBC.
* Database Provider không phụ thuộc UI.
* Không có Circular Dependency.

---

# 12. KẾ HOẠCH TRIỂN KHAI THEO SUB-PHASE

## Phase 3.1 – Infrastructure Foundation

Công việc:

* Tạo Infrastructure Maven Modules.
* Thiết lập Dependency.
* Chuẩn hóa Package Naming.
* Thiết lập Unit Test.
* Kiểm tra Dependency Rules.

Kết quả:

Infrastructure có cấu trúc độc lập và build thành công.

## Phase 3.2 – Configuration Management

Công việc:

* Configuration Model.
* Configuration Provider.
* Configuration Loader.
* Configuration Validator.
* Default Configuration.
* Configuration Migration Strategy.

Kết quả:

Có thể đọc, kiểm tra và quản lý cấu hình ứng dụng.

## Phase 3.3 – Logging System

Công việc:

* Tích hợp SLF4J.
* Tích hợp Logback.
* Cấu hình Log Levels.
* Log Rotation.
* Error Logging.
* Sensitive Data Masking.

Kết quả:

Toàn bộ ứng dụng có thể sử dụng chung Logging System.

## Phase 3.4 – File System & Local Storage

Công việc:

* Application Directory Manager.
* File System Service.
* Storage API.
* JSON Storage.
* Atomic Write.
* Backup Strategy.

Kết quả:

Ứng dụng có thể lưu trữ dữ liệu cục bộ an toàn.

## Phase 3.5 – Security Infrastructure

Công việc:

* Secure Storage API.
* Windows Credential Storage.
* macOS Keychain Strategy.
* Credential Reference.
* Secret Handling.
* Security Test.

Kết quả:

Có cơ chế lưu và truy xuất thông tin nhạy cảm.

## Phase 3.6 – Database API & JDBC Foundation

Công việc:

* Database API.
* Database Provider Interface.
* Database Connection Interface.
* Connection Configuration.
* JDBC Factory.
* Exception Translator.
* Connection Lifecycle.

Kết quả:

Có nền tảng Database Connectivity chung.

## Phase 3.7 – Database Providers

Công việc:

* SQL Server Provider.
* PostgreSQL Provider.
* SQLite Provider.
* Connection Validation.
* Connection Testing.
* Provider Registry.

Kết quả:

Có thể kết nối và quản lý ba loại Database.

## Phase 3.8 – Infrastructure Adapter

Công việc:

* Project Repository Adapter.
* Workspace Repository Adapter nếu cần.
* Configuration Adapter.
* Storage Integration.
* Core Integration.

Kết quả:

Core Application có thể sử dụng Infrastructure thông qua các Interface đã định nghĩa.

## Phase 3.9 – Testing & Documentation

Công việc:

* Unit Test.
* Integration Test.
* Architecture Test.
* Security Test.
* Performance Test cơ bản.
* Documentation.
* Build Validation.

Kết quả:

Infrastructure sẵn sàng tích hợp với Desktop Framework và các Feature Module.

---

# 13. CẤU TRÚC THƯ MỤC SAU PHASE 03

```text
infrastructure/
│
├── infrastructure-common/
│
├── configuration/
│   └── src/main/java/
│       └── com/lltool/infrastructure/configuration/
│           ├── api/
│           ├── model/
│           ├── loader/
│           ├── provider/
│           └── validator/
│
├── logging/
│   ├── src/main/java/
│   └── src/main/resources/
│
├── filesystem/
│   └── src/main/java/
│       └── com/lltool/infrastructure/filesystem/
│
├── storage-api/
│
├── storage-local/
│
├── security/
│
├── database-api/
│
├── database-jdbc/
│
├── database-sqlserver/
│
├── database-postgresql/
│
└── database-sqlite/
```

Chỉ tạo những module thực sự cần thiết.

Không bắt buộc tách mọi package thành một Maven Module riêng.

---

# 14. CÔNG NGHỆ SỬ DỤNG

| Công nghệ              | Mục đích                                    |
| ---------------------- | ------------------------------------------- |
| Java 25                | Ngôn ngữ chính                              |
| Maven                  | Build và quản lý Module                     |
| SLF4J                  | Logging API                                 |
| Logback                | Logging Implementation                      |
| Jackson                | Đọc và ghi JSON                             |
| Java NIO               | File System                                 |
| JDBC                   | Database Connectivity                       |
| HikariCP               | Connection Pool cho Database Server khi cần |
| Microsoft JDBC Driver  | SQL Server                                  |
| PostgreSQL JDBC Driver | PostgreSQL                                  |
| Xerial SQLite JDBC     | SQLite                                      |
| JUnit 5                | Unit Test                                   |
| ArchUnit               | Architecture Test                           |

Các thư viện phải được quản lý phiên bản tập trung trong Maven Parent POM.

Không sử dụng thư viện mới nếu Java Standard Library đã đáp ứng tốt nhu cầu.

---

# 15. TIÊU CHÍ NGHIỆM THU

| STT | Tiêu chí             | Yêu cầu                          |
| --- | -------------------- | -------------------------------- |
| 1   | Maven Build          | Build thành công                 |
| 2   | Configuration        | Đọc và ghi cấu hình              |
| 3   | Logging              | Ghi Log và Rotation              |
| 4   | File System          | Thao tác file ổn định            |
| 5   | Local Storage        | Lưu và đọc dữ liệu               |
| 6   | Atomic Write         | Hạn chế hỏng file khi ghi        |
| 7   | Security             | Không lưu Password Plain Text    |
| 8   | Database API         | Có hợp đồng Provider             |
| 9   | SQL Server           | Kết nối thành công               |
| 10  | PostgreSQL           | Kết nối thành công               |
| 11  | SQLite               | Kết nối thành công               |
| 12  | Connection Lifecycle | Connect/Disconnect hoạt động     |
| 13  | Provider Registry    | Đăng ký và tìm Provider          |
| 14  | Adapter              | Core sử dụng được Infrastructure |
| 15  | Architecture Test    | Không vi phạm Dependency Rules   |
| 16  | Documentation        | Có tài liệu kỹ thuật             |

---

# 16. BÀI KIỂM TRA THỰC TẾ

Sau Phase 03 cần thực hiện được các bài kiểm tra sau.

## Test 1 – Configuration

```text
Application Start
       |
       v
Configuration Loader
       |
       v
Read settings.json
       |
       v
Validate Configuration
       |
       v
Configuration Available
```

## Test 2 – Local Storage

```text
Create Project
       |
       v
ProjectRepository
       |
       v
Local Storage Adapter
       |
       v
Write Project Data
       |
       v
Read Project Data
       |
       v
Verify Data
```

## Test 3 – Database Connectivity

```text
Database Connection Request
       |
       v
Database Provider Registry
       |
       v
Resolve Provider
       |
       v
Create JDBC Connection
       |
       v
Test Connection
       |
       v
Return Connection Status
```

Kiểm tra lần lượt:

* SQL Server.
* PostgreSQL.
* SQLite.

## Test 4 – Core Integration

```text
Core Application
       |
       v
ProjectRepository Interface
       |
       v
LocalProjectRepository
       |
       v
Storage Provider
       |
       v
File System
```

Kiểm tra việc thay đổi Infrastructure Implementation mà không cần thay đổi Domain và Use Case.

---

# 17. QUY TẮC QUẢN LÝ PHẠM VI

Trong quá trình triển khai:

* Không thay đổi công nghệ JavaFX đã lựa chọn.
* Không đưa Infrastructure Logic vào Controller.
* Không đưa JDBC vào Core Domain.
* Không để UI gọi JDBC trực tiếp.
* Không lưu Password trong JSON.
* Không hardcode Database Connection String.
* Không tạo Connection Pool không cần thiết.
* Không tạo quá nhiều Maven Modules.
* Không triển khai REST API.
* Không triển khai Plugin System.
* Không triển khai Cloud Synchronization.
* Không triển khai các tính năng nghiệp vụ chưa đến giai đoạn.

Mọi thay đổi kiến trúc phải nêu rõ:

1. Lý do thay đổi.
2. Module bị ảnh hưởng.
3. Dependency mới.
4. Ảnh hưởng đến các Phase trước.
5. Cách kiểm thử.

---

# 18. KẾT QUẢ ĐẦU RA

Sau Phase 03 cần có:

* [ ] Infrastructure Maven Modules.
* [ ] Configuration Management.
* [ ] Logging System.
* [ ] File System Service.
* [ ] Local Storage API.
* [ ] Local Storage Implementation.
* [ ] Atomic File Writer.
* [ ] Secure Storage API.
* [ ] Database API.
* [ ] JDBC Infrastructure.
* [ ] SQL Server Provider.
* [ ] PostgreSQL Provider.
* [ ] SQLite Provider.
* [ ] Database Provider Registry.
* [ ] Connection Lifecycle Management.
* [ ] Infrastructure Adapters.
* [ ] Unit Tests.
* [ ] Integration Tests.
* [ ] Architecture Tests.
* [ ] Infrastructure Documentation.

**KẾT THÚC PHASE 03**

Ứng dụng có một Infrastructure Layer hoàn chỉnh ở mức nền tảng.

Core có thể sử dụng các dịch vụ lưu trữ, cấu hình và Database Connectivity thông qua Interface mà không phụ thuộc vào công nghệ triển khai cụ thể.

Phase tiếp theo:

**PHASE 04 – DESKTOP FRAMEWORK**

Triển khai JavaFX Desktop Shell, Window Management, Navigation, Application Lifecycle và cơ chế tích hợp Core với Desktop UI.
