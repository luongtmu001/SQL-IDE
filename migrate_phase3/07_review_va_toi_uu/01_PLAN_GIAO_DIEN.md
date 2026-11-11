# File 1 — Kế hoạch sửa giao diện Phase 3 (làm trước)

> Phạm vi: chỉ phần nhìn và tương tác của 6 tab Phase 3. Không đụng tới truy vấn CSDL, sinh SQL, logic lưu — những việc đó nằm ở `02_PLAN_BACKEND.md` và làm sau.
> Ảnh hiện trạng: `screenshots/*.png` (render offscreen 1500×880, theme tối). Vùng Monaco trống trong ảnh là do WebView không render offscreen, không phải lỗi.

## 1. Quy tắc cho Gemini

1. Làm đúng thứ tự Bước 0 → Bước 8. Mỗi bước một commit.
2. Không sửa file trong `core/`, `infrastructure/`, `app/framework/` — trừ đúng 1 dòng icon ở Bước 0. Không đổi logic nghiệp vụ, không đổi chữ ký hàm public mà test đang gọi.
3. Không hardcode màu: cấm `#rrggbb`, `rgba(...)`, `Color.web(...)` trong Java và CSS của Phase 3. Chỉ dùng token `-color-*`, `-ide-*`.
4. Không dùng `setStyle(...)` inline cho màu, cỡ chữ, độ đậm. Chuyển sang style class.
5. Trong `updateItem(...)` của mọi cell: không `getStyleClass().add(...)` nếu class đã có (dùng `if (!contains)` hoặc add một lần trong constructor).
6. Tuân thủ `docs/java-desktop-ui-designer-skill-v1.0/.../rules/02, 03, 04` và mockup đã duyệt trong từng thư mục `0x_*/mockup.html`.
7. Sau mỗi bước: chạy `mvnw.cmd -o test` (phải pass hết) và chạy harness chụp ảnh ở **4 cấu hình**: 1280×720 và 1500×880, theme tối và theme sáng. Lưu ảnh vào `07_review_va_toi_uu/screenshots/after/<bước>/`.
8. Ba tab còn chạy dữ liệu mẫu (Schema Compare, Dependency Graph, SQL Visualizer): trong đợt giao diện giữ nguyên dữ liệu mẫu nhưng phải hiện nhãn `DỮ LIỆU MẪU` trên footer của tab để không ai nhầm là dữ liệu thật.

## 2. Tiêu chí nghiệm thu chung (áp cho mọi màn hình)

- Ở 1280×720: không nhãn nào bị cắt bằng "…", không phần nào của tab bị cắt ở mép trái/phải, không có thanh cuộn ngang ở cấp tab.
- Ở theme sáng: mọi chữ đọc được (độ tương phản ≥ 4.5:1 với nền).
- `grep -rE "#[0-9a-fA-F]{3,6}\b|rgba?\(|Color\.web" ` trên các file Phase 3 (danh sách ở mục 4, Bước 2) trả về 0 dòng.
- Mỗi toolbar chỉ có tối đa 1 nút kiểu primary.
- Một ngôn ngữ cho nhãn: tiếng Việt, giữ nguyên thuật ngữ SQL (JOIN, WHERE, DDL, PK…). Viết thường, hoa chữ đầu. Không viết song ngữ kiểu "Dòng (Line)".

## 3. Tổng quan các bước

| Bước | Nội dung | Kết quả |
|---|---|---|
| 0 | Công cụ kiểm tra + sửa lỗi icon làm vỡ Schema Compare | Chụp được đủ 6 màn hình |
| 1 | Token màu mới | Nền tảng cho Bước 2 |
| 2 | Thay toàn bộ màu hardcode và style inline | Theme sáng đọc được |
| 3 | Component dùng chung: toolbar, tiêu đề pane, footer, badge, trạng thái rỗng | 6 tab nhìn cùng một hệ |
| 4 | Thống nhất nhãn và ngôn ngữ | Hết nhãn HOA / song ngữ |
| 5 | Canvas dùng chung cho 2 màn hình đồ thị | Zoom / kéo / fit đúng |
| 6 | Sửa từng màn hình | Theo danh sách mục 4 |
| 7 | Hộp thoại Lưu DDL và Xem trước DML | Đồng bộ với Bước 3 |
| 8 | Chụp lại toàn bộ, báo cáo | Ảnh trước / sau |

## 4. Chi tiết từng bước

### Bước 0 — Chuẩn bị

**0.1. Harness chụp ảnh.** Chuyển `tools/UiSnapshotHarness.java.txt` thành `src/test/java/app/ui/UiSnapshotHarness.java`. Chỉ chạy khi có `-Dsnap.out`. Bổ sung:
- tham số `-Dsnap.size=1280x720` và `-Dsnap.theme=dark|light`;
- chụp thêm `SaveDesignDialog`, `PreviewDataChangesDialog`;
- Table Data Editor: nạp dữ liệu mẫu trực tiếp vào lưới (xem 0.3) để chụp được lưới có dữ liệu.

```
mvnw.cmd -o -q test -Dtest=UiSnapshotHarness "-DargLine=-Dsnap.out=D:/tmp/snap -Dsnap.size=1280x720 -Dsnap.theme=light"
```

**0.2. Sửa icon không tồn tại.** `core/core-domain/.../schemacompare/model/SchemaObjectType.java:7`: `fth-table` → `fth-grid`. Hiện tại cây kết quả Schema Compare ném `IllegalArgumentException: Icon description 'fth-table' is invalid!`.
Thêm test `IconLiteralTest`: duyệt mọi giá trị của `SchemaObjectType`, `DiffStatus`, `DependencyNodeType`, `QueryNodeType`, gọi `new FontIcon(literal)`.

**0.3. Dữ liệu mẫu cho lưới Data Editor.** `TableDataEditorTab.loadDataAsync()` luôn mở kết nối thật kể cả ở chế độ mock. Thêm nhánh: khi `BackendFlags.mock(METADATA)` → sinh 30 dòng khớp cột của `createMockTableDesign` (có 2 ô NULL, 1 chuỗi tiếng Việt dài). Đây là thay đổi logic duy nhất được phép ở file này.

Nghiệm thu: harness chụp đủ 6 tab + 2 dialog; `IconLiteralTest` pass.

### Bước 1 — Token màu

Thêm vào `src/main/resources/css/ide-vars.css` (giá trị mặc định cho theme tối) và ghi đè trong `light-theme.css`:

| Token | Dùng cho |
|---|---|
| `-ide-graph-bg` | nền canvas đồ thị (mặc định = `-ide-editor-bg`) |
| `-ide-graph-edge`, `-ide-graph-edge-active` | đường nối và mũi tên |
| `-ide-graph-stage-line` | đường phân cột giai đoạn |
| `-ide-node-table-fg/bg/border` | badge + icon node bảng |
| `-ide-node-view-fg/bg/border` | view |
| `-ide-node-proc-fg/bg/border` | procedure / function |
| `-ide-node-trigger-fg/bg/border` | trigger |
| `-ide-op-source/join/filter/aggregate/sort/output` | màu toán tử SQL Visualizer |
| `-ide-diff-added-fg/bg`, `-ide-diff-removed-fg/bg`, `-ide-diff-changed-fg/bg` | Schema Compare |
| `-ide-code-inline-fg` | đoạn SQL trong thẻ (thay `#93c5fd`) |
| `-ide-shadow` | màu bóng đổ (thay `rgba(0,0,0,0.35)`, `rgba(59,130,246,..)`) |

Ưu tiên ánh xạ về token AtlantaFX có sẵn (`-color-accent-*`, `-color-success-*`, `-color-warning-*`, `-color-danger-*`, `-color-fg-muted`…) thay vì đặt hex mới. Chỉ đặt hex trong `ide-vars.css` / `light-theme.css`.

Nghiệm thu: test `AtlantaThemesTest` / `SettingsAndThemesTest` vẫn pass; thêm 1 test kiểm tra mỗi token mới có mặt trong cả 2 file.

### Bước 2 — Thay màu hardcode và style inline

Số chỗ cần thay (đếm ngày 2026-11-11):

| File | Số chỗ hex/rgba |
|---|---|
| `css/schema-compare.css` | 18 |
| `css/dependency-graph.css` | 17 |
| `css/sql-visualizer.css` | 10 |
| `css/table-designer.css` | 1 |
| `ui/visualizer/SqlVisualizerCanvas.java` | 16 |
| `ui/dependency/DependencyGraphCanvas.java` | 9 |
| `ui/compare/SchemaCompareTab.java` | 3 (dòng 351, 360, 397) |
| `ui/dependency/DependencyGraphTab.java` | 1 (dòng 162) |
| `ui/visualizer/SqlVisualizerTab.java` | 1 (dòng 188) |

Cách làm:
- Shape (`CubicCurve`, `Polygon`) nhận màu qua style class: `.graph-edge { -fx-stroke: -ide-graph-edge; }`, `.graph-edge:highlighted` dùng `PseudoClass`. Bỏ `setStroke(Color.web(...))`.
- `FontIcon.setIconColor(Color.web(...))` → style class `.icon-accent`, `.icon-success`, `.icon-muted` (thêm vào `components.css`).
- Nền ảnh PNG xuất ra (`DependencyGraphCanvas.java:337`, tương tự ở `SqlVisualizerCanvas`) → lấy màu nền thực tế của canvas (`getBackground()`), không dùng `#121215`.
- Mọi `setStyle("-fx-font-size…/-fx-font-weight…/-fx-text-fill…")` trong 6 gói UI Phase 3 và 2 dialog → style class (định nghĩa ở Bước 3).

Nghiệm thu: lệnh grep ở mục 2 trả 0 dòng; ảnh theme sáng đọc được.

### Bước 3 — Component dùng chung

Tạo trong `app.ui.components` + CSS trong `components.css`. Sau đó thay thế ở cả 6 tab.

| Component | Mô tả | Thay cho |
|---|---|---|
| `WorkspaceToolbar` | 3 nhóm trái / giữa / phải. Khi thiếu chỗ: nút có `priority` thấp chuyển vào menu "…" bên phải; nút có nhãn dài rút về icon + tooltip. Chiều cao cố định 36px, khoảng cách 6px, một cỡ nút duy nhất. | 6 toolbar tự dựng bằng `HBox` |
| `PaneHeader` | Icon 13px + tiêu đề (chữ thường, đậm, 0.9em) + badge đếm tuỳ chọn + vùng nút bên phải. Cao 28px. | `tde-pane-header`, `script-drawer-header`, tiêu đề bảng vị trí khớp, `dep-inspector-header` |
| `WorkspaceFooter` | Thanh trạng thái 24px: các ô cách nhau bằng separator mảnh, ô cuối căn phải. Có sẵn ô `DỮ LIỆU MẪU` (ẩn/hiện). | 6 footer |
| `Badge` (đã có) | Chỉ dùng các biến thể: trung tính, accent, success, warning, danger. | `tde-badge-pk`, `tde-badge-nopk`, `diff-badge-*`, `node-badge-*`, badge cam "N matches" |
| `EmptyState` (đã có) | Thêm biến thể lỗi: icon cảnh báo + tiêu đề ngắn + nút "Thử lại" + phần "Chi tiết" thu gọn chứa thông báo kỹ thuật. | `new Label("Lỗi kết nối: …")`, `new Label("Đang tải dữ liệu…")` làm placeholder |
| `SegmentedToggle` | Nhóm nút chọn một (nền `-color-bg-subtle`, mục chọn nền `-color-accent-subtle`, chữ `-color-accent-fg`). Không cho bỏ chọn hết. | Sub-tab Table Designer, nhóm hướng Dependency Graph, nhóm chế độ SQL Visualizer, nhóm lọc Schema Compare |
| `FilterChip` | Chip bật/tắt: tắt = viền mờ; bật = nền `-color-accent-subtle` + dấu tick. | Chip Views / Procedures / Functions / Triggers |

Quy tắc màu nhấn:
- Nút primary duy nhất của mỗi tab: Table Designer = "Lưu"; Data Editor = "Chạy"; Search = "Tìm kiếm"; Compare = "So sánh"; Dependency = không có; Visualizer = "Phân tích".
- "Lưu" ở Data Editor là nút thường, chỉ chuyển sang kiểu nhấn (success) khi số thay đổi > 0.
- Toggle bật (Criteria, SQL, Inspector, DDL preview) dùng nền `-color-accent-subtle`, không dùng `accent` đặc.

Nghiệm thu: test đơn vị cho `WorkspaceToolbar` (thu hẹp bề rộng → nút ưu tiên thấp vào menu), `SegmentedToggle` (bấm lại mục đang chọn vẫn chọn — thay cho lỗi ở `TableDesignerTab.java:340-345, 402-404`).

### Bước 4 — Nhãn và ngôn ngữ

| Hiện tại | Đổi thành |
|---|---|
| Run (F5) / Save (N) / Discard / Add Row / Delete Row / Duplicate / Preview DML / Inspector / Criteria | Chạy / Lưu (N) / Hoàn tác / Thêm dòng / Xoá dòng / Nhân bản / Xem DML / Thông tin bảng / Bộ lọc |
| CRITERIA PANE — ĐIỀU KIỆN LỌC & SẮP XẾP | Bộ lọc và sắp xếp |
| SQL PANE — CÂU TRUY VẤN SELECT ĐỒNG BỘ TỰ ĐỘNG | Câu truy vấn |
| DATA GRID — CHỈNH SỬA DỮ LIỆU TRỰC TIẾP + dòng gợi ý phím | Dữ liệu (gợi ý phím chuyển vào tooltip của icon "?") |
| Cột (Column) / Toán tử (Operator) / Giá trị lọc (Filter) / Hoặc (Or…) / Sắp xếp (Sort) | Cột / Toán tử / Giá trị / Hoặc / Sắp xếp |
| ● EDITABLE / 🔒 CHỈ ĐỌC (Read-Only) | Cho phép sửa / Chỉ đọc (dùng `Badge`, không dùng emoji) |
| ✱ ✏ 🗙 trong cột `#` và thống kê | `FontIcon`: `fth-plus`, `fth-edit-2`, `fth-trash-2` (emoji hiển thị khác nhau giữa các máy) |
| Cột (Columns) / Chỉ mục (Indexes) / Khóa ngoại (FK) | Cột / Chỉ mục / Khoá ngoại |
| VỊ TRÍ KHỚP TRONG FILE HIỆN TẠI / Dòng (Line) / Cột (Col) / Đoạn mã khớp (Matching Code Snippet) | Vị trí khớp / Dòng / Cột / Đoạn mã |
| VIEWS (2 đối tượng) … **4 matches** + badge từng mục | Views (2) — bỏ số match in đậm ở dòng nhóm |
| "N matches" | "N kết quả" |
| KỊCH BẢN NÂNG CẤP TỰ ĐỘNG (GENERATED MIGRATION SCRIPT) | Script đồng bộ |
| Sinh Script Di chuyển (Deploy) | Sinh script |
| + New in Source / - Deleted in Source / Δ Modified / Identical | Chỉ có ở nguồn / Chỉ có ở đích / Khác nhau / Giống nhau |
| Cả hai chiều / Phụ thuộc vào (Depends On) / Được dùng bởi (Referenced By) | Cả hai / Phụ thuộc vào / Được dùng bởi |
| Cấp 2 (Khuyên dùng) | Cấp 2 |
| ROOT TARGET | Gốc |
| Luồng dữ liệu (Pipeline) / Cây cú pháp (AST) | bỏ nhóm này (xem 6.6) |
| Câu lệnh SQL phân tích (Hỗ trợ đồng bộ hai chiều với sơ đồ): | Câu lệnh SQL |

Nghiệm thu: grep trong 6 gói UI không còn chuỗi nhãn nào có dạng `Tiếng Việt (English)`; không còn ký tự emoji trong chuỗi giao diện.

### Bước 5 — Canvas dùng chung cho đồ thị

Tạo `app.ui.components.ZoomableGraphPane`, chuyển `DependencyGraphCanvas` và `SqlVisualizerCanvas` sang kế thừa/dùng nó. Phải xử lý đúng các điểm đang sai:

| Vấn đề hiện tại | Vị trí | Yêu cầu |
|---|---|---|
| `contentGroup` nằm trong `StackPane` căn giữa → mỗi lần kéo node hay zoom, cả đồ thị nhảy | `DependencyGraphCanvas.java:56-61` | `contentGroup.setManaged(false)`; vị trí chỉ do `translate`/`scale` quyết định |
| Không có clip → đồ thị vẽ đè lên toolbar và inspector | cả 2 canvas | Clip `Rectangle` bind theo `widthProperty/heightProperty` |
| Kéo node khi zoom ≠ 100% bị trôi | `DependencyGraphCanvas.java:216-233` | Chia delta chuột cho `scale.getX()` |
| Nút + / − zoom từ góc trên trái | `:280-290` | Zoom quanh tâm vùng nhìn |
| `fitToScreen()` chạy trước khi layout xong, dùng kích thước giả 200×80 → đồ thị chỉ chiếm ~25% vùng vẽ, chữ không đọc được | `:143-147, 299-332` | Gọi sau `applyCss(); layout()` + 1 pulse; lề 40px; không thu nhỏ dưới 0.6 khi ≤ 30 node; không phóng quá 1.0 |
| Mọi cạnh xuất phát cùng một điểm, nhãn đặt ở trung điểm đường thẳng nên đè lên node | `:397-428` | Điểm neo phân bố đều dọc cạnh phải/trái của node theo số cạnh; nhãn đặt tại điểm t=0.5 của đường cong, nền đặc; ẩn nhãn khi zoom < 0.7 |
| Kéo nền bằng chuột trái dễ nhầm với chọn | `:68-84` | Con trỏ `OPEN_HAND` / `CLOSED_HAND` khi kéo; bấm nền trống bỏ chọn node |
| Thiếu chỉ báo zoom | — | Nhãn "85%" cạnh nhóm nút zoom; nút "Vừa màn hình" và "100%" |

Thẻ node (dùng chung 2 màn): rộng tối thiểu 200, tối đa 280; tên dài cắt bằng "…" + tooltip đủ tên; bo góc theo `appearance.panelStyle` (không ghi cứng `-fx-border-radius: 6/8` — để `shape.css` / `shape-flat.css` quyết định).

Nghiệm thu: test đơn vị cho phép tính fit (đầu vào bounds + viewport → scale, translate); ảnh chụp: đồ thị chiếm ≥ 60% vùng vẽ, không nhãn nào đè node, chữ node đọc được ở 1280×720.

### Bước 6 — Từng màn hình

#### 6.1. Table Designer — `ui/designer/*`, ảnh `01_table_designer.png`

| ID | Việc | Vị trí |
|---|---|---|
| G-TD-1 | Dãy 9 nút icon không nhãn trên lưới cột → `+ Thêm cột ▾` (menu: phía trên, phía dưới, thêm N dòng), `Nhân bản`, `Xoá`, `↑`, `↓`, `Khoá chính`; ô "Chỉ hiện cột đã đổi" căn phải. | `ColumnGrid.java` (toolbar) |
| G-TD-2 | Sub-tab dùng `SegmentedToggle` (sửa luôn lỗi bấm lại thì mất trạng thái chọn). | `TableDesignerTab.java:326-349, 392-406` |
| G-TD-3 | Cột "Mô tả" rỗng kéo dài hết lưới → đặt chính sách co giãn: Tên cột 22%, Kiểu 16%, Độ dài 9%, Scale 7%, Not Null 8%, Mặc định 16%, Mô tả phần còn lại. Đổi tiêu đề "Độ dài" → "Độ dài / Precision". | `ColumnGrid.java` |
| G-TD-4 | Hai toggle góc phải (DDL, Inspector) đang tô xanh đặc → kiểu toggle nhẹ (Bước 3). | `TableDesignerTab.java:296-312` |
| G-TD-5 | Thanh trạng thái của editor SQL (`100 % · Ln/Ch · SPACES · LF · UTF-8`) lọt vào dưới pane DDL → ẩn khi `EmbeddedMonaco` ở chế độ chỉ đọc nhúng trong tab custom. | `DdlPreviewPane.java`, `EmbeddedMonaco.java` |
| G-TD-6 | Tab "Trigger": thay màn hình thông báo bằng `EmptyState` chuẩn ("Chưa hỗ trợ sửa trigger tại đây" + nút "Mở trong Object Explorer"). Nội dung thật làm ở File 2. | `TableDesignerTab.java:371-390` |
| G-TD-7 | Footer dùng `WorkspaceFooter`; "✓ Hợp lệ" / "⚠ N lỗi" dùng `Badge` success/danger, bấm vào badge lỗi mở danh sách đầy đủ. | `TableDesignerTab.java:408-422, 540-551` |
| G-TD-8 | Inspector bên phải: bề rộng tối thiểu 300, tối đa 420; các nhóm có khoảng cách 12px đồng đều. | `ColumnInspector.java` |

#### 6.2. Table Data Editor — `ui/dataeditor/*`, ảnh `02_table_data_editor.png`, mockup `02_table_data_editor/mockup.html`

| ID | Việc | Vị trí |
|---|---|---|
| G-DE-1 | Pane bộ lọc rỗng đang chiếm 50% chiều cao → mặc định **thu gọn**; khi mở cao theo số điều kiện (tối đa 5 dòng rồi cuộn), không dùng divider 50/50. | `TableDataEditorTab.java:251, 410-422` |
| G-DE-2 | Toolbar chuyển sang `WorkspaceToolbar`: trái = Chạy, Lưu (N), Hoàn tác │ Thêm dòng, Xoá dòng, Nhân bản; giữa = Bộ lọc, SQL, "Số dòng" + combo, ô tìm nhanh (dùng `SearchField`); phải = badge PK, Xem DML, Thông tin bảng. Ưu tiên thấp (vào menu "…" trước): Nhân bản, Xem DML. | `:189-321` |
| G-DE-3 | Dải tiêu đề trống phía trên lưới khi chưa có cột → ẩn header lưới cho tới khi có cột. Placeholder dùng `EmptyState` (đang tải / lỗi + Thử lại / bảng không có dòng nào). | `:388-392, 514, 542, 587` |
| G-DE-4 | Icon tiêu đề lưới hiển thị sai hình → `fth-grid` (kiểm tra bằng `IconLiteralTest`). | `:365` |
| G-DE-5 | Cột `#`: cố định bên trái khi cuộn ngang (đặt lưới số dòng riêng đồng bộ cuộn dọc, hoặc cột đầu `setReorderable(false)` + nền `-ide-grid-header-bg` nếu không làm được cố định — ghi rõ lựa chọn trong báo cáo). Trạng thái dòng hiển thị bằng icon + vạch màu 3px ở mép trái dòng (thêm = success, sửa = warning, xoá = danger). | `:604-622` |
| G-DE-6 | Ô dữ liệu: số căn phải + font mono; `NULL` hiển thị chữ nghiêng mờ không có ngoặc vuông; ô đã sửa chỉ đổi nền `-color-warning-subtle` (bỏ viền + chữ đậm làm lưới giật). Dòng đánh dấu xoá: gạch ngang + nền `-color-danger-subtle`, không giảm opacity. Class `row-inserted` đã có trong CSS nhưng chưa được gắn — gắn vào `TableRow`. | `EditableDataTableCell.java:168-190`, `table-data-editor.css:80-104` |
| G-DE-7 | Độ rộng cột: tự tính theo nội dung 50 dòng đầu, tối thiểu 80, tối đa 360 (hiện cố định 140). Tiêu đề cột hiện tên + kiểu dữ liệu chữ nhỏ mờ bên dưới (báo cáo ghi có nhưng chưa làm). | `:629-656` |
| G-DE-8 | Badge "PK: —" đang tô màu cảnh báo khi chưa biết PK → trung tính; có PK = accent; không PK = danger kèm tooltip giải thích hậu quả. | `:594-602`, CSS `.tde-badge-*` |
| G-DE-9 | Footer dùng `WorkspaceFooter`; không hiện "Cho phép sửa" khi chưa tải được dữ liệu. | `:430-483` |
| G-DE-10 | Pane SQL: thay `TextArea` bằng `EmbeddedMonaco` chỉ đọc (có tô màu), cao 3–6 dòng. | `:323-354` |
| G-DE-11 | Ngăn "Thông tin bảng": rộng 300, có hiệu ứng trượt 150ms, tiêu đề dùng `PaneHeader`. | `ConstraintInspectorPane.java` |

#### 6.3. Object Content Search — `ui/search/*`, ảnh `03_object_search.png`

Bố cục hiện tại ổn, chỉ tinh chỉnh.

| ID | Việc | Vị trí |
|---|---|---|
| G-OS-1 | 4 chip loại đối tượng dùng `FilterChip` (hiện bật/tắt nhìn như nhau). | `ObjectContentSearchTab.java` (toolbar) |
| G-OS-2 | Badge "N kết quả" trong cây dùng badge trung tính; dòng nhóm chỉ ghi "Views (2)". | `ObjectTreeCell` (`:811-913`) |
| G-OS-3 | Bảng vị trí khớp: cột `#` 48px, Dòng 64px, Cột 64px, cố định; "Đoạn mã" chiếm phần còn lại; phần khớp trong đoạn mã được tô nền (dùng `TextFlow`). | `:360-392` |
| G-OS-4 | `lblSnippet.setMaxWidth(260)` cứng → bind theo bề rộng cell. | `:852` |
| G-OS-5 | Bỏ `getStyleClass().add(...)` lặp trong `updateItem`. | `:386, 866` |
| G-OS-6 | Trạng thái ban đầu (chưa tìm): `EmptyState` ở cả hai pane, pane phải ghi "Chọn một đối tượng để xem định nghĩa". | — |

#### 6.4. Schema Compare — `ui/compare/*`, mockup `04_schema_compare/mockup.html`

Chưa có ảnh hiện trạng (màn hình không render được trước Bước 0). Sau Bước 0 chụp ảnh "trước" rồi mới sửa.

| ID | Việc | Vị trí |
|---|---|---|
| G-SC-1 | Hai hộp NGUỒN / ĐÍCH: mỗi hộp gồm badge + 3 combo. Dưới 1400px tự xếp thành 2 dòng (nguồn trên, đích dưới), nút đảo chiều nằm giữa. Combo có `minWidth` 120 và co giãn đều. | `SchemaCompareTab.java:187-233` |
| G-SC-2 | Dòng lọc: dùng `SegmentedToggle` cho Tất cả / Khác biệt / Chỉ ở nguồn / Chỉ ở đích / Khác nhau, số đếm hiển thị bằng badge nhỏ trong mỗi mục. | `:235-282` |
| G-SC-3 | Cây khác biệt: badge trạng thái dùng `Badge` + token `-ide-diff-*`; dòng "Giống nhau" chữ mờ; checkbox căn thẳng hàng. Sau khi lọc giữ nguyên mục đang chọn (hiện nhảy về mục đầu). | `SchemaTreeCell` (`:837-926`), `rebuildTree` (`:661-725`) |
| G-SC-4 | Tiêu đề hai khung DDL: bỏ màu chữ riêng (`#60a5fa`, `#fbbf24`), dùng badge NGUỒN / ĐÍCH + tên đối tượng; chia đúng 50/50 theo divider của `SplitPane` bên dưới. | `:339-382` |
| G-SC-5 | Tô khác biệt giữa hai khung: dùng Monaco diff editor (thêm hàm bridge `createDiffEditor` trong `editor-bridge.js`), thay cho 2 editor rời. Nếu không kịp: tô nền dòng khác nhau bằng decorations và ghi rõ trong báo cáo. | `:366-380`, `monaco/editor-bridge.js` |
| G-SC-6 | Khung script phía dưới dùng `PaneHeader`; thu gọn được về 28px và mở lại bằng chính header (hiện đóng rồi không có nút mở lại ngoài việc sinh script). | `:384-433` |
| G-SC-7 | Hộp thoại Options: 2 checkbox + cảnh báo dùng banner cảnh báo chuẩn của `DialogChrome`. | `:554-588` |
| G-SC-8 | Footer: thêm ô `DỮ LIỆU MẪU` (bật cho tới khi File 2 nối backend thật). | `:435-442` |

#### 6.5. Dependency Graph — `ui/dependency/*`, ảnh `05_dependency_graph.png`

| ID | Việc | Vị trí |
|---|---|---|
| G-DG-1 | Toolbar bị cắt chữ ("Đối tượng…", "Cả hai c…", "Độ…", "B…", "Xu…") → `WorkspaceToolbar`: trái = combo đối tượng gốc (co giãn, tối thiểu 200); giữa = hướng (`SegmentedToggle`), độ sâu; phải = nhóm zoom + "Vừa màn hình", Làm mới, Xuất PNG (nút thường, không phải primary). | `DependencyGraphTab.java:150-254` |
| G-DG-2 | Combo "Bố cục" không có tác dụng → bỏ khỏi toolbar (thêm lại khi có bố cục thứ hai). | `:221-230` |
| G-DG-3 | Canvas chuyển sang `ZoomableGraphPane` (Bước 5). | `DependencyGraphCanvas.java` |
| G-DG-4 | Inspector bị cắt mép phải (badge "TABLE", tab thứ ba) → `minWidth` 320; tên tab ngắn "Thuộc tính", "DDL", "Các bước"; tên đối tượng dài cắt "…" + tooltip. | `DependencyInspectorPanel.java`, `DependencyGraphTab.java:137-140` |
| G-DG-5 | Dùng `setCustomContent(key, root)` như 4 tab kia thay cho `setContent(root)`. | `:143` |
| G-DG-6 | Chú giải màu loại node (bảng / view / procedure / trigger) ở góc dưới trái canvas. | mới |
| G-DG-7 | Footer mới: số node, số cạnh, ô `DỮ LIỆU MẪU`. | mới |

#### 6.6. SQL Visualizer — `ui/visualizer/*`, ảnh `06_sql_visualizer.png`

| ID | Việc | Vị trí |
|---|---|---|
| G-SV-1 | Toàn bộ tab bị cắt hai bên khi hẹp (mất chữ đầu nhãn, nút "Xuất PNG" bị cắt) → `setCustomContent`; toolbar dùng `WorkspaceToolbar`; mọi con trong `VBox` gốc có `minWidth = 0`. | `SqlVisualizerTab.java:124-147, 176-263` |
| G-SV-2 | Nút "Cây cú pháp (AST)" không làm gì → bỏ cả nhóm chuyển chế độ. | `:221-234` |
| G-SV-3 | Badge "Chi phí: 32%" và "12.450 dòng" là số cứng trong code → ẩn badge và dòng số liệu khi giá trị null; ở đợt này đặt null (việc xoá số trong service nằm ở File 2 nhưng giao diện không được hiện số bịa). Inspector bỏ 2 dòng "Chi phí ước tính", "Dữ liệu ước tính" khi null. | `SqlVisualizerCanvas.java`, `SqlVisualizerInspector.java` |
| G-SV-4 | Ô SQL: `TextArea` → `EmbeddedMonaco` (tô màu, đồng bộ vùng chọn với node). Cao tối thiểu 6 dòng, kéo giãn được bằng `SplitPane` (hiện cố định 110px và chỉ thấy 3 dòng). | `:265-288, 346-355` |
| G-SV-5 | 6 cột giai đoạn rộng cố định 260px → node "Đầu ra" tràn khỏi màn hình. Bỏ cột của giai đoạn không có node; khoảng cách cột tính theo bề rộng vùng vẽ (tối thiểu 220); fit sau layout (Bước 5). | `QueryFlowAnalyzer.calculatePipelineLayout` (chỉ sửa hằng số bố cục) + `SqlVisualizerCanvas.java` |
| G-SV-6 | Inspector dưới: lưới nhãn – giá trị lệch cột → `GridPane` 2 cột (nhãn 140px cố định), giống `ColumnInspector`. Tiêu đề dùng `PaneHeader`. | `SqlVisualizerInspector.java` |
| G-SV-7 | Combo "Mẫu truy vấn" chiếm chỗ lớn nhất toolbar → chuyển thành menu "Mẫu ▾" nhỏ ở cuối nhóm trái. | `:181-208` |
| G-SV-8 | Footer mới: số bước, ô `DỮ LIỆU MẪU` cho tới khi parser được thay ở File 2 (ghi "Phân tích gần đúng"). | mới |

### Bước 7 — Hộp thoại

| ID | Việc | Vị trí |
|---|---|---|
| G-DL-1 | `SaveDesignDialog`: vùng script dùng `EmbeddedMonaco` chỉ đọc; cảnh báo thao tác phá huỷ dùng banner danger của `DialogChrome`; thứ tự nút: Huỷ │ Mở trong tab SQL │ Thực thi (primary, chuyển danger khi có thao tác phá huỷ). | `ui/dialogs/SaveDesignDialog.java` |
| G-DL-2 | `PreviewDataChangesDialog`: 3 badge đếm (thêm / sửa / xoá) ở đầu; script trong `EmbeddedMonaco`; cùng thứ tự nút. Khi mở từ "Xem DML" chỉ có nút Đóng + Mở trong tab SQL (không có Thực thi). | `ui/dialogs/PreviewDataChangesDialog.java` |

### Bước 8 — Chụp lại và báo cáo

Nộp `07_review_va_toi_uu/BAO_CAO_GIAO_DIEN.md` gồm:
1. Bảng ID đã xong / chưa xong (kèm lý do).
2. Ảnh trước – sau cho từng màn hình ở 4 cấu hình.
3. Kết quả lệnh grep màu hardcode (phải 0 dòng) và `mvnw.cmd -o test`.
4. Danh sách test mới.
5. Những chỗ phải chọn phương án thay thế (ví dụ G-DE-5, G-SC-5) và lý do.

## 5. Những việc KHÔNG làm trong file này

- Không nối Schema Compare / Dependency Graph với CSDL thật, không thay parser SQL Visualizer.
- Không sửa cách sinh INSERT/UPDATE/DELETE, không sửa xử lý phím Delete, không sửa luồng lưu.
- Không sửa `ObjectSearchService`, `SchemaCompareService`, `DependencyGraphService`, `TableDataEditorService`, `TableDesignerService`.

Tất cả nằm ở `02_PLAN_BACKEND.md`.
