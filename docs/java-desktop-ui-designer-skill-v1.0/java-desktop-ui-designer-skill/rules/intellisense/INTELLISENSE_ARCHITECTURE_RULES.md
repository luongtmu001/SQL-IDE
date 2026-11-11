# LLTool -- INTELLISENSE ARCHITECTURE RULES

**Architecture:** DataGrip-inspired Metadata Introspection\
**Database:** SQL Server + PostgreSQL\
**Backend:** Python\
**Frontend:** Existing LLTool UI + CodeMirror\
**Strategy:** Local-first, Cache-first, Context-aware

------------------------------------------------------------------------

# 1. CORE PRINCIPLES

## 1.1. Metadata-first Architecture

Metadata là nguồn dữ liệu chính cho IntelliSense.

Completion Engine không được trực tiếp truy vấn Database để lấy metadata
trong quá trình người dùng gõ SQL.

Luồng bắt buộc:

``` text
Database
    |
    v
Introspection Engine
    |
    v
Metadata Model
    |
    v
Metadata Cache
    |
    v
Metadata Provider
    |
    v
SQL Resolver
    |
    v
Completion Engine
    |
    v
CodeMirror
```

## 1.2. Separation of Responsibilities

Các thành phần phải độc lập:

  Component              Responsibility
  ---------------------- ---------------------------
  Introspection Engine   Thu thập metadata
  Database Adapter       Xử lý metadata theo DBMS
  Metadata Model         Chuẩn hóa dữ liệu
  Metadata Cache         Lưu trữ metadata
  Metadata Provider      Cung cấp metadata
  SQL Resolver           Resolve object và context
  Completion Engine      Tạo suggestions
  Frontend Provider      Hiển thị suggestions

Không đặt toàn bộ logic trong một module.

Không đưa logic metadata vào `main.py`.

Không để CodeMirror phụ thuộc trực tiếp vào SQL Server hoặc PostgreSQL.

------------------------------------------------------------------------

# 2. INTROSPECTION LEVELS

LLTool áp dụng mô hình ba cấp độ lấy cảm hứng từ DataGrip.

## LEVEL 1 -- OBJECT NAMES

Thu thập:

-   Database names.
-   Schema names.
-   Table names.
-   View names.
-   Procedure names.
-   Function names.
-   Object types.

Mục đích:

-   Database Explorer.
-   Object Completion.
-   Object Resolution cơ bản.

Không cần tải toàn bộ columns hoặc source code.

## LEVEL 2 -- STRUCTURAL METADATA

Thu thập:

-   Table columns.
-   Column types.
-   Nullability.
-   Primary keys.
-   Foreign keys.
-   Constraints.
-   Index metadata.
-   Function signatures.
-   Procedure parameters.
-   View structure.

Mục đích:

-   Column Completion.
-   Alias Resolution.
-   JOIN Resolution.
-   Type-aware Completion.
-   SQL Inspection.

## LEVEL 3 -- FULL METADATA

Thu thập thêm:

-   View definitions.
-   Procedure definitions.
-   Function definitions.
-   Trigger definitions.
-   Object source code.
-   Extended object attributes.

Mục đích:

-   DDL Viewer.
-   Navigation.
-   Object Inspection.
-   Advanced SQL Analysis.

IMPORTANT:

Ba cấp độ trên là cách tổ chức metadata của LLTool dựa trên mô hình
DataGrip, không phải cam kết rằng mọi loại metadata của hai DBMS đều có
thể chia chính xác như nhau.

------------------------------------------------------------------------

# 3. LOADING STRATEGY

## 3.1. Connection Initialization

Khi connection được thiết lập:

``` text
Connection Established
        |
        v
Identify DBMS
        |
        v
Load Cached Metadata
        |
        v
Load Database / Schema Scope
        |
        v
Check Metadata Version
        |
        v
Background Introspection
        |
        v
Update Metadata Cache
```

Không block giao diện.

Không bắt buộc tải xong toàn bộ metadata mới cho phép mở SQL Editor.

## 3.2. Lazy Loading

Metadata không cần thiết không được tải trước.

Ví dụ:

``` sql
SELECT *
FROM dbo.Customers c
WHERE c.
```

Nếu structural metadata đã tồn tại:

``` text
Resolve alias c
      |
      v
Resolve Customers
      |
      v
Get columns from cache
      |
      v
Return suggestions
```

Nếu chưa có metadata:

-   Đánh dấu metadata chưa sẵn sàng.
-   Đưa yêu cầu loading vào background scheduler.
-   Không chặn editor.
-   Không thực hiện truy vấn lặp lại cho cùng một object.
-   Cập nhật cache sau khi tải thành công.

## 3.3. Loading Priority

Ưu tiên:

1.  Database và schema names.
2.  Table và view names.
3.  Columns của schema được chọn.
4.  Routine signatures.
5.  Object definitions.

Cho phép điều chỉnh theo quy mô database.

------------------------------------------------------------------------

# 4. METADATA MODEL

Xây dựng Unified Metadata Model.

Ví dụ:

``` python
@dataclass
class DatabaseObject:
    object_id: str
    connection_id: str
    database: str
    schema: str
    name: str
    object_type: str

    metadata_level: int
    metadata_status: str

    columns: list
    parameters: list
    definition: str | None
```

Ví dụ trạng thái:

``` text
NOT_LOADED
LOADING
PARTIAL
LOADED
STALE
FAILED
```

Yêu cầu:

-   Không phụ thuộc DBMS.
-   Không phụ thuộc Frontend.
-   Không phụ thuộc CodeMirror.
-   Không lưu connection credentials trong metadata cache.
-   Có khả năng mở rộng DBMS.
-   Hỗ trợ metadata versioning.

Database Adapter chịu trách nhiệm chuyển đổi metadata riêng của DBMS
thành Unified Metadata Model.

Không đưa SQL Server-specific logic vào Metadata Provider.

Không đưa PostgreSQL-specific logic vào Completion Engine.

------------------------------------------------------------------------

# 5. CACHE ARCHITECTURE

## 5.1. Two-Level Cache

### L1 -- Memory Cache

Dùng cho:

-   Object lookup.
-   Column lookup.
-   Schema lookup.
-   Alias resolution.
-   Completion.

Yêu cầu:

-   Fast lookup.
-   Thread-safe.
-   Connection isolation.
-   Không truy vấn DB khi cache đã đáp ứng yêu cầu.

### L2 -- Persistent Cache

Dùng để:

-   Khôi phục metadata sau khi khởi động lại.
-   Giảm thời gian khởi tạo connection.
-   Hạn chế truy vấn metadata không cần thiết.

Có thể sử dụng SQLite làm persistent storage nếu phù hợp với kiến trúc
hiện tại.

## 5.2. Cache Key

Không được sử dụng object name làm cache key duy nhất.

Định danh phải bao gồm:

``` text
Connection ID
    +
Database
    +
Schema
    +
Object Type
    +
Object Name
```

Phải xử lý đúng quy tắc phân biệt chữ hoa/chữ thường của từng DBMS.

## 5.3. Cache Invalidation

Metadata phải được đánh dấu stale khi:

-   Connection thay đổi.
-   Database thay đổi.
-   Schema thay đổi.
-   DDL được thực thi.
-   Người dùng yêu cầu refresh.
-   Metadata version không còn hợp lệ.

Không xóa toàn bộ cache nếu chỉ một object thay đổi.

## 5.4. Cache Consistency

Không sử dụng metadata stale như metadata đã xác thực.

Phải phân biệt:

-   Cached metadata.
-   Loading metadata.
-   Fresh metadata.
-   Failed metadata.

Khi refresh thất bại, không được xóa metadata cũ một cách vô điều kiện.

------------------------------------------------------------------------

# 6. SQL RESOLVER INTEGRATION

SQL Resolver phải sử dụng Metadata Provider làm nguồn metadata.

Các chức năng bắt buộc:

## 6.1. Object Resolution

``` sql
SELECT *
FROM dbo.Customers
```

Resolve đúng:

-   Database.
-   Schema.
-   Object.
-   Object type.

## 6.2. Alias Resolution

``` sql
SELECT *
FROM dbo.Customers c
JOIN dbo.Orders o
    ON c.CustomerID = o.CustomerID
```

Resolver phải xây dựng alias map:

``` python
{
    "c": "dbo.Customers",
    "o": "dbo.Orders"
}
```

## 6.3. Column Resolution

``` sql
SELECT *
FROM dbo.Customers c
WHERE c.
```

Chỉ lấy columns thuộc Customers.

## 6.4. JOIN Resolution

``` sql
SELECT *
FROM dbo.Customers c
JOIN dbo.Orders o
    ON c.CustomerID = o.
```

Chỉ lấy columns phù hợp với alias o.

## 6.5. Advanced Context

Phải hỗ trợ theo lộ trình:

-   Multiple JOIN.
-   CTE.
-   Subquery.
-   Nested CTE.
-   Derived tables.
-   Temporary tables.
-   Variables.
-   Multiple database contexts.

Không giả định mọi object đều có thể resolve bằng tên đơn giản.

------------------------------------------------------------------------

# 7. COMPLETION ENGINE

## 7.1. Local-first

Completion Engine phải ưu tiên xử lý metadata trong RAM.

Không được:

-   Query database mỗi lần gõ.
-   Gọi API để lấy toàn bộ metadata mỗi lần gõ.
-   Tải toàn bộ object definitions để gợi ý column.
-   Chờ background introspection hoàn thành mới hiển thị suggestions.

## 7.2. Candidate Filtering

Filtering phải được thực hiện dựa trên SQL context.

Ví dụ:

``` sql
SELECT *
FROM dbo.Customers c
WHERE c.
```

Candidate source:

``` text
Customers.Columns
```

Không phải:

``` text
All Database Columns
```

## 7.3. Request Management

Bắt buộc có:

-   Debounce phù hợp.
-   Request cancellation.
-   Duplicate request prevention.
-   Stale response protection.
-   Context validation.

Response của request cũ không được ghi đè kết quả của request mới.

## 7.4. Performance Targets

Đây là mục tiêu thiết kế cần được benchmark:

  Operation                   Target
  ----------------------- ----------
  Cache object lookup       \< 10 ms
  Cache column lookup       \< 10 ms
  Alias resolution          \< 20 ms
  Candidate filtering       \< 30 ms
  Completion from cache     \< 50 ms

Không tính thời gian tải metadata lần đầu vào các chỉ số cache-hit.

------------------------------------------------------------------------

# 8. SMART REFRESH

LLTool cần triển khai cơ chế cập nhật metadata thông minh.

Ví dụ:

``` sql
ALTER TABLE dbo.Customers
ADD PhoneNumber VARCHAR(20);
```

Luồng:

``` text
SQL Execution
      |
      v
DDL Detection
      |
      v
Identify Affected Object
      |
      v
Mark Metadata Stale
      |
      v
Refresh Specific Object
      |
      v
Update Cache
```

Nếu không xác định được object bị ảnh hưởng:

-   Refresh schema liên quan.
-   Hoặc yêu cầu người dùng refresh toàn bộ phạm vi introspection.

Không được giả định mọi thay đổi đều có thể xác định chính xác bằng cách
phân tích câu SQL.

Các trường hợp dynamic SQL, procedure gọi gián tiếp và thay đổi từ
session khác cần cơ chế fallback.

------------------------------------------------------------------------

# 9. DATABASE ADAPTER RULES

Tách biệt hai adapter:

``` text
metadata/
    adapters/
        base_adapter.py
        sqlserver_adapter.py
        postgres_adapter.py
```

Base Adapter định nghĩa interface chung.

SQL Server Adapter:

-   System catalog queries.
-   SQL Server object types.
-   SQL Server columns.
-   SQL Server routine metadata.

PostgreSQL Adapter:

-   PostgreSQL system catalogs.
-   PostgreSQL object types.
-   PostgreSQL columns.
-   PostgreSQL routine metadata.

Không ép hai DBMS sử dụng cùng một SQL query.

Chỉ chuẩn hóa kết quả sau khi thu thập metadata.

Phải kiểm tra quyền truy cập system catalogs và khả năng tương thích
phiên bản database.

------------------------------------------------------------------------

# 10. UI INDEPENDENCE

Phase Metadata Engine không được thay đổi:

-   Existing SQL Editor UI.
-   Existing CodeMirror configuration.
-   Existing Database Explorer layout.
-   Existing Result Grid.
-   Existing Theme.
-   Existing Toolbar.
-   Existing IntelliSense visual design.

Metadata Engine chỉ thay đổi cách dữ liệu được cung cấp cho
IntelliSense.

Nếu cần thay đổi Completion Provider, phải giữ nguyên giao diện và hành
vi hiển thị hiện tại.

------------------------------------------------------------------------

# 11. PROJECT STRUCTURE

Cấu trúc đề xuất:

``` text
app/
    services/
        metadata/
            __init__.py

            metadata_engine.py
            metadata_model.py
            metadata_provider.py
            metadata_cache.py
            metadata_loader.py
            metadata_scheduler.py
            metadata_refresh.py

            adapters/
                __init__.py
                base_adapter.py
                sqlserver_adapter.py
                postgres_adapter.py

        intelligence/
            sql_resolver.py
            alias_resolver.py
            object_resolver.py
            column_resolver.py
            completion_engine.py
```

Đây là cấu trúc định hướng.

Agent phải kiểm tra cấu trúc thực tế trước khi tạo module.

Không tạo module trùng chức năng với module hiện tại.

Không di chuyển hoặc đổi tên module hiện tại nếu chưa có lý do kỹ thuật
rõ ràng.

------------------------------------------------------------------------

# 12. THREADING AND CONCURRENCY

Metadata loading phải chạy độc lập với UI thread.

Bắt buộc:

-   Background execution.
-   Thread-safe cache.
-   Per-connection loading state.
-   Duplicate loading prevention.
-   Cancellation support.
-   Error isolation.
-   Connection lifecycle management.

Không cho phép một connection loading metadata làm ảnh hưởng đến các
connection khác.

Không để nhiều request cùng tải một object khi object đó đang LOADING.

------------------------------------------------------------------------

# 13. SECURITY AND DATABASE SAFETY

Metadata queries phải:

-   Sử dụng parameterized queries khi phù hợp.
-   Không thực thi SQL từ tên object chưa được xác thực.
-   Tôn trọng quyền truy cập của database user.
-   Không yêu cầu quyền admin chỉ để hỗ trợ IntelliSense.
-   Không lưu password trong metadata cache.
-   Không làm thay đổi dữ liệu database.

Introspection mặc định chỉ đọc metadata.

------------------------------------------------------------------------

# 14. TESTING REQUIREMENTS

Mỗi Phase phải có kiểm thử.

### Functional Tests

-   SQL Server metadata.
-   PostgreSQL metadata.
-   Object completion.
-   Column completion.
-   Alias resolution.
-   JOIN resolution.
-   CTE resolution.
-   Multiple connections.

### Cache Tests

-   Cache hit.
-   Cache miss.
-   Cache invalidation.
-   Persistent cache restore.
-   Stale metadata.
-   Concurrent loading.

### Performance Tests

-   Small database.
-   Medium database.
-   Large database.
-   High object count.
-   Multiple SQL Editor tabs.
-   Multiple connections.

### Regression Tests

Đảm bảo:

-   Không mất chức năng IntelliSense cũ.
-   Không thay đổi SQL execution.
-   Không thay đổi Result Grid.
-   Không ảnh hưởng Database Explorer.
-   Không ảnh hưởng session manager.

------------------------------------------------------------------------

# 15. IMPLEMENTATION PHASES

Thứ tự triển khai bắt buộc:

  Phase   Scope
  ------- ----------------------------
  00      Audit current architecture
  01      Metadata Engine
  02      Introspection Levels
  03      Memory + Persistent Cache
  04      SQL Resolver Integration
  05      Completion Optimization
  06      Smart Refresh

Không triển khai tất cả trong một lần.

Mỗi Phase phải:

1.  Phân tích dependencies.
2.  Xác định file ảnh hưởng.
3.  Lập implementation plan.
4.  Triển khai riêng.
5.  Kiểm thử.
6.  Regression test.
7.  Báo cáo kết quả.

------------------------------------------------------------------------

# 16. FINAL ARCHITECTURE RULE

Nguyên tắc quan trọng nhất:

**IntelliSense must be independent from database network latency once
required metadata is available.**

Diễn giải:

Khi metadata đã có trong cache, Code Completion không được phụ thuộc vào
tốc độ phản hồi của database server.

Database chỉ tham gia khi:

-   Metadata chưa tồn tại.
-   Metadata cần refresh.
-   Người dùng yêu cầu lấy thông tin mới.
-   Cần xác minh trạng thái object.

Không được hy sinh độ chính xác của SQL Resolver để đạt tốc độ.

Không được thay đổi logic nghiệp vụ hiện tại nếu không có yêu cầu.

Không được thay đổi giao diện trong các Phase xây dựng Metadata Engine.
