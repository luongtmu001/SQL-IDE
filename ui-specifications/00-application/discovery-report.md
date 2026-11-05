# Discovery Report — Workflow 01
# Báo cáo khảo sát dự án — Bước 01

| | |
|---|---|
| Ngày | 2026-11-04 |
| Ứng dụng nguồn | luoBTool / LLTool IDE v1.3.1 (Python 3.14 + pywebview/WebView2) |
| Dự án đích | SQL-IDE (JavaFX 21) |
| Trạng thái Gate 1 | `PASS_WITH_KNOWN_GAPS` — thiếu ảnh chụp màn hình thực tế |
| Backend | **Không thay đổi** bất kỳ mã nguồn nào (cả Python lẫn Java) |

---

## 1. Phạm vi đã khảo sát / Scope inspected

- Entry point, trình tự khởi động, tạo cửa sổ: `run.py`, `main.py`, `app/desktop/windows.py`.
- Toàn bộ 16 API mixin (`app/api/*`, `app/bravo/api.py`) để lần cầu nối JS → Python mở cửa sổ.
- 15 template HTML và 8 partial; cấu trúc DOM của `index.html` (3003 dòng).
- 99 file JS trong `static/js/` (đã tải về và tìm kiếm toàn văn); các file CSS chính.
- Theme, typography, icon, cài đặt (`theme-registry.js`, `settings.js`, `data/settings.json`).
- **Chưa chạy ứng dụng** → không có bằng chứng `observed`; mọi kết luận là `code_verified` hoặc `inferred`.

## 2. Số liệu độ phủ / Coverage counts

| Hạng mục | Số lượng |
|---|---:|
| Cửa sổ top-level (kể cả splash và companion Google Sheets) | 16 |
| Cửa sổ có trigger đã xác minh (kể cả 2 cửa sổ mở lúc khởi động) | 15 |
| Cửa sổ không tìm thấy trigger UI | 1 (WIN-EVENT-LISTENER) |
| Loại tab trong workspace chính (`tabType`) | 11 |
| Modal / overlay trong cửa sổ chính | 14 |
| Nhóm context menu | 12 (explorer có ≥ 17 loại node) |
| Menu cấp 1 trên navbar | 6 (File, Edit, Format, View, Settings, Help) + 3 nút (Snippets, Google Sheets, BRAVO) |
| Nút trên action bar: Run, Debug, Stop, Commit, Rollback, Parse, Format, Plan, Import, Export, Compare, Syntax ref, History, File history, Object search (không tính overflow) | 15 |
| Theme tích hợp sẵn | 38 (+ import `.vsix`) |
| Bridge class | 2 chính (MainApi 234 method, BravoApi 111) + 11 `*SubwindowApi` |

Số nút/menu chi tiết sẽ được đếm đầy đủ ở bước 03 cho từng màn hình.

## 3. Phát hiện chính / Key findings

1. **Kiến trúc:** một cửa sổ chính dạng single-page (`index.html`) cộng với 13 sub-window riêng. Mỗi sub-window có template và API riêng, theme title bar được đồng bộ qua DWM. (`code_verified`)
2. **Title bar là native Windows**, chỉ đổi màu qua DWM; menu nằm trong trang (navbar Bootstrap), có thể tự ẩn. SQL-IDE JavaFX hiện dùng **title bar tùy biến undecorated kiểu VS Code** (menu nhúng trong title bar) — đây là thay đổi bố cục so với nguồn, cần được ghi nhận và duyệt. (`code_verified` cho phía Python)
3. **Editor là Monaco.** Rule 01 của skill cấm thay Monaco nếu chưa được yêu cầu. SQL-IDE hiện dùng RichTextFX, và `docs/feature_04_monaco_editor_integration_analysis.md` đang phân tích tích hợp Monaco. → Cần chốt công nghệ editor được duyệt.
4. **Mô hình "cửa sổ chính + fallback modal":** Settings, About, Update mở sub-window qua IPC; nếu thất bại thì hiện modal trong trang. (`code_verified`)
5. **Tính năng có hai hình thức:** Activity Monitor và Schema Compare tồn tại cả dạng **tab** trong workspace lẫn **cửa sổ riêng** (pop-out / menu View). Cần quyết định giữ cả hai hay gộp. (`code_verified`)
6. **Result panel** có 5 tab: Result (AG Grid + structured viewer), Messages, Errors, Logs, Plan; footer thống kê có sparkline. (`code_verified`)
7. **Nút BRAVO** chỉ hiện khi bật add-on `addons.bravo_tool.enabled`. (`code_verified`)

## 4. Ứng viên lỗi thời / Legacy & dead-code candidates (cần xác nhận, KHÔNG tự xóa)

| Mục | Lý do | Bằng chứng |
|---|---|---|
| `templates/partials/*.html` (8 file) | Không được include hay tham chiếu ở đâu | tìm kiếm toàn bộ `*.py/*.js/*.html` |
| WIN-EVENT-LISTENER | Có bridge `open_event_listener_window` nhưng không có lời gọi nào từ JS/HTML | tìm kiếm `static/`, `templates/` |
| Listener `#menu-tools-profiler`, `#ide-profiler-btn` | Phần tử không tồn tại trong `index.html` → Profiler chỉ mở được qua context menu | `index.html:2566–2575` |
| `static/vendor/tabulator` | Không được `index.html` nạp | danh sách thư mục |
| `tests/snapshots/api_surface.txt` | Không phản ánh các sub-window API mới | so sánh với `windows.py` |

## 5. Điều chưa biết / Unknowns

- Hình thức thực tế của mọi màn hình (kích thước vùng, màu, khoảng cách) — chưa có ảnh chụp.
- Event Listener bị ẩn có chủ ý hay là lỗi thiếu trigger.
- Kích thước cửa sổ companion Google Sheets.
- Phím tắt đầy đủ (sẽ thu thập ở bước 03 từ menu, Monaco keybindings và `tabs.js`).
- Các trạng thái chỉ xuất hiện khi có kết nối DB thật (lock/blocking, plan, linked server, debug).

## 6. Trở ngại môi trường / Environmental blockers

- Ứng dụng chỉ chạy trên Windows (WebView2 + DWM + pythonnet); môi trường phân tích không chạy được → **không có screenshot**.
- Đề xuất: bạn tự chụp, hoặc cho phép mình điều khiển máy (computer use) để chạy `python run.py` và chụp ở kích thước cửa sổ và tỉ lệ hiển thị cố định. Chỉ thao tác an toàn, không kết nối DB production.

## 7. Kiểm tra Gate 1 / Gate 1 checklist

- [x] Đã xác định cửa sổ chính và các cửa sổ phụ.
- [x] Đã kiểm kê menu, dialog, context menu và tab view ở mức tổng quan (chi tiết ở bước 02/03).
- [x] Đã ghi lại file nguồn và entry point (`source-index.md`).
- [x] Đã liệt kê rõ màn hình chưa biết hoặc không truy cập được.
- [x] Không suy diễn màn hình nào từ trí nhớ; mọi mục đều có đường dẫn file làm bằng chứng.
- [ ] Ảnh chụp màn hình ở kích thước và scale đã biết — **còn thiếu**.

## 8. Cần bạn xác nhận trước khi sang bước 02 / Items requiring approval

1. **Phạm vi kiểm kê:** gồm cả 16 cửa sổ, hay trước mắt chỉ cửa sổ chính + Settings + các dialog kết nối (phần SQL-IDE đã có khung)?
2. **Cách xử lý phần JavaFX đã làm (Phase 1–5):** dùng skill để đặc tả lại và so sánh với UI JavaFX hiện có, hay chỉ áp dụng cho màn hình chưa làm?
3. **Editor:** giữ Monaco (nhúng WebView trong JavaFX) hay chấp nhận RichTextFX? Theo rule 01, mặc định là giữ Monaco.
4. **Title bar:** chấp nhận title bar tùy biến kiểu VS Code (khác nguồn) hay quay lại title bar native?
5. **Các mục lỗi thời ở mục 4:** loại khỏi phạm vi hay vẫn đặc tả?
6. **Ảnh chụp màn hình:** bạn cung cấp, hay để mình chạy app trên máy bạn?

## 9. File đã tạo / Files created

```
SQL-IDE/ui-specifications/00-application/
  project-map.md
  application-map.json
  source-index.md
  discovery-report.md
```
