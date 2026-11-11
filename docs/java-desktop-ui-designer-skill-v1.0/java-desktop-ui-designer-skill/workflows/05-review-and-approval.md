# Workflow 05 — Review & Approval
# Quy trình 05 — Kiểm tra và phê duyệt

## Các khía cạnh

1. **Độ phủ:** mọi vùng, component, nút, menu của nguồn đều có đích hoặc ngoại lệ đã duyệt.
2. **Hành vi:** so với Python, kể cả lỗi và trường hợp biên.
3. **An toàn:** chỉ đọc, không chạm PROD, không lộ mật khẩu, test không ghi vào dữ liệu thật.
4. **Hình thức:** R-UI-01, token, phân cấp, trạng thái.
5. **Desktop:** co giãn, kích thước tối thiểu, focus, bàn phím.
6. **Hiệu năng:** `docs/performance-rules.md`, có số đo.
7. **Truy vết:** ID, mapping, trạng thái mới nhất.

## Cách so sánh

- Chạy LLTool và SQL-IDE cùng kích thước cửa sổ (đã dùng 1456×816) và cùng display scale.
- So toàn màn hình và từng vùng, gồm cả mọi menu.
- So hành vi, không chỉ so ảnh. Mục tiêu là tương đương VS Code, không phải giống từng pixel.
- Ghi mỗi chênh lệch: mức độ, bằng chứng, đề xuất.

**G1 trong `phase7-review.md` vẫn mở:** việc so ảnh nguồn và đích cạnh nhau chưa làm.

## Từ vựng mapping

`mapping_status`: `not_started`, `analyzed`, `designed`, `implemented_mock`, `implemented_real`, `verified`, `reviewed`, `approved`, `deferred`, `not_applicable_approved`.

`behavior_parity`: `not_checked`, `matched`, `partial`, `changed_with_approval`.

`implemented_real` và `verified` được thêm ở bản 2.0 cho Giai đoạn 8. Ghi `verified` phải kèm cách kiểm chứng trong `differences` hoặc `evidence.md`. Chỉ ghi `approved` khi người dùng đã duyệt.

## Tài liệu trạng thái phải giữ đồng bộ

| Khi | Cập nhật |
|---|---|
| Xong một việc | `HANDOFF.md` |
| Xong một mốc D*, K* | `04-reports/phase8-plan.md` |
| Sửa lỗi T*, đổi kế hoạch | `04-reports/migration-plan.md` |
| Đổi cấu trúc code | `00-application/PROJECT-OVERVIEW.md` |
| Đổi control | `01-screens/<ID>/mapping.json`, `03-traceability/` |
| Xong mốc cuối | Tạo `04-reports/phase8-review.md` (D9) |

## Xin duyệt

Trình bày: ảnh nguồn và đích nếu có, những gì giữ và đổi, số lượng, khoảng trống, cách đã kiểm chứng, phần người dùng cần tự kiểm.

Chờ người dùng đồng ý rõ ràng rồi mới sang mốc sau.
