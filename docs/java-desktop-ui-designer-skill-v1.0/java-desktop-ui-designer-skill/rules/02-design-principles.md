# Design Principles
# Nguyên tắc thiết kế

## Design direction / Định hướng

**Hybrid Modern Native** combines:
- VS Code-like workspace architecture: activity/navigation rail, sidebar, tabs, editor/workspace, dockable tool panels, status bar.
- DataGrip-like database workflows: connection explorer, object navigation, query tabs, result grids, database context actions.
- Modern desktop visual language: restrained surfaces, clear hierarchy, consistent spacing, polished states, accessible contrast.

Không sao chép nguyên trạng VS Code, DataGrip hay IntelliJ. Chỉ học hỏi các mẫu tương tác phù hợp với nghiệp vụ.

## Desktop-first rules / Quy tắc ưu tiên desktop

1. Optimize for resizable desktop windows, not responsive web breakpoints.
2. Preserve high information density without making controls cramped.
3. Prefer predictable keyboard and mouse interactions.
4. Use docking, splitting, tabbing, and contextual actions where they improve IDE workflows.
5. Avoid oversized cards, excessive whitespace, decorative gradients, and mobile-style navigation.
6. Make primary actions discoverable; place secondary actions in menus or context menus.
7. Keep destructive actions visually distinct and require confirmation when the current application does so or when risk warrants it.
8. Provide visible focus indicators and keyboard accessibility.

## Layout principles / Nguyên tắc bố cục

- Define regions and ownership before styling.
- Use flexible constraints for resizable areas; avoid hardcoding every pixel.
- Distinguish fixed, preferred, minimum, and flexible dimensions.
- Record split-pane ratios and minimum sizes from observation when possible.
- Do not collapse or remove regions just to make a screenshot look cleaner.
- Document behavior when panels are hidden, docked, floated, or resized.

## Visual consistency / Nhất quán thị giác

All screens share one design system for color roles, typography, spacing, corner radii, borders, focus, shadows, icons, and motion. Screen-specific styling requires a documented reason.

## Native mapping / Ánh xạ native

Prefer JavaFX controls when they meet the interaction and performance requirements. Use custom JavaFX components when the required behavior or appearance cannot be achieved cleanly with standard controls. Do not select a control solely because its name resembles the web component.

Examples:
- Navigation tree → `TreeView` or a custom virtualized tree if required.
- Split workspace → `SplitPane`.
- Tabs → `TabPane` or a custom tab strip if IDE-specific behavior is required.
- Dialog → `Dialog` / `DialogPane`.
- Context actions → `ContextMenu`.
- Data grid → evaluate `TableView` and specialized grid options against sorting, editing, virtualization, multi-result, and large-data requirements.
- SQL editor → retain the approved editor technology; do not assume JavaFX TextArea is an equivalent replacement.
