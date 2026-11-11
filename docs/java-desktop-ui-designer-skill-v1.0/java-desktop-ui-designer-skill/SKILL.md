---
name: java-desktop-ui-designer
description: Continue the SQL-IDE project, a Java 21 + JavaFX desktop SQL IDE migrated from the Python/PyWebView app LLTool. Use for any work in this repository - fixing or adding screens, wiring real backend logic (Phase 8, Gate D), Python parity checks, IntelliSense/syntax work, reviews and handoff updates. Read HANDOFF.md first; it holds the current done / not-done status.
---

# SQL-IDE — Java Desktop UI & Migration Skill (v2.0)

## Nhiệm vụ / Mission

Tiếp tục dự án **SQL-IDE**: ứng dụng desktop Java 21 + JavaFX 21.0.5, chuyển đổi từ **LLTool** (Python + PyWebView).

Dự án **không còn ở giai đoạn dựng prototype**. UI đã xong (Giai đoạn 0–7) và đang tích hợp logic thật (Giai đoạn 8, Gate D đã được duyệt ngày 2026-11-05). Mặc định app chạy với **backend thật, mọi kết nối chỉ đọc**.

Continue the SQL-IDE project. It is past the prototype stage: the UI exists and real backend integration is under way, read-only by default.

## Hành động đầu tiên / First action

1. Đọc `HANDOFF.md`. Đây là nguồn trạng thái mới nhất.
2. Đọc `rules/01-scope-and-guardrails.md`.
3. Chạy `git status` và `git worktree list`. Trạng thái trong tài liệu có thể đã cũ.
4. Xác định loại việc, rồi chỉ đọc workflow tương ứng:

| Việc | Đọc |
|---|---|
| Sửa UI của màn hình đã có | `workflows/04-ui-change.md`, `rules/02`, `rules/03`, `rules/04`, `rules/07-ui-consistency-rules.md` |
| Thêm màn hình mới | `workflows/02-screen-inventory.md`, `workflows/03-screen-analysis.md`, `rules/07-ui-consistency-rules.md` |
| Nối logic thật, làm tiếp mốc D* | `workflows/06-backend-integration.md`, `rules/06-backend-and-performance.md` |
| Đối chiếu với Python, IntelliSense, Check Syntax | `workflows/07-python-parity.md`, `templates/parity-diff.template.md` |
| Khảo sát lại mã Python | `workflows/01-project-discovery.md` |
| Review, nghiệm thu, cập nhật trạng thái | `workflows/05-review-and-approval.md`, `rules/05-quality-gates.md` |

## Quy tắc bắt buộc / Mandatory rules

1. **Không sửa dự án Python.** Chỉ đọc: `D:\Luong\NB_Personal\Python\IDE-SQL-Lite_New_Pyweb_Claude`.
2. **Giữ an toàn dữ liệu.** Không tắt chế độ chỉ đọc, không chạy DML/DDL lên server thật, không ghi mật khẩu vào repo hoặc tài liệu.
3. **Không chạy `mvnw test` trên `master` khi chưa cách ly dữ liệu test.** Xem cảnh báo A1 trong `HANDOFF.md`.
4. **Không tự suy diễn hành vi.** Dùng nhãn `observed`, `code_verified`, `inferred`, `unknown`. Không nghiệm thu thay người dùng.
5. **Mỗi mốc dừng để duyệt.** Im lặng không phải là đồng ý. Không tự commit, merge hay đẩy nhánh khi người dùng chưa yêu cầu.
6. **Luôn áp dụng R-UI-01** (`appearance.panelStyle`) và không hard-code màu (`rules/03`).
7. **Luôn áp dụng `docs/performance-rules.md`.** Việc ảnh hưởng tốc độ phải có số đo trước và sau.
8. **Giữ Monaco trong một WebView dùng chung.** Không đổi công nghệ editor hay lưới khi chưa được duyệt.
9. **Không làm mất chức năng.** Tính năng chưa làm vẫn hiện mờ với hậu tố `(chưa hỗ trợ)`, không xóa khỏi menu.
11. **Mọi giao diện tuân theo `rules/07-ui-consistency-rules.md`** (R-UI-10 … R-UI-24): màu theo theme, 4 vùng cỡ chữ, kích thước co giãn theo chữ, icon thật theo cài đặt, vùng resize được. **Hỏi người dùng có cần mockup không trước khi làm giao diện.**
10. **Không gọi một việc là xong khi chưa kiểm chứng.** Biên dịch được hoặc test mock xanh chưa đủ để kết luận logic thật đã đúng.

## Khi sửa code / When changing code

- Giữ ID ổn định (`SCR-*`, `REG-*`, `BTN-*`, `INT-*`). Không đánh số lại, không dùng lại ID cũ.
- Sửa đúng phạm vi, không refactor rộng, kể cả `MainController` và `ResultsController` dù hai file này đã lớn.
- Mọi lời gọi DB, file, mạng chạy nền, không chạy trên FX thread.
- Sửa `core/` hoặc `infrastructure/` thì phải cài lại module trước khi chạy app gốc (`HANDOFF.md`, mục 3).

## Báo cáo khi xong / Completion report

Báo cáo bằng tiếng Việt, gồm:

- Đã làm gì, các file đã tạo hoặc sửa.
- Đã kiểm chứng thế nào: lệnh, số test, số đo, phần nào do người dùng cần tự kiểm trên máy.
- Điều chưa rõ, giả định, khác biệt so với Python.
- Việc đang chờ người dùng duyệt.
- **Cập nhật `HANDOFF.md`** và tài liệu trạng thái liên quan.
