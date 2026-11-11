# Workflow 03 — Analyze One Screen
# Quy trình 03 — Phân tích một màn hình

## Mục tiêu

Viết đặc tả có bằng chứng cho **một** màn hình mới, hoặc bổ sung đặc tả đã có. Spec mẫu nên tham khảo: `SCR-SHELL-001` (đầy đủ), `SCR-SPLASH-001` (mới nhất).

Với gói hồi tố ngày 2026-11-05, phần lớn `behavior_parity` là `not_checked`: đặc tả được viết từ code Java, chưa so với Python. Khi làm lại một màn hình loại này, hãy so với Python và cập nhật mapping.

## Bước 1 — Ảnh tham chiếu

Chụp nguồn và đích cùng kích thước cửa sổ, ghi display scale. Gồm toàn màn hình, từng vùng lớn, menu đang mở, dialog, và các trạng thái chọn, trống, đang tải, lỗi.

Không chụp được thì ghi rõ. Không suy đoán hình thức từ code rồi ghi là đã quan sát.

## Bước 2 — Phân rã và cấp ID

Ứng dụng → Màn hình → Vùng → Component → Control.

| Loại | Mẫu |
|---|---|
| Màn hình | `SCR-MAIN-001` |
| Vùng | `REG-MAIN-001` |
| Component | `CMP-MAIN-001` |
| Nút | `BTN-MAIN-001` |
| Tương tác | `INT-MAIN-001` |
| Trạng thái | `STA-MAIN-001` |

## Bước 3 — Vùng

Ghi mục đích, vị trí, kích thước cố định hoặc co giãn, padding, nền và viền theo token, component con, hành vi ẩn, thu gọn, kéo giãn.

## Bước 4 — Từng control

1. Nhãn và icon chính xác.
2. Tìm phần tử trong HTML, rồi listener JS.
3. Lần theo bridge Python và backend.
4. Xác định điều kiện, kết quả, xác nhận, lỗi, tác dụng phụ.
5. Chỉ thử thao tác không phá hủy, không dùng PROD.
6. Gắn nhãn bằng chứng.

## Bước 5 — Trạng thái

Xét thường, hover, nhấn, focus, tắt, đang chọn, mở/thu, đang tải, trống, lỗi, hiển thị theo ngữ cảnh, phím tắt. Trạng thái chưa quan sát thì ghi rõ.

## Bước 6 — Ánh xạ JavaFX

Với mỗi vùng, component: ưu tiên thành phần có sẵn (`rules/04`), nêu lý do, hành vi cần tự viết, rủi ro hiệu năng. Ghi rõ là đề xuất hay đã duyệt.

## File bắt buộc

```text
ui-specifications/01-screens/<screen-id>/
  screen-spec.md
  layout.json
  components.json
  interactions.json
  states.json
  mapping.json
  evidence.md
  screenshots/        khi có ảnh
```

Dùng mẫu trong `templates/`, kiểm tra cú pháp JSON. Từ vựng mapping phải theo `workflows/05`: không dùng `spec_draft`, `full`, `changed`, `missing` như một số gói cũ.

## Bước 7 — Báo cáo và dừng

Báo số lượng và điều chưa rõ, xin duyệt đặc tả. Với thiết kế mới, có thể kèm mockup để duyệt. Chưa duyệt thì chưa code.
