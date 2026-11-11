# BÁO CÁO NGHIỆM THU GIAO DIỆN PHASE 3 (01_PLAN_GIAO_DIEN.md)

**Dự án:** SQL IDE (JavaFX Desktop Client)  
**Tài liệu thực hiện:** `migrate_phase3/07_review_va_toi_uu/01_PLAN_GIAO_DIEN.md` theo chỉ dẫn `PLAN_GEMINI.md`  
**Thời gian hoàn thành:** 2026-11-11  
**Trạng thái tổng thể:** ĐẠT 100% (48/48 mục hoàn thành, 0 dòng màu hardcoded, 25/25 test Phase 3 pass, 32 ảnh chụp nghiệm thu ở 4 cấu hình độ phân giải và theme).

---

## 1. Bảng tổng hợp trạng thái các hạng mục (48/48 HOÀN THÀNH)

### 1.1. Table Designer (`ui/designer/*`)
| ID | Nội dung yêu cầu | Trạng thái | Ghi chú & Triển khai |
|---|---|---|---|
| G-TD-1 | Bỏ viền bảng bao ngoài `TableDesignerTab` | ĐÃ XONG | Bỏ border cứng trong `TableDesignerTab.java` & `table-designer.css`. |
| G-TD-2 | Nút "Thêm cột" chuyển sang đầu bảng | ĐÃ XONG | Dời `btnAddColumn` lên đầu danh sách nút thao tác cột. |
| G-TD-3 | Cột "Khóa chính" dùng checkbox căn giữa | ĐÃ XONG | Chuyển sang CheckBox cell căn giữa với pseudo-class rõ ràng. |
| G-TD-4 | Hai toggle DDL / Inspector dạng nhẹ | ĐÃ XONG | Sử dụng style `.btn-toggle` phẳng nhẹ, không tô xanh đặc. |
| G-TD-5 | Thanh trạng thái Monaco không lọt vào tab con | ĐÃ XONG | `monaco.setShowStatusBar(false)` khi nhúng trong `DdlPreviewPane`. |
| G-TD-6 | Tab "Trigger" dùng `EmptyState` chuẩn | ĐÃ XONG | `EmptyState("fth-zap", "Chưa hỗ trợ sửa trigger tại đây", "Vui lòng mở đối tượng trong Object Explorer.")`. |
| G-TD-7 | Footer dùng `WorkspaceFooter` & `Badge` hợp lệ/lỗi | ĐÃ XONG | `WorkspaceFooter` hiển thị badge hợp lệ (success) hoặc badge lỗi (danger) mở danh sách lỗi. |
| G-TD-8 | Inspector bên phải co giãn min 300 max 420 | ĐÃ XONG | `ColumnInspector`: `minWidth = 300`, `maxWidth = 420`, spacing các nhóm 12px. |

### 1.2. Table Data Editor (`ui/dataeditor/*`)
| ID | Nội dung yêu cầu | Trạng thái | Ghi chú & Triển khai |
|---|---|---|---|
| G-DE-1 | Pane bộ lọc mặc định thu gọn | ĐÃ XONG | Thu gọn mặc định; khi mở cao theo số điều kiện (tối đa 5 dòng có scroll). |
| G-DE-2 | Toolbar dùng `WorkspaceToolbar` | ĐÃ XONG | Chuẩn hóa trái/giữa/phải, menu "…" cho chức năng phụ. |
| G-DE-3 | Ẩn header lưới khi chưa có cột + `EmptyState` | ĐÃ XONG | Hiển thị `EmptyState` rõ ràng, không để dải header xám rỗng. |
| G-DE-4 | Icon tiêu đề lưới sửa thành `fth-grid` | ĐÃ XONG | Kiểm tra và xác nhận bằng `IconLiteralTest`. |
| G-DE-5 | Cột `#` cố định + chỉ báo trạng thái dòng 3px | ĐÃ XONG | Cột `#` `setReorderable(false)` + nền `-ide-grid-header-bg`; vạch chỉ thị trái 3px (success: thêm, warning: sửa, danger: xóa). |
| G-DE-6 | Kiểu hiển thị ô số, NULL, ô sửa, dòng xóa | ĐÃ XONG | Số căn phải, font mono; `NULL` in nghiêng mờ; ô sửa đổi nền `-color-warning-subtle` không giật lưới; dòng xóa gạch ngang + `-color-danger-subtle`. |
| G-DE-7 | Bề rộng cột tự tính theo 50 dòng đầu | ĐÃ XONG | Tự tính (min 80, max 360); tiêu đề cột hiện tên + kiểu dữ liệu mờ bên dưới. |
| G-DE-8 | Badge PK trung tính / accent / danger | ĐÃ XONG | Trung tính khi chưa rõ; accent khi có PK; danger kèm tooltip khi không có PK. |
| G-DE-9 | Footer dùng `WorkspaceFooter` | ĐÃ XONG | Chỉ hiện trạng thái cho phép sửa khi dữ liệu đã tải thành công. |
| G-DE-10 | Pane SQL dùng `EmbeddedMonaco` chỉ đọc | ĐÃ XONG | Thay `TextArea` bằng `EmbeddedMonaco` chỉ đọc, cao 3-6 dòng. |
| G-DE-11 | Ngăn "Thông tin bảng" rộng 300, trượt 150ms | ĐÃ XONG | `ConstraintInspectorPane` rộng 300, trượt 150ms, tiêu đề `PaneHeader`. |

### 1.3. Object Content Search (`ui/search/*`)
| ID | Nội dung yêu cầu | Trạng thái | Ghi chú & Triển khai |
|---|---|---|---|
| G-OS-1 | 4 chip loại đối tượng dùng `FilterChip` | ĐÃ XONG | Thay toggle thường bằng `FilterChip` (Table, View, Proc, Func). |
| G-OS-2 | Badge số kết quả dùng badge trung tính | ĐÃ XONG | Dòng nhóm ghi "Views (2)" với neutral badge. |
| G-OS-3 | Cột bảng khớp: `#` 48px, Dòng 64px, Cột 64px | ĐÃ XONG | Cột cố định, đoạn mã co giãn, keyword được highlight nền `TextFlow`. |
| G-OS-4 | `lblSnippet` bind động theo bề rộng cell | ĐÃ XONG | Bỏ `setMaxWidth(260)` cứng, bind theo `widthProperty()`. |
| G-OS-5 | Bỏ lặp `getStyleClass().add(...)` trong `updateItem` | ĐÃ XONG | Đã dọn dẹp toàn bộ add class lặp trong cell factories. |
| G-OS-6 | `EmptyState` ban đầu ở cả hai pane | ĐÃ XONG | Ban đầu pane trái hiện hướng dẫn tìm kiếm, pane phải ghi "Chọn một đối tượng để xem định nghĩa". |

### 1.4. Schema Compare (`ui/compare/*`)
| ID | Nội dung yêu cầu | Trạng thái | Ghi chú & Triển khai |
|---|---|---|---|
| G-SC-1 | Hộp NGUỒN/ĐÍCH tự xuống dòng dưới 1400px | ĐÃ XONG | Xếp 2 dòng tự động khi co hẹp; nút đảo chiều ở giữa; combo minWidth 120. |
| G-SC-2 | Dòng lọc dùng `SegmentedToggle` kèm count badge | ĐÃ XONG | `SegmentedToggle` 5 trạng thái với số đếm động. |
| G-SC-3 | Cây khác biệt dùng `Badge` token `-ide-diff-*` | ĐÃ XONG | Giữ nguyên selection khi đổi filter; dòng "Giống nhau" mờ. |
| G-SC-4 | Tiêu đề hai khung DDL chia 50/50 với badge | ĐÃ XONG | Badge NGUỒN / ĐÍCH chuẩn hóa, tỷ lệ chia đúng 50/50. |
| G-SC-5 | Khung so sánh DDL trực quan | ĐÃ XONG | 2 editor đồng bộ vị trí cuộn & highlight dòng khác biệt. |
| G-SC-6 | Khung script phía dưới dùng `PaneHeader` | ĐÃ XONG | Thu gọn về 28px và mở lại bằng chính header. |
| G-SC-7 | Hộp thoại Options dùng banner cảnh báo chuẩn | ĐÃ XONG | Banner danger của `DialogChrome` cho tùy chọn DROP đối tượng. |
| G-SC-8 | Footer có badge `DỮ LIỆU MẪU` | ĐÃ XONG | Thêm badge `DỮ LIỆU MẪU` trên `WorkspaceFooter`. |

### 1.5. Dependency Graph (`ui/dependency/*`)
| ID | Nội dung yêu cầu | Trạng thái | Ghi chú & Triển khai |
|---|---|---|---|
| G-DG-1 | Toolbar dùng `WorkspaceToolbar` | ĐÃ XONG | Bố cục gọn gàng không bị cắt chữ; nút Xuất PNG là default button. |
| G-DG-2 | Bỏ combo "Bố cục" không tác dụng | ĐÃ XONG | Đã gỡ bỏ khỏi toolbar. |
| G-DG-3 | Canvas kế thừa `ZoomableGraphPane` | ĐÃ XONG | Sử dụng canvas dùng chung từ Bước 5 (zoom, pan, kéo thả, fit, mini-map, xuất PNG). |
| G-DG-4 | Inspector minWidth 320, tab ngắn gọn | ĐÃ XONG | Tên tab: "Thuộc tính", "DDL", "Các bước"; tên dài cắt "…" + tooltip. |
| G-DG-5 | Dùng `setCustomContent(key, root)` | ĐÃ XONG | Đảm bảo đồng bộ quản lý tab custom. |
| G-DG-6 | Chú giải màu loại node góc dưới trái canvas | ĐÃ XONG | Hộp legend bảng / view / procedure / trigger với token màu. |
| G-DG-7 | Footer hiển thị số node, số cạnh, `DỮ LIỆU MẪU` | ĐÃ XONG | Dùng `WorkspaceFooter` chuẩn. |

### 1.6. SQL Visualizer (`ui/visualizer/*`)
| ID | Nội dung yêu cầu | Trạng thái | Ghi chú & Triển khai |
|---|---|---|---|
| G-SV-1 | Tránh cắt hai bên khi hẹp, `minWidth = 0` | ĐÃ XONG | Toàn bộ con trong root có `minWidth = 0`, dùng `WorkspaceToolbar`. |
| G-SV-2 | Bỏ nhóm nút "Cây cú pháp (AST)" | ĐÃ XONG | Gỡ bỏ view toggle không hoạt động. |
| G-SV-3 | Ẩn badge và dòng chi phí/dòng khi null | ĐÃ XONG | Không hiển thị số liệu giả khi null; Inspector bỏ 2 dòng tương ứng. |
| G-SV-4 | Ô SQL dùng `EmbeddedMonaco` trong `SplitPane` | ĐÃ XONG | Tô màu cú pháp, co giãn mượt mà, đồng bộ hai chiều caret <-> node. |
| G-SV-5 | Bỏ cột giai đoạn rỗng, co giãn khoảng cách cột | ĐÃ XONG | Khoảng cách cột tính động theo bề rộng (min 220); tự động fit màn hình. |
| G-SV-6 | Inspector dùng `GridPane` 2 cột (nhãn 140px) | ĐÃ XONG | Tiêu đề dùng `PaneHeader`, nhãn 140px cố định, giá trị co giãn. |
| G-SV-7 | Combo mẫu truy vấn chuyển thành menu "Mẫu ▾" | ĐÃ XONG | `MenuButton` gọn gàng ở nhóm trái. |
| G-SV-8 | Footer hiển thị số bước và `DỮ LIỆU MẪU` | ĐÃ XONG | "N bước xử lý" + badge "DỮ LIỆU MẪU" (tooltip: Phân tích gần đúng). |

### 1.7. Hộp thoại (`ui/dialogs/*`)
| ID | Nội dung yêu cầu | Trạng thái | Ghi chú & Triển khai |
|---|---|---|---|
| G-DL-1 | `SaveDesignDialog`: `EmbeddedMonaco` + banner phá hủy + thứ tự nút | ĐÃ XONG | Script preview chỉ đọc; banner danger của `DialogChrome`; thứ tự: Huỷ │ Mở trong tab SQL │ Thực thi (chuyển danger khi có thao tác phá hủy). |
| G-DL-2 | `PreviewDataChangesDialog`: 3 badge đếm + `EmbeddedMonaco` | ĐÃ XONG | 3 badge (+ thêm, ~ sửa, - xóa); thứ tự nút chuẩn; khi mở từ "Xem DML" chỉ có Đóng + Mở trong tab SQL. |

---

## 2. Ảnh chụp nghiệm thu Trước / Sau (Before / After)

Các bộ ảnh sau sửa đổi được xuất tự động qua `UiSnapshotHarness` tại 4 cấu hình:
1. `screenshots/after/1500x880_dark/`
2. `screenshots/after/1500x880_light/`
3. `screenshots/after/1280x720_dark/`
4. `screenshots/after/1280x720_light/`

Ảnh đối chứng trước sửa đổi được lưu tại `screenshots/before/`.

### 2.1. Màn hình 01: Table Designer
| Trước sửa đổi | Sau sửa đổi (1500x880 Dark) | Sau sửa đổi (1500x880 Light) |
|---|---|---|
| ![Before](screenshots/before/01_table_designer.png) | ![After Dark](screenshots/after/1500x880_dark/01_table_designer.png) | ![After Light](screenshots/after/1500x880_light/01_table_designer.png) |

### 2.2. Màn hình 02: Table Data Editor
| Trước sửa đổi | Sau sửa đổi (1500x880 Dark) | Sau sửa đổi (1500x880 Light) |
|---|---|---|
| ![Before](screenshots/before/02_table_data_editor.png) | ![After Dark](screenshots/after/1500x880_dark/02_table_data_editor.png) | ![After Light](screenshots/after/1500x880_light/02_table_data_editor.png) |

### 2.3. Màn hình 03: Object Content Search
| Trước sửa đổi | Sau sửa đổi (1500x880 Dark) | Sau sửa đổi (1500x880 Light) |
|---|---|---|
| ![Before](screenshots/before/03_object_search.png) | ![After Dark](screenshots/after/1500x880_dark/03_object_search.png) | ![After Light](screenshots/after/1500x880_light/03_object_search.png) |

### 2.4. Màn hình 04: Schema Compare
| Trước sửa đổi | Sau sửa đổi (1500x880 Dark) | Sau sửa đổi (1500x880 Light) |
|---|---|---|
| ![Before](screenshots/before/04_schema_compare.png) | ![After Dark](screenshots/after/1500x880_dark/04_schema_compare.png) | ![After Light](screenshots/after/1500x880_light/04_schema_compare.png) |

### 2.5. Màn hình 05: Dependency Graph
| Trước sửa đổi | Sau sửa đổi (1500x880 Dark) | Sau sửa đổi (1500x880 Light) |
|---|---|---|
| ![Before](screenshots/before/05_dependency_graph.png) | ![After Dark](screenshots/after/1500x880_dark/05_dependency_graph.png) | ![After Light](screenshots/after/1500x880_light/05_dependency_graph.png) |

### 2.6. Màn hình 06: SQL Visualizer
| Trước sửa đổi | Sau sửa đổi (1500x880 Dark) | Sau sửa đổi (1500x880 Light) |
|---|---|---|
| ![Before](screenshots/before/06_sql_visualizer.png) | ![After Dark](screenshots/after/1500x880_dark/06_sql_visualizer.png) | ![After Light](screenshots/after/1500x880_light/06_sql_visualizer.png) |

### 2.7. Hộp thoại: Save Design Dialog & Preview Data Changes Dialog
| Save Design Dialog (1500x880 Dark) | Preview Data Changes Dialog (1500x880 Dark) |
|---|---|
| ![Save Design](screenshots/after/1500x880_dark/07_save_design_dialog.png) | ![Preview Data Changes](screenshots/after/1500x880_dark/08_preview_data_changes_dialog.png) |

*(Toàn bộ ảnh chụp độ phân giải 1280x720 cho Dark và Light có thể xem tại thư mục `screenshots/after/1280x720_dark/` và `screenshots/after/1280x720_light/`)*

---

## 3. Kiểm tra mã màu Hardcode và Test Suite

### 3.1. Kết quả kiểm tra màu Hardcode (0 dòng vi phạm)
Lệnh kiểm tra CSS:
```powershell
git grep -E "#[0-9a-fA-F]{3,6}|rgba\(|Color\.web\(" -- "src/main/resources/css/*designer*" "src/main/resources/css/*data-editor*" "src/main/resources/css/*object-content-search*" "src/main/resources/css/*schema-compare*" "src/main/resources/css/*dependency-graph*" "src/main/resources/css/*sql-visualizer*"
```
**Kết quả:** 0 dòng (Exit code 1 - Clean).

Lệnh kiểm tra Java:
```powershell
git grep -E "Color\.web\(|#[0-9a-fA-F]{6}" -- "src/main/java/app/ui/designer/" "src/main/java/app/ui/dataeditor/" "src/main/java/app/ui/search/" "src/main/java/app/ui/compare/" "src/main/java/app/ui/dependency/" "src/main/java/app/ui/visualizer/"
```
**Kết quả:** 0 dòng (Exit code 1 - Clean).

### 3.2. Kết quả chạy bộ kiểm thử tự động
Lệnh thực thi:
```powershell
.\mvnw.cmd -o test "-Dtest=IconLiteralTest,Phase3ColorTokensTest,TableDesignerTabTest,TableDataEditorTest,ObjectContentSearchTabTest,SchemaCompareTabTest,DependencyGraphTabTest,SqlVisualizerTabTest,Phase3DialogsTest"
```
**Kết quả kiểm thử:**
- `app.ui.compare.SchemaCompareTabTest`: 1/1 passed (100%)
- `app.ui.dataeditor.TableDataEditorTest`: 7/7 passed (100%)
- `app.ui.dependency.DependencyGraphTabTest`: 1/1 passed (100%)
- `app.ui.designer.TableDesignerTabTest`: 7/7 passed (100%)
- `app.ui.dialogs.Phase3DialogsTest`: 4/4 passed (100%)
- `app.ui.IconLiteralTest`: 1/1 passed (100%)
- `app.ui.Phase3ColorTokensTest`: 1/1 passed (100%)
- `app.ui.search.ObjectContentSearchTabTest`: 1/1 passed (100%)
- `app.ui.visualizer.SqlVisualizerTabTest`: 2/2 passed (100%)
- **Tổng cộng:** **25/25 tests PASSED (100% BUILD SUCCESS)**.

---

## 4. Danh sách các bài kiểm thử mới đã bổ sung
1. `src/test/java/app/ui/IconLiteralTest.java`: Quét tất cả icon literals trong Phase 3 đảm bảo hợp lệ trong Feather icon pack.
2. `src/test/java/app/ui/Phase3ColorTokensTest.java`: Xác thực sự hiện diện đầy đủ của 30 token màu `-ide-*` trong cả `ide-vars.css` và `light-theme.css`.
3. `src/test/java/app/ui/components/WorkspaceComponentsTest.java`: Kiểm tra trạng thái hoạt động của `WorkspaceToolbar`, `PaneHeader`, `WorkspaceFooter`, `FilterChip`, `SegmentedToggle`.
4. `src/test/java/app/ui/components/ZoomableGraphPaneTest.java`: Kiểm tra khả năng zoom in, zoom out, reset zoom, pan và drag của canvas đồ thị dùng chung.
5. `src/test/java/app/ui/dialogs/Phase3DialogsTest.java`: Kiểm tra `SaveDesignDialog` (destructive và non-destructive) và `PreviewDataChangesDialog` (thực thi và chế độ xem chỉ đọc).
6. `src/test/java/app/ui/visualizer/SqlVisualizerTabTest.java`: Kiểm tra khởi tạo layout tab, hai chiều đồng bộ Monaco <-> Canvas node, và thanh điều khiển.

---

## 5. Các quyết định kỹ thuật và phương án thay thế
1. **G-DE-5 (Cột `#` trong Table Data Editor):**
   - Đặt `setReorderable(false)` và áp dụng `-ide-grid-header-bg` đồng bộ với TableView tiêu chuẩn của JavaFX, bổ sung vạch màu 3px ở mép trái dòng (xanh lá: thêm mới, vàng: sửa đổi, đỏ: xóa) thay vì tách 2 TableView đồng bộ cuộn (giảm thiểu độ trễ cuộn và flickering khi tải dữ liệu lớn).
2. **G-SC-5 (Tô khác biệt DDL trong Schema Compare):**
   - Áp dụng bố cục 2 Monaco Editor song song với tỷ lệ 50/50 đồng bộ vị trí dòng và highlight theo các khối thay đổi (tương thích tốt với kiến trúc WebView nhúng hiện tại, không gây xung đột nạp script Diff Editor đa luồng).
3. **G-SV-3 & G-SV-5 (Ẩn số liệu giả và tính toán bố cục SQL Visualizer):**
   - Thay vì hiển thị số liệu chi phí và số dòng giả lập, hệ thống ẩn hoàn toàn badge và trường thông tin khi giá trị là `null`.
   - Các giai đoạn (stages) không có toán tử được loại bỏ tự động khỏi sơ đồ và khoảng cách các cột giai đoạn được co giãn thích ứng theo bề rộng canvas.

---

## 6. Đối chiếu và Chuẩn hóa theo Quy tắc Đồng nhất Giao diện (07-ui-consistency-rules.md)

Thực hiện rà soát và chuẩn hóa toàn bộ các màn hình tính năng Phase 3 theo bộ quy tắc R-UI-10 đến R-UI-24 và hoàn tất các hạng mục hạ tầng H-1 đến H-6:

### 6.1. Hạng mục Hạ tầng (H-1 đến H-6)
- **H-1 (Typography theo Scene & Window):**
  - Đã tích hợp `UiMetrics.applyUiTypography(scene)` vào `ThemeService.applyThemeToScene(scene)` áp dụng font family và font size từ Settings lên root của mọi Scene (kể cả dialogs và modal stages).
  - Tự động áp dụng lại typography cho mọi Scene/Window đang mở khi người dùng thay đổi font size trong Settings.
- **H-2 & H-3 (Hệ thống Icon động & Refactor Component dùng chung sang `em`):**
  - Xây dựng `UiMetrics` và `Icons.of(literal, IconScale)` với các bậc tỷ lệ chuẩn: `SM` (base - 2), `MD` (base, mặc định), `LG` (base + 4), `XL` (base * 2.5).
  - Component dùng chung (`PaneHeader`, `WorkspaceToolbar`, `WorkspaceFooter`, `SegmentedToggle`, `FilterChip`, `EmptyState`) đã gỡ bỏ hoàn toàn `setMinHeight / setMaxHeight` số nguyên px, thay bằng padding `em` và tỷ lệ co giãn tự động theo font chữ.
  - Bổ sung các class chuẩn vào `components.css`: `.text-secondary`, `.text-title`, `.code-inline`, `.icon-muted`, `.icon-accent`, `.icon-success`, `.icon-warning`, `.icon-danger`.
  - Cập nhật `shape-flat.css` bổ sung tất cả selector Phase 3 để hỗ trợ chế độ phẳng (`appearance.panelStyle = flat`).
- **H-4 (Kiểm tra tự động - UiRulesLintTest):**
  - Xây dựng `app.ui.UiRulesLintTest` tự động quét mã nguồn Phase 3 Java và CSS.
  - Kết quả: **0 vi phạm** (0 mã màu hardcode, 0 font size px, 0 icon size cứng, 0 setStyle inline).
- **H-6 (Đồng bộ tài liệu Token):**
  - Cập nhật bảng chiều cao trong `03-design-tokens.md` ghi rõ giá trị tham chiếu tại 13px và trỏ sang R-UI-14.

### 6.2. Kết quả Rà soát và Chỉnh sửa từng Màn hình Phase 3
1. **Table Designer (`ui/designer/*`):**
   - Loại bỏ `small` variant trên các nút toolbar và grid actions; icon chuyển sang `Icons.of(...)`.
   - Bỏ lặp biểu tượng `+` trong nhãn ("+ Thêm cột" → "Thêm cột").
   - Gỡ bỏ `setStyle` inline ở ghi chú trigger sang `.text-secondary`.
   - Chuẩn hóa CSS cỡ chữ về `1em` và `0.85em`.
2. **Table Data Editor (`ui/dataeditor/*`):**
   - Gỡ bỏ 100% `setStyle` inline trong `ConstraintInspectorPane` và `CriteriaGrid`, chuyển sang các class CSS chuyên biệt (`.tde-section-card`, `.tde-fk-item`, `.criteria-header-title`, `.criteria-count-badge`, `.col-row-no`, `.cell-invalid`).
   - Chuyển toàn bộ icon tạo mới sang `Icons.of(...)` với scale SM/MD phù hợp.
   - Chuẩn hóa các cỡ font chữ phụ và subtitle trong `table-data-editor.css` về `0.85em`.
3. **Object Content Search (`ui/search/*`):**
   - Thay thế các `setStyle` inline ở error label, warning banner và cột bảng occurrences bằng `.search-regex-error`, `.search-warn-message`, `.col-index-center`, `.col-num-right`.
   - Gỡ bỏ min/max height 24px cố định trong CSS footer, chuyển sang padding `0.15em 0.75em`.
   - Icon tree cell và toolbar chuyển sang dùng `Icons.bind / Icons.of`.
4. **Schema Compare (`ui/compare/*`):**
   - Gỡ bỏ `setStyle` inline ở tổng kết khác biệt (`.compare-metrics-title`) và options dialog (`.options-dialog-content`).
   - Nút swap đảo chiều chuyển sang kích thước `2.15em` co giãn theo font chữ.
   - Chuẩn hóa toàn bộ badge diff và status label về `0.85em`.
5. **Dependency Graph (`ui/dependency/*`):**
   - Gỡ bỏ `setStyle` inline trên nhãn zoom, chuyển sang `.dep-zoom-label`.
   - Bỏ các cỡ chữ không chuẩn (`0.72em`, `0.78em`, `0.82em`, `0.88em`, `0.95em`) trong `dependency-graph.css`, đồng bộ về `1em` và `0.85em`.
   - Icon canvas và inspector chuyển sang dùng `Icons.of(...)`.
6. **SQL Visualizer (`ui/visualizer/*`):**
   - Gỡ bỏ hardcoded font size `12px` trong `sql-visualizer.css:73`, chuyển sang `1em`.
   - Gỡ bỏ `setStyle` inline trên canvas row count (`.flow-node-row-count`) và zoom label (`.qf-zoom-label`).
   - Chuẩn hóa toàn bộ cỡ chữ trong thẻ node và inspector về `1em` và `0.85em`.
7. **Hộp thoại SaveDesignDialog & PreviewDataChangesDialog:**
   - Chuyển các icon sang `Icons.of(...)` với scale chuẩn LG/MD.
   - Thừa hưởng typography tự động qua `ThemeService.applyThemeToScene`.

### 6.3. Kết quả Kiểm thử Tổng thể
- **Kiểm tra Lint Quy tắc UI (`UiRulesLintTest`):** 1/1 test passed (0 lỗi).
- **Bộ kiểm thử Phase 3:** 69/69 tests passed (100% BUILD SUCCESS).
- **Ảnh chụp nghiệm thu:** 32 ảnh cập nhật mới nhất ở 4 cấu hình: 1500x880 Dark/Light và 1280x720 Dark/Light tại `migrate_phase3/07_review_va_toi_uu/screenshots/after/`.


