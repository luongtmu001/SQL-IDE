# Evidence Log — Settings window
# Nhật ký bằng chứng — Cửa sổ Cài đặt

Cập nhật: 2026-11-05. Đặc tả hồi tố (UI đích đã có), đọc từ mã JavaFX trong repo. Phía nguồn Python chỉ dựa vào `00-application/screen-inventory.json` / `screen-inventory.md`; mã Python **không** được đọc lại ở lượt này.

## Runtime observations / Quan sát khi chạy

| Evidence ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| EVD-SET-R01 | Nguồn: cửa sổ Cài đặt, các nhóm Editor, Grid, Appearance, SQL | `settings-editor.jpg`, `settings-grid.jpg`, `settings-appearance.jpg`, `settings-sql.jpg` | observed |
| EVD-SET-R02 | Nguồn: nút "Đóng" trong trang không đóng được cửa sổ khi gọi từ instance thứ hai (có thể do chạy 2 instance) | `screen-inventory.md` §3 mục 5 | inferred |

## Source-code references / Tham chiếu mã nguồn

| Evidence ID | File | Symbol / Selector | Finding | Confidence |
|---|---|---|---|---|
| EVD-SET-C01 | `fxml/settings-dialog.fxml` | `btnNav* / section* / settings-row` | 10 nút điều hướng, 10 nhóm, 47 dòng control (ComboBox/Spinner/CheckBox/TextField), JSON area | code_verified |
| EVD-SET-C02 | `ui/dialogs/SettingsDialog.java` | `show` | DialogChrome 900×640; giữ lại chỉ ô tìm ở title cũ; Save + dirty label chuyển xuống footer; onCloseRequest(canClose) | code_verified |
| EVD-SET-C03 | `controllers/SettingsDialogController.java` | `handleSaveAndApply / canClose / dirtyLabel` | Lưu áp dụng ngay; hỏi xác nhận khi đóng với thay đổi chưa lưu | code_verified |
| EVD-SET-C04 | `services/SettingsService.java` | `settings.json` | Ghi trong AppDirectoryManager.getConfigDir() (dự phòng ~/.lltool/config/settings.json) | code_verified |

## Unknowns / Điểm chưa xác định

| Item | What is unknown | Attempts made | Impact | Blocking? |
|---|---|---|---|---|
| UNK-SET-001 | Nhóm thứ 10 so với 9 nhóm của nguồn | Chưa mở `settings.js` để đối chiếu | Thấp | Không |
| UNK-SET-002 | Cách lọc theo ô tìm kiếm | Chưa đọc handler | Thấp | Không |
| UNK-SET-003 | Xem trước phông chữ trực tiếp (nguồn) | Không thấy trong FXML đích | Thấp | Không |
| UNK-SET-004 | Nút Lưu có tắt khi không thay đổi; Khôi phục mặc định có xác nhận | Chưa đọc đoạn mã | Thấp | Không |
| UNK-SET-005 | Phiên bản hiển thị 1.2.6 (FXML) so với AppInfo.VERSION | Chưa so | Thấp | Không |
| UNK-SET-006 | Ảnh đích | Chưa chụp | Trung bình | Chặn review |

## Safety notes / Lưu ý an toàn

Lưu thay đổi `sql.sqlprotection`, `sql.timeoutSeconds`, `updates.updateUrl` ảnh hưởng an toàn khi chạy SQL thật; chỉ sửa trên máy thử. Khi D3 nối `sql.sqlprotection` thật, cần kiểm tuỳ chọn tắt guard.
