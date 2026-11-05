# Component Rules
# Quy tắc component

## Component catalog coverage

Catalog every interactive or meaningful visual element:
- Buttons, icon buttons, split buttons.
- Main menu items and nested menu items.
- Toolbar actions.
- Context-menu actions.
- Tabs and tab controls.
- Inputs, selectors, search boxes.
- Tree nodes and expand/collapse controls.
- Grid headers, row actions, cell editors, pagination.
- Dialog controls.
- Status indicators and clickable status items.
- Drag handles, splitters, resize handles.
- Keyboard shortcuts and command palette actions.

## Button specification / Đặc tả button

Every button must record:
- Stable ID.
- Visible label and accessible name.
- Icon and icon source.
- Screen region and parent component.
- Tooltip.
- Primary/secondary/destructive classification.
- Enabled/disabled conditions.
- Hover, pressed, focused, selected states where relevant.
- Click or keyboard event.
- Verified outcome.
- Confirmation behavior.
- Shortcut.
- Evidence and confidence.
- JavaFX target mapping.

Do not infer behavior from label or icon. Trace the handler and backend call where possible.

## Menu specification / Đặc tả menu

Record hierarchy, order, separators, accelerators, checked state, enabled state, submenu, and action result. Include menus that appear only on right-click or under a particular selection.

## Component reuse / Tái sử dụng

Identify repeated patterns and propose shared components. Do not prematurely abstract components that have materially different behavior. Shared components must expose documented properties and states.

## Accessibility

Every icon-only control needs an accessible name and tooltip where appropriate. Ensure focus visibility, keyboard reachability, adequate contrast, and non-color-only status communication.
