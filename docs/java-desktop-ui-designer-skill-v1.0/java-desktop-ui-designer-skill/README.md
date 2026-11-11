# SQL-IDE Java Desktop Skill
# Skill tiếp tục dự án SQL-IDE

**Phiên bản:** 2.0 (2026-11-08). Thay cho bản 1.0 ngày 2026-11-04, vốn chỉ viết cho giai đoạn dựng prototype.  
**Dự án:** SQL-IDE, `D:\Luong\NB_Personal\NB_Java\SQL-IDE`  
**Nguồn tham chiếu (chỉ đọc):** LLTool Python + PyWebView  
**Phong cách:** tương đương VS Code, chuyên về cơ sở dữ liệu như DataGrip.

## Thay đổi so với bản 1.0

| Bản 1.0 | Thực tế, bản 2.0 |
|---|---|
| Skill dùng chung cho mọi dự án Python sang JavaFX | Skill riêng cho SQL-IDE |
| Việc chính là khảo sát và dựng prototype mock | UI đã xong, việc chính là nối logic thật và kiểm chứng |
| Cấm nối backend | Gate D đã duyệt. Mặc định backend thật, mọi kết nối chỉ đọc |
| Token là ví dụ minh họa | Dùng token đã duyệt, biến `-ide-*` và theme registry |
| 5 workflow | Thêm workflow nối backend và đối chiếu Python; workflow prototype đổi thành sửa UI |
| Không có trạng thái dự án | Có `HANDOFF.md` ghi việc đã làm, chưa làm, cảnh báo và việc tiếp theo |
| README nhắc tới `templates/application-map.template.md` | File này chưa từng tồn tại, đã bỏ tham chiếu |

## Thứ tự đọc

Không đọc hết một lần.

| Mục đích | File |
|---|---|
| Bắt đầu mọi phiên làm việc | `HANDOFF.md`, `SKILL.md`, `rules/01-scope-and-guardrails.md` |
| Sửa giao diện | `workflows/04-ui-change.md`, `rules/02-design-principles.md`, `rules/03-design-tokens.md`, `rules/04-component-rules.md` |
| Nối logic thật | `workflows/06-backend-integration.md`, `rules/06-backend-and-performance.md` |
| Đối chiếu với Python | `workflows/07-python-parity.md`, `templates/parity-diff.template.md` |
| Thêm màn hình mới | `workflows/02-screen-inventory.md`, `workflows/03-screen-analysis.md`, `templates/*` |
| Khảo sát lại Python | `workflows/01-project-discovery.md` |
| Nghiệm thu | `workflows/05-review-and-approval.md`, `rules/05-quality-gates.md` |

Kiến trúc và vị trí code nằm ở `ui-specifications/00-application/PROJECT-OVERVIEW.md`. File đó lập ngày 2026-11-05, phần sai lệch đã ghi trong `HANDOFF.md`, mục 8.

## Vị trí đầu ra

Tài liệu dự án nằm trong repo, không nằm trong thư mục skill:

```text
ui-specifications/
  00-application/     tổng quan, danh mục màn hình, bản đồ nguồn Python
  01-screens/         22 gói đặc tả (SCR-*)
  02-design-system/   tokens.md, tokens.json
  03-traceability/    ma trận truy vết
  04-reports/         migration-plan, phase7-review, phase8-plan
docs/
  performance-rules.md
  feature_*.md        phân tích từng tính năng
```

## Quy ước ngôn ngữ

- Tên file, ID, định danh code, khóa JSON: tiếng Anh.
- Giải thích và báo cáo: tiếng Việt trước, thêm tiếng Anh khi có ích.
- Chữ hiển thị trong app: tiếng Việt, giữ nguyên tên kỹ thuật như trong LLTool.
