# Giai đoạn 0: Số đo hiện trạng IntelliSense

> Kế hoạch: [feature_09_intellisense_syntax_parity_plan.md](feature_09_intellisense_syntax_parity_plan.md)
> Ngày đo: 2026-11-07. Số liệu gốc: `docs/feature_09_baseline_raw/`.
> **Mã nguồn đo:** bản worktree hiện tại, gồm cả các thay đổi D1 đến D8.

## 1. Kết luận

| Giả thuyết | Kết quả | Căn cứ |
|---|---|---|
| **G1** JS trên WebKit của JavaFX chậm hơn Chromium nhiều lần | **Đúng** | Cùng code engine Python: `analyze` chậm hơn 5 lần, lọc và xếp hạng chậm hơn 6 lần |
| **G2** Giật chủ yếu do dựng DOM popup | **Sai với danh sách vừa; chưa kết luận với danh sách lớn** | Bài đo: `render` 14 đến 61 ms. App thật: danh sách 1.000 mục mở mất 850 đến 999 ms (mục 8) |
| **G3** Gửi toàn bộ văn bản sang Java mỗi phím là đáng kể | **Sai** với script thường | Script 16 KB: 1 ms; 100 KB: 2 ms; 500 KB: 5 ms mỗi phím |
| **G4** Nguồn gốc 3 giây sau kết nối | **Đã rõ** (mục 8) | 3,2 giây là tổng các bước; 2 giây là đọc cột chạy nền, không chặn giao diện |

> **Đọc mục 8 trước:** đó là số đo trên app thật và server thật. Các mục 3 đến 5 là bài đo tổng hợp với script dài hơn và đã **tắt MRU**, nên bỏ sót nguyên nhân lớn nhất của lần chạy thật.

**Phát hiện từ bài đo tổng hợp:**

1. **Script 15 KB, bật IntelliSense, gõ 60 ký tự ở tốc độ 10 ký tự/giây: đáng ra 6 giây, WebKit mất 24,6 giây; Edge 7,4 giây.** Luồng giao diện bị chặn gần như liên tục. Metadata trong bài đo trả lời ngay, nên độ trễ này **không đến từ database**.
2. **Vì quá chậm nên popup gần như không kịp hiện:** cùng 60 ký tự, WebKit vẽ popup 2 lần, Edge 9 lần. Mỗi phím mới hủy yêu cầu đang chạy. Đây có thể là lý do bạn thấy "không có gợi ý" (cần xác nhận trên app thật).
3. **Ngay cả khi tắt IntelliSense, script 15 KB trên WebKit vẫn bị trễ:** 8,6 giây thay vì 6, 112 lần bị chặn từ 50 ms trở lên. Tắt tô màu SQL, hoặc ẩn editor để không vẽ, thì hết hẳn. Edge không gặp hiện tượng này.
4. **Web Worker chạy được trên WebKit của JavaFX.** Chạy `analyze` 16 KB trong Worker tốn 147 ms mỗi lần, nhưng luồng giao diện chỉ trễ tối đa 29 ms trong lúc đó.
5. **Không thể đạt độ mượt như Python chỉ bằng cách port đúng logic.** Python chạy `analyze` ngay ở mỗi lần đổi nội dung. Trên WebKit, mỗi lần tốn 138 ms với script 16 KB, tức hơn khoảng cách giữa hai phím khi gõ nhanh.

## 2. Cách đo

- **Cùng một code trên hai trình duyệt.** Máy chủ cục bộ (`src/test/intellisense-perf/server.js`) phục vụ các trang đo cho:
    - **Edge 156 headless:** nhân Chromium, cùng họ với WebView2 của Python.
    - **JavaFX 21 WebView:** WebKit 619, trình duyệt của app Java.
- **Code Python** chỉ được đọc qua HTTP GET, không sửa. Bài đo engine dùng nguyên file trong `static/js`.
- **Tốc độ gõ** cố định 10 ký tự/giây, tài liệu từ các câu `SELECT` lặp lại.
- **Metadata** do JavaScript giả lập, trả lời ngay trong lượt tiếp theo.

**Giới hạn của số đo:**

- Chưa có server thật, nên chưa biết thời gian truy vấn thực tế.
- Popup Python chưa được đo. Bài đo Monaco và gõ phím dùng code popup của Java trên cả hai trình duyệt.
- Edge chạy headless, không GPU; cửa sổ WebView đặt ngoài màn hình. Máy vẫn có các ứng dụng khác chạy song song, nên hãy đọc số theo **bội số**, không theo giá trị tuyệt đối.
- WebKit chỉ đo được đến 1 ms, nên giá trị nhỏ có sai số.
- `generate` chạy bất đồng bộ nên phần sau `await` không tính riêng được (mục 4 có cột "chưa phân bổ").

## 3. Cùng code engine Python trên hai trình duyệt (G1)

Thời gian trung vị, đơn vị ms. Bội số = WebKit / Edge.

| Việc | Quy mô | Edge | WebKit | Bội số |
|---|---|---:|---:|---:|
| `analyze` | 1 KB | 2,5 | 13 | 5,2 |
| | 16 KB | 25,2 | 138 | 5,5 |
| | 54 KB | 64,6 | 499 | 7,7 |
| | 100 KB | 121,2 | 1.102 | 9,1 |
| `getCompletions` sau `FROM` | 500 object | 4,9 | 14 | 2,9 |
| | 5.000 object | 40,9 | 146 | 3,6 |
| | 20.000 object | 153,3 | 634 | 4,1 |
| Lọc và xếp hạng | 500 object | 3,4 | 14 | 4,1 |
| | 5.000 object | 25,6 | 150 | 5,9 |
| | 20.000 object | 93,6 | 676 | 7,2 |
| Dựng danh sách (DOM) | 200 dòng | 20,7 | 56 | 2,7 |

**Engine Java hiện tại so với engine Python:**

| Việc | Quy mô | Python trên WebKit | Java trên WebKit | Java trên Edge |
|---|---|---:|---:|---:|
| `analyze` | 16 KB | 138 | 204 | 7,4 |
| | 54 KB | 499 | 624 | 26,1 |
| Lọc và xếp hạng | 5.000 object | 150 | 155 | 16,3 |

Trên WebKit, engine Java **không nhanh hơn** engine Python, thậm chí `analyze` 16 KB còn chậm hơn; riêng con số này cần đo lặp lại vì trên Edge kết quả lại ngược chiều.

## 4. Thời gian đi vào đâu khi gõ (G2)

Gõ 60 ký tự, 5.000 object, lẽ ra hết **6.000 ms**.

| Tình huống | Edge: thực tế (ms) | Edge: lần chặn ≥ 50 ms | WebKit: thực tế (ms) | WebKit: lần chặn ≥ 50 ms | WebKit: tổng bị chặn (ms) | WebKit: chặn dài nhất (ms) |
|---|---:|---:|---:|---:|---:|---:|
| 1 KB, tắt IS | 6.264 | 0 | 6.832 | 2 | 155 | 99 |
| 1 KB, bật IS | 7.247 | 11 | 14.554 | 63 | 11.480 | 1.086 |
| 15 KB, tắt IS | 6.279 | 0 | 8.636 | 112 | 8.367 | 543 |
| 15 KB, tắt IS, không tô màu | 6.217 | 0 | 6.752 | 0 | 0 | 44 |
| 15 KB, tắt IS, không vẽ | 6.229 | 0 | 6.725 | 0 | 0 | 30 |
| 15 KB, bật IS | 7.409 | 8 | **24.602** | 97 | 24.047 | 761 |
| 15 KB, bật IS, không vẽ | 7.327 | 8 | 19.507 | 66 | 17.637 | 769 |

**Chi tiết WebKit, 15 KB, bật IS** (tổng thời gian trong từng bước):

| Bước | Số lần | Tổng (ms) | Trung bình (ms) | Tối đa (ms) | Tỷ trọng so với thời gian bị chặn |
|---|---:|---:|---:|---:|---:|
| `analyze` | 91 | 11.473 | 126 | 268 | 48% |
| `filter` | 10 | 1.326 | 133 | 265 | 6% |
| `rules.resolve` (D6, chỉ có ở Java) | 34 | 840 | 25 | 149 | 3% |
| `rank` | 10 | 225 | 23 | 102 | 1% |
| `popup.render` | 2 | 27 | 14 | 16 | 0% |
| Chưa phân bổ (vẽ, tô màu, phần bất đồng bộ của `generate`, Monaco) | | khoảng 10.000 | | | khoảng 42% |

**Nhận xét:**

- `analyze` là bước đơn lẻ nặng nhất, gọi 91 lần cho 60 phím (D5 không giúp được vì popup ít khi mở).
- `filter` chênh lệch bất thường: 133 ms trên WebKit, so với 10,5 ms trên Edge (khoảng 13 lần).
- `rules.resolve` là phần do tôi thêm (D6), không phải bước nặng nhất nhưng là chi phí Python không có.

**Số lượng object không phải nguyên nhân chính khi gõ:** 15 KB, bật IS, 500 object vẫn bị chặn tổng 40 giây; 5.000 object là 49 giây (lượt đo gõ 113 ký tự).

## 5. Các phép đo khác

**Popup hiện sau khi gõ dấu cách sau `FROM`** (đã gồm độ trễ chờ cố định trong code):

| Số object | Edge (ms) | WebKit (ms) |
|---:|---:|---:|
| 500 | 168 | 382 |
| 5.000 | 208 | 528 |
| 20.000 | 335 | 1.121 |

**Gửi văn bản sang Java mỗi phím (G3):** đo trên cầu nối thật của WebView, nơi nhận không làm gì.

| Kích thước | Trung vị (ms) | Tối đa (ms) |
|---:|---:|---:|
| 16 KB | 1 | 1 |
| 54 KB | 1 | 2 |
| 100 KB | 2 | 4 |
| 500 KB | 5 | 20 |

**Web Worker trên WebKit:** `typeof Worker = function`. `analyze` 16 KB trong Worker: trung vị 147 ms, tối đa 176 ms. Luồng giao diện lúc đó: p95 18 ms, tối đa 29 ms.

## 6. Việc còn lại: đo G4 trên server thật

Đã thêm dòng thời gian đo, **tắt mặc định**, chỉ ghi thời lượng và số đếm, không ghi SQL hay mật khẩu. Chạy:

```text
run-ui-perf.cmd
```

Thao tác:

1. Tắt các bản app khác đang chạy, rồi kết nối một tab vào database bạn hay dùng.
2. Khi kết nối xong, gõ ngay `SELECT * FROM ` rồi gõ tên một bảng.
3. Mở một script dài bạn thường dùng, gõ khoảng 30 giây ở cuối script.
4. Đóng app. Kết quả nằm ở `target\perf\app-timeline-*.log`.

## 7. Cách đo lại (dùng cho Giai đoạn 5)

Thêm `focus` và `listpaint` vào `-Dperf.pages` để đo riêng MRU và phím mũi tên trong danh sách.

```text
node src/test/intellisense-perf/server.js
powershell -ExecutionPolicy Bypass -File src/test/intellisense-perf/run-edge.ps1
mvnw -Dtest=IntellisensePerfIT test "-DargLine=-Dperf.pages=engine,editor,breakdown,worker -Dsqlide.dataDir=target/test-appdata -Duser.home=target/test-home"
```

Kết quả nằm trong `target/perf/*.json`.

## 8. Số đo trên app thật, server thật

**Nguồn:** `docs/feature_09_baseline_raw/app-timeline-20261107-145647.log`, chạy 2,5 phút bằng `run-ui-perf.cmd`.

**Môi trường suy ra từ log (cần bạn xác nhận):** PostgreSQL (app có hỏi `foreignServers`), database `Bravo10Setup`, danh sách object khoảng 356 KB JSON, cột của 2.322 object. Script gõ thử chỉ khoảng **2 KB**, nên trường hợp script dài **chưa được kiểm chứng trên app thật**.

**Mã nguồn đo:** bản đã có `MetadataSnapshotStore` (D1). Log này không cho biết nguyên nhân của 3 giây **trước** khi có D1.

### 8.1. G4: các mốc khi kết nối

| Mốc | Thời điểm từ lúc bắt đầu | Thời lượng | Có chặn giao diện? |
|---|---:|---:|---|
| Mở phiên của tab | 0,67 s | 694 ms | Không |
| Giao diện treo sau khi kết nối | 0,70–1,41 s | **683 ms** | **Có, chưa rõ nguyên nhân** |
| Mở kết nối đọc metadata | 0,86 s | 150 ms | Không |
| Đọc tên object: gợi ý bảng dùng được từ đây | **1,18 s** | 319 ms | Không |
| Đọc cột, tham số, khóa ngoại | **3,22 s** | **2.040 ms** | Không (không có lần treo nào trong lúc đọc) |

- **Kết luận:** 3,2 giây là tổng thời gian, trong đó 2 giây đọc cột là phần lớn nhất và chạy nền.
- **Popup đầu tiên** sau kết nối: 166 ms, không chờ server.
- **Tab thứ hai cùng connection:** không đọc lại metadata (D1 đúng ở điểm này), mở phiên 217 ms. Vẫn treo **423 ms**. Vậy lần treo sau kết nối **không do metadata**.

### 8.2. Giao diện bị treo lúc nào (≥ 100 ms)

| Giai đoạn | Số lần | Tổng (ms) | Dài nhất (ms) |
|---|---:|---:|---:|
| Khởi động app | 10 | 8.184 | 3.992 |
| Kết nối tab 1 | 1 | 683 | 683 |
| Gõ ở tab 1 | 14 | 3.853 | 612 |
| Kết nối tab 2 | 3 | 747 | 423 |
| Gõ ở tab 2, danh sách nhỏ | 2 | 244 | 144 |
| **14:57:55–14:58:18, sau danh sách 1.000 mục** | **28** | **20.877** | **1.019** |
| Gõ trong script 2 KB | 31 | 11.065 | 866 |

**Gõ không hề giật khi danh sách nhỏ:** từ 14:57:33 đến 14:57:52, 65 lần sửa mà không có lần treo nào.

### 8.3. Thời gian trong từng bước khi gõ

| Bước | Số lần | Tổng (ms) | Trung bình (ms) |
|---|---:|---:|---:|
| **`rank`** | 107 | **5.676** | 53 (đỉnh **636**) |
| `render` | 81 | 2.731 | 34 (đỉnh 489) |
| `analyze` | 208 | 1.292 | 6 |
| `filter` | 107 | 981 | 9 (đỉnh 243) |
| `rules` (D6) | 74 | 315 | 4 |
| `detail` | 146 | 52 | 0,4 |

**Popup hiện sau khi trigger:**

| Cỡ danh sách | Số lần | Trung vị |
|---|---:|---:|
| Từ 100 mục | 12 | **617 ms** |
| Dưới 100 mục | 29 | 18 ms |

### 8.4. Nguyên nhân đã kiểm chứng: MRU trong `rank`

`getMruBonus` gọi `loadHistory()` **cho từng ứng viên**: đọc `localStorage`, `JSON.parse` toàn bộ lịch sử, rồi có thể quét hết các khóa (`completion-ranker.js:131-172`). Bài đo tổng hợp tắt MRU nên không thấy.

`rank`, đo riêng, trung vị ms, mỗi ô chỉ 3 lần đo nên có nhiễu (ô 1.000 ứng viên trên WebKit gần bằng ô 3.000):

| Số ứng viên | MRU tắt | MRU bật, lịch sử rỗng | MRU bật, 200 mục lịch sử |
|---:|---:|---:|---:|
| 1.000, Edge | 10 | 15 | 474 |
| 1.000, WebKit | 91 | 132 | **1.445** |
| 3.000, Edge | 16 | 40 | 1.561 |
| 3.000, WebKit | 187 | 475 | **1.524** |

- **So với Python:** đoạn này **giống hệt Python** (`diff` file ranker chỉ khác phần D6). Python cũng chịu chi phí này khi lịch sử dài; Edge ở 3.000 ứng viên cũng lên 1,5 giây.
- **Hệ quả:** lịch sử MRU càng dài, gợi ý càng chậm, tức là **dùng càng lâu càng giật**. Hiện chưa biết lịch sử trên máy bạn dài bao nhiêu.
- **Sửa chỗ này là cải tiến so với mẫu**, không phải ánh xạ, nên cần bạn duyệt (mẫu ở mục 8 của kế hoạch).

### 8.5. Chưa giải thích được

**Treo khoảng 800 ms lặp lại suốt 20 giây (14:57:59–14:58:18):** đây là khối treo lớn nhất. Không có thao tác sửa văn bản trong khoảng này; có 37 lần cập nhật panel chi tiết và các câu trả lời `parameters` về chậm 1,1 đến 1,9 giây.

Tôi đã thử tái hiện với 1.000 mục, panel chi tiết mở và phím mũi tên, nhưng **không tái hiện được**:

| Đo riêng trên WebKit | Trung vị | Lớn nhất |
|---|---:|---:|
| Riêng thao tác đổi mục chọn | 6 ms | 16 ms |
| Đến lượt chạy kế tiếp của luồng (gồm vẽ) | 16–28 ms | 18–802 ms |

Giả thuyết ban đầu của tôi ("do duyệt 1.000 dòng") vì thế **không đủ căn cứ**. Khác biệt còn lại so với bài đo: cửa sổ thật trên màn hình, luồng `parameters` thật, thao tác thật của bạn. Đã thêm lấy mẫu stack của luồng giao diện khi treo vào `run-ui-perf.cmd` để lần chạy sau chỉ ra đúng chỗ.

Tương tự, chưa rõ nguyên nhân: treo 423–683 ms sau khi kết nối; treo 1,2–4 giây lúc khởi động.

### 8.6. Danh sách việc theo mức ảnh hưởng đo được (chỉ để chọn, chưa làm)

| # | Việc | Bằng chứng | Khác Python? |
|---|---|---|---|
| 1 | Không đọc lại lịch sử MRU cho từng ứng viên | Mục 8.4: chênh 8 đến 16 lần ở bài đo | Cải tiến |
| 2 | Tìm và sửa cơn treo 800 ms | Mục 8.5: 20,9 giây; cần đo thêm | Chưa biết |
| 3 | Dựng danh sách lớn nhanh hơn (chỉ dựng phần nhìn thấy) | Danh sách 150 đến 1.000 mục mất 450–999 ms; `performance-rules.md` §1–2 yêu cầu | Cải tiến, không đổi giao diện |
| 4 | Chạy `analyze` trong Web Worker | Chỉ đáng làm nếu script dài là trường hợp thường gặp | Thay đổi kiến trúc |
| 5 | Đọc cột lười thay vì nạp sẵn (Q9) | 2 giây chạy nền, không chặn giao diện, nên không cấp bách | Về gần Python |
