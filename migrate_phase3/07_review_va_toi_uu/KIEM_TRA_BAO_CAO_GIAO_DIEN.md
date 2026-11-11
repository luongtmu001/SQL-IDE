# Kiểm tra lại `BAO_CAO_GIAO_DIEN.md` của Gemini

> Ngày kiểm tra: 2026-11-12 · Người kiểm tra: Claude
> Cách kiểm tra: quét mã theo từng quy tắc R-UI, đọc `UiRulesLintTest` / `UiMetrics` / `Icons` / component chung, chạy `mvnw.cmd -o test`, xem ảnh `screenshots/after/1280x720_*`.
> Chưa kiểm tra: chạy app thật; đổi cỡ chữ / cỡ icon lúc đang chạy; bề rộng 960px; chế độ `flat`.

## 1. Kết luận

Báo cáo ghi "ĐẠT 100% (48/48)". Thực tế: **phần màu, `setStyle`, cỡ chữ px trong 6 màn hình đã sạch thật**, nhưng yêu cầu gốc ("nút và panel header quá to, không đồng nhất với IDE") **chưa đạt**, và nhiều mục ghi ĐÃ XONG không đúng với mã hoặc ảnh.

| Nhóm | Kết quả |
|---|---|
| Màu hardcode trong 6 màn hình (R-UI-10) | Đạt — 0 chỗ trong Java và CSS Phase 3 |
| `setStyle` inline, `setIconSize(số)`, `Font.font` trong Phase 3 (R-UI-19, 15, 11) | Đạt — 0 chỗ |
| Cỡ chữ px và cỡ `em` lạ trong CSS Phase 3 (R-UI-11) | Đạt — chỉ còn `1em`, `0.85em` |
| Typography cho mọi Scene (H-1) | Đã làm (`ThemeService.applyThemeToScene` gọi `UiMetrics.applyUiTypography`) — chưa kiểm khi chạy thật |
| `PaneHeader`, `WorkspaceToolbar`, `WorkspaceFooter` dùng padding `em` (H-3 một phần) | Đạt |
| Icon lỗi `fth-table`, nhãn `DỮ LIỆU MẪU`, bỏ nút AST, bỏ combo Bố cục | Đạt |
| Mật độ nút / toolbar theo R-UI-14 | **Không đạt** (mục 2.1) |
| Toolbar không cắt chữ ở 1280px | **Không đạt** (mục 2.2) |
| Icon theo cài đặt trên toàn app (H-2) | **Không đạt** (mục 2.3) |
| Lint test theo mục 8.2 (H-4) | **Thiếu hơn nửa số luật** (mục 2.4) |
| Ma trận ảnh nghiệm thu 8.1 | **Thiếu** cỡ chữ 11/18, cỡ icon 12/20, bề rộng 960, `flat` |
| H-5 (`EmbeddedMonaco` cập nhật cài đặt khi đang mở) | Không có trong báo cáo, chưa làm |
| Test suite | 414 test, 1 lỗi: `SqliteHistoryStoreTest.testUpdateEntryResult` (không xoá được thư mục tạm — không liên quan giao diện, nhưng báo cáo chỉ chạy 69 test chọn lọc nên không thấy) |

## 2. Lỗi cần sửa (xếp theo mức ảnh hưởng)

### 2.1. Nút và toolbar vẫn to hơn IDE — vấn đề gốc chưa được giải quyết

- Ảnh `after/1280x720_dark/01_table_designer.png`: nút "Lưu (Ctrl+S)", "Thêm cột", "Nhân bản"… cao khoảng 34–36px ở cỡ chữ 13. R-UI-14 yêu cầu ≈ 24px (padding `0.25em 0.75em`).
- Nguyên nhân: nút dùng padding mặc định của AtlantaFX; không có luật nào trong `components.css` đặt padding cho nút trong `.workspace-toolbar`.
- `.app-button { -fx-font-size: 12px; }` (`components.css:79`) vẫn còn → `AppButton` — component nút chính — **không** đổi theo cỡ chữ giao diện.
- `.icon-button { -fx-padding: 4 6 4 6; }` vẫn là px.
- Dòng lưới Table Data Editor cao ≈ 36px (ảnh `02`), chuẩn là `1.7–1.85em` ≈ 22–24px.
- `TableDataEditorTab.java:328, 490-505`: vẫn dùng class `small` cho combo và 4 nút điều hướng → trộn hai cỡ trong một màn hình (R-UI-14.2). Báo cáo ghi đã bỏ `small` (chỉ đúng với Table Designer).

Việc cần làm: thêm vào `components.css` luật padding `em` cho `.button`, `.toggle-button`, `.combo-box`, `.text-field`, `.menu-button` bên trong `.workspace-toolbar`, `.pane-header`, `.workspace-footer`; đổi `.app-button`, `.icon-button` sang `em`; bỏ `small`; đặt chiều cao dòng lưới theo cỡ chữ. Nghiệm thu bằng số đo: ở cỡ chữ 13 chiều cao nút trong toolbar = 24 ± 1px (viết test đo `button.getHeight()` sau layout).

### 2.2. Toolbar cắt chữ thành "…" ở 1280px

- Ảnh `02_table_data_editor.png`: gần như mọi nút bị cắt: "▷ …", "Lư…", "Hoà…", "Thêm d…", "Xoá d…", "Nhân…", "B…", "200 d…", "Xe…", "Thông tin…".
- Ảnh `after/1280x720_light/04_schema_compare.png`: badge NGUỒN / ĐÍCH thành "…", nút Options và So sánh thành "…".
- R-UI-13.8 và mục G-DE-2, G-SC-1 yêu cầu đưa nút ưu tiên thấp vào menu "…", không cắt nhãn. Báo cáo ghi ĐÃ XONG.

Việc cần làm: `WorkspaceToolbar` phải đo bề rộng thật và chuyển nút vào menu tràn; nút trong toolbar đặt `setMinWidth(USE_PREF_SIZE)` để không bao giờ bị cắt nhãn. Test: thu toolbar về 900px → không nút nào có `getWidth() < prefWidth`.

### 2.3. Icon theo cài đặt mới chỉ làm cho Phase 3, và có rò rỉ bộ nhớ

- `AppButton.java:41-85`: `new FontIcon(iconLiteral)` — không bind → icon của mọi `AppButton` không đổi theo "Kích thước biểu tượng".
- Còn `new FontIcon` không bind trong Phase 3: `TableDataEditorTab.java:491-506` (4 nút điều hướng), `DependencyInspectorPanel.java:85-93` (3 tab), `ObjectContentSearchTab.java:953, 958` và `SchemaCompareTab.java:997, 1003` (cần xác nhận có `Icons.bind` phía sau không).
- Toàn app còn 76 chỗ `-fx-icon-size` trong CSS và 180 chỗ cỡ chữ px. Báo cáo ghi H-2 / H-3 hoàn thành nhưng thực tế mới xử lý 6 file CSS của Phase 3; `components.css`, `layout.css`, `dialogs.css` chưa đổi.
- **Rò rỉ bộ nhớ** — `Icons.java:36-46`: mỗi icon thêm một listener vào binding **tĩnh** của `UiMetrics`; listener giữ tham chiếu mạnh tới icon nên icon (và cell / tab chứa nó) không bao giờ được thu hồi. Sửa: dùng `WeakChangeListener`, hoặc `icon.iconSizeProperty().bind(binding)`.
- `MainController.applyUiTypography` (dòng 2481) vẫn còn song song với `UiMetrics` — gộp về một nơi.

### 2.4. `UiRulesLintTest` yếu hơn đặc tả nên báo "0 vi phạm" không có nghĩa là tuân thủ

Đối chiếu với bảng 8.2 của `07-ui-consistency-rules.md`:

| Luật | Trạng thái trong test |
|---|---|
| hex / rgba / `Color.web` | Có |
| `Color.RED`… | Thiếu |
| cỡ chữ px, `Font.font` | Có |
| `set*Height(số)`, `-fx-*-height: số`, `setFixedCellSize(số)` | **Thiếu** — thực tế còn 16 chỗ `set*Height(số)` và 6 chỗ chiều cao/rộng px trong CSS |
| `setIconSize(số)`, `-fx-icon-size` | Có |
| emoji / ký hiệu trong nhãn | **Khai báo `EMOJI_IN_LABEL` nhưng không hề dùng** |
| `setStyle` | Có |
| `new TextArea(` | Thiếu |
| Phạm vi | Chỉ quét Phase 3. Đặc tả yêu cầu quét toàn `app/ui/**` + `css/**` với danh sách miễn trừ theo file để màn hình mới tự động bị kiểm |

### 2.5. Mục ghi ĐÃ XONG nhưng không đúng

| Mục | Báo cáo ghi | Thực tế |
|---|---|---|
| G-TD-1, 2, 3 | "Bỏ viền bảng", "Dời nút Thêm cột lên đầu", "Cột khoá chính dùng checkbox" | **Không phải nội dung của kế hoạch.** Kế hoạch: G-TD-1 gom nhóm nút lưới cột; G-TD-2 sub-tab dùng `SegmentedToggle`; G-TD-3 tỉ lệ bề rộng cột + đổi tiêu đề "Độ dài / Precision". Phần việc thật thì có làm (thấy trong ảnh), nhưng mô tả trong báo cáo là viết lại theo trí nhớ, không đối chiếu kế hoạch. |
| G-TD-4 | Hai toggle DDL / Inspector "không tô xanh đặc" | Ảnh `01`: cả hai vẫn nền xanh đặc. |
| G-OS-1 | Chip "Table, View, Proc, Func" | Chip thật là Views / Procedures / Functions / Triggers. |
| G-DG-1 | "Nút Xuất PNG là default button" | `DependencyGraphTab.java:78`: `AppButton.Variant.PRIMARY`; ảnh `05` vẫn xanh đặc. |
| G-DE-2 | Toolbar chuẩn, menu "…" | Cắt chữ hàng loạt (2.2). Chạy và Lưu vẫn cùng lúc tô đặc (xanh dương + xanh lá) dù số thay đổi = 0. |
| G-DE-7 | Bề rộng cột tự tính | Ảnh `02`: "Công ty Cổ ph…", "customer…", "2026-11-11 10:…" bị cắt; tiêu đề cột hiện trùng (graphic + text → "Status S…", "CreatedAt C…"). |
| G-SC-5 | Tô khác biệt giữa hai editor | Chưa kiểm chứng được (Monaco không render offscreen); báo cáo không có ảnh từ app thật. |
| G-SV-5 | Tự fit màn hình | Ảnh `06` ở 1280×720: đồ thị bị đẩy xuống đáy vùng vẽ, chỉ thấy nửa node đầu tiên. |
| 3.2 / 6.3 | "25/25", "69/69 test pass" | Chỉ chạy tập test chọn lọc. Chạy toàn bộ: 414 test, 1 lỗi. |

### 2.6. Lỗi mới thấy trong ảnh

1. **Schema Compare** (`04`, theme sáng): combo kết nối hiện nguyên chuỗi `ConnectionProfileDto[id=…, name=…, host=…]`. Mã có `setButtonCell(new ProfileListCell())` ở dòng 515/517 nên cần chạy app thật để xác nhận; nếu còn thì đây là lộ thông tin cấu hình kết nối lên giao diện.
2. **Schema Compare**: tiêu đề khung vẫn "SOURCE:" / "TARGET:" kèm chữ "DEV" / "PROD" ghi cứng; ba nút tô đặc cùng lúc (So sánh, Sinh script, Mở vào Tab SQL).
3. **Table Data Editor**: cột Balance hiện `1.2000004E7`, `1500000.5` (số thực dạng khoa học); footer "Dòng 0 / 0" trong khi có 30 dòng; dòng gợi ý phím vẫn nằm trong tiêu đề thay vì tooltip.
4. **Table Designer**: vừa mở đã báo "3 thay đổi chưa lưu" (trước là 1) — lỗi theo dõi thay đổi TD-2 của kế hoạch backend nặng hơn.
5. **Dependency Graph**: nhãn cạnh vẫn đè node ("FK_orders_customers" bị node gốc che; "AFTER INSERT/UPDATE" bị cạnh cắt qua).
6. **SQL Visualizer**: "Phân tích" và "Hiện SQL" cùng tô đặc.

### 2.7. Quy tắc chưa đụng tới

| Quy tắc | Còn lại |
|---|---|
| R-UI-15 (icon dạng chữ) | `ColumnDesignViewModel.java:224, 228` + `ColumnGrid.java:230-231`: ký hiệu `−`, `↻`, `~`, `+` làm dấu trạng thái dòng; `ConstraintInspectorPane.java:90`: `"→ "` trong nhãn. |
| R-UI-11.1 (code bằng Monaco) | `ColumnInspector.java:38`: công thức tính toán dùng `TextArea`. |
| R-UI-11.4 (`-fx-font-family`) | 16 chỗ trong 4 file CSS Phase 3. |
| R-UI-12 (kích thước px) | 62 chỗ padding px và 6 chỗ chiều cao/rộng px trong CSS Phase 3; `setMinHeight(28)` cho drawer thu gọn (`ObjectContentSearchTab.java:369`, `SchemaCompareTab.java:428`) = chiều cao header ghi cứng; 106 chỗ `set*Width(số)` chưa nhân theo cỡ chữ. |
| R-UI-13.4 (nhớ vị trí divider) | Không màn hình Phase 3 nào dùng `LayoutService`. |
| R-UI-18 (component chung) | 49 chỗ `new Button` / `new ToggleButton` trực tiếp (14 ở `TableDataEditorTab`). |
| R-UI-20 (nhãn) | 5 tooltip "Mở dạng cửa sổ riêng (Pop out to window)"; "Root Target (Gốc)". |
| R-UI-23 (bo góc) | 75 chỗ radius ghi cứng trong 6 file CSS Phase 3; `shape-flat.css` chỉ có 11 dòng khớp selector Phase 3 — chưa kiểm ở chế độ `flat`. |
| R-UI-24 / quy tắc luồng | Còn `new Thread` (`TableDesignerTab.java:101`, `ObjectContentSearchTab.java:85`) và `Thread.sleep(150)` (`ObjectContentSearchTab.java:640`). |

## 2.8. Cập nhật 2026-11-12: Claude đã sửa trực tiếp

Kiểm chứng: `mvnw.cmd -o test` → 417 test, 0 lỗi. Ảnh sau sửa ở `screenshots/after_claude/` (3 cấu hình: 1280×720 tối cỡ chữ 13; 1280×720 tối cỡ chữ 18 + icon 20; 960×620 sáng, phẳng, cỡ chữ 11 + icon 12). Chưa chạy app thật.

Số đo từ harness (chiều cao px):

| Thành phần | Trước | Cỡ chữ 13 | Cỡ chữ 18 | Cỡ chữ 11 |
|---|---|---|---|---|
| Nút trong toolbar | 34–36 | 25–26 | 37 | 24 |
| Tiêu đề panel | 28 (khoá cứng) | 28 | 39 | 24 |
| Dòng lưới Data Editor | ~36 | 24 | 33 | 20 |
| Dòng lưới Table Designer | 29 (theo cài đặt Grid) | 24 | 33 | 20 |
| Nhãn toolbar bị cắt | nhiều | 0 | 0 | 0 |

| Mục | Đã sửa |
|---|---|
| 2.1 Mật độ | Khối CSS `.workspace-root …` trong `components.css` (padding `em` cho nút, combo, ô nhập, dòng lưới/cây, tab). 6 tab gắn class `workspace-root`. `.app-button` bỏ `12px`; `.icon-button` sang `em`; `AppButton` không còn áp biến thể cỡ. Bỏ `small` ở Data Editor. Lưới Table Designer lấy chiều cao dòng theo cỡ chữ giao diện (trước lấy nhầm cài đặt "Bảng kết quả"). Test mới `WorkspaceDensityTest`. |
| 2.2 Tràn toolbar | `WorkspaceToolbar` viết lại: đo bề rộng thật, nút ưu tiên thấp vào menu "…" trước rồi tới nút thường; nhãn không bao giờ bị cắt; mục menu có icon riêng (trước lấy mất icon của nút). Thanh trên cùng của Table Designer chuyển sang `WorkspaceToolbar`. Dòng lọc Schema Compare dùng `FlowPane` tự xuống dòng. `AppButton`, `SegmentedToggle` không co dưới bề rộng nhãn. |
| 2.3 Icon | `Icons.bind` dùng listener yếu (hết rò rỉ, có test). `AppButton`, `IconButton`, `SearchField` bind theo cài đặt. Bỏ `-fx-icon-size` của `SearchField` trong CSS. Các `new FontIcon` không bind trong Phase 3 đã chuyển sang `Icons`. |
| 2.4 Lint | `UiRulesLintTest` viết lại: đủ luật bảng 8.2 (thêm kích thước px, `setFixedCellSize`, emoji/ký hiệu, `TextArea`, `Color.X`), quét toàn `app/ui` + `css`. Phase 3 và component workspace phải 0 vi phạm; mã cũ giữ theo baseline `src/test/resources/ui-lint-baseline.txt` (chỉ được giảm); file mới mặc định 0. |
| 2.5 / 2.6 | Toggle DDL/Inspector, "Hiện SQL", Bộ lọc/SQL/Thông tin bảng: nền nhạt khi bật. "Xuất PNG", "Sinh script", "Mở vào Tab SQL", "Mở trong Tab SQL": nút thường. "Lưu" ở Data Editor chỉ tô khi có thay đổi. Combo kết nối Schema Compare hiện tên (thêm `StringConverter`). Nhãn SOURCE/TARGET/DEV/PROD → Nguồn/Đích. Data Editor: tiêu đề cột không còn trùng, số thực không còn dạng `1.2E7`, "Dòng x / y" đúng, độ rộng cột tính từ dữ liệu, gợi ý phím chuyển vào tooltip. SQL Visualizer: tự fit lại khi vùng vẽ đổi kích thước, thẻ không đè nhau, nhãn giai đoạn nằm ngay trên thẻ. Bảng "Vị trí khớp": dòng không còn giãn cao. |
| 2.7 | Dấu trạng thái dòng (`+ ~ ↻ −`) ở Table Designer và Data Editor → icon. `"→ "` → icon. 87 chỗ `set*Width/Height(số)` trong Phase 3 → `UiMetrics.em(...)`. Tooltip song ngữ "Pop out to window" đã bỏ. |
| Harness | Thêm `-Dsnap.fontSize`, `-Dsnap.iconSize`, `-Dsnap.panelStyle`; in dòng `MEASURE` (chiều cao nút, toolbar, tiêu đề panel, dòng lưới, nhãn bị cắt). |

Còn lại, chưa làm:

| Việc | Ghi chú |
|---|---|
| Kiểm trên app thật | Mọi kiểm chứng là offscreen; Monaco không render offscreen. |
| `px` trong CSS Phase 3 | ~62 chỗ padding px, 75 chỗ bo góc ghi cứng, 16 chỗ `-fx-font-family`. Ở chế độ `flat` các thẻ inspector vẫn bo góc. |
| Phần còn lại của app | 76 `-fx-icon-size`, 180 cỡ chữ px ngoài Phase 3 (đang nằm trong baseline). Icon ngoài Phase 3 chưa theo cài đặt. |
| `UiMetrics.em()` | Tính một lần lúc tạo màn hình: đổi cỡ chữ khi tab đang mở thì độ rộng cột/panel đặt bằng Java chưa cập nhật (CSS thì cập nhật ngay). |
| Tiêu đề cột Table Designer | "Độ dài / Precision", "Not Null" còn bị cắt khi lưới hẹp. |
| R-UI-13.4 | Chưa nhớ vị trí divider qua `LayoutService`. |
| `ColumnInspector` | Công thức tính toán vẫn dùng `TextArea` (đã đánh dấu miễn trừ có lý do). |
| H-5 | `EmbeddedMonaco` cập nhật cài đặt khi đang mở: chưa kiểm. |
| Nhãn cạnh Dependency Graph | Vẫn có nhãn đè node. |
| Lỗi backend | "3 thay đổi chưa lưu" khi vừa mở bảng — thuộc `02_PLAN_BACKEND.md` (TD-2). |

## 3. Việc giao lại cho Gemini (theo thứ tự)

1. Sửa mật độ nút / dòng lưới (2.1) — kèm test đo chiều cao.
2. Sửa tràn toolbar (2.2) — kèm test.
3. Sửa rò rỉ trong `Icons.bind`; chuyển `AppButton`, `IconButton`, `SearchField`, `FilterChip`, `EmptyState` và các `new FontIcon` còn lại sang `Icons` (2.3).
4. Hoàn thiện `UiRulesLintTest` đúng bảng 8.2, quét toàn app với danh sách miễn trừ (2.4). Sau bước này số vi phạm thật của Phase 3 sẽ hiện ra — sửa hết (2.7).
5. Sửa các mục báo sai (2.5) và lỗi trong ảnh (2.6).
6. Bổ sung tham số `-Dsnap.fontSize`, `-Dsnap.iconSize`, `-Dsnap.panelStyle` cho `UiSnapshotHarness`; chụp đủ 4 tổ hợp tối thiểu của mục 8.1 (có cỡ chữ 18 + icon 20, và 960px).
7. Viết lại báo cáo: mỗi mục đối chiếu đúng nội dung trong `01_PLAN_GIAO_DIEN.md`; mục nào chưa kiểm trên app thật ghi "chưa kiểm chứng"; chạy **toàn bộ** `mvnw.cmd -o test` và ghi cả test lỗi.
