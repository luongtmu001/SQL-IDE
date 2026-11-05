# Design Tokens
# Hệ thống token thiết kế

Create one central token specification before implementing multiple screens. Do not invent final brand colors from a screenshot if the source values can be inspected.

Tạo một bộ token tập trung trước khi triển khai nhiều màn hình. Không tự chọn màu thương hiệu cuối cùng nếu có thể trích xuất giá trị từ ứng dụng nguồn hoặc người dùng đã quy định.

## Required token groups

- Color roles: `app-bg`, `surface-1`, `surface-2`, `surface-elevated`, `border-subtle`, `text-primary`, `text-secondary`, `text-muted`, `accent`, `success`, `warning`, `danger`, `focus`.
- Typography: font family, UI size scale, editor font, weights, line heights.
- Spacing: consistent base scale and named spacing tokens.
- Sizing: title bar, toolbar, activity rail, sidebar, status bar, controls.
- Shape: corner radii, border widths.
- Elevation: shadow levels where appropriate.
- Motion: duration, easing, reduced-motion behavior.
- Iconography: icon library, size scale, stroke/fill conventions.

## Token requirements

Each token must include:
- Token name.
- Light and dark values if both themes are supported.
- Semantic purpose.
- Usage examples.
- Contrast/accessibility notes where relevant.
- Source: existing application, chosen design reference, or user-approved proposal.

## Example (illustrative only)

```json
{
  "color": {
    "app-bg": {"dark": "#17191D", "light": "#F5F6F8"},
    "surface-1": {"dark": "#202329", "light": "#FFFFFF"},
    "text-primary": {"dark": "#E8EAF0", "light": "#20242B"},
    "accent": {"dark": "#6EA8FE", "light": "#2868C7"}
  },
  "spacing": {
    "xs": 4,
    "sm": 8,
    "md": 12,
    "lg": 16,
    "xl": 24
  }
}
```

Values above are placeholders, not mandated values. Replace them only after inspection and approval.
