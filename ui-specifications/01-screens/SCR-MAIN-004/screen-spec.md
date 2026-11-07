# Result panel — Screen Specification
# Đặc tả màn hình: Bảng kết quả

- Screen ID: `SCR-MAIN-004`
- Source: `templates/index.html#ide-result-panel`, `static/js/query.js`, `grid-result.js`, `execution-plan-tab.js`, `structured-data-viewer.js` (theo screen-inventory.json)
- Target: `fxml/results.fxml`, `ResultsController`, `ui/results/{ColumnFilterPopup,DesktopGridExportService,FrozenColumnSupport,GridDataExportUtils,GridFilterState}`
- Status: `implemented_mock`
- Evidence confidence: observed (chỉ tab Results của nguồn) + code_verified (đích)
- Last inspected: `2026-11-05`
- Loại đặc tả: **hồi tố** (UI đích đã được dựng trước khi có đặc tả này). Chưa qua Gate B; cần bạn duyệt.

## 1. Overview / Tổng quan

- Purpose / Mục đích: Hiển thị kết quả truy vấn (lưới, nhiều result set), thông báo SQL, nhật ký thực thi, execution plan, và một terminal mô phỏng.
- Entry point / Cách mở: Hiện dưới editor khi tab SQL đã kết nối; chạy câu lệnh (F5) tự mở nếu bật tùy chọn "Auto-show Results" trong Settings. Ctrl+Alt+R phóng to/thu lại.
- Parent screen / Màn hình cha: `SCR-MAIN-001`
- User roles or context / Ngữ cảnh: Gắn với tab SQL đang chọn; kết quả thuộc phiên của tab.
- Related screens / Liên quan: SCR-MAIN-003 (nguồn câu lệnh), SCR-MAIN-005 (tóm tắt lần chạy cuối), SCR-SET-001 (nhóm Grid, Messages), DLG-014 (lớp phủ đang tải)

## 2. Visual reference / Tham chiếu hình ảnh

Nguồn: `00-application/screenshots/source/main-full-1456x816.jpg` (chỉ thấy tab Results: 12 dòng · 101 ms · 8.6 KB, sparkline). Đích: chưa có ảnh trong `01-screens/SCR-MAIN-004/screenshots/`. Các tab Messages/Errors/Logs/Plan của nguồn **chưa được quan sát**.

## 3. Layout / Bố cục

`VBox` (minHeight 150, prefHeight 260) chứa một `TabPane` không cho đóng tab với 5 tab:

1. **Results** — thanh mini (ô lọc, thống kê, So sánh [hoãn, tắt], menu Export, Phóng to, Pop-out) · `StackPane` (lưới đơn `TableView` / nhiều lưới xếp dọc `multiGridScroll` / trạng thái rỗng / lớp phủ tải) · footer thống kê (dòng, thời gian, dung lượng, kết nối, "Trend", sao chép JSON).
2. **Messages** — thanh công cụ (xóa, sao chép, xuống dòng) + `TextArea` chỉ đọc (PRINT/RAISERROR/số dòng).
3. **Timeline** — thanh công cụ (xóa, sao chép) + `TableView` 4 cột Time · Level · Message · Duration.
4. **Execution Plan** — thanh (Est Plan, Act Plan, Raw Text, sao chép, badge dialect/loại, tổng cost, số toán tử, thời gian) + vùng nội dung: trạng thái rỗng / đang tạo / `SplitPane` 62/38 (cây toán tử + Operator Inspector) / văn bản thô.
5. **Terminal** — `TextArea` + dòng nhập với prompt cố định `postgres=#`; nội dung là **mô phỏng** ("PostgreSQL 16.2 Interactive SQL Terminal (Mock)").

Tiêu đề tab Results và Timeline kèm số lượng động (ví dụ "Results (50)", "Timeline (4)" là giá trị mẫu trong FXML).

## 4. Regions / Các vùng

| Region ID | Tên | Mục đích | Vị trí | Kích thước |
|---|---|---|---|---|
| `REG-RES-001` | Results panel | Khung chứa các tab kết quả | bottom | width=fill, height=260px, min_height=150px, resizable=True, flexible=True |
| `REG-RES-002` | Tab bar | 5 tab, không đóng được | top | width=fill, height=auto, resizable=False, flexible=True |
| `REG-RES-003` | Results mini toolbar | Lọc, thống kê, Export, Maximize, Pop-out | top | width=fill, height=auto, resizable=False, flexible=True |
| `REG-RES-004` | Grid stack | Lưới đơn / nhiều lưới / rỗng / đang tải | center | width=fill, height=fill, resizable=True, flexible=True |
| `REG-RES-005` | Execution stats footer | Dòng · thời gian · dung lượng · kết nối · trend · sao chép | bottom | width=fill, height=auto, resizable=False, flexible=True |
| `REG-RES-006` | Messages toolbar + area | Thông báo SQL dạng văn bản | center | width=fill, height=fill, resizable=False, flexible=True |
| `REG-RES-007` | Timeline toolbar + table | Nhật ký thực thi theo thời gian | center | width=fill, height=fill, resizable=False, flexible=True |
| `REG-RES-008` | Plan toolbar | Lấy plan, đổi chế độ xem, badge | top | width=fill, height=auto, resizable=False, flexible=True |
| `REG-RES-009` | Plan content | Rỗng / đang tạo / cây+inspector / thô | center | width=fill, height=fill, resizable=False, flexible=True |
| `REG-RES-010` | Terminal | Output + dòng nhập | center | width=fill, height=fill, resizable=False, flexible=True |

## 5. Components and buttons / Component và button

Chi tiết đầy đủ trong `components.json`.

| ID | Tên | Loại | Nhãn | Phím tắt | Handler | Tin cậy |
|---|---|---|---|---|---|---|
| `CMP-RES-001` | Results tab | tab | Results (n) | unknown | `unknown` | code_verified |
| `CMP-RES-002` | Messages tab | tab | Messages | unknown | `unknown` | code_verified |
| `CMP-RES-003` | Timeline tab | tab | Timeline (n) | unknown | `unknown` | code_verified |
| `CMP-RES-004` | Execution Plan tab | tab | Execution Plan | unknown | `unknown` | code_verified |
| `CMP-RES-005` | Terminal tab | tab | Terminal | unknown | `unknown` | code_verified |
| `CMP-RES-006` | Results filter | text_field | unknown | Ctrl+F (qua menu ngữ cảnh) | `ResultsController#focusFilterField` | code_verified |
| `CMP-RES-007` | Results stats | label | 50 rows retrieved (42 ms) | unknown | `unknown` | code_verified |
| `BTN-RES-008` | Compare results | button | So sánh (chưa hỗ trợ) | unknown | `unknown` | code_verified |
| `BTN-RES-009` | Export menu | menu_button | Export | unknown | `handleExportCsv/Json/Markdown/OpenInExcel/SaveResultsAs` | code_verified |
| `BTN-RES-010` | Maximize / Restore | icon_button | unknown | Ctrl+Alt+R | `handleToggleMaximize` | code_verified |
| `BTN-RES-011` | Pop out | icon_button | unknown | unknown | `handleUndock` | code_verified |
| `CMP-RES-012` | Results grid | table | unknown | unknown | `ResultsController` | code_verified |
| `CMP-RES-013` | Multi-result stack | scroll_pane | unknown | unknown | `ResultsController` | code_verified |
| `CMP-RES-014` | Grid context menu | menu | unknown | unknown | `ResultsController` | code_verified |
| `CMP-RES-015` | Empty state | empty_state | No query executed yet | unknown | `unknown` | code_verified |
| `CMP-RES-016` | Loading overlay | overlay | Executing query and fetching rows... | unknown | `ResultsController#showLoading` | code_verified |
| `CMP-RES-017` | Footer rows | label | 0 dòng | unknown | `unknown` | code_verified |
| `CMP-RES-018` | Footer duration | label | 0 ms | unknown | `unknown` | code_verified |
| `CMP-RES-019` | Footer size | label | 0 B | unknown | `unknown` | code_verified |
| `CMP-RES-020` | Footer connection | label | #conn · default · (dbo) | unknown | `unknown` | code_verified |
| `CMP-RES-021` | Footer trend | label | Trend: | unknown | `unknown` | code_verified |
| `BTN-RES-022` | Copy execution metrics | icon_button | unknown | unknown | `handleCopyExecutionStats` | code_verified |
| `BTN-RES-023` | Clear SQL messages | icon_button | unknown | unknown | `handleClearSqlMessages` | code_verified |
| `BTN-RES-024` | Copy SQL messages | icon_button | unknown | unknown | `handleCopySqlMessages` | code_verified |
| `BTN-RES-025` | Word wrap toggle | toggle_button | unknown | unknown | `handleToggleSqlMessagesWrap` | code_verified |
| `CMP-RES-026` | SQL messages area | text_area | unknown | unknown | `appendSqlMessage` | code_verified |
| `BTN-RES-027` | Clear timeline | icon_button | unknown | unknown | `handleClearMessages` | code_verified |
| `BTN-RES-028` | Copy timeline | icon_button | unknown | unknown | `handleCopyAllMessages` | code_verified |
| `CMP-RES-029` | Timeline table | table | unknown | unknown | `unknown` | code_verified |
| `BTN-RES-030` | Est Plan | button | Est Plan | Ctrl+L | `handleFetchEstimatedPlan` | code_verified |
| `BTN-RES-031` | Act Plan | button | Act Plan | Ctrl+M | `handleFetchActualPlan` | code_verified |
| `BTN-RES-032` | Plan view mode | toggle_button | Raw Text | unknown | `handleTogglePlanViewMode` | code_verified |
| `BTN-RES-033` | Copy plan | icon_button | unknown | unknown | `handleCopyExecutionPlan` | code_verified |
| `CMP-RES-034` | Plan badges + metrics | label | SQLSERVER / ESTIMATED / cost / operators / duration | unknown | `unknown` | code_verified |
| `CMP-RES-035` | Plan empty state | empty_state | Chưa có Execution Plan | unknown | `unknown` | code_verified |
| `CMP-RES-036` | Plan loading | overlay | Đang tạo Execution Plan... | unknown | `unknown` | code_verified |
| `CMP-RES-037` | Plan operator tree | tree | Operator Hierarchy & Cost Breakdown | unknown | `unknown` | code_verified |
| `CMP-RES-038` | Operator Inspector | table | Operator Inspector | unknown | `unknown` | code_verified |
| `CMP-RES-039` | Plan raw text | text_area | unknown | unknown | `unknown` | code_verified |
| `CMP-RES-040` | Terminal output | text_area | unknown | unknown | `ResultsController#setupTerminal` | code_verified |
| `CMP-RES-041` | Terminal input | text_field | postgres=# | Enter | `handleTerminalCommand` | code_verified |

## 6. Interactions / Tương tác

Chi tiết trong `interactions.json`.

| ID | Component | Trigger | Kết quả | Xác nhận |
|---|---|---|---|---|
| `INT-RES-001` | `CMP-RES-006` | key | Lưới chỉ hiện dòng khớp; thống kê cập nhật | no |
| `INT-RES-002` | `BTN-RES-009` | click | Mở hộp chọn file rồi ghi | hộp chọn file |
| `INT-RES-003` | `BTN-RES-009` | click | Tạo file tạm và mở bằng ứng dụng mặc định | no |
| `INT-RES-004` | `BTN-RES-010` | click | LayoutService đổi preset RESULTS_MAXIMIZED | no |
| `INT-RES-005` | `BTN-RES-011` | click | Mở cửa sổ riêng (PoC) | no |
| `INT-RES-006` | `CMP-RES-012` | right_click | Hiện menu theo cột đang chọn | no |
| `INT-RES-007` | `CMP-RES-012` | click | Chọn ô/vùng/toàn cột | no |
| `INT-RES-008` | `CMP-RES-012` | click | Sắp xếp hoặc mở bộ lọc cột (ColumnFilterPopup) | no |
| `INT-RES-009` | `CMP-RES-014` | click | Cột dính trái khi cuộn ngang (FrozenColumnSupport) | no |
| `INT-RES-010` | `CMP-RES-014` | click | Mở hộp xem giá trị | no |
| `INT-RES-011` | `CMP-RES-014` | click | Sinh INSERT (không chạy) | no |
| `INT-RES-012` | `CMP-RES-014` | click | Mở hộp in | no |
| `INT-RES-013` | `BTN-RES-022` | click | Clipboard có rows/durationMs/columns/timestamp; toast xác nhận | no |
| `INT-RES-014` | `BTN-RES-023` | click | TextArea rỗng | không |
| `INT-RES-015` | `BTN-RES-025` | click | TextArea.wrapText đổi | no |
| `INT-RES-016` | `BTN-RES-030` | click | Hiện loading rồi cây/Raw text; không chạy truy vấn | no |
| `INT-RES-017` | `BTN-RES-031` | click | Chạy câu lệnh để đo (chỉ khi câu lệnh chỉ đọc) | chưa có xác nhận PROD (kế hoạch D5) |
| `INT-RES-018` | `BTN-RES-032` | click | Đổi khối hiển thị | no |
| `INT-RES-019` | `CMP-RES-037` | selection_changed | Inspector hiện thuộc tính toán tử | no |
| `INT-RES-020` | `CMP-RES-041` | key | Thêm dòng vào output; lịch sử lệnh | no |
| `INT-RES-021` | `CMP-RES-016` | click | Hiện "Đang hủy…" rồi gọi cancelAction | no |

## 7. States / Trạng thái

Chi tiết trong `states.json`.

| ID | Trạng thái | Mô tả | Điều kiện vào |
|---|---|---|---|
| `STA-RES-001` | empty | Biểu tượng lưới + "No query executed yet" | chưa chạy câu nào |
| `STA-RES-002` | loading | Spinner + "Executing query and fetching rows..." | đang chạy |
| `STA-RES-003` | normal | Lưới một result set, cột # cố định | một result set |
| `STA-RES-004` | normal | Nhiều lưới xếp dọc, mỗi lưới có thanh tiêu đề thu gọn | từ hai result set trở lên (chế độ Danh sách) |
| `STA-RES-005` | selected | Mục Freeze đổi theo cột đã ghim | chuột phải trên cột |
| `STA-RES-006` | disabled | Nút mờ "(chưa hỗ trợ)" | luôn (hoãn) |
| `STA-RES-007` | empty | "Chưa có Execution Plan" + 2 nút | chưa lấy plan |
| `STA-RES-008` | loading | Spinner "Đang tạo Execution Plan..." | đang lấy plan |
| `STA-RES-009` | normal | Cây + Inspector (62/38) | có plan, chế độ cây |
| `STA-RES-010` | normal | TextArea chỉ đọc, xuống dòng | chế độ Raw Text |
| `STA-RES-011` | selected | Panel được phóng to (RESULTS_MAXIMIZED) hoặc thu về mặc định | toggle Ctrl+Alt+R |
| `STA-RES-012` | error | Chưa xác định cách hiện lỗi truy vấn (nguồn có tab Errors) | unknown |

## 8. JavaFX design proposal / Đề xuất JavaFX

- Proposed layout: Giữ như đã dựng (5 tab, mini toolbar, footer).
- Standard JavaFX controls: TabPane, TableView, TreeView, SplitPane, TextArea, MenuButton, ContextMenu.
- Custom components: Hỗ trợ lưới: FrozenColumnSupport, ColumnFilterPopup, GridFilterState; LoadingOverlay.
- Retained embedded components: Không có.
- Design improvements: Đề xuất (chưa làm): thống nhất ngôn ngữ nhãn; quyết định số phận tab Terminal; thêm chỗ hiện lỗi truy vấn rõ ràng.
- Compatibility/performance considerations: `TableView` với 100k dòng là rủi ro (D4); mỗi lúc chỉ dựng bảng của tab đang mở ở chế độ Tab (grid.resultLayout).

## 9. Known gaps and questions / Điểm chưa rõ

| # | Điểm chưa rõ | Ảnh hưởng | Chặn triển khai? |
|---|---|---|---|
| 1 | Nguồn chưa quan sát 4/5 tab | Không đối chiếu được hành vi | Không |
| 2 | Tab Errors của nguồn chưa có tương đương rõ ràng | Có thể mất chức năng | Không |
| 3 | Tab Terminal chưa có phê duyệt | Phạm vi | Không |
| 4 | T7: sash editor/results chưa kéo được; T11: cột # chưa cố định khi nhiều lưới | Lỗi giao diện đang mở | Không |

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.

> Các ô trên cố ý để trống: đặc tả hồi tố chưa được bạn duyệt, và chưa so ảnh nguồn/đích cho màn hình này.
