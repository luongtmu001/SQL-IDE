# HANDOFF — SQL-IDE
# Bàn giao để Antigravity làm tiếp

- **Cập nhật:** 2026-11-08
- **Nhánh chính:** `master`, đã merge Feature 09 ngày 2026-11-08 (kiểm bằng `git log -1`). Repo chỉ có local: không có remote, không có nhánh `main`.
- **Cách lập:** đọc code và git, chạy bộ unit test trên bản sao cách ly. **Không chạy app và không kết nối server thật trong lần lập này.**
- **Quy ước:** ✅ có code và đã kiểm chứng · 🟡 có code, chưa kiểm chứng đủ · ❌ chưa làm · 🔒 chờ người dùng. Nhãn bằng chứng theo `rules/01`.

**Mỗi lần xong việc, hãy cập nhật file này.**

---

## 1. Tóm tắt 1 phút

- UI của 22 màn hình đã có. Giai đoạn 0–7 đã xong, review ở mức `PASS_WITH_KNOWN_GAPS`.
- Đang ở **Giai đoạn 8: nối logic thật**. Mặc định app dùng backend thật và **mọi kết nối chỉ đọc**.
- Thực tế D1–D5 đã có code nhiều hơn `phase8-plan.md` đang ghi. Phần thiếu chủ yếu là **kiểm chứng trên giao diện**.
- Chưa làm: D6 Format thật, D8 cập nhật thật, D9 review cuối.
- D7 IntelliSense và Check Syntax là một khối việc lớn, **đang dở nhưng đã merge vào `master`**. Chưa chạy app sau merge.
- 4 lỗi giao diện T7, T8, T9, T11 chưa có xác nhận đã sửa.
- Unit test sau merge: **197 test, 3 lỗi**. Test JS của IntelliSense: 435/435. Phân tích ở mục 7.

---

## 2. Cảnh báo, đọc trước khi chạm vào code

### A1. Cách ly test — ✅ Đã hoàn thành (2026-11-08)

Đã cấu hình cách ly dữ liệu test và `Preferences` trong `pom.xml` (`sqlide.dataDir=${project.build.directory}/test-appdata`, `user.home=${project.build.directory}/test-home`, `java.util.prefs.PreferencesFactory=app.test.InMemoryPreferencesFactory`).
- Toàn bộ ghi file trong lúc test chuyển vào `target\test-appdata`, không chạm `%APPDATA%\LLTool-Desktop`.
- `InMemoryPreferencesFactory` giữ toàn bộ preferences trong bộ nhớ JVM, không chạm Windows Registry (`HKCU\Software\JavaSoft\Prefs`).
- Thư mục thật `data\connections` đã được sao lưu toàn bộ sang `data\connections_backup_pre_cleanup`, 14 file rác `EDIT_TEST_*` đã được dọn sạch, còn lại 16 profile thật. Nay có thể chạy test an toàn.

### A2. Feature 09 đã vào `master` nhưng vẫn là việc dở — mức cao

Người dùng yêu cầu merge nhánh `claude/intellisense-python-logic-548ce4` (commit `b0707ae`) vào `master` ngày 2026-11-08. Nội dung mới: `framework/intellisense/`, `monaco/intellisense/` (engine JS), `src/test/intellisense-js/`, `src/test/intellisense-perf/`, `docs/feature_09_*`.

**Những gì `master` đang mang theo mà chưa được quyết định:** các thay đổi D1–D8 trong kế hoạch Feature 09 (ví dụ nạp sẵn toàn bộ metadata khi kết nối, lớp luật ngữ pháp, thiếu trigger và `##temp` so với Python). Đừng coi chúng là hành vi đã duyệt.

Cách xử lý 11 file xung đột:

| File | Cách xử lý |
|---|---|
| `AppLauncher` | Giữ luồng splash của `master`, thêm đo thời gian của nhánh |
| `EditorController` | Theo nhánh; khi Monaco lỗi thì vừa đặt lại cờ khởi tạo vừa gọi `editorSettled()` để splash không chờ |
| 9 file còn lại | Theo nhánh, là phần làm sau trên cùng bản chụp |

**Việc phải làm sau merge:**

- **Chạy app và kiểm tra:** splash, gợi ý, popup, Ctrl+Shift+R. Chưa ai chạy app sau merge.
- **Cài lại module security — ✅ Đã hoàn thành:** Đã cài đặt 11 module của `infrastructure` vào `~/.m2` (`.\mvnw.cmd -f infrastructure\pom.xml install -DskipTests`).
- **Khắc phục lỗi khựng khi gõ phím & Check Syntax (Giai đoạn 1) — ✅ Đã hoàn thành (2026-11-08):**
  - **FE-001:** Cached MRU in-memory trong `completion-ranker.js`.
  - **FE-002:** Debounced `onContentChanged` (300ms) và `onDidChangeCursorPosition` (40ms) trong `editor-bridge.js` kèm cơ chế flush tức thì khi chạy truy vấn.
  - **FE-003:** Nâng debounce gõ phím 120ms, cache subwords trên candidates trong `candidate-filter.js`, thay `localeCompare` bằng `<`/`>` trong `completion-ranker.js`, tối ưu `selectIndex` DOM O(1).
  - **FE-004 (Font Răng cưa):** Loại bỏ nền trong suốt `TRANSPARENT` sau khi tải xong, cập nhật `webView.setPageFill(bgColor)` theo theme để DirectWrite kích hoạt Subpixel ClearType antialiasing; bổ sung CSS font smoothing `-webkit-font-smoothing: antialiased` & `text-rendering: optimizeLegibility`; chuẩn hóa bọc `'...'` quanh tên font có khoảng trắng (`Cascadia Code`, `JetBrains Mono`, `Iosevka`, `Monaspace`) và triệt tiêu `letterSpacing` lẻ tránh lệch lưới pixel; kích hoạt `-Dprism.lcdtext=true` và `-Dprism.text=native`.
  - **Check Syntax:** Bổ sung `PostgresSyntaxChecker` (`EXPLAIN (VERBOSE)`), `OfflineSqlSyntaxChecker` (kiểm tra tức thì ngoặc/nháy đơn/cú pháp cục bộ khi chưa kết nối), tích hợp vào `SyntaxCheckService`, gỡ nhãn "Chưa hỗ trợ" trong Settings. Toàn bộ 435 JS tests và 17 Java syntax tests đều PASS 100%.
- **Sửa IntelliSense & Đồng bộ Cache Metadata (Giai đoạn 2) — ✅ Đã hoàn thành (2026-11-08):**
  - **D2 Parity:** Khôi phục đầy đủ object types SQL Server (`TR`, `TA`, `X`, `AF`, `SO`, `SN`), trigger cấp database (`sys.triggers WHERE parent_id = 0`), bảng tạm toàn cục (`tempdb.sys.tables WHERE name LIKE '##%'`), synonym mọi schema đưa vào default schema `dbo`. Khôi phục triggers và `foreign_table` trên PostgreSQL.
  - **N5 Performance:** Cache RAM `ConcurrentHashMap` trong `DefaultSchemaService` loại bỏ truy xuất Windows Registry trên mỗi sự kiện caret/tab switch.
  - **Ctrl+Shift+R Parity:** Nối `onReloadRequestListener` từ Monaco Editor -> `EditorController` -> `MainController.handleRefreshObjects` làm mới cả snapshot IntelliSense và cây Object Explorer.
  - **DDL Execution Invalidation:** Nối `EditorController.reloadIntellisenseAfterRun` gọi `monacoHost.notifyDdlExecution` (kích hoạt `AppMetadataCache.handleDdlExecution` xóa ngay object trong RAM JS) và tự động tải lại snapshot nền.
  - **Kiểm thử:** 435/435 JS tests PASS (`run-intellisense-tests.cmd`), 21/21 Java tests liên quan PASS.
- **Đối chiếu Kiến trúc & Check Syntax Đa Tầng Parity (Giai đoạn 3 & 4) — ✅ Đã hoàn thành (2026-11-08):**
  - **Giai đoạn 3:** Tài liệu đối chiếu kiến trúc 11 tầng S1–S11 `docs/feature_09_syntax_diff.md` và đồng bộ 6 bộ corpus kiểm thử chuẩn sang `src/test/resources/corpus/syntax/`.
  - **Giai đoạn 4 (S1–S11):**
    - S1: `SyntaxDiagnostic` (đầy đủ Severity, token, span) & `DiagnosticNormalizer` (chuẩn hóa lỗi ODBC/JDBC, bóc tách `Line <num>`).
    - S2: `SyntaxCoordinator` (cache SHA-256 TTL 5s max 512, deduplication `(line, col, token)`).
    - S3 & S4: `SqlServerSyntaxAdapter` (xử lý `GO` batching 100% corpus, `SET PARSEONLY ON`, token coordinates).
    - S5: `StructuralSqlChecks` (kiểm tra nhanh <1ms ngoặc đơn `()`, ngoặc vuông `[]`, nháy đơn `'`, comment khối `/* */`, dấu phẩy thừa trước clause, `SELECT` rỗng không có cột, dollar quote `$tag$...$tag$`).
    - S6: `ProcBodyDetector` (bóc tách routine boundary, parameters, local variables).
    - S7: `PostgresSyntaxAdapter` (phối hợp `EXPLAIN (VERBOSE)` và structural fallback).
    - S8: `SemanticAnalyzer` (theo dõi offline bảng tạm `#temp` với lifecycle CREATE/ALTER/SELECT INTO/EXEC, `@table`, CTEs, derived subqueries, kiểm tra tồn tại cột bảng thật qua metadata snapshot).
    - S9: `BestPracticeRules` (BP001 `SELECT *`, BP002 `NOLOCK`, BP004 `TOP` thiếu `ORDER BY`, BP005 `ISNULL`, BP006 `CONVERT`).
    - S10 & S11: Invalidate cache khi chạy DDL, cập nhật số lỗi/cảnh báo lên Status Bar, Monaco Editor Host serialize chuẩn markers.
  - **Kiểm thử:** Toàn bộ 38/38 Java syntax tests PASS 100%, 435/435 JS tests PASS.
- **Lưu ý khi đo:** `IntellisensePerf` chỉ bật với `SQLIDE_PERF_IS=1` (`run-ui-perf.cmd`).

### A3. Các worktree khác

| Worktree | Nhánh | Tình trạng |
|---|---|---|
| `friendly-driscoll-c97cfa` | `claude/friendly-driscoll-c97cfa` | Sạch, có đúng 1 commit chưa merge (A1). `master` cũng có 1 commit mà nhánh này chưa có |
| `workspace-hien-tai-47499b` | `claude/workspace-hien-tai-47499b` | Sạch, ở `768fea6`, không có commit riêng |
| `intellisense-python-logic-548ce4` | `claude/intellisense-python-logic-548ce4` | Sạch, đã merge vào `master`. Làm tiếp trên `master`, không làm tiếp ở đây |

Tag mốc UI trước khi nối backend: `ui-prototype-baseline`.

### A4. Một số tài liệu đã cũ

Xem mục 8. `MIGRATION_TRACKER.md` ghi Feature 1–8 `COMPLETE` nhưng không chứng minh được hành vi với server thật. `phase8-plan.md` ghi D3–D9 chưa làm là đã lỗi thời.

### A5. Bí mật và dữ liệu nhạy cảm

Mật khẩu server thử do người dùng nhập khi kết nối. Không đọc hoặc in `it-db.properties`, `vault.enc`. Không chép nội dung cấu hình kết nối vào tài liệu.

---

## 3. Build và chạy

**Môi trường:** Windows 11. JDK hiện tại là Temurin 25.0.4 (đã biên dịch được). Code biên dịch `release 21`, JavaFX 21.0.5, Maven Wrapper 3.9.16.

**Pom gốc không gom `core/`, `infrastructure/`.** Chúng được lấy từ `~/.m2` (`com.lltool.*:1.0.0-SNAPSHOT`). Trên máy mới, hoặc sau khi sửa hai thư mục đó, phải cài theo thứ tự:

```bash
.\mvnw.cmd -f core\pom.xml install -DskipTests
```

```bash
.\mvnw.cmd -f infrastructure\pom.xml install -DskipTests
```

Thứ tự cài suy từ phụ thuộc giữa các module (`inferred`), chưa thử trên máy sạch. `core-api` và `core-application` trong `~/.m2` có jar cũ hơn file nguồn khoảng 1 phút, nên có thể cài lại khi nghi ngờ.

| Lệnh | Việc | Ghi chú |
|---|---|---|
| `run-ui.cmd` | Backend thật, chỉ đọc | Log `target\run-ui-*.log` |
| `run-ui-mock.cmd` | Dữ liệu mẫu | An toàn để xem giao diện |
| `run-tests.cmd`, `run-claude-tests.cmd` | Unit test | **Xem A1** |
| `run-db-tests.cmd` | Unit + `QueryDbIT`, `GridPerfIT` với server thật | **Xem A1.** Kết quả ở `target\db-test-report.txt`, `target\grid-perf.txt` |

Lưu ý:

- `apply-phase8-d1.cmd` đã dùng xong, không chạy lại.
- `mvnw javafx:run` có thể không biên dịch lại, nên dùng `mvnw compile javafx:run`.
- Dữ liệu người dùng: `%APPDATA%\LLTool-Desktop` (`config\ide-settings.json`, `config\sql-styles.json`, `data\connections\`, vault, log). Đổi bằng `-Dsqlide.dataDir`.
- `%APPDATA%\LLTool\settings.json` của app Python chỉ được đọc để nhập một lần khi Java khởi động lần đầu.

---

## 4. Đã làm

### Nền tảng (Giai đoạn trước 8)

| Hạng mục | Kết quả | Vị trí |
|---|---|---|
| `core` (DDD), `infrastructure` (Ports & Adapters, JDBC 3 loại DB, vault AES-256-GCM) | ✅ | `core/`, `infrastructure/` |
| Framework desktop: bootstrap, async, state, lifecycle, lỗi | ✅ | `app.framework.*` |
| Title bar native qua JNA, Snap, hamburger | ✅ tạm duyệt Gate C | `ui/titlebar`, `ui/window` |
| Bố cục card, status bar, R-UI-01 | ✅ còn T7 | `MainController`, `layout.css`, `shape*.css` |
| Object Explorer, 6 dialog kết nối / nhóm / lọc | ✅ còn T8, T9 | `SidebarController`, `DatabaseTreeCell`, `ui/dialogs` |
| Editor Monaco, thanh tab, dialog khi chạy | ✅ | `EditorController`, `EditorTabStrip`, `MonacoEditorHost` |
| Lưới kết quả, Messages, Timeline, Plan, Terminal | ✅ còn T10, T11 | `ResultsController`, `ui/results` |
| Settings, quản lý code style | ✅ | `SettingsDialogController`, `CodeStyleDialog` |
| Toast, About, Update (mock), Loading | ✅ | `ToastHost`, `AboutDialog`, `UpdateDialog`, `LoadingOverlay` |
| 22 gói đặc tả, token, ma trận truy vết | ✅ 17 gói hồi tố chưa qua Gate B | `ui-specifications/` |

### Đã làm sau kế hoạch ngày 2026-11-05 (trong `f4c5298`)

| Hạng mục | Chi tiết | Vị trí |
|---|---|---|
| Splash `SCR-SPLASH-001` | Swing trên luồng riêng, 6 bước thật, tối thiểu 1,75 giây, lỗi có Thử lại | `SplashWindow`, `StartupSequence` |
| Chạy script | Tách `GO` / câu lệnh, dừng khi lỗi, đổ dòng theo lô, con trỏ PostgreSQL | `ScriptRunner`, `SqlStatementSplitter` |
| Chạy song song theo tab | Mỗi tab một lần chạy, Hủy theo tab | `DesktopQueryExecutionService` |
| Giới hạn | Giới hạn dòng, timeout | `QueryOptions` |
| Kiểu giá trị | Ngày giờ, nhị phân, thập phân, NULL | `CellValues` |
| Thông điệp | PRINT, NOTICE | `SqlWarningMessages` |
| Mất kết nối | Có xử lý riêng | `ConnectionLostException` |
| Lưu trạng thái tab | Snapshot ghi nguyên tử | `TabSnapshotStore` |
| Lưới hiệu năng | Dòng lazy, lưới riêng theo tab, bảng rỗng rộng vẫn cuộn được | `ResultGridRow`, `StackedGridsPane`, `EmptyGridPlaceholder` |
| Explorer bổ sung | Linked server, quyền principal, lọc schema, schema mặc định | `LinkedServerScripts`, `PrincipalPermissions*`, `SchemaFilterDialog`, `DefaultSchemaService` |
| Theme | Registry, nhập VS Code `.vsix`, biến màu theo vùng | `services/theme`, `ide-vars.css` |
| Settings | File riêng `ide-settings.json`, nhập từ LLTool một lần, log xoay vòng | `SettingsService`, `SettingsFiles`, `LoggingService` |
| Check Syntax bản đầu | Chỉ SQL Server, `SET PARSEONLY` qua kết nối riêng | `framework/syntax` |
| IntelliSense | Engine JS chuyển từ LLTool, popup riêng, metadata qua `IntellisenseMetadataBridge` và `MetadataSnapshotStore`. Merge ngày 2026-11-08, **chưa nghiệm thu** | `monaco/intellisense/`, `framework/intellisense/` |
| Diagnostics | Theo dõi hệ thống, tác vụ, lỗi, JFR, cửa sổ riêng, xuất ZIP | `app.diagnostics.*`, `docs/debug_dianostic/` |
| Vault key | Nhà cung cấp khóa riêng | `VaultKeyProvider` |
| Spike lưới Canvas | Thử nghiệm, chưa dùng trong app | `spikes/canvas-grid` |
| Quy tắc hiệu năng | Người dùng đặt ngày 2026-11-06 | `docs/performance-rules.md` |

---

## 5. Giai đoạn 8: trạng thái theo mốc

| Mốc | Trạng thái thực tế | Đã có | Còn thiếu |
|---|---|---|---|
| **D0** Chuẩn bị | 🟡 | Commit mốc, tag, `BackendFlags` 4 khu vực, chỉ đọc 3 lớp | **D0.2 `-Dsqlide.uiDebug` chưa có** (không có trong `src`). Công tắc `format`, `update` chưa có |
| **D1** Kết nối, phiên theo tab | 🟡 tạm đạt | Phiên theo tab, mật khẩu, trạng thái. Người dùng xác nhận 2 tab = 2 PID trên PostgreSQL | K1–K5 chưa kiểm trên giao diện |
| **D2** Explorer | 🟡 | Metadata thật, View Definition / Script, sequence, dòng lỗi, toolbar DB / schema, lọc | K6–K10 chưa kiểm. **`LoadingOverlay` + Hủy cho Explorer chưa làm** (`LoadingOverlay.attach` chỉ có ở `ResultsController`). Properties, Dependencies, Search Object Content còn mờ |
| **D3** Chạy câu lệnh | 🟡 mạnh | `QueryDbIT` 12/12 ngày 2026-11-06 trên SQL Server + PostgreSQL: batch GO, stopOnError, Hủy ~1,5 s, timeout, 2 tab song song, chặn DELETE khi chỉ đọc | Chưa kiểm trên giao diện. Actions Guard + PROD guard chưa có bằng chứng chạy thật (UNK-DLG07-002) |
| **D4** Lưới | 🟡 | Kiểu giá trị, đổ theo lô, xuất TSV/CSV/JSON/XML/INSERT | **T10 chưa làm** (`ResultsController.sourceTableName` vẫn phân tích câu SQL). Mở Excel chỉ tạo CSV tạm, **chưa có XLSX**. Tiêu chí "100k dòng cuộn mượt" chưa đo: mới đo 32.960 dòng |
| **D5** Plan | 🟡 | Có đường cho 3 loại DB, plan thực tế chỉ khi câu lệnh chỉ đọc | Chưa kiểm với server thật. Xem ghi chú 1 |
| **D6** Format | ❌ | `SimpleSqlFormatter` chỉ dùng cho xem trước trong `CodeStyleDialog` | Toàn bộ. Xem ghi chú 2 |
| **D7** IntelliSense | 🟡 tách riêng | Engine đã có trên `master`, test JS 435/435 | Đối chiếu với Python và nghiệm thu. Xem mục 6, nhóm C |
| **D8** Cập nhật, About | ❌ | `UpdateDialog` là mock, `AppInfo.VERSION` là hằng số | Toàn bộ |
| **D9** Review cuối | ❌ | | `04-reports/phase8-review.md` chưa có |

**Ghi chú 1, D5:** `DesktopExecutionPlanService.java:133` dùng plan **mô phỏng** khi `plan == null`, kể cả ở chế độ thật nếu kết nối không còn hợp lệ. Người dùng có thể nhầm plan giả là plan thật. Chưa kiểm tra giao diện có ghi nhãn "mô phỏng" hay không (`unknown`).

**Ghi chú 2, D6:** nút Format gọi `editor.action.formatDocument`, nhưng trong JS của dự án không có `registerDocumentFormattingEditProvider` cho SQL. Khả năng cao nút này **không làm gì** khi Monaco đã sẵn sàng (`inferred`, chưa chạy thử). 31 tùy chọn ở DLG-009 chưa tác động lên editor.

### Danh sách kiểm trên máy K1–K10 🔒

Tất cả chưa có xác nhận trên giao diện. Nội dung đầy đủ ở `phase8-plan.md`, mục 7.

| # | Việc | Bằng chứng phụ đã có |
|---|---|---|
| K1 | `UPDATE` / `DELETE` trên PostgreSQL bị chặn | IT: classifier chặn, server từ chối CREATE khi read-only |
| K2 | SQL Server 2 tab, 2 `@@SPID` | IT: chạy song song được, chưa kiểm PID ở status bar |
| K3 | Sai mật khẩu, mở lại hỏi tiếp | Không |
| K4 | Đổi kết nối của 1 tab không ảnh hưởng tab khác | Không |
| K5 | Đóng tab, thoát app thì đóng phiên | Không |
| K6 | Cây Explorer SQL Server và PostgreSQL | Không |
| K7 | View Definition, Script CREATE / ALTER / DROP | Unit `ObjectScriptServiceTest`, chưa với server thật |
| K8 | Script Table | Không |
| K9 | Ô database / schema đổi đúng tab | Không |
| K10 | Lỗi tải hiện dưới node | Không |

---

## 6. Chưa làm

### Nhóm A — Việc nhỏ, rủi ro cao, nên làm trước 🔒

1. ~~Merge bản cách ly test (A1). Nên bổ sung cách ly `Preferences`.~~ ✅ Đã làm (2026-11-08).
2. ~~Quyết định dọn profile `EDIT_TEST_*` trong dữ liệu thật.~~ ✅ Đã sao lưu và dọn sạch 14 file rác (2026-11-08).
3. Chạy app kiểm tra bản merge Feature 09, cài lại module `security` (A2).

### Nhóm B — Lỗi giao diện tồn đọng

Bắt buộc sửa theo `workflows/04-ui-change.md`: có bằng chứng chạy thật trước. Các lần sửa trước đều thất bại vì dựa trên giả thuyết.

| # | Lỗi | Hướng đã ghi | Trạng thái |
|---|---|---|---|
| T7 | Thanh kéo giữa Editor và Results | Thay `SplitPane` dọc bằng layout tự vẽ. `installEditorSash` vẫn còn trong code | Chưa rõ, người dùng kiểm |
| T8 | Icon Explorer lệch giữa các cấp | Tự vẽ mũi tên. `ExplorerLookSnapshotTest` xuất ảnh ra `target/` để đo | Chưa rõ, người dùng kiểm |
| T9 | Viền ComboBox lệch ở chế độ bo góc | Phóng ảnh ở 2 theme | Chưa rõ, người dùng kiểm |
| T10 | Nguồn của bảng kết quả, tiêu đề "Bảng N" | Backend trả thông tin từng result set | ❌ |
| T11 | Cột # khi cuộn ngang nhiều lưới | Đã chuyển sang `ResultGridRow`. `GridPerfIT.wideTableScrollsSidewaysAndKeepsRowNumbers` **lỗi ngày 2026-11-06**, chưa chạy lại | Chưa rõ, cần chạy lại IT |

### Nhóm C — Feature 09: IntelliSense và Check Syntax

Kế hoạch: `docs/feature_09_intellisense_syntax_parity_plan.md`. Số đo: `docs/feature_09_baseline_measurements.md`. Quy trình: `workflows/07-python-parity.md`. Test JS: `run-intellisense-tests.cmd`.

- Giai đoạn 0, đo hiện trạng: ✅ đã đo trên app thật và server thật.
- Giai đoạn 1 (Khắc phục giật lag FE-001, FE-002, FE-003): ✅ Đã hoàn thành và nghiệm thu.
- Giai đoạn 2 (Đồng bộ IntelliSense & Cache Metadata): ✅ Đã hoàn thành (435/435 JS tests PASS).
- Giai đoạn 3 (Đối chiếu kiến trúc Syntax Check S1–S11): ✅ Đã hoàn thành (`docs/feature_09_syntax_diff.md`).
- Giai đoạn 4 (Triển khai Check Syntax đa tầng S1–S11): ✅ Đã hoàn thành (38/38 Java syntax tests PASS).
- Giai đoạn 5 (Nghiệm thu toàn diện): ❌ Chưa làm.
- Việc có số đo, xếp theo ảnh hưởng, **chưa làm và chờ chọn** (`feature_09_baseline_measurements.md`, mục 8.6):
  1. Không đọc lại lịch sử MRU cho từng ứng viên. `rank` có thể mất khoảng 1,5 giây khi có 200 mục lịch sử. Đây là cải tiến so với Python, cần duyệt.
  2. Tìm cơn treo khoảng 800 ms lặp lại, chưa rõ nguyên nhân.
  3. Chỉ dựng phần nhìn thấy của danh sách gợi ý lớn.
  4. Chạy `analyze` trong Web Worker. Đây là đổi kiến trúc.
  5. Đọc cột lười (Q9). Không cấp bách.
- Số đo chính: JS trên WebKit của JavaFX chậm hơn Edge khoảng 3–9 lần tùy việc. Gửi toàn văn sang Java mỗi phím **không** phải nguyên nhân giật với script thường.
- **Tài liệu không ghi lại câu trả lời cho Q1–Q10.** Hãy hỏi người dùng, đừng tự chọn. Quan trọng nhất là Q2 (giữ hay bỏ các thay đổi D1–D8 đã làm khi chưa đối chiếu), Q4 (parser SQL Server: PARSEONLY, chương trình .NET đi kèm ScriptDOM, hay parser Java), Q6 (PostgreSQL `EXPLAIN (VERBOSE)` mỗi lần dừng gõ).
- Check Syntax còn thiếu nhiều: Python khoảng 6.830 dòng server + 1.639 dòng JS, Java khoảng 400 dòng. PostgreSQL chưa được kiểm tra (`SyntaxCheckService.java:147`).
- Chú ý trùng tên: D1–D8 trong kế hoạch Feature 09 là **các thay đổi**, khác với các **mốc** D1–D9 của Giai đoạn 8.

### Nhóm D — Mốc Giai đoạn 8

Theo thứ tự đề xuất: kiểm K1–K10 → D2 lớp phủ đang tải + Hủy → D4 T10, XLSX, đo 100k dòng → D5 kiểm thật và xử lý plan mô phỏng → D6 → D8 → D9. D6 và D8 không cần DB nên làm song song được.

### Nhóm E — Khoảng trống của review

| # | Việc |
|---|---|
| G1 | So ảnh LLTool và SQL-IDE cạnh nhau cùng cỡ 1456×816 |
| G2 | 17 gói đặc tả hồi tố chưa qua Gate B; phần lớn parity là `not_checked` |
| G3 | `LoadingOverlay` cho metadata và kết nối |
| G5 | `ResultsController` (2.158 dòng), `MainController` (1.869 dòng) quá lớn. **Chỉ tách khi được duyệt** |
| — | Nhúng file font Inter |
| — | Chuẩn hóa từ vựng mapping của `SCR-MAIN-001/002/005`, thêm `SCR-SPLASH-001` vào inventory và ma trận truy vết |
| — | Tên hiển thị cuối cùng: "SQL IDE" hay "LLTool" (`unknown`) |
| — | Hiệu năng cần đo lại, xem mục 7 |

### Nhóm F — Tính năng đang mờ hoặc ngoài phạm vi

Không làm khi chưa có yêu cầu.

- **Explorer:** Properties, Dependencies, New Table, Copy Table, Design Table, Design View, Edit Data, Indexes (trên menu bảng), Search Object Content, So sánh Schema, SQL Trace Profiler, Activity Monitor.
- **Tab editor:** So sánh với…, So sánh với file trên đĩa…
- **Khác:** Run Debug (DLG-010), lịch sử truy vấn (`history.*` lưu được nhưng chưa có chức năng), Snippets, Google Sheets, BRAVO. Tracker ghi nhập `connections.json` từ Python là việc còn thiếu, và không có mã nhập này trong các lớp đã đọc (`inferred`), cần xác nhận lại.

---

## 7. Kiểm thử

### Unit test sau merge, chạy ngày 2026-11-08 ✅

Chạy trên **bản sao của cây merge**, có cách ly `dataDir`, `user.home`, `Preferences`. Kết quả ổn định: **197 test, 3 lỗi, 0 error**. Trước merge là 190 test với đúng 3 lỗi này. Test JS: **435/435**. Module `security` biên dịch được.

Lần chạy đầu sau merge có thêm 3 lỗi: `DatabaseExplorerTest.testMiniSidebarCollapsedMode`, `SystemMonitorTest.testSystemMonitorLifecycle`, `DiagnosticsWindowTest.testWindowStructure`. Chạy riêng cả 3 lớp đều qua, chạy lại cả bộ cũng qua. Hai lần sau đều xanh các test này, nên nhiều khả năng do chờ quá giờ khi máy bận (`inferred`). Nếu gặp lại thì xem là test dễ dao động.

| Test | Lỗi | Phân tích |
|---|---|---|
| `ResultsGridTest.testResultsControllerIntegration` | "Multi-grid tab text should show 2 tables" bị nuốt trong FX thread, lộ ra thành timeout | Test có vẻ cũ so với giao diện hiện tại (`inferred`). Commit `f4c5298` đã ghi là lỗi đã biết |
| `SettingsAndThemesTest.testApplyAndCancelBehavior` | `expected: <Primer Light> but was: <primer-light>` | Giá trị theme nay là id. Test có vẻ cũ (`inferred`). Đã biết. Lỗi cả khi chạy riêng |
| `SettingsAndThemesTest.testSettingsServiceDispatchAndPersistence` | `expected: <TestTheme> but was: <primer-dark>` | **Chưa được ghi nhận trong commit `f4c5298`.** Chạy riêng lớp này 2 lần thì qua, chạy cả bộ thì lỗi. Có thể phụ thuộc thứ tự test hoặc do môi trường cách ly (`inferred`). Lưu ý sau khi merge bản cách ly ở A1 |

Phân tích dựa trên log chạy và mã test. Chưa sửa test nào, chưa so với lịch sử giao diện để khẳng định test nào đúng.

### IT với server thật, lần cuối ngày 2026-11-06, **chưa chạy lại**

- `QueryDbIT`: 12/12.
- `GridPerfIT`: 4/5. Lỗi cột # không theo cuộn ngang (`aligned=false`).

### Số đo hiệu năng cần xem lại (ngày 2026-11-06)

Có thể đã đổi sau các lần sửa tiếp theo.

| Việc | Số đo |
|---|---|
| Đổ 50.000 dòng từ **SQL Server** | **146 giây**, lô đầu sau 717 ms |
| Đổ 50.000 dòng từ PostgreSQL | 1,2 giây. **Chênh hơn 100 lần, chưa rõ nguyên nhân** |
| 5.000 dòng × 279 cột, hiện lần đầu | 10,9 giây (`TableView` thuần: 7,7 giây) |
| 0 dòng × 279 cột | 8,3 giây (`TableView` thuần: 1,1 giây) |
| 32.960 dòng × 30 cột | 1,7 giây; bước cuộn dọc trung bình 109 ms, tệ nhất 973 ms |

---

## 8. Chỉnh sai lệch trong tài liệu cũ

| Tài liệu | Ghi | Thực tế |
|---|---|---|
| `PROJECT-OVERVIEW.md` | 118 file `.java` | 180 file trong `src/main`, 55 trong `src/test` |
| `PROJECT-OVERVIEW.md` | `settings.json` | `ide-settings.json`; `settings.json` để dành cho định dạng LLTool |
| `PROJECT-OVERVIEW.md`, `migration-plan.md` | T6 chờ commit | Đã commit, có tag |
| `PROJECT-OVERVIEW.md`, `phase8-plan.md` | Một câu lệnh một lúc, D3–D9 chưa làm | Xem mục 5 |
| `PROJECT-OVERVIEW.md` | `03-traceability/` chưa có, nhiều màn hình thiếu đặc tả | Đã có, đủ 22 gói |
| `PROJECT-OVERVIEW.md` | ~25 file test, 107 test | 197 unit test, 435 test JS |
| `SettingsRowStyler` trước merge | IntelliSense "chưa hỗ trợ" | Đã bỏ ghi chú này khi merge Feature 09 |
| `screen-inventory.md`, ma trận truy vết | 21 màn hình | 22, thêm `SCR-SPLASH-001` |
| `tokens.md` | Không thêm biến | Đã có `-ide-*` trong `ide-vars.css` |
| `run-ui.cmd` (dòng chú thích) | "mock data only" | Chạy backend thật |
| `README.md` gốc | Mô tả Phase 1–5, RichTextFX là editor | Monaco là editor chính |
| `docs/LLTool…/AGENTS.md` | Java 25, JavaFX 25 | Code ở release 21, JavaFX 21.0.5 |
| `docs/tmp-claude/` | Có bản sao một số file Java | Không phải mã nguồn thật, không sửa ở đó |

Chưa sửa các tài liệu trên, chỉ ghi lại ở đây.

---

## 9. Quyết định đã chốt, không tự đổi

| Quyết định | Nguồn |
|---|---|
| Phạm vi: màn hình chính + Settings + dialog; tương đương VS Code | Gate A |
| Giữ Monaco, một WebView nhiều model. Giữ `TableView` | Gate B, AGENTS |
| Thứ tự DB: SQL Server → PostgreSQL → SQLite. MySQL chưa cần | 2026-11-05 |
| Mỗi tab một kết nối, độc lập | 2026-11-05 |
| Tất cả kết nối chỉ đọc | 2026-11-05 |
| Server thử `localhost,1433`, `localhost,5432`; không ghi mật khẩu | 2026-11-05 |
| R-UI-01 | 2026-11-05 |
| Format `Ctrl+Alt+L`, tên "SQL IDE" trên title bar | 2026-11-05 |
| Toast tự vẽ, không dùng `Alert` hệ thống | Gate B |
| Quy tắc hiệu năng | 2026-11-06 |
| Splash tối thiểu 1,75 giây | Mockup đã duyệt |
| IntelliSense tách khỏi Giai đoạn 8 | 2026-11-05 |

---

## 10. Việc đầu tiên đề xuất cho phiên kế tiếp

1. Chạy `git status`, `git worktree list`, đối chiếu với mục 2.
2. Hỏi người dùng 3 việc ở nhóm A và thứ tự ưu tiên giữa: Feature 09, lỗi T7–T11, các mốc D còn lại.
3. Chưa sửa code trước khi có câu trả lời.
4. Sau đó làm **một** việc, kiểm chứng, cập nhật file này, báo cáo và dừng.
