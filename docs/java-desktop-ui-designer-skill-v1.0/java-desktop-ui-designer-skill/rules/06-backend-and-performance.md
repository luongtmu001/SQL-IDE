# Backend & Performance Rules
# Quy tắc logic thật và hiệu năng

## 1. Thứ tự module

`core` → `infrastructure` → app gốc (`app.*`).

- `core/` không phụ thuộc JavaFX hay hạ tầng. ArchUnit kiểm tra điều này.
- `infrastructure/` theo Ports & Adapters.
- `app.framework.*` nối UI với core và hạ tầng. Controller không gọi JDBC trực tiếp.
- Pom gốc **không gom** `core/` và `infrastructure/`. Sửa hai nơi này thì phải cài lại trước khi chạy app gốc.

## 2. Công tắc backend

`app.framework.backend.BackendFlags`:

| Cờ | Ý nghĩa |
|---|---|
| Mặc định | Thật ở mọi khu vực |
| `-Dsqlide.backend=mock` hoặc `SQLIDE_BACKEND=mock` | Dữ liệu mẫu cho mọi khu vực |
| `-Dsqlide.backend.<connections\|metadata\|query\|plan>=mock\|real` | Riêng từng khu vực |
| `-Dsqlide.mockMetadata=true` | Cờ cũ, nghĩa là mock hết |
| `-Dsqlide.readOnly` | Mặc định `true` |
| `-Dsqlide.dataDir` hoặc `SQLIDE_DATA_DIR` | Đổi thư mục dữ liệu |

Khu vực format và update **chưa có** công tắc, dù kế hoạch D0.3 có nhắc tới.

## 3. Bảo vệ chỉ đọc (phải giữ cả 3 lớp)

1. `SqlStatementClassifier` kiểm tra trước khi gửi, kể cả trước hộp xác nhận PROD.
2. JDBC `setReadOnly(true)`.
3. PostgreSQL `SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY`.

Thứ tự khi chạy: chỉ đọc → Actions Guard (`SqlProtectionService`, DLG-007a) → cảnh báo PROD (DLG-007b) → thực thi. Plan thực tế chỉ chạy khi câu lệnh chỉ đọc.

## 4. Phiên và kết nối

- **Mỗi tab một kết nối** (`TabSessionManager`, khóa theo `documentId`). Các tab độc lập, chạy song song, Hủy chỉ tác động tab đó.
- Explorer dùng pool chỉ đọc riêng theo profile + database (`DesktopMetadataService`).
- Mật khẩu lấy theo thứ tự: vừa nhập → trong bộ nhớ → vault (`CredentialResolver`). Lưu mã hóa, hết hạn sau 30 ngày.
- Loại DB chỉ gồm SQL Server, PostgreSQL, SQLite. Loại khác phải báo lỗi rõ, không coi nhầm thành PostgreSQL.
- Khi có lỗi, không lưu kết quả rỗng vào cache.

## 5. Đặc thù từng DB

Không giả định SQL giữa các DB tương đương. Mã riêng của DB đặt trong provider hoặc lớp chuyên trách.

- Đổi database: SQL Server `setCatalog`, PostgreSQL mở lại kết nối.
- Tách script: `GO` cho SQL Server, theo câu lệnh cho PostgreSQL (`SqlStatementSplitter`, `ScriptRunner`).
- Thông điệp: SQL Server `PRINT`, PostgreSQL `NOTICE` qua `SqlWarningMessages`.
- SQL Server `SET SHOWPLAN_XML` và `SET STATISTICS XML` phải luôn tắt trong `finally`.

## 6. Hiệu năng

Toàn văn: `docs/performance-rules.md`. Bản tóm tắt:

- Không làm I/O, JDBC, đọc file trên FX thread.
- Lưới: dựng một lần, ít, theo phần nhìn thấy. Thêm theo lô bằng `setAll` hoặc `addAll`. Không quét toàn bộ dữ liệu để tính giao diện.
- Dữ liệu từ server: đổ theo lô (`ScriptRunner.RowSink`), hiện lô đầu ngay, hủy được giữa chừng.
- Cây: tải con khi mở node, thêm một lần.
- Cache phải có cách làm mới.
- Không đọc file hay `Preferences` trong code chạy thường xuyên. Trên Windows mỗi lần đọc `Preferences` là một lần đọc registry.
- Không tạo đối tượng nặng trong `updateItem`. Debounce 150–200 ms khi quét dữ liệu theo phím gõ.
- **WebKit của JavaFX chậm hơn Chromium khoảng 3–9 lần theo số đo ngày 2026-11-07.** Code JS chạy tốt trên LLTool chưa chắc chạy tốt ở đây. Đo trên JavaFX WebView, không đo trên trình duyệt ngoài.

## 7. Kiểm thử

| Loại | Cách chạy | Ghi chú |
|---|---|---|
| Unit | `run-tests.cmd`, `run-claude-tests.cmd` | Mock. Đọc cảnh báo A1 trong `HANDOFF.md` trước khi chạy |
| IT với server thật | `run-db-tests.cmd` | `QueryDbIT`, `GridPerfIT`, cấu hình ở `it-db.properties`, chỉ câu lệnh đọc. **Chỉ chạy khi người dùng đồng ý** |
| Giao diện | `run-ui.cmd`, `run-ui-mock.cmd` | Người dùng kiểm trên máy |

Không chạy `QueryDbIT` hay `GridPerfIT` trong vòng lặp kiểm tra thông thường.
