# Scope, Boundaries & Guardrails
# Phạm vi, ranh giới và nguyên tắc bảo vệ

## 1. Nguồn chuẩn / Source of truth

| Câu hỏi | Nguồn chuẩn |
|---|---|
| Chức năng, luồng nghiệp vụ, ý nghĩa dữ liệu | Code Python của LLTool (chỉ đọc). Tài liệu Python có thể đã cũ: khi lệch thì **lấy code làm chuẩn** |
| Trạng thái code Java hiện tại | Code trên `master`, rồi đến `HANDOFF.md` |
| Thiết kế giao diện đã chốt | Quyết định của người dùng trong `ui-specifications/` (Gate B, Gate C, R-UI-01) |
| Mục tiêu giao diện | **Tương đương VS Code**, không sao chép 100% bản Python |

`MIGRATION_TRACKER.md` ghi Feature 1–8 là `COMPLETE`, nhưng các dòng đó có trước Giai đoạn 8 và phần lớn dựa trên test mock. **Không dùng file đó để kết luận tính năng đã xong với server thật.** Riêng các rule ở `docs/LLTool_Java_Migration_3_Files/AGENTS.md` vẫn có giá trị, trừ dòng "Java 25, JavaFX 25": `pom.xml` thực tế dùng release 21 và JavaFX 21.0.5.

## 2. Phạm vi / Scope

**Trong phạm vi:** màn hình chính, Settings, các dialog và splash; logic thật theo các mốc D1–D9; IntelliSense và Check Syntax theo kế hoạch Feature 09.

**Ngoài phạm vi khi chưa có yêu cầu:** table designer, data editor, dependency graph, object search, SQL visualizer, activity monitor, schema compare, Profiler, Query History, Snippets, BRAVO, Google Sheets, Report Gen, MySQL. Lối vào của các tính năng này vẫn được giữ và hiện mờ `(chưa hỗ trợ)`.

## 3. Không âm thầm bỏ chức năng / No silent feature loss

Mọi thao tác trong LLTool phải có mặt trong danh mục component và ma trận truy vết. Muốn bỏ hoặc gộp phải ghi lý do và xin duyệt. Tính năng mới thì đề xuất riêng, không gộp vào việc đang làm.

## 4. Nhãn bằng chứng / Evidence labels

- `observed`: thấy trực tiếp trên app đang chạy.
- `code_verified`: xác nhận từ code.
- `inferred`: suy luận, chưa kiểm chứng.
- `unknown`: chưa biết.

Không trình bày `inferred` hay `unknown` như sự thật. Với kết quả kiểm thử, ghi rõ: mock, IT với server thật, hay người dùng đã thử trên máy.

## 5. An toàn dữ liệu / Data safety

- Mọi kết nối **chỉ đọc** (`sqlide.readOnly=true`) theo quyết định ngày 2026-11-05. Không đổi mặc định này khi chưa được duyệt riêng.
- Không chạy lệnh ghi, xóa, đổi quyền hay `EXPLAIN ANALYZE` câu ghi lên server thật, kể cả để thử.
- Chỉ thử trên server thử local: SQL Server `localhost,1433`, PostgreSQL `localhost,5432`. **Không thử trên PROD.**
- Không ghi mật khẩu vào repo, tài liệu, log hay lời nhắc. Không đọc hoặc in nội dung `%APPDATA%\LLTool-Desktop\it-db.properties` và `vault.enc`.
- `%APPDATA%\LLTool\` thuộc app Python: chỉ đọc. `%APPDATA%\LLTool-Desktop\` là dữ liệu thật của người dùng: test không được ghi vào đây.
- Không tự kết nối DB lúc khởi động (giữ bản sửa T1).

## 6. Kiểm soát thay đổi / Change control

- Không đổi lược đồ dữ liệu đã lưu khi chưa có phương án tương thích hoặc nhập dữ liệu.
- Không đổi dependency hay framework lớn khi chưa nêu đánh đổi và được duyệt.
- Không đổi hoặc thay Monaco, không thay `TableView` khi chưa được duyệt.
- Mỗi lần chỉ làm một mốc hoặc một màn hình.
- Không commit, merge, tạo tag hay push khi người dùng chưa yêu cầu.
- Không xóa worktree và nhánh `claude/*`: có nhánh còn việc chưa merge (`HANDOFF.md`, mục 2).

## 7. Điểm duyệt / Approval gates

| Gate | Nội dung | Trạng thái |
|---|---|---|
| A | Danh mục màn hình và thứ tự | Đã duyệt |
| B | Đặc tả từng màn hình | Đã chốt khi làm từng giai đoạn. Riêng 17 gói đặc tả viết hồi tố ngày 2026-11-05 **chưa qua Gate B** |
| C | Prototype | Đã qua cho 21 màn hình, kèm khoảng trống đã ghi. `SCR-SHELL-001` là tạm duyệt |
| D | Tích hợp logic thật | Đã đồng ý ngày 2026-11-05, **duyệt riêng từng mốc D1…D9** |

Dừng tại mỗi điểm duyệt. Im lặng không phải là đồng ý.
