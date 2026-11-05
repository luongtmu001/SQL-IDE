# [Screen Name] — Screen Specification
# Đặc tả màn hình [Tên màn hình]

- Screen ID: `SCR-AREA-001`
- Source: Python + PyWebView
- Target: JavaFX
- Status: `discovered`
- Evidence confidence: `observed / code_verified / inferred / unknown`
- Last inspected: `YYYY-MM-DD`

## 1. Overview / Tổng quan

- Purpose / Mục đích:
- Entry point / Cách mở màn hình:
- Parent screen / Màn hình cha:
- User roles or context / Vai trò hoặc ngữ cảnh:
- Related screens / Màn hình liên quan:

## 2. Visual reference / Tham chiếu hình ảnh

List screenshot filenames, window dimensions, display scale, theme, and capture state.

## 3. Layout / Bố cục

Describe hierarchy, region order, split ratios, fixed/flexible dimensions, min sizes, resize behavior.

## 4. Regions / Các vùng

For each region:
- Region ID:
- Name:
- Purpose:
- Position:
- Child components:
- Visual properties:
- Visibility/collapse behavior:
- Resize/docking behavior:
- Evidence:

## 5. Components and buttons / Component và button

Reference `components.json`. Explain unusual or high-impact controls here.

## 6. Interactions / Tương tác

Reference `interactions.json`. Include keyboard, mouse, drag/drop, navigation, menu, dialog, and state transitions.

## 7. States / Trạng thái

Reference `states.json`. Document empty, loading, error, selected, disabled, expanded, and other relevant states.

## 8. JavaFX design proposal / Đề xuất JavaFX

- Proposed layout:
- Standard JavaFX controls:
- Custom components:
- Retained embedded components:
- Design improvements:
- Compatibility/performance considerations:

## 9. Known gaps and questions / Điểm chưa rõ

List each unknown, impact, evidence checked, and whether it blocks implementation.

## 10. Acceptance criteria / Tiêu chí nghiệm thu

- [ ] All regions represented.
- [ ] All controls accounted for.
- [ ] Interactions mapped.
- [ ] Relevant states represented.
- [ ] Resize behavior reviewed.
- [ ] Mapping completed.
- [ ] User approved specification.
