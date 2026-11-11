# Workflow 07 — Python Parity
# Quy trình 07 — Đối chiếu với Python

Dùng khi người dùng yêu cầu Java phải khớp logic Python, đang áp dụng cho Feature 09 (IntelliSense và Check Syntax). Kế hoạch chi tiết: `docs/feature_09_intellisense_syntax_parity_plan.md`.

## Nguyên tắc

1. Python là mẫu, chỉ đọc.
2. Mọi logic Java phải ánh xạ được về Python, hoặc là khác biệt đã được duyệt.
3. Chỗ mập mờ thì hỏi, không tự chọn.
4. Việc chỉ sửa logic thì không sửa FXML, CSS, DOM popup, bố cục, màu, chữ hiển thị. Nếu cần đổi giao diện thì dừng và hỏi.
5. Không kết luận khi chưa có số đo. Giả thuyết phải được đo rồi mới được dùng làm căn cứ sửa.

## Các bước

1. **Đọc hai bên.** Có dẫn `file:dòng` cho mỗi nhận định.
2. **Chạy so sánh.** Dùng corpus chung khi có, cùng đầu vào, xuất bảng khác biệt.
3. **Lập bảng khác biệt** theo `templates/parity-diff.template.md`.
4. **Chờ người dùng duyệt từng dòng:** giữ theo Python, giữ khác biệt, hay cải tiến.
5. **Sửa theo thứ tự:** khôi phục phần thiếu → xử lý các khác biệt đã duyệt → tối ưu theo số đo.
6. **Đo lại** và so với mốc ban đầu.

Mỗi giai đoạn dừng để duyệt. Chỉ đưa vào `master` và commit khi người dùng yêu cầu.

## Lưu ý về nền chạy

LLTool chạy JS trên Edge WebView2, tách khỏi Python. SQL-IDE chạy JS trên WebKit của JavaFX, **chung luồng FX với toàn app**. Vì vậy logic đúng như Python vẫn có thể giật. Web Worker chạy được trên WebKit này và là hướng đã có số đo, nhưng chưa được duyệt.
