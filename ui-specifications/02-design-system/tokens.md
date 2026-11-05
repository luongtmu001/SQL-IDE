# Design Tokens — SQL IDE (JavaFX)

- **Trạng thái:** approved — 2026-11-05 (Q1 Inter, Q2 grid 12px; cả hai chỉnh được trong Settings)
- **Mục tiêu thị giác:** tương đương VS Code, chạy trên AtlantaFX (Primer Light/Dark)
- **File máy đọc:** `tokens.json` (cùng thư mục)

## Nguyên tắc

1. Runtime **luôn dùng biến looked-up `-color-*` của AtlantaFX**, không hard-code hex. Hex dưới đây chỉ để tham chiếu (giá trị GitHub Primer — *inferred*).
2. Hai biến không có sẵn trong AtlantaFX được định nghĩa ở `base.css` (`.root`):
   `-color-bg-muted: derive(-color-bg-subtle, -6%)`, `-color-accent-fg-emphasis: -color-fg-emphasis`.
   Không dùng thêm biến nào ngoài danh sách AtlantaFX + 2 biến này (tránh ClassCastException String → Paint).
3. Ngoại lệ literal duy nhất: nút Close của cửa sổ `#e81123` (chuẩn Windows).

## 1. Màu theo vai trò

| Token | Biến JavaFX | Light | Dark | Dùng cho |
|---|---|---|---|---|
| app-bg | `-color-bg-subtle` | #f6f8fa | #161b22 | Nền cửa sổ, khe giữa panel |
| surface-1 | `-color-bg-default` | #ffffff | #0d1117 | Editor, Explorer, grid |
| surface-2 | `-color-bg-subtle` | #f6f8fa | #161b22 | Title bar, header panel, pill toolbar |
| surface-elevated | `-color-bg-overlay` | #ffffff | #161b22 | Menu, popup, dialog, notification |
| surface-hover | `-color-bg-muted` | derive −6% | derive −6% | Hover trên nền subtle |
| border-subtle | `-color-border-subtle` | #d8dee4 | #21262d | Hairline chia panel |
| border-default | `-color-border-default` | #d0d7de | #30363d | Viền combo, input |
| text-primary | `-color-fg-default` | #1f2328 | #e6edf3 | Chữ chính |
| text-secondary | `-color-fg-muted` | #656d76 | #7d8590 | Nhãn, chữ phụ |
| text-muted | `-color-fg-subtle` | #6e7781 | #6e7681 | Placeholder, disabled, title bar inactive |
| accent | `-color-accent-emphasis` | #0969da | #1f6feb | Nút Run, selection, focus |
| on-accent | `-color-fg-emphasis` | #ffffff | #ffffff | Chữ trên nền accent |
| success | `-color-success-fg` | #1a7f37 | #3fb950 | Kết nối OK, query thành công |
| warning | `-color-warning-fg` | #9a6700 | #d29922 | Cảnh báo |
| danger | `-color-danger-fg` / `-emphasis` | #d1242f | #f85149 | Lỗi, Stop, xóa |
| focus | `-color-accent-emphasis` | #0969da | #1f6feb | Viền focus 1px |

### Badge môi trường (chỉ ở context toolbar)

| Env | Nền | Chữ |
|---|---|---|
| DEV | accent-subtle | accent-fg |
| TEST / QA | success-subtle | success-fg |
| STAGING / UAT | warning-subtle | warning-fg |
| PROD | danger-emphasis | fg-emphasis (đặc, nổi bật nhất) |

## 2. Typography

| Token | Giá trị | Nguồn |
|---|---|---|
| ui-font | **Inter** → Segoe UI Variable → Segoe UI (chỉnh trong Settings) | LLTool dùng Inter — **Q1 đã chốt** |
| ui-size | chrome 12 · body 13 · small 11 · caption 10 (px) | VS Code |
| editor-font | JetBrains Mono 13, line-height 1.5 | LLTool: JetBrains Mono 12 |
| grid-font | JetBrains Mono **12** (chỉnh trong Settings) | LLTool: 15 — **Q2 đã chốt** |
| messages-font | Cascadia Mono → Consolas 12 | LLTool: Source Code Pro 13 |
| weight | 400 regular · 600 semibold | — |

## 3. Spacing (thang 2/4)

`xxs 2 · xs 4 · sm 6 · md 8 · lg 12 · xl 16 · xxl 24` (px)

## 4. Kích thước khung

| Thành phần | Kích thước | Nguồn |
|---|---|---|
| Title bar | 34 | titlebar.css (đã duyệt) |
| Context toolbar | 32 | layout.css (đã duyệt) |
| Tab strip | 32 | đề xuất (VS Code 35) |
| Panel header | 28 | chuẩn hoá Phase 1.9 |
| Status bar | 22 | VS Code |
| Control | 24 (compact 22) | — |
| Dòng cây Explorer | 22 | VS Code |
| Dòng grid | 24 | — |
| Icon | chrome 13 · tree 12 · badge 10 | — |
| Nút cửa sổ | 46 × chiều cao title bar | chuẩn Windows |
| Sidebar | mặc định 260, tối thiểu 180, rail 46 | LLTool 260 |
| Tỉ lệ chia | editor/results 65/35, sidebar 22% | — |

## 5. Hình khối, độ nổi, chuyển động

- **Radius:** control 4 · pill 5 · badge 3 · menu/card 8. Viền 1px.
- **Elevation:** panel phẳng (không đổ bóng); menu `dropshadow(gaussian, rgba(0,0,0,.18), 12, 0, 0, 4)`; dialog `dropshadow(gaussian, rgba(0,0,0,.25), 24, 0, 0, 8)`.
- **Motion:** fast 100ms · base 160ms · ease-out; giảm chuyển động → tức thì.

## 6. Icon

- Chrome và toolbar: Ikonli **Feather** (`fth-*`), nét outline.
- Loại object DB: **MaterialDesign2** (`mdi2-*`), màu theo vai trò: table = accent, view = tím, function = success, procedure = warning.

## Quyết định đã duyệt

- **Q1 — Font UI: Inter**, người dùng đổi được trong Settings. Nguồn LLTool không nhúng Inter (dựa vào font của máy), nên bản JavaFX phải **nhúng Inter TTF (giấy phép SIL OFL 1.1)** vào `resources/fonts` và nạp bằng `Font.loadFont` khi khởi động. Nếu nạp lỗi thì dùng Segoe UI.
- **Q2 — Font grid: 12px**, người dùng đổi được trong Settings.
- Editor và grid dùng JetBrains Mono 400/500/600. File TTF có sẵn ở `static/vendor/fonts` của nguồn, chép sang khi làm Phase 1.
