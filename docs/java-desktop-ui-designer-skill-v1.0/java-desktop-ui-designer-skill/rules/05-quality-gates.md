# Quality Gates & Acceptance Criteria
# Cổng chất lượng và tiêu chí nghiệm thu

## Gate 1 — Đặc tả màn hình mới

- [ ] Có đủ bộ file trong `ui-specifications/01-screens/<ID>/`.
- [ ] Mọi vùng, component, nút có ID ổn định.
- [ ] Đã có tương tác, trạng thái, bằng chứng, điều chưa rõ.
- [ ] Có ánh xạ Python sang JavaFX.

## Gate 2 — Thay đổi giao diện

- [ ] App chạy được và đúng khi co giãn cửa sổ.
- [ ] Đúng cả `rounded` lẫn `flat`, cả theme sáng lẫn tối.
- [ ] Không hard-code màu hay bán kính.
- [ ] Mọi control đã ghi trong đặc tả đều có mặt.
- [ ] Có ảnh chụp thật khi sửa lỗi hình ảnh. Không kết luận chỉ từ đọc CSS.

## Gate 3 — Tích hợp logic thật (mỗi mốc D*)

- [ ] Có công tắc mock/thật khi khu vực cần.
- [ ] Không chặn FX thread. Có timeout, lỗi hiển thị rõ, hủy được khi việc có thể kéo dài.
- [ ] Giữ các lớp bảo vệ chỉ đọc.
- [ ] Đúng cho từng loại DB trong phạm vi: SQL Server, PostgreSQL, SQLite.
- [ ] Đã đạt tiêu chí của mốc trong `04-reports/phase8-plan.md`.
- [ ] Test tự động không cần server chạy được bằng `-Dsqlide.backend=mock`. Test cần server đặt tên `*IT`.
- [ ] Người dùng đã thử trên máy các mục không tự động được.

## Gate 4 — Đối chiếu Python

- [ ] Mỗi khác biệt có một mục theo `templates/parity-diff.template.md`.
- [ ] Khác biệt được phân loại: giữ theo Python, khác đã duyệt, cải tiến đã duyệt.
- [ ] Chạy cùng corpus trên hai bên khi có corpus.

## Gate 5 — Hiệu năng

- [ ] Đã rà theo `docs/performance-rules.md`.
- [ ] Có số đo trước và sau khi việc ảnh hưởng tốc độ.

## Gate 6 — Hoàn tất một việc

- [ ] Biên dịch được.
- [ ] Bộ unit test không có lỗi mới, so với danh sách lỗi đã biết trong `HANDOFF.md`.
- [ ] Không còn việc hoặc file tạm ngoài phạm vi.
- [ ] Đã cập nhật `HANDOFF.md` và tài liệu trạng thái liên quan.

## Nhãn hoàn thành

Chỉ dùng `PASS`, `PASS_WITH_KNOWN_GAPS`, `NEEDS_REVISION`, `BLOCKED`.

Không ghi `PASS` khi còn control chưa ghi nhận hoặc tương tác quan trọng chưa kiểm chứng.

Giữ riêng hai khái niệm: `implemented` (đã có code) và `verified` (đã kiểm chứng, ghi rõ bằng cách nào). Chỉ người dùng mới đánh dấu được `approved`.
