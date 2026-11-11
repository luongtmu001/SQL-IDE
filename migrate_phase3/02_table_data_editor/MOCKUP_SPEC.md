# Đặc tả Thiết kế & Mockup: Table Data Editor (SSMS Query Designer & Edit Rows)

**Ngày lập:** 2026-11-11  
**Mục tiêu:** Cung cấp mockup trực quan, hiện đại, tương tác cao chuẩn hóa trải nghiệm người dùng theo đúng nguyên bản thiết kế từ Python (`data_editor.md`) và phong cách SSMS / DataGrip.

---

## 1. File Mockup Tương tác
- **Bản xem trực quan HTML:** [`migrate_phase3/02_table_data_editor/mockup.html`](file:///d:/Luong/NB_Personal/NB_Java/SQL-IDE/migrate_phase3/02_table_data_editor/mockup.html)
- **Bản nhúng Generative UI:** [`C:\Users\luonght\.gemini\antigravity\brain\dad46b64-90e6-432e-9d51-876ef0ce1410\table_data_editor_mockup.html`](file:///C:/Users/luonght/.gemini/antigravity/brain/dad46b64-90e6-432e-9d51-876ef0ce1410/table_data_editor_mockup.html)

---

## 2. Cấu trúc Giao diện 4 Phân vùng (4-Pane System)

Khác với bảng dữ liệu phẳng đơn điệu, Table Data Editor được tổ chức thành 4 phân vùng có thể co giãn (Splitter) và bật/tắt (Toggle) linh hoạt:

```
┌────────────────────────────────────────────────────────────────────────┐
│ TOP TOOLBAR: Run | Save (3) | Discard | +Add | -Del | Dup | Panes | Top│
├────────────────────────────────────────────────────────────────────────┤
│ [1] CRITERIA PANE (Collapsible, height: ~140px)                       │
│     Grid tiêu chí lọc: Column | Operator | Filter | Or... | Sort | Seq │
├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄ Splitter 1 (Co giãn) ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┤
│ [2] SQL PANE (Collapsible, height: ~100px)                            │
│     SELECT TOP 200 [Id], [Name] FROM dbo.Customers WHERE ... ORDER BY..│
├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄ Splitter 2 (Co giãn) ┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┤
│ [3] DATA GRID PANE (Chiếm 100% không gian còn lại)     │ [4] INSPECTOR │
│     Sticky Row Header | Sticky Column Header (Icons)   │     DRAWER    │
│     1  [Normal Row]                                    │  - Schema     │
│     ✏ 2 [Modified Row: Amber Flag ◣, Dirty Cells]      │  - PK Details │
│     ✏ 3 [Modified Row: [NULL] Cell via Ctrl+0]         │  - FK Links   │
│     🗙 4 [Deleted Row: Red Strike-through Line]         │  - Unique     │
│     ✱ * [New Inserted Row: Green Accent]               │  - Check      │
├────────────────────────────────────────────────────────┴───────────────┤
│ BOTTOM STATUS BAR: Row 2/200 | 3 Unsaved Changes | 0 Errors | EDITABLE │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Chi tiết Các Thành phần Giao diện

### 3.1. Top Toolbar (Thanh công cụ đỉnh)
1. **Nhóm Thao tác Dữ liệu:**
   - **`Run (F5)`**: Nạp lại dữ liệu với các điều kiện trong Criteria Pane hoặc câu lệnh trong SQL Pane.
   - **`Save (Ctrl+S)`**: Mở hộp thoại Preview DML an toàn, hiển thị badge số lượng thay đổi (`+1 INSERT`, `~1 UPDATE`, `-1 DELETE`) và áp dụng qua Transaction.
   - **`Discard`**: Hoàn tác tất cả thay đổi trên client và tải lại dữ liệu sạch từ DB.
2. **Nhóm Quản lý Dòng:**
   - **`+ Add Row (Ctrl+N)`**: Chèn dòng mới có biểu tượng `✱` ở cuối bảng.
   - **`- Delete Row (Delete)`**: Đánh dấu xóa dòng hiện tại với hiệu ứng gạch ngang đỏ `🗙`.
   - **`Duplicate`**: Nhân bản dòng đang chọn sang dòng mới (tự động bỏ qua cột Identity/PK).
3. **Nhóm Bật/Tắt Phân vùng (Pane Toggles):**
   - Nút **`Criteria`**: Ẩn/hiện vùng điều kiện lọc.
   - Nút **`SQL`**: Ẩn/hiện vùng kịch bản SELECT đồng bộ.
   - Nút **`Grid`**: Ẩn/hiện vùng bảng dữ liệu.
4. **Nhóm Giới hạn & Tìm kiếm:**
   - Dropdown **`Top:`** 50, 100, 200 (mặc định), 500, 1000, All.
   - Ô nhập **Quick Search**: Lọc nhanh tức thời trên dữ liệu đã tải trong RAM.
5. **Nhóm Trạng thái Bảng & Khóa:**
   - Badge kết nối: `DEV-SQLSERVER / NorthwindDB`.
   - Badge Primary Key: `🔑 PK: Id` (vàng nổi bật) hoặc `⚠️ No PK` (cảnh báo đỏ).
   - Nút **`Inspector`**: Mở ngăn kéo (Drawer) hiển thị danh mục tất cả ràng buộc của bảng.

---

### 3.2. Criteria Pane (Phân vùng Điều kiện lọc & Sắp xếp)
Cung cấp bảng nhập tiêu chí tương tự SSMS:
- **Cột (Column):** Dropdown chọn tên cột kèm kiểu dữ liệu (VD: `Country (nvarchar)`, `Balance (decimal)`).
- **Toán tử (Operator):** `=`, `<>`, `>`, `<`, `>=`, `<=`, `LIKE`, `NOT LIKE`, `IN`, `BETWEEN`, `IS NULL`, `IS NOT NULL`.
- **Giá trị lọc (Filter):** Nhập giá trị chuỗi, số hoặc biểu thức.
- **Hoặc (Or...):** Điều kiện mở rộng `OR`.
- **Sắp xếp (Sort):** `None`, `ASC`, `DESC`.
- **Thứ tự (Sort Order):** Số thứ tự ưu tiên sắp xếp (1, 2, 3).
- **Thao tác:** Nút `+ Thêm tiêu chí`, `Xóa hết`, và xóa từng dòng.

---

### 3.3. SQL Pane (Phân vùng Kịch bản SQL Đồng bộ)
- Tự động sinh câu lệnh `SELECT TOP N ... FROM ... WHERE ... ORDER BY ...` theo phương ngữ của CSDL (T-SQL cho SQL Server, Postgres SQL cho PostgreSQL).
- Cho phép copy nhanh kịch bản hoặc mở rộng câu truy vấn phức tạp.

---

### 3.4. Data Grid Pane (Bảng dữ liệu Chỉnh sửa Trực tiếp)
1. **Cột Tiêu đề (Sticky Column Header):**
   - Biểu tượng `🔑` màu vàng cho Primary Key.
   - Biểu tượng `🔒` màu xám cho cột Read-only / Identity / Computed (không cho sửa).
   - Biểu tượng `🔗` cho cột Foreign Key.
   - Biểu tượng `🔤` cho Unique Constraint.
   - Chip hiển thị kiểu dữ liệu CSDL (`int`, `nvarchar(100)`, `decimal(18,2)`, `datetime2`).
2. **Cột Dòng (Sticky Row Header):**
   - Số thứ tự dòng cố định bên trái (`1, 2, 3...`).
   - Con trỏ dòng đang kích hoạt `▶`.
   - Biểu tượng dòng đã sửa: `✏` (màu hổ phách).
   - Biểu tượng dòng mới thêm: `✱` (màu xanh lá cây).
   - Biểu tượng dòng đánh dấu xóa: `🗙` (màu đỏ kèm hiệu ứng gạch ngang cả dòng).
3. **Ô Dữ liệu (Cell States & Visual Feedback):**
   - **Dirty Cell:** Nền màu hổ phách nhạt `bg-amber-500/15` kèm tam giác đánh dấu ở góc trên bên phải `cell-dirty-flag`.
   - **Null Value:** Hiển thị chữ `[NULL]` in nghiêng màu xám mờ (`text-gray-500 italic`).
   - **Phím tắt nhanh:**
     - `Ctrl + 0`: Đặt giá trị ô thành `[NULL]`.
     - `Enter`: Commit giá trị ô đang sửa và chuyển xuống dòng dưới.
     - `Tab`: Commit và chuyển sang ô bên phải.
     - `Escape`: Hủy chỉnh sửa ô, quay về giá trị trước đó.
     - `Ctrl + S`: Mở hộp thoại lưu toàn bộ thay đổi.
     - `Delete`: Đánh dấu xóa dòng đang chọn.

---

### 3.5. Slide-out Drawer: Schema & Constraints Inspector
Ngăn kéo bên phải mở ra khi bấm nút `Inspector`:
- **Thông tin bảng:** Schema, Engine, Khóa chính, Quyền thao tác.
- **Primary Key:** Danh sách cột cấu thành PK và Index đi kèm.
- **Foreign Keys:** Danh sách liên kết bảng cha, cột tham chiếu, quy tắc `On Delete / On Update`.
- **Unique Constraints:** Danh sách ràng buộc duy nhất.
- **Check Constraints:** Biểu thức điều kiện kiểm tra hợp lệ.

---

### 3.6. Hộp thoại DML Preview Dialog (Chuẩn DialogChrome)
Khi người dùng bấm **`Save`** hoặc **`Preview DML`**:
- Hiển thị badge thống kê: `+1 INSERT`, `~1 UPDATE`, `-1 DELETE`.
- Kịch bản DML đầy đủ bọc trong `BEGIN TRANSACTION ... COMMIT TRANSACTION`.
- Tự động áp dụng **Optimistic Concurrency Check** (`WHERE Id = @id AND Col1 = @oldVal1...`) để đảm bảo không bao giờ ghi đè dữ liệu của phiên khác.
- Các nút hành động:
  - **`Thực thi ngay`** (Primary button): Chạy transaction áp dụng xuống DB.
  - **`Mở trong tab SQL`** (Secondary button): Xuất kịch bản ra tab soạn thảo mới.
  - **`Hủy`** (Cancel button): Đóng hộp thoại.

