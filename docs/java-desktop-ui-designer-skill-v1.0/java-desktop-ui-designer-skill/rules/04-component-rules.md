# Component Rules
# Quy tắc component

## Dùng lại trước khi tạo mới

| Nhu cầu | Dùng | Vị trí |
|---|---|---|
| Khung dialog | `DialogChrome` (title bar 34px, Esc đóng, theo panelStyle) | `ui/dialogs` |
| Thông báo, lỗi có chi tiết | `MessageDialog`, `ConfirmDialog` | `ui/dialogs` |
| Toast | `NotificationService` → `ToastHost` | `services`, `ui/components` |
| Nút, nút icon | `AppButton`, `IconButton`, `ComponentFactory` | `ui/components` |
| Tìm kiếm, badge, trạng thái | `SearchField`, `Badge`, `StatusIndicator` | `ui/components` |
| Trống, đang tải | `EmptyState`, `LoadingView`, `LoadingOverlay` | `ui/components` |
| Card bo góc | `RoundedCard` | `ui/components` |
| Cây kết nối theo nhóm | `ConnectionTreeBuilder` | `ui/components` |
| Dòng, menu Explorer | `DatabaseTreeCell`, `LazyDatabaseTreeItem` | `ui/components` |
| Lọc, xuất, cột # của lưới | `GridFilterState`, `ColumnFilterPopup`, `GridDataExportUtils`, `FrozenColumnSupport`, `ResultGridRow` | `ui/results` |
| Monaco nhỏ trong dialog | `EmbeddedMonaco` | `ui/editor` |
| Dòng cài đặt kiểu VS Code | `SettingsRowStyler` | `ui/settings` |

`AppDialog` chỉ còn là lớp chuyển tiếp sang `MessageDialog`, không dùng cho code mới.

## Danh mục phải có

Ghi mọi phần tử có tương tác hoặc có ý nghĩa: nút, menu lồng nhau, toolbar, menu chuột phải, tab, ô nhập, node cây, header lưới, control dialog, trạng thái bấm được, thanh kéo, phím tắt.

## Đặc tả một nút

ID ổn định, nhãn, tên trợ năng, icon và nguồn icon, vùng và component cha, tooltip, loại (chính, phụ, phá hủy), điều kiện bật, các trạng thái, sự kiện, kết quả đã xác minh, xác nhận, phím tắt, bằng chứng, ánh xạ JavaFX.

Không suy hành vi từ nhãn hay icon. Lần theo handler và lời gọi backend.

## Đặc tả menu

Ghi thứ tự, dấu phân cách, phím tắt, trạng thái chọn và bật, menu con, kết quả. Gồm cả menu chỉ hiện khi bấm chuột phải hoặc khi chọn đối tượng nhất định.

## Tính năng chưa làm

Giữ mục trong menu, hiện mờ và dùng đúng hậu tố:

- `DatabaseTreeCell.deferred(...)` với `DEFERRED_SUFFIX` = ` (chưa hỗ trợ)`.
- `SqlEditorTab.deferred(...)` cho menu tab.

Control chưa có logic thật không được giả vờ hoạt động. Dữ liệu mẫu phải có nhãn rõ, như `UpdateDialog` đang ghi là mock.

## Mô hình mock và thật

Control gọi backend phải đi qua dịch vụ có công tắc `BackendFlags`. Không đọc thẳng `sqlide.backend` trong controller nếu đã có `BackendFlags`.

## Trợ năng / Accessibility

Control chỉ có icon phải có tên trợ năng và tooltip. Đảm bảo focus nhìn thấy được, dùng được bằng bàn phím, đủ tương phản, không truyền đạt trạng thái chỉ bằng màu.
