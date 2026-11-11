# UI Consistency Rules
# Quy tắc đồng nhất giao diện (bắt buộc cho mọi màn hình)

> Áp dụng cho mọi giao diện tạo mới hoặc sửa: tab, panel, dialog, popup, menu.
> Bổ sung cho `02-design-principles.md` (R-UI-01), `03-design-tokens.md`, `04-component-rules.md`. Khi mâu thuẫn về **kích thước**, file này thắng: bảng "Chiều cao" trong `03` chỉ còn là giá trị tại cỡ chữ mặc định 13px (xem R-UI-14).
> Mã quy tắc `R-UI-10` … `R-UI-24` dùng để trích dẫn trong báo cáo và review.

## 0. Trước khi viết dòng code giao diện nào

1. **Hỏi người dùng: "Màn hình này có cần làm mockup trước không?"** (R-UI-16). Chờ trả lời.
2. Mở app, nhìn vùng tương đương đã có (context toolbar, Explorer, panel Results). Màn hình mới phải **cùng mật độ** với chúng: cùng chiều cao nút, cùng cỡ icon, cùng cỡ chữ tiêu đề.
3. Tìm component dùng chung trước khi tự dựng (R-UI-18).

---

## 1. Màu sắc theo theme

### R-UI-10 — Mọi màu đi theo theme đang chọn

- Chỉ dùng token `-ide-*` và `-color-*` (thứ tự ưu tiên trong `03-design-tokens.md`).
- **Cấm** trong Java và CSS: `#rrggbb`, `rgb()/rgba()`, `Color.web(...)`, `Color.RED`…, `derive()` trên màu ghi cứng. Ngoại lệ duy nhất: nút Close `#e81123`.
- Shape, đường nối, icon nhận màu qua style class (`-fx-stroke`, `-fx-fill`, `-fx-icon-color`), không gán màu bằng Java.
- Bóng đổ, nền mờ, viền focus cũng phải từ token. Thiếu token → thêm vào `ide-vars.css` theo thủ tục "Khi cần token mới" ở `03`, có giá trị cho cả theme sáng và tối.
- Vùng Monaco theo `MonacoThemes`; không đặt màu riêng cho editor nhúng.
- Đổi theme trong Settings phải có tác dụng **ngay** trên mọi cửa sổ, tab, dialog đang mở, không cần mở lại.
- Scene mới (dialog, cửa sổ phụ, popup) phải gọi `ThemeService.applyThemeToScene(scene)`. Stylesheet riêng của màn hình thêm **sau** lời gọi này và không được chứa màu ghi cứng.
- Ảnh xuất ra (PNG đồ thị…) lấy màu nền thực tế của vùng vẽ.

Kiểm tra: mỗi màn hình phải nhìn đúng ở ít nhất 1 theme tối, 1 theme sáng và 1 theme VS Code nhập vào.

---

## 2. Bốn vùng cỡ chữ

### R-UI-11 — Mỗi phần tử thuộc đúng một vùng, lấy font từ đúng một mục cài đặt

| Vùng | Mục trong Settings | Áp dụng cho | Nguồn trong code |
|---|---|---|---|
| **A. Trình soạn thảo** | Editor → font, cỡ chữ, line-height | Mọi nơi hiển thị code: editor SQL, xem DDL, preview script, xem XML/JSON, diff, đoạn SQL trong dialog xác nhận | `IdeSettings.getEditor()` → `MonacoEditorHost` / `EmbeddedMonaco.updateEditorSettings` |
| **B. Lưới kết quả** | Grid → font, cỡ chữ | Lưới kết quả truy vấn (mọi lưới `.results-table`) | `IdeSettings.getGrid()` → `ResultsController.applySettings` |
| **C. Thông báo SQL** | Messages → font, cỡ chữ | Chỉ tab Messages của kết quả truy vấn | `IdeSettings.getMessages()` → `ResultsController.applySettings` |
| **D. Giao diện** | Giao diện/Theme → font, cỡ chữ | **Mọi thứ còn lại**: menu, nút, nhãn, tiêu đề panel, tab, tiêu đề cột của các chức năng, cây Explorer, footer, dialog, tooltip, badge | `IdeSettings.getUiFontSize()/getUiFontFamily()` → `-fx-font-size` trên root của Scene |

Quy tắc:

1. **Vùng A — code luôn hiển thị bằng `EmbeddedMonaco`** (chỉ đọc nếu cần). Cấm dùng `TextArea`, `Label`, `Text` với font monospace tự đặt để hiện code nhiều dòng.
2. **Vùng B và C** không được dùng cho bất kỳ thứ gì khác ngoài đúng phạm vi ghi trong bảng.
3. **Vùng D — không ghi cỡ chữ tuyệt đối.** Root của Scene đã mang `-fx-font-size` của người dùng; phần tử con chỉ dùng đơn vị tương đối `em`:

   | Vai trò | Cỡ | Class |
   |---|---|---|
   | Chữ thường (nút, nhãn, ô nhập, dòng cây, ô lưới chức năng) | `1em` (không khai báo) | — |
   | Tiêu đề panel, tiêu đề cột | `1em`, đậm | `.pane-header-title` |
   | Chữ phụ, gợi ý, footer, badge | `0.85em` | `.text-secondary` |
   | Tiêu đề dialog, tiêu đề trạng thái rỗng | `1.15em`, đậm | `.text-title` |

   Không có cỡ nào khác. Không dùng `0.72em`, `0.78em`, `0.82em`, `0.9em`… tuỳ hứng.
4. **Cấm** `-fx-font-size: <số>px|pt` và `setFont(Font.font(.., <số>))` ở mọi nơi ngoài `base.css` và code áp cài đặt của 4 vùng. Cấm `-fx-font-family` ngoài các điểm đó.
5. Đoạn code **một dòng** nằm trong danh sách/cây/bảng (ví dụ snippet kết quả tìm kiếm) thuộc vùng D: dùng class `.code-inline` (font family của Editor, cỡ `0.92em`), không tự chọn font.
6. Đổi cỡ chữ trong Settings phải có tác dụng **ngay**, kể cả với tab và dialog đang mở. Component nào tự áp font phải đăng ký `SettingsService.addSettingsListener`.
7. Scene mới (dialog, cửa sổ phụ) phải nhận font vùng D giống cửa sổ chính (xem mục 9 — việc hạ tầng H-1).

8. **Đã chốt (người dùng duyệt 2026-11-11):** mọi lưới **ngoài** lưới kết quả truy vấn đều thuộc vùng **D** — kể cả lưới dữ liệu của Table Data Editor, lưới cột trong Table Designer, điều kiện lọc, danh sách chỉ mục. Không áp cài đặt Grid (vùng B) cho các lưới này.

---

## 3. Kích thước co giãn theo cỡ chữ

### R-UI-12 — Không cố định kích thước của thứ chứa chữ

Khi cỡ chữ đổi, nút, tiêu đề panel, dòng lưới, tab, ô nhập, badge phải tự lớn/nhỏ theo.

**Cấm:**

- `setPrefHeight/setMinHeight/setMaxHeight(<số>)` và `-fx-pref/min/max-height: <số>px` trên control có chữ (nút, label, header, toolbar, footer, tab, ô nhập, dòng).
- `setPrefWidth(<số>)` trên nút và nhãn. Bề rộng nút = nội dung + padding.
- `setFixedCellSize(<số ghi cứng>)`. Nếu cần fixed cell size để tối ưu, tính từ cỡ chữ: `Math.round(fontSize * 1.85)` và cập nhật khi cài đặt đổi.
- `setMaxWidth(<số>)` để cắt chữ thay cho bố cục đúng.

**Dùng:**

- Chiều cao sinh ra từ **padding + chữ**. Padding và khoảng cách ghi bằng `em`.
- Giới hạn bằng `em` khi cần: `-fx-min-height: 2.15em`.
- Bề rộng cột lưới: tính theo nội dung hoặc tỉ lệ; giá trị khởi tạo nhân với `fontSize / 13.0`.
- Số px tuyệt đối chỉ được dùng cho: viền (1px), bán kính bo (do `shape.css` quản), kích thước tối thiểu của cả panel/cửa sổ, vùng vẽ canvas.

### R-UI-14 — Mật độ chuẩn của IDE (để không vẽ to hơn phần còn lại)

Giá trị quy đổi tại cỡ chữ 13px chỉ để đối chiếu bằng mắt; trong code luôn ghi `em`.

| Thành phần | Quy tắc | ≈ tại 13px |
|---|---|---|
| Nút có chữ | padding `0.25em 0.75em`; không dùng class cỡ `large`, không tự tăng padding | cao 24 |
| Nút chỉ có icon | padding `0.3em 0.45em` | 24 × 24 |
| Ô nhập, ComboBox, Spinner | cùng chiều cao với nút đứng cạnh | cao 24 |
| Toolbar của tab/panel | padding `0.3em 0.6em`, khoảng cách `0.45em`; **một hàng** | cao 32 |
| Tiêu đề panel (`PaneHeader`) | padding `0.3em 0.75em`, min-height `2.15em` | cao 28 |
| Thanh sub-tab / segmented | nút con padding `0.25em 0.75em`; cả thanh không cao hơn toolbar | cao ≤ 28 |
| Dòng cây, dòng danh sách, dòng lưới chức năng | `1.7em` – `1.85em` | 22 – 24 |
| Footer / status của tab | padding `0.15em 0.75em`, chữ `0.85em` | cao 22 |
| Khoảng cách giữa các nhóm | `0.3em`, `0.45em`, `0.6em`, `0.9em`, `1.2em` | 4, 6, 8, 12, 16 |

Quy tắc kèm theo:

1. Mọi control trên **cùng một hàng** có cùng chiều cao (nút, combo, ô nhập, nút icon, segmented).
2. Không trộn biến thể cỡ của AtlantaFX (`small`, `large`) trong cùng màn hình. Mặc định không dùng biến thể cỡ nào.
3. Nút icon-kèm-chữ: icon và chữ nằm trên **một dòng**. Cấm đặt icon phía trên chữ làm nút cao gấp đôi.
4. Mỗi toolbar tối đa **một** nút primary (nền accent đặc). Nút bật/tắt đang bật dùng nền `-color-accent-subtle`, không dùng nền accent đặc.
5. Màn hình mới đặt cạnh context toolbar và panel Results của IDE: nút và tiêu đề panel không được cao hơn hay chữ to hơn các vùng đó.

---

## 4. Vùng hiển thị và resize

### R-UI-13 — Nhiều vùng thì kéo đổi kích thước được

1. Màn hình có từ 2 vùng nội dung trở lên (danh sách + chi tiết, lưới + preview, canvas + inspector, trên + dưới) → đặt trong `SplitPane`. Cấm ghép bằng `HBox/VBox/BorderPane` với kích thước cố định.
2. Mỗi vùng có `minWidth`/`minHeight` hợp lý để không bị kéo về 0, trừ vùng được thiết kế để thu gọn hẳn.
3. Vùng ẩn/hiện được (inspector, preview, drawer): khi hiện lại phải về đúng vị trí divider trước đó.
4. Vị trí divider được nhớ qua `LayoutService` theo khoá của màn hình; mở lại tab/app giữ nguyên.
5. Divider dùng style chung của app (hover đổi màu `-ide-sash-hover`), không tự vẽ.
6. Cột của mọi lưới kéo đổi bề rộng được; cột cuối không để trống kéo dài vô nghĩa.
7. Dialog có nội dung dài (danh sách, script) phải resize được và nhớ kích thước; dialog xác nhận ngắn thì không cần.
8. Khi cửa sổ hẹp lại: toolbar đưa nút ưu tiên thấp vào menu "…" (`WorkspaceToolbar`), không cắt chữ thành "…", không để nội dung tràn ra ngoài mép. Kiểm ở bề rộng 960px (tối thiểu của cửa sổ chính).

---

## 5. Icon

### R-UI-15 — Chỉ dùng icon thật, kích thước theo cài đặt

**Không vẽ icon bằng chữ:**

- Cấm ký tự, emoji, ký hiệu Unicode làm icon: `+`, `-`, `×`, `✓`, `⚠`, `▶`, `◀`, `●`, `🔑`, `🔒`, `✏`, `✱`, `🗙`, `Δ`, `⏮`…
- Cấm lặp icon trong nhãn: nút đã có icon cộng thì nhãn là `Thêm cột`, không phải `+ Thêm cột`.
- Dùng `FontIcon` (Ikonli): ưu tiên bộ Feather `fth-*`, thiếu thì Material Design `mdi2*`. Một khái niệm = một icon trên toàn app (bảng: `fth-grid`; view: `fth-eye`; khoá chính: `fth-key`…); tra trong `DatabaseTreeCell` và các enum `*NodeType` trước khi chọn icon mới.
- Literal mới phải tồn tại — test `IconLiteralTest` phải pass. (`fth-table` không tồn tại.)
- Dấu chevron của ComboBox/menu theo R-UI-01.
- Nút chỉ có icon bắt buộc có tooltip và tên trợ năng.

**Kích thước theo "Giao diện/Theme → Kích thước biểu tượng":**

- Nguồn: `IdeSettings.getAppearance().getIconSize()` (mặc định 13).
- **Cấm** `icon.setIconSize(<số>)` và `-fx-icon-size: <số>px` trong code màn hình.
- Tạo icon qua helper dùng chung (việc hạ tầng H-2), chọn theo bậc:

  | Bậc | Kích thước | Dùng cho |
  |---|---|---|
  | `SM` | base − 2 | badge, chevron, icon trong ô lưới, footer |
  | `MD` (mặc định) | base | nút, toolbar, tiêu đề panel, dòng cây, menu, tab |
  | `LG` | base + 4 | tiêu đề dialog |
  | `XL` | base × 2.5 | trạng thái rỗng (`EmptyState`) |

- Đổi cài đặt phải có tác dụng ngay trên mọi icon đang hiển thị.
- Vùng chứa icon (nút icon, ô trong cây) co giãn theo icon, không cố định (R-UI-12).
- Màu icon từ token (R-UI-10); mặc định thừa hưởng `-color-fg-default`, trạng thái dùng class `.icon-muted/.icon-accent/.icon-success/.icon-warning/.icon-danger`.

---

## 6. Mockup

### R-UI-16 — Hỏi về mockup trước khi làm, có mockup thì theo mockup

1. Trước khi làm giao diện mới hoặc đổi bố cục màn hình đã có: **hỏi người dùng có cần mockup không** và dừng chờ trả lời. Im lặng không phải là đồng ý.
2. Nếu cần: làm mockup HTML tĩnh đặt cạnh tài liệu của tính năng (`…/mockup.html`), kèm ghi chú những gì mockup **không** thể hiện (trạng thái lỗi, rỗng, đang tải). Chờ duyệt rồi mới code.
3. Đã có mockup được duyệt → **tuân thủ** về: bố cục vùng, thứ tự và nhóm control, nội dung nhãn, trạng thái, luồng thao tác.
4. Mockup **không** quyết định: mã màu, cỡ chữ px, chiều cao px, cỡ icon. Những thứ đó theo file này (token, `em`, cài đặt người dùng). Mockup HTML thường vẽ to hơn IDE — không sao chép kích thước từ mockup.
5. Mockup mâu thuẫn với quy tắc, hoặc không làm đúng được bằng JavaFX → dừng lại hỏi, không tự chọn.
6. Xong việc: chụp ảnh màn hình thật đặt cạnh mockup trong báo cáo và liệt kê mọi điểm khác.
7. Sửa nhỏ không đổi bố cục (đổi nhãn, sửa lỗi căn lề) không cần hỏi mockup.

---

## 7. Quy tắc bổ sung (đề xuất thêm)

### R-UI-17 — Áp dụng ngay, cho mọi cửa sổ
Theme, panelStyle, 4 vùng cỡ chữ, cỡ icon: thay đổi có tác dụng ngay trên cửa sổ chính, mọi tab đang mở, dialog đang mở, cửa sổ phụ. Không yêu cầu khởi động lại, không dựng lại lưới dữ liệu.

### R-UI-18 — Dùng component chung, không tự dựng lại
| Nhu cầu | Dùng |
|---|---|
| Toolbar của tab/panel | `WorkspaceToolbar` |
| Tiêu đề panel | `PaneHeader` |
| Footer / status của tab | `WorkspaceFooter` |
| Nút, nút icon | `AppButton`, `IconButton` |
| Chọn một trong nhiều (sub-tab, chế độ xem) | `SegmentedToggle` |
| Chip bật/tắt | `FilterChip` |
| Ô tìm kiếm | `SearchField` |
| Nhãn trạng thái, bộ đếm | `Badge`, `StatusIndicator` |
| Rỗng, lỗi, đang tải | `EmptyState`, `LoadingOverlay` |
| Dialog | `DialogChrome` |
| Hiển thị code | `EmbeddedMonaco` |
| Canvas đồ thị | `ZoomableGraphPane` |

Cấm `new Button(...)` rồi tự gắn style class kích thước; cấm tự dựng `HBox` làm header/toolbar/footer. Component chung thiếu khả năng → mở rộng component đó, không sao chép ra bản riêng.

### R-UI-19 — Style nằm trong CSS, không nằm trong Java
Cấm `setStyle("…")` cho màu, cỡ chữ, độ đậm, padding, viền. Dùng style class. `setStyle` chỉ được dùng ở code áp cài đặt font của 4 vùng. Trong `updateItem(...)` của cell không `getStyleClass().add(...)` lặp lại — thêm một lần hoặc kiểm `contains`; trạng thái dùng `PseudoClass`.

### R-UI-20 — Nhãn và ngôn ngữ
Một ngôn ngữ cho nhãn: tiếng Việt, giữ thuật ngữ SQL (JOIN, WHERE, DDL, PK, Trigger). Viết thường, hoa chữ đầu; không VIẾT HOA cả cụm; không song ngữ kiểu `Dòng (Line)`. Phím tắt và gợi ý thao tác để trong tooltip, không nhồi vào tiêu đề. Chữ dài bị cắt phải có tooltip đủ nội dung.

### R-UI-21 — Đủ trạng thái
Mỗi vùng dữ liệu có đủ: đang tải (`LoadingOverlay`, có Huỷ nếu > 1 giây), rỗng (`EmptyState`), lỗi (`EmptyState` biến thể lỗi + Thử lại + chi tiết thu gọn), chỉ đọc/bị khoá. Control có đủ hover, focus nhìn thấy, disabled, selected — lấy từ theme, không tự tô. Không truyền đạt trạng thái chỉ bằng màu.

### R-UI-22 — Không dữ liệu giả không dán nhãn
Màn hình còn chạy dữ liệu mẫu phải hiện nhãn `DỮ LIỆU MẪU` trên footer. Không hiển thị số liệu bịa (số dòng, chi phí, phần trăm).

### R-UI-23 — Bo góc
Theo R-UI-01. Cấm `-fx-border-radius` / `-fx-background-radius` ghi cứng trong CSS của màn hình; nếu buộc phải thêm thì thêm luật phẳng tương ứng trong `shape-flat.css`.

### R-UI-24 — Bàn phím
Mọi thao tác chính có phím tắt hoặc đi được bằng Tab. Phím tắt của tab chỉ xử lý khi focus không nằm trong ô nhập văn bản (không bắt `Delete`, `Ctrl+N`… ở event filter cấp tab khi người dùng đang gõ). Không trùng phím tắt toàn cục của IDE.

---

## 8. Nghiệm thu (bắt buộc trước khi báo xong)

### 8.1. Ma trận kiểm tra bằng ảnh chụp

Chụp bằng `UiSnapshotHarness` (hoặc app thật) và đính vào báo cáo:

| Biến | Giá trị |
|---|---|
| Theme | 1 tối, 1 sáng |
| panelStyle | `rounded`, `flat` |
| Cỡ chữ giao diện | 11, 13, 18 |
| Cỡ icon | 12, 13, 20 |
| Bề rộng | 960, 1280, 1500 |

Không cần chụp đủ mọi tổ hợp; tối thiểu: (tối, rounded, 13, 13, 1280), (sáng, flat, 13, 13, 1280), (tối, rounded, 18, 20, 1280), (tối, rounded, 11, 12, 960).

Đạt khi: không chữ nào bị cắt hoặc tràn; không control nào giữ nguyên kích thước khi chữ đổi; icon đổi cỡ đồng loạt; không vùng nào giữ màu của theme cũ; ở 960px không mất control nào (vào menu "…" là được).

### 8.2. Kiểm tra tự động

Test `UiRulesLintTest` (việc hạ tầng H-4) quét `src/main/java/app/ui/**` và `src/main/resources/css/**`, fail khi gặp (trừ danh sách miễn trừ ghi trong chính test):

| Mẫu | Quy tắc |
|---|---|
| `#[0-9a-fA-F]{3,8}\b`, `rgba?\(`, `Color\.web\(`, `Color\.[A-Z_]+\b` | R-UI-10 |
| `-fx-font-size:\s*[\d.]+(px\|pt)`, `Font\.font\(` | R-UI-11 |
| `set(Pref\|Min\|Max)Height\(\s*\d`, `-fx-(pref\|min\|max)-height:\s*\d+(px)?;`, `setFixedCellSize\(\s*\d` | R-UI-12 |
| `setIconSize\(\s*\d`, `-fx-icon-size:\s*\d` | R-UI-15 |
| ký tự emoji / ký hiệu trong chuỗi nhãn (`[←-⯿\u{1F300}-\u{1FAFF}]`) | R-UI-15 |
| `\.setStyle\(` | R-UI-19 |
| `new TextArea\(` trong gói hiển thị code | R-UI-11 |

Code cũ vi phạm: đưa vào danh sách miễn trừ theo file, **không được thêm file mới vào danh sách**, và mỗi lần sửa một file thì gỡ file đó khỏi danh sách.

### 8.3. Báo cáo

Ghi rõ: đã hỏi mockup chưa và câu trả lời; mã quy tắc nào chưa đạt và vì sao; ảnh theo 8.1; kết quả `UiRulesLintTest`.

---

## 9. Hiện trạng và việc hạ tầng phải làm trước

Rà soát code ngày 2026-11-11 cho thấy các quy tắc trên **chưa thể đạt** nếu thiếu các phần sau. Làm xong H-1 → H-4 rồi mới áp quy tắc cho màn hình.

| ID | Hiện trạng | Việc cần làm |
|---|---|---|
| H-1 | Font giao diện chỉ được gán inline lên `mainWindowRoot` (`MainController.applyUiTypography`). Dialog và cửa sổ phụ là Scene riêng nên **chưa chắc** nhận cỡ chữ này (cần kiểm tra). | Chuyển vào `ThemeService` (hoặc service `UiMetrics`): áp `-fx-font-family/-fx-font-size` lên root của **mọi** Scene trong `applyThemeToScene`, và áp lại cho mọi cửa sổ đang mở khi cài đặt đổi. |
| H-2 | "Kích thước biểu tượng" hiện chỉ tác dụng lên cây Explorer (`DatabaseTreeCell.setIconSize`). Phần còn lại ghi cứng: > 50 chỗ `-fx-icon-size: Npx` trong CSS và nhiều `setIconSize(N)` trong Java. | Tạo `app.ui.components.Icons`: `Icons.of(literal)`, `Icons.of(literal, IconScale)` trả `FontIcon` có `iconSize` bind vào thuộc tính dùng chung `UiMetrics.iconSizeProperty()` (cập nhật từ `SettingsService`). `AppButton`, `IconButton`, `PaneHeader`, `EmptyState`, `Badge`, `SegmentedToggle`, `FilterChip` chuyển sang dùng nó. Gỡ dần `-fx-icon-size` trong CSS (CSS thắng giá trị gán bằng code nên phải gỡ thì bind mới có tác dụng — cần kiểm chứng trên Ikonli đang dùng). |
| H-3 | Kích thước ghi cứng tràn lan: 534 chỗ (`-fx-font-size` px, `set*Height(N)`, `-fx-*-height`, `setIconSize`) trong 64 file. Ngay component chung cũng cố định: `PaneHeader` đặt min = pref = max = 28 và `setIconSize(13)`; `WorkspaceToolbar`, `WorkspaceFooter`, `SegmentedToggle`, `FilterChip` có chiều cao số; `ResultsController` tính dòng lưới `max(24, size*2)` (đúng hướng, giữ). | Sửa **component chung trước** theo bảng R-UI-14 (padding `em`, bỏ `setMaxHeight`), rồi mới tới từng màn hình. Thêm vào `components.css` các class `.text-secondary`, `.text-title`, `.code-inline`, `.icon-*`. Đổi `px` → `em` trong `components.css`, `layout.css`, `dialogs.css` theo từng file, mỗi file một commit có ảnh trước/sau. |
| H-4 | Chưa có kiểm tra tự động nên lỗi lặp lại mỗi lần tạo màn hình mới. | Viết `UiRulesLintTest` (mục 8.2) với danh sách miễn trừ ban đầu = các file đang vi phạm. |
| H-5 | `EmbeddedMonaco` áp cài đặt Editor lúc khởi tạo; **chưa xác nhận** nó cập nhật khi người dùng đổi cài đặt lúc đang mở. | Kiểm tra; nếu chưa thì đăng ký listener và gọi `updateEditorSettings`. |
| H-6 | Bảng "Chiều cao" trong `03-design-tokens.md` ghi số px cố định (toolbar 32, panel header 28, control 24, dòng cây 22). | Sửa `03` để ghi rõ đó là giá trị tại 13px và trỏ sang R-UI-14. |

Lưu ý kỹ thuật: JavaFX CSS **không** hỗ trợ biến tra cứu cho kích thước (chỉ cho màu), nên không thể làm `-ide-icon-size` như một token CSS. Kích thước theo cài đặt phải đi bằng `em` (thừa hưởng từ `-fx-font-size` của root) hoặc bind thuộc tính trong Java.
