# Java Desktop UI Designer Skill
# Skill thiết kế giao diện Java Desktop

**Version:** 1.0  
**Primary target:** JavaFX desktop application  
**Source UI:** Existing Python + PyWebView application  
**Design direction:** Hybrid Modern Native — professional IDE structure with modern desktop visual language.

## Purpose / Mục đích

This skill guides an AI coding agent to inspect an existing Python/PyWebView application, document every screen and UI element, redesign the UI for JavaFX, build runnable UI prototypes with mock data, review them, and wait for approval before integrating application logic.

Skill này hướng dẫn AI Agent khảo sát ứng dụng Python/PyWebView hiện tại, đặc tả từng màn hình và thành phần giao diện, thiết kế lại bằng JavaFX, dựng prototype chạy được với dữ liệu giả, kiểm tra chất lượng và chờ phê duyệt trước khi tích hợp logic.

## Read order / Thứ tự đọc

Do not load every file at once. Read only the task-specific files.

| Task | Required files |
|---|---|
| Understand the skill | `SKILL.md`, `rules/01-scope-and-guardrails.md` |
| Discover the Python project | `workflows/01-project-discovery.md` |
| Inventory screens | `workflows/02-screen-inventory.md`, `templates/application-map.template.md` |
| Analyze one screen | `workflows/03-screen-analysis.md`, relevant templates in `templates/` |
| Define visual language | `rules/02-design-principles.md`, `rules/03-design-tokens.md`, `rules/04-component-rules.md` |
| Build JavaFX prototype | `workflows/04-prototype-implementation.md` |
| Review and acceptance | `workflows/05-review-and-approval.md`, `rules/05-quality-gates.md` |

## Output location / Vị trí đầu ra

Create documentation in the target project, not inside this reusable skill folder:

```text
ui-specifications/
  00-application/
  01-screens/
  02-design-system/
  03-traceability/
  04-reports/
```

## Non-negotiable workflow / Quy trình bắt buộc

1. Discover before designing.
2. Inventory all screens before selecting implementation order.
3. Analyze one screen at a time.
4. Record evidence and uncertainty; never invent behavior.
5. Preserve existing capabilities unless the user explicitly approves a change.
6. Build UI-only prototypes with mock data first.
7. Review against screenshots, specifications, and traceability.
8. Stop for user approval before backend/business-logic integration.

## Bilingual convention / Quy ước song ngữ

- File names, IDs, code identifiers, component names: English.
- Explanations and user-facing reports: Vietnamese first, English second where useful.
- Technical terms may remain in English, followed by a Vietnamese explanation on first use.
- Keep JSON keys and enum values in English for predictable parsing.
