# Workflow 04 — UI Change
# Quy trình 04 — Sửa giao diện

Thay cho "Workflow 04 — JavaFX UI Prototype" của bản 1.0. UI đã có, nên việc thường gặp là sửa trên code thật.

## Trước khi sửa

1. Đọc đặc tả của màn hình trong `ui-specifications/01-screens/<ID>/` và quyết định trong `04-reports/migration-plan.md`.
2. Tìm đoạn code bằng `PROJECT-OVERVIEW.md`, mục 14, thay vì đọc hết file lớn.
3. Nêu phạm vi, nguyên nhân, file sẽ sửa. Thay đổi lớn hoặc đổi hình thức đã duyệt thì xin duyệt trước, có thể kèm mockup.

## Quy tắc

- Dùng FXML, CSS, thành phần sẵn có. Không gói cả màn hình vào một lớp mới, không dựng control lớn trong Java khi FXML làm được.
- Tuân thủ R-UI-01 và token.
- Không đổi logic khi việc được giao chỉ là giao diện.
- Giữ ID trong đặc tả và cập nhật `mapping.json` khi control thay đổi.
- Không thêm chữ tiếng Anh mới vào chỗ đang dùng tiếng Việt, trừ tên kỹ thuật theo LLTool.

## Sửa lỗi hình ảnh: phải có bằng chứng chạy thật

Bài học từ T7, T8, T9, T11: cả 4 lỗi đã được sửa theo giả thuyết từ đọc code và **đều không đạt**.

1. Tái hiện và lấy số đo hoặc ảnh chụp trước. Có thể dùng snapshot node như `ExplorerLookSnapshotTest` (xuất PNG vào `target/`), hoặc log cây node, kích thước, CSS. Chế độ `-Dsqlide.uiDebug` có trong kế hoạch D0.2 nhưng **chưa được cài đặt**.
2. Xác nhận nguyên nhân bằng số đo.
3. Sửa, chụp lại, so sánh trước và sau.
4. Báo "chờ người dùng kiểm". Không tự ghi là đã xong.

## Kiểm tra

| Mục | Kiểm |
|---|---|
| Cửa sổ | Thu nhỏ đến mức tối thiểu, phóng to, kéo giãn |
| Panel | `rounded` và `flat` |
| Theme | Một sáng, một tối, nếu có liên quan màu thì thêm một theme VS Code đã nhập |
| Dữ liệu | Tên dài, danh sách rỗng, đang tải, lỗi |
| Chế độ | `run-ui-mock.cmd`, và `run-ui.cmd` khi cần dữ liệu thật |

Ghi rõ phần nào đã tự chạy, phần nào chờ người dùng kiểm.

## Prototype mock

Vẫn dùng khi làm màn hình mới mà chưa duyệt logic. Dữ liệu mẫu nằm tách biệt, có nhãn rõ, thao tác ghi không gọi backend thật, và `BackendFlags` là nơi rẽ nhánh.
