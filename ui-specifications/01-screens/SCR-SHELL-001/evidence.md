# Evidence Log — Window Shell & Title Bar (SCR-SHELL-001)
# Nhật ký bằng chứng

## Runtime observations / Quan sát khi chạy (2026-11-04)

| ID | Observation | Screenshot | Confidence |
|---|---|---|---|
| EVD-001 | Source: native Windows caption "luoBTool IDE" + navbar row (logo, File/Edit/Format/View/Settings/Help, Snippets, Sheets, BRAVO Tool, theme, settings, DEV badge) | `source-main-titlebar-1456x816.jpg` | observed |
| EVD-002 | Source navbar dropdowns could not be captured (rendered by `msedgewebview2.exe`, not grantable) | — | observed (limitation) |
| EVD-010 | Target restored window has Win11 rounded corners + shadow | `target-corner-left.jpg`, `target-corner-right.jpg` | observed |
| EVD-011 | Target at ~1075 px width: menu titles truncated to "…", "…", "V…", "Navig…", "T…", "Win…", "H…" | `target-narrow-menu-ellipsis.jpg` | observed |
| EVD-012 | Target double-click on empty caption maximizes; taskbar visible; restore icon shown; double-click restores | `target-maximized.jpg`, `target-maximized-taskbar.jpg` | observed |
| EVD-013 | Target drag on caption moves window | `target-after-drag.jpg` | observed |
| EVD-014 | Target right-click on empty caption: no system menu appeared | — | observed |
| EVD-015 | Target right-edge drag resizes window | `target-narrow-1075.jpg` | observed |
| EVD-016 | Target close button hover turns red | `target-close-hover.jpg` | observed |
| EVD-017 | Target taskbar click minimizes/restores window | — | observed |
| EVD-018 | Hover on target maximize button for 2 s: no Snap Layouts flyout visible in capture | — | observed (possibly masked shell UI) |
| EVD-019 | Alt+Space on this machine opens ChatGPT desktop (global hotkey) — system menu shortcut untestable | — | observed |

## Source-code references / Tham chiếu mã nguồn

| ID | File | Symbol | Finding | Confidence |
|---|---|---|---|---|
| EVD-003 | `templates/index.html:139–368` | navbar | Menu items, icons, shortcuts (see components.json) | code_verified |
| EVD-004 | `static/js/autohide-navbar.js`, `#ide-top-hover-sensor` | auto-hide | Navbar hides; revealed by top-edge hover; setting `appearance.autoHideNavbar` | code_verified |
| EVD-005 | `static/js/app.js:48`, `static/js/theme.js` | theme toggle | Toggle theme + DWM caption recolour (`apply_titlebar_theme`) | code_verified |
| EVD-006 | `static/js/settings.js:2108–2125` | `openSettings()` | Opens sub-window via IPC; modal fallback | code_verified |
| EVD-007 | `app/utils/dwm.py:233` | `apply_dwm_titlebar_theme` | Native caption colour from theme | code_verified |
| EVD-020 | `SQL-IDE/.../WindowsWindowAdapter.java:246–339` | `windowProc` | Handles NCCALCSIZE, NCHITTEST, GETMINMAXINFO, SIZE, NCMOUSE*; **no** WM_NCRBUTTONUP / WM_CONTEXTMENU / SC_KEYMENU handling | code_verified |
| EVD-021 | `WindowsWindowAdapter.java:345–383` | `hitTest` | Resize band `RESIZE_BORDER_DIP = 6` measured **inside** window rect | code_verified |
| EVD-022 | `WindowsWindowAdapter.java:249–268` | NCCALCSIZE | Restored: client = full window rect (no outer invisible frame) | code_verified |
| EVD-023 | `TitleBarController.java:checkMenuResponsiveness` | hamburger | Fixed threshold 950 px; menus can be truncated above it | code_verified |
| EVD-024 | `css/titlebar/titlebar.css` | — | No inactive-window rule | code_verified |
| EVD-025 | `fxml/main-window.fxml:58–170` | MenuBar | Target menu set (File, Edit, View, Navigate, Tools, Window, Help) | code_verified |
| EVD-026 | `target/run-ui.log` | DesktopBootstrap / JDBC | Native adapter installed (no fallback message); startup attempted real JDBC connection to `10.20.30.40:5433/staging_db` (failed); repeated CSS ClassCastException | observed |

## Unknowns / Điểm chưa xác định

| Item | Unknown | Attempts | Impact | Blocking? |
|---|---|---|---|---|
| UNK-001 | Snap Layouts flyout | hover 2 s, screenshot | Medium | No |
| UNK-002 | Alt+Space system menu | blocked by ChatGPT hotkey | Low | No |
| UNK-003 | Display scale | not exposed by tools | Low | No |
| UNK-004 | Exact source caption height | native, varies by DPI | Low | No |

## Safety notes / Lưu ý an toàn

- Source app: only menus, Settings (not saved), dialogs (cancelled) were opened in a second instance, which was then closed. No queries run, no connections tested.
- Target app: not closed via × during test. Only window move, resize, maximize/restore and hover were exercised.
- Target app attempts real DB connections at startup → must be fixed before prototype review (Gate 3).
