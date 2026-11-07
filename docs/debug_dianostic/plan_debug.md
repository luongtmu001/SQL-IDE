# LLTool – APPLICATION DIAGNOSTICS & DEBUG CENTER

**Document:** Master Implementation Plan  
**Project:** LLTool Java Desktop  
**Architecture:** Non-invasive Diagnostics  
**UI Framework:** JavaFX  
**Status:** Planned

---

# 1. PROJECT OBJECTIVE

Xây dựng hệ thống Application Diagnostics & Debug Center để theo dõi, phân tích và chẩn đoán hoạt động của ứng dụng LLTool.

Hệ thống phải hỗ trợ:

- Theo dõi quá trình khởi động.
- Theo dõi CPU, RAM, JVM và Threads.
- Theo dõi các tác vụ đang thực thi.
- Thu thập runtime exceptions.
- Theo dõi JavaFX và WebView.
- Theo dõi Database operations.
- Phân tích hiệu năng bằng Java Flight Recorder.
- Xuất báo cáo phục vụ Developer và AI Agent.

## 1.1. Architectural Principle

**NON-INVASIVE – ZERO BUSINESS LOGIC MODIFICATION**

Diagnostics là một hệ thống độc lập, không tham gia xử lý nghiệp vụ.

Các yêu cầu bắt buộc:

1. Không thay đổi business logic hiện tại.
2. Không thay đổi SQL execution logic.
3. Không thay đổi Database connection logic.
4. Không thay đổi Monaco Editor logic.
5. Không thay đổi Result Grid logic.
6. Không thay đổi các luồng xử lý dữ liệu.
7. Không thay đổi giao diện MainWindow.
8. Không thay đổi cấu trúc dữ liệu nghiệp vụ.
9. Không yêu cầu các module nghiệp vụ phụ thuộc vào Diagnostics.
10. Không để Diagnostics trở thành điều kiện bắt buộc để ứng dụng hoạt động.

Nếu một tính năng cần instrumentation tại module nghiệp vụ, Agent phải dừng và đề xuất riêng, không tự ý chỉnh sửa.

---

# 2. SYSTEM OPERATING MODES

Hệ thống có hai chế độ hoạt động.

## 2.1. Background Monitoring

Luôn hoạt động khi LLTool khởi chạy.

Chỉ thu thập các thông tin nhẹ:

- Application uptime.
- JVM memory.
- Heap usage.
- Non-heap usage.
- Thread count.
- CPU usage.
- Application startup duration.
- Uncaught exceptions có thể quan sát an toàn.

Không thực hiện:

- CPU profiling chuyên sâu.
- JFR recording liên tục.
- Stack trace sampling liên tục.
- Database instrumentation.
- WebView JavaScript instrumentation.

## 2.2. On-demand Debug Mode

Người dùng chủ động bật Debug Mode từ Diagnostics Center.

Có ba mức:

| Level | Name | Scope |
| --- | --- | --- |
| 1 | Basic | Basic application diagnostics |
| 2 | Balanced | Task, JavaFX, WebView, Database diagnostics |
| 3 | Advanced | JFR, profiling, deadlock analysis |

Debug Mode phải có khả năng bật/tắt trong runtime.

Một số cấu hình JVM hoặc profiling cần được khởi tạo từ đầu JVM. Agent phải ghi rõ những giới hạn này và không được tuyên bố hỗ trợ runtime toggle nếu công nghệ không cho phép.

---

# 3. SYSTEM ARCHITECTURE

Tạo module độc lập:

```text
diagnostics/
│
├── core/
│   ├── DiagnosticsManager.java
│   ├── DiagnosticsConfig.java
│   ├── DiagnosticsEvent.java
│   ├── DiagnosticsSession.java
│   └── DiagnosticsLevel.java
│
├── monitoring/
│   ├── SystemMonitor.java
│   ├── JvmMonitor.java
│   ├── MemoryMonitor.java
│   ├── CpuMonitor.java
│   └── ThreadMonitor.java
│
├── startup/
│   ├── StartupProfiler.java
│   ├── StartupTask.java
│   └── StartupReport.java
│
├── tasks/
│   ├── TaskTracker.java
│   └── TaskRecord.java
│
├── errors/
│   ├── ExceptionCollector.java
│   └── ErrorRecord.java
│
├── integrations/
│   ├── JavaFxDiagnostics.java
│   ├── WebViewDiagnostics.java
│   └── DatabaseDiagnostics.java
│
├── advanced/
│   ├── FlightRecorderManager.java
│   ├── DeadlockDetector.java
│   └── CpuProfiler.java
│
├── storage/
│   ├── DiagnosticsStorage.java
│   ├── DiagnosticsLogger.java
│   └── DiagnosticsExporter.java
│
└── ui/
    ├── DiagnosticsWindow.java
    ├── OverviewView.java
    ├── StartupView.java
    ├── PerformanceView.java
    ├── TasksView.java
    ├── ErrorsView.java
    ├── WebViewDiagnosticsView.java
    ├── DatabaseDiagnosticsView.java
    └── LogsView.java
```

Đây là cấu trúc logic đề xuất. Agent phải kiểm tra package convention hiện tại trước khi triển khai.

Không di chuyển hoặc đổi tên module hiện có.

---

# 4. IMPLEMENTATION PHASES

## PHASE 01 – DIAGNOSTICS CORE

Objective:

Xây dựng nền tảng Diagnostics độc lập.

Tasks:

- DiagnosticsManager.
- DiagnosticsConfig.
- DiagnosticsSession.
- DiagnosticsEvent.
- DiagnosticsLevel.
- Event collection.
- Runtime configuration.
- Diagnostics lifecycle.

Requirements:

- Không phụ thuộc business modules.
- Có thể hoạt động khi không có Diagnostics UI.
- Có cơ chế graceful shutdown.
- Xử lý lỗi nội bộ độc lập.
- Không làm gián đoạn ứng dụng khi Diagnostics gặp lỗi.

Deliverables:

- Diagnostics Core.
- API documentation.
- Architecture documentation.

Acceptance Criteria:

- Khởi tạo và đóng Diagnostics thành công.
- Không ảnh hưởng MainWindow.
- Không phát sinh lỗi khi Diagnostics bị tắt.
- Không tạo dependency ngược vào business modules.

## PHASE 02 – BACKGROUND MONITORING

Objective:

Theo dõi trạng thái JVM và ứng dụng ở mức nhẹ.

Tasks:

- CPU monitoring.
- Heap monitoring.
- Non-heap monitoring.
- Thread count.
- JVM uptime.
- Application uptime.
- GC statistics.

Technology:

- ManagementFactory.
- MemoryMXBean.
- ThreadMXBean.
- GarbageCollectorMXBean.
- OperatingSystemMXBean.

Requirements:

- Sampling interval cấu hình được.
- Không chạy trên JavaFX Application Thread.
- Giới hạn bộ nhớ lưu metrics.
- Không ghi file liên tục nếu không cần thiết.

Acceptance Criteria:

- Thu thập được metrics thực tế.
- Phân biệt process CPU và system CPU.
- Không làm tăng đáng kể CPU/RAM.
- Có thể dừng monitoring khi ứng dụng shutdown.

## PHASE 03 – STARTUP PROFILER

Objective:

Đo thời gian khởi động ứng dụng.

Tasks:

- JVM startup information.
- Application startup timeline.
- Startup task duration.
- Task status.
- Startup report.
- Startup bottleneck identification.

Mỗi task có:

```text
Task Name
Start Timestamp
End Timestamp
Duration
Thread Name
Status
Error
```

Requirements:

- Chỉ instrument các điểm khởi tạo được phép.
- Không thay đổi logic khởi tạo hiện tại.
- Không trì hoãn startup để chờ profiler.
- Không bắt buộc mọi startup task phải được đo ngay trong phiên bản đầu.

Acceptance Criteria:

- Có báo cáo startup.
- Xác định được các giai đoạn chậm.
- Không làm thay đổi thứ tự khởi tạo ứng dụng.

## PHASE 04 – DEBUG LEVEL MANAGER

Objective:

Quản lý ba mức Debug.

Level 1 – Basic:

- Startup monitoring.
- CPU.
- Memory.
- Threads.
- Application logs.
- Runtime errors.

Level 2 – Balanced:

- Toàn bộ Level 1.
- Task tracking.
- JavaFX diagnostics.
- WebView diagnostics.
- Database diagnostics.

Level 3 – Advanced:

- Toàn bộ Level 2.
- Java Flight Recorder.
- CPU profiling.
- Deadlock analysis.
- Advanced JVM events.

Requirements:

- Người dùng chủ động bật Debug Mode.
- Chuyển đổi level khi runtime nếu khả thi.
- Tự động giải phóng tài nguyên khi tắt.
- Không yêu cầu restart đối với các tính năng hỗ trợ runtime toggle.
- Không bật Advanced profiling mặc định.

## PHASE 05 – TASK & ERROR TRACKING

Objective:

Theo dõi các tác vụ và lỗi runtime có thể quan sát.

Tasks:

- Task lifecycle tracking.
- Task duration.
- Task status.
- Exception collection.
- Stack trace.
- Error categorization.
- Error timestamp.

Task status:

- QUEUED
- RUNNING
- COMPLETED
- FAILED
- CANCELLED

Important:

Chỉ theo dõi được đầy đủ các tác vụ được instrument hoặc đăng ký qua Diagnostics API.

Không tự ý sửa các tác vụ nghiệp vụ để thêm tracking.

Nếu cần tích hợp sâu hơn, phải đề xuất thay đổi riêng.

## PHASE 06 – JAVAFX & WEBVIEW DIAGNOSTICS

Objective:

Chẩn đoán các vấn đề giao diện và WebView.

JavaFX:

- JavaFX Application Thread monitoring.
- UI responsiveness.
- Long-running UI operations.
- JavaFX exceptions có thể quan sát.

WebView:

- WebView initialization duration.
- Page loading duration.
- Load failures.
- JavaScript errors khi có thể tích hợp qua API hiện có.

Monaco:

- Editor initialization duration.
- Editor loading failures nếu có thể quan sát mà không sửa logic editor.

Requirements:

- Không thay đổi UI layout.
- Không thay đổi Monaco implementation.
- Không can thiệp vào editor lifecycle.
- Không block JavaFX Application Thread.

## PHASE 07 – DATABASE DIAGNOSTICS

Objective:

Theo dõi các hoạt động Database ở mức chẩn đoán.

Thông tin:

- Connection initialization duration.
- Connection timeout.
- Connection errors.
- Metadata loading duration nếu đã có điểm quan sát.
- Database operation duration nếu có instrumentation được phê duyệt.

Security:

- Không thu thập password.
- Không thu thập token.
- Không lưu connection string đầy đủ.
- Không lưu SQL query đầy đủ mặc định.
- Không thay đổi JDBC execution behavior.

Không được sửa Database service chỉ để tích hợp Diagnostics trong phase này.

Nếu cần tích hợp sâu, phải tạo đề xuất riêng và chờ phê duyệt.

## PHASE 08 – DIAGNOSTICS CENTER UI

Objective:

Xây dựng cửa sổ Diagnostics riêng.

Không tích hợp vào MainWindow.

Layout:

```text
Diagnostics Center
│
├── Header
│   ├── Application Status
│   ├── Debug Mode Toggle
│   ├── Debug Level
│   └── Export Report
│
├── Sidebar
│   ├── Overview
│   ├── Startup Analysis
│   ├── Performance
│   ├── Running Tasks
│   ├── Error Monitor
│   ├── WebView
│   ├── Database
│   └── Logs
│
└── Content
```

Requirements:

- Tuân thủ theme hiện tại.
- Cửa sổ độc lập.
- Không thay đổi MainWindow.
- Không ảnh hưởng menu và toolbar hiện tại.
- Có trạng thái dữ liệu khi Debug Mode OFF.
- Có thông báo khi một chức năng yêu cầu level cao hơn.

## PHASE 09 – ADVANCED PROFILING

Objective:

Triển khai công cụ phân tích chuyên sâu.

Tasks:

- Java Flight Recorder integration.
- Recording lifecycle.
- CPU profiling.
- Thread analysis.
- Deadlock detection.
- Advanced JVM events.

Requirements:

- Chỉ hoạt động khi người dùng bật Advanced.
- Có giới hạn recording duration và dung lượng.
- Có cơ chế stop recording.
- Không ghi dữ liệu nhạy cảm mặc định.
- Có thể xuất file JFR.

## PHASE 10 – REPORT & EXPORT

Objective:

Tạo báo cáo để Developer và AI Agent phân tích.

Export formats:

- JSON.
- TXT.
- JFR.

Report structure:

```text
diagnostics/
│
├── sessions/
│
├── reports/
│
└── exports/
```

Report bao gồm:

- Application information.
- Java version.
- OS information.
- Session ID.
- Startup report.
- Performance metrics.
- Runtime errors.
- Task records.
- Debug level.
- Timestamp.

Có khả năng tạo một Diagnostics Bundle cho một session.

---

# 5. STRICT DEVELOPMENT RULES

## 5.1. Business Logic Protection

Đây là quy tắc ưu tiên cao nhất.

Agent không được:

- Refactor business logic.
- Đổi tên class nghiệp vụ.
- Đổi tên method nghiệp vụ.
- Thay đổi SQL execution flow.
- Thay đổi JDBC connection flow.
- Thay đổi Monaco Editor.
- Thay đổi Result Grid.
- Thay đổi dữ liệu cấu hình hiện tại.
- Thay đổi thứ tự xử lý nghiệp vụ.

Nếu không thể triển khai một chức năng mà không thay đổi các thành phần trên, phải báo cáo trước.

## 5.2. Dependency Isolation

Diagnostics không được trở thành dependency bắt buộc của business modules.

Business modules không được phụ thuộc vào Diagnostics để thực thi chức năng.

Diagnostics phải chịu được trường hợp:

- Initialization failed.
- Storage unavailable.
- Export failed.
- UI initialization failed.
- Monitoring service stopped.

## 5.3. Performance Protection

- Không block UI Thread.
- Không tạo thread không giới hạn.
- Không polling quá thường xuyên.
- Không lưu metrics vô hạn.
- Có giới hạn log rotation.
- Có cơ chế cleanup.
- Không tự động bật profiling chuyên sâu.

## 5.4. Security Protection

- Không log credentials.
- Không log sensitive connection information.
- Không thu thập SQL đầy đủ mặc định.
- Không tự động gửi dữ liệu ra ngoài.
- Không tự động upload diagnostics.

---

# 6. GLOBAL ACCEPTANCE CRITERIA

Chỉ hoàn thành dự án khi đáp ứng:

1. Diagnostics hoạt động độc lập.
2. MainWindow không bị thay đổi.
3. Các chức năng SQL IDE vẫn hoạt động như trước.
4. Không có thay đổi business logic.
5. Background Monitoring hoạt động ổn định.
6. Debug Mode có ba cấp độ.
7. Diagnostics Center hoạt động độc lập.
8. Có Startup Profiler.
9. Có Runtime Error Monitor.
10. Có Performance Monitor.
11. Có Report Export.
12. Có cơ chế tắt và giải phóng tài nguyên.

---

# 7. AGENT EXECUTION INSTRUCTIONS

Agent phải thực hiện theo từng Phase.

Trước mỗi Phase:

1. Đọc cấu trúc dự án.
2. Xác định package convention.
3. Kiểm tra các dependency hiện tại.
4. Xác định phạm vi file được phép tạo hoặc chỉnh sửa.
5. Báo cáo nguy cơ ảnh hưởng business logic.

Sau mỗi Phase:

1. Báo cáo file mới.
2. Báo cáo file đã chỉnh sửa.
3. Giải thích các dependency mới.
4. Chạy build.
5. Kiểm tra regression.
6. Xác nhận không có thay đổi business logic.

Không tự ý triển khai Phase tiếp theo khi Phase hiện tại chưa được nghiệm thu.

**FINAL RULE:**

If a diagnostic feature requires modification of existing business logic, stop and report the limitation. Do not implement the modification without explicit approval.

---

# 8. FINAL ARCHITECTURE DECISION

**Always-on Lightweight Monitoring**

-

**On-demand Debug Mode**

-

**Three Debug Levels**

-

**Standalone Diagnostics Window**

-

**Independent Diagnostics Module**

-

**Zero Business Logic Modification**

Đây là kiến trúc chính thức để triển khai Application Diagnostics & Debug Center cho LLTool.
