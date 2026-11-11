# LLTool Java Migration — Kế hoạch thực thi Phase 3 (Advanced Workspaces)

Tài liệu này tổng hợp tình trạng khảo sát, đánh giá hiện trạng từ file [MIGRATION_TRACKER.md](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/LLTool_Java_Migration_3_Files/MIGRATION_TRACKER.md) và thiết lập lộ trình thực thi chuẩn hóa cho Phase 3 tuân thủ nghiêm ngặt các quy tắc thiết kế:
- [04-component-rules.md](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/rules/04-component-rules.md) (Tái sử dụng Component, DialogChrome, Badges, SearchField, EmptyState)
- [02-design-principles.md](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/rules/02-design-principles.md) (Chuẩn Desktop VS Code / DataGrip, hỗ trợ `rounded` và `flat` theo `appearance.panelStyle`, không dùng dialog hệ thống)
- [03-design-tokens.md](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/java-desktop-ui-designer-skill-v1.0/java-desktop-ui-designer-skill/rules/03-design-tokens.md) (Hệ thống biến `-ide-*`, `-color-*`, typography JetBrains Mono / Inter, không hardcode hex)
- [SOURCE_ANALYSIS.md](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/LLTool_Java_Migration_3_Files/SOURCE_ANALYSIS.md) (Đối chiếu source Python thực tế, bảo toàn nghiệp vụ)
- [AGENTS.md](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/LLTool_Java_Migration_3_Files/AGENTS.md) (Kiến trúc phân tầng sạch, Clean Architecture, xử lý bất đồng bộ, Read-Only guardrails)

---

## 1. Tình trạng các tính năng Phase 3 trong MIGRATION_TRACKER.md

Trong [MIGRATION_TRACKER.md](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/docs/LLTool_Java_Migration_3_Files/MIGRATION_TRACKER.md) mục `### Phase 3 — Existing advanced workspaces`:

| STT | Feature / Workspace | Trạng thái trong Tracker | Khảo sát thực tế Python | Hiện trạng Java | Đánh giá & Định hướng |
|:---:|:---|:---:|:---|:---|:---|
| **1** | **Table Designer** | `NOT_ANALYZED` | Đầy đủ (`table_designer_service.py`, `designer_api.py`, `table-designer.js`) | **Đã triển khai 1 phần** (`TableDesign.java`, `TableDesignerTab.java`, `ColumnGrid.java`, `TableDesignerService.java`) | **Ưu tiên 1:** Hoàn thiện các sub-tab (Index, FK, Unique, Check) và `SaveDesignDialog`, **KHÔNG tạo mới từ đầu**. |
| **2** | **Table Data Editor** | `NOT_ANALYZED` | Đầy đủ (`table_data_editor_service.py`, `table_editor_api.py`, `table-data-editor.js`) | Chưa có | **Ưu tiên 2:** Chỉnh sửa dữ liệu trực tiếp trên bảng, phân trang, lọc, lock optimistic, commit/rollback an toàn. |
| **3** | **Object Content Search** | `NOT_ANALYZED` | Đầy đủ (`object_search_service.py`, `object-search/`) | Chưa có | **Ưu tiên 3:** Tìm kiếm từ khóa/regex trong DDL Views, Stored Procs, Functions, Triggers, tích hợp nhảy đến Monaco line. |
| **4** | **Schema Compare** | `NOT_ANALYZED` | Đầy đủ (`schema_compare_service.py`, `schema_compare_api.py`, `schema-compare.js`) | Chưa có | **Ưu tiên 4:** So sánh cấu trúc 2 database/schema, diff chi tiết đối tượng, sinh script DDL đồng bộ. |
| **5** | **Dependency Graph** | `NOT_ANALYZED` | Đầy đủ (`postgres_dependency/`, `bravo_relationship_service.py`) | Chưa có | **Ưu tiên 5:** Trực quan hóa phụ thuộc giữa bảng, view, function, trigger, cột; tính toán các bước thay đổi an toàn (Impact steps). |
| **6** | **SQL Visualizer** | `NOT_ANALYZED` | Đầy đủ (`query_flow_service.py`, `visualization/sql-visualizer.js`) | Chưa có | **Ưu tiên 6:** AST Query Flow diagram trực quan hóa luồng dữ liệu (CTE, Join, Filter, Aggregate, Order, Project) đồng bộ Monaco. |
| 7 | Bravo Layout Compare | `NOT_ANALYZED` | Có service (`bravo_layout_compare_service.py`) | Chưa có | Gộp khảo sát và tái sử dụng hạ tầng của Feature 4 (Schema Compare). |
| 8 | Activity Monitor | `NOT_ANALYZED` | Có (`activity_service.py`, `activity_api.py`, `activity-monitor.js`) | Chưa có | Quản lý tiến trình/session máy chủ CSDL (chuyển tiếp sau 6 tính năng lõi). |
| 9 | User Permissions | `NOT_ANALYZED` | Có (`database_users_api.py`, `user-permissions.js`, Java đã có `PrincipalPermissionsView.java`) | Đã có một phần UI | Quản lý user/role/permission (chuyển tiếp sau 6 tính năng lõi). |
| 10 | Google Sheets integration | `NOT_ANALYZED` | Có (`google_sheets_api.py`, `google_sheets_window.html`) | Chưa có | Tiện ích xuất dữ liệu tích hợp ngoài (triển khai ở đợt hoàn thiện cuối). |

---

## 2. Thứ tự triển khai được phê duyệt

Tuân thủ đúng thứ tự người dùng đề xuất:
1. `01_table_designer`: Hoàn thiện Table Designer (hoàn thành TD4 & TD5, Dialog Lưu DDL, các Sub-tab).
2. `02_table_data_editor`: Xây dựng Table Data Editor (chỉnh sửa dữ liệu trực tiếp, Dirty Tracking, Safe DML).
3. `03_object_content_search`: Tìm kiếm nội dung DDL đối tượng (View, Proc, Function, Trigger).
4. `04_schema_compare`: So sánh lược đồ 2 CSDL, Monaco Diff Viewer, sinh kịch bản đồng bộ.
5. `05_dependency_graph`: Biểu đồ quan hệ phụ thuộc đối tượng và cột CSDL (PostgreSQL & SQL Server).
6. `06_sql_visualizer`: Biểu đồ trực quan luồng truy vấn (Query Flow AST) đồng bộ thời gian thực với Monaco.

---

## 3. Cấu trúc thư mục tài liệu `migrate_phase3`

```text
migrate_phase3/
├── README.md                           <- Master plan & tổng quan trạng thái
├── 01_table_designer/
│   ├── REPORT.md                       <- Khảo sát code Python & Java hiện có, danh sách file sửa/thêm
│   └── PLAN.md                         <- Kế hoạch hoàn thiện chi tiết tuân thủ Design Rules
├── 02_table_data_editor/
│   ├── REPORT.md                       <- Khảo sát code Python & Java hiện có, danh sách file sửa/thêm
│   └── PLAN.md                         <- Kế hoạch triển khai Table Data Editor
├── 03_object_content_search/
│   ├── REPORT.md                       <- Khảo sát code Python & Java hiện có, danh sách file sửa/thêm
│   └── PLAN.md                         <- Kế hoạch triển khai Object Content Search
├── 04_schema_compare/
│   ├── REPORT.md                       <- Khảo sát code Python & Java hiện có, danh sách file sửa/thêm
│   └── PLAN.md                         <- Kế hoạch triển khai Schema Compare & Sync
├── 05_dependency_graph/
│   ├── REPORT.md                       <- Khảo sát code Python & Java hiện có, danh sách file sửa/thêm
│   └── PLAN.md                         <- Kế hoạch triển khai Dependency Graph & Impact Analysis
└── 06_sql_visualizer/
    ├── REPORT.md                       <- Khảo sát code Python & Java hiện có, danh sách file sửa/thêm
    └── PLAN.md                         <- Kế hoạch triển khai Query Flow / SQL Visualizer
```

