package app.ui.window.platform;

import com.sun.jna.CallbackReference;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import javafx.stage.Stage;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Windows-specific adapter that turns an UNDECORATED JavaFX stage into a real
 * Win32 "frameless" window (same technique used by VS Code / Electron, Windows Terminal).
 *
 * <p>The window procedure of the stage is sub-classed so that:
 * <ul>
 *   <li>{@code WM_NCCALCSIZE} removes the native title bar / borders (client area = whole window).</li>
 *   <li>{@code WM_NCHITTEST} tells Windows which pixels are the caption (drag area),
 *       resize borders and the maximize button. Windows itself then provides:
 *       dragging, Aero Snap (drag to edge/corner with preview), double-click maximize,
 *       Win+Arrow snapping, Win11 Snap Layouts flyout and the system menu.</li>
 *   <li>{@code WM_GETMINMAXINFO} limits the maximized size to the monitor work area so
 *       the window never covers the taskbar.</li>
 * </ul>
 *
 * <p>All callbacks are executed on the JavaFX Application Thread (Glass pumps the Win32
 * message loop on that thread), so it is safe to query the scene graph from them.
 */
public final class WindowsWindowAdapter {

    // ---- Win32 constants ----
    private static final int GWL_STYLE = -16;
    private static final int GWLP_WNDPROC = -4;

    private static final int WS_POPUP = 0x80000000;
    private static final int WS_CAPTION = 0x00C00000;
    private static final int WS_SYSMENU = 0x00080000;
    private static final int WS_THICKFRAME = 0x00040000;
    private static final int WS_MINIMIZEBOX = 0x00020000;
    private static final int WS_MAXIMIZEBOX = 0x00010000;
    private static final int WS_MAXIMIZE = 0x01000000;

    private static final int WM_SIZE = 0x0005;
    private static final int WM_GETMINMAXINFO = 0x0024;
    private static final int WM_NCDESTROY = 0x0082;
    private static final int WM_NCCALCSIZE = 0x0083;
    private static final int WM_NCHITTEST = 0x0084;
    private static final int WM_NCMOUSEMOVE = 0x00A0;
    private static final int WM_NCLBUTTONDOWN = 0x00A1;
    private static final int WM_NCLBUTTONUP = 0x00A2;
    private static final int WM_NCLBUTTONDBLCLK = 0x00A3;
    private static final int WM_NCRBUTTONUP = 0x00A5;
    private static final int WM_SYSCOMMAND = 0x0112;
    private static final int WM_NCMOUSELEAVE = 0x02A2;

    // System menu commands / flags
    private static final int SC_SIZE = 0xF000;
    private static final int SC_MOVE = 0xF010;
    private static final int SC_MINIMIZE = 0xF020;
    private static final int SC_MAXIMIZE = 0xF030;
    private static final int SC_CLOSE = 0xF060;
    private static final int SC_KEYMENU = 0xF100;
    private static final int SC_RESTORE = 0xF120;
    private static final int MF_BYCOMMAND = 0x00000000;
    private static final int MF_ENABLED = 0x00000000;
    private static final int MF_GRAYED = 0x00000001;
    private static final int TPM_RIGHTBUTTON = 0x0002;
    private static final int TPM_RETURNCMD = 0x0100;

    private static final int SIZE_RESTORED = 0;
    private static final int SIZE_MINIMIZED = 1;
    private static final int SIZE_MAXIMIZED = 2;

    private static final int HTCLIENT = 1;
    private static final int HTCAPTION = 2;
    private static final int HTMAXBUTTON = 9;
    private static final int HTLEFT = 10;
    private static final int HTRIGHT = 11;
    private static final int HTTOP = 12;
    private static final int HTTOPLEFT = 13;
    private static final int HTTOPRIGHT = 14;
    private static final int HTBOTTOM = 15;
    private static final int HTBOTTOMLEFT = 16;
    private static final int HTBOTTOMRIGHT = 17;

    private static final int SW_MAXIMIZE = 3;
    private static final int SW_MINIMIZE = 6;
    private static final int SW_RESTORE = 9;

    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

    private static final int MONITOR_DEFAULTTONEAREST = 2;

    private static final int TME_LEAVE = 0x00000002;
    private static final int TME_NONCLIENT = 0x00000010;

    /** Resize border thickness in logical (DIP) pixels (used for the top edge, and as fallback). */
    private static final double RESIZE_BORDER_DIP = 6;

    /**
     * When true, the restored window keeps the standard (invisible on Win10/11) left/right/bottom
     * resize frame OUTSIDE the visible content, exactly like Windows Terminal / native apps, so the
     * resize band never overlaps scrollbars or content. Only the top edge is drawn by the app and
     * gets an in-client resize band. Set to false to restore the previous "client == window" mode.
     */
    private static final boolean NATIVE_OUTER_RESIZE_FRAME = true;

    /** Minimum time between two system-menu popups (avoids double popups from Alt+Space). */
    private static final long SYSTEM_MENU_DEBOUNCE_MS = 300;

    /** Hit-test callbacks working in JavaFX scene coordinates. */
    public interface HitTester {
        /** @return true if the scene point is an empty, draggable area of the title bar. */
        boolean isCaption(double sceneX, double sceneY);

        /** @return true if the scene point lies on the maximize / restore button. */
        boolean isMaximizeButton(double sceneX, double sceneY);
    }

    /** Receives native maximize / restore / minimize notifications. */
    public interface StateListener {
        void onMaximizedChanged(boolean maximized);
    }

    /** Hover / pressed visual feedback for the maximize button (Windows owns its mouse input). */
    public interface MaximizeButtonFeedback {
        void setHover(boolean hover);
        void setPressed(boolean pressed);
        void fire();
    }

    private final Stage stage;
    private final HWND hwnd;
    private final HitTester hitTester;
    private final StateListener stateListener;
    private final MaximizeButtonFeedback maxButtonFeedback;

    // Strong reference: the native side keeps only a raw pointer to this callback.
    private final WinUser.WindowProc windowProc = this::windowProc;
    private Pointer defaultWindowProc;
    private boolean maxButtonHover;
    private boolean maxButtonPressed;
    private boolean trackingNcLeave;
    private long lastSystemMenuTime;

    private WindowsWindowAdapter(Stage stage, HWND hwnd, HitTester hitTester,
                                 StateListener stateListener, MaximizeButtonFeedback feedback) {
        this.stage = stage;
        this.hwnd = hwnd;
        this.hitTester = hitTester;
        this.stateListener = stateListener;
        this.maxButtonFeedback = feedback;
    }

    public static boolean isSupported() {
        return Platform.isWindows() && Platform.is64Bit();
    }

    /**
     * Installs the native frameless behaviour on an already shown stage.
     *
     * @return the adapter, or {@code null} when not supported / installation failed
     *         (callers should then fall back to the pure JavaFX drag & resize handlers).
     */
    public static WindowsWindowAdapter install(Stage stage, HitTester hitTester,
                                               StateListener stateListener,
                                               MaximizeButtonFeedback feedback,
                                               Consumer<Throwable> errorHandler) {
        if (!isSupported() || stage == null || !stage.isShowing()) {
            return null;
        }
        try {
            HWND hwnd = findStageHwnd(stage);
            if (hwnd == null) {
                return null;
            }
            WindowsWindowAdapter adapter = new WindowsWindowAdapter(stage, hwnd, hitTester, stateListener, feedback);
            adapter.attach();
            return adapter;
        } catch (Throwable t) {
            if (errorHandler != null) errorHandler.accept(t);
            return null;
        }
    }

    /** Finds the HWND of the stage by temporarily giving it a unique title. */
    private static HWND findStageHwnd(Stage stage) {
        String originalTitle = stage.getTitle();
        boolean titleBound = stage.titleProperty().isBound();
        if (titleBound) {
            // Cannot rename temporarily; try the current title directly.
            return findOwnWindow(originalTitle);
        }
        String marker = "sqlide-" + UUID.randomUUID();
        try {
            stage.setTitle(marker);
            return findOwnWindow(marker);
        } finally {
            stage.setTitle(originalTitle);
        }
    }

    private static HWND findOwnWindow(String title) {
        if (title == null) return null;
        HWND hwnd = User32.INSTANCE.FindWindow(null, title);
        if (hwnd == null) return null;
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        return pid.getValue() == Kernel32.INSTANCE.GetCurrentProcessId() ? hwnd : null;
    }

    private void attach() {
        User32 u32 = User32.INSTANCE;

        // Give the popup window a full standard frame so Windows enables snapping,
        // Win+Arrow, minimize/maximize animations and the system menu.
        int style = u32.GetWindowLong(hwnd, GWL_STYLE);
        style &= ~WS_POPUP;
        style |= WS_CAPTION | WS_SYSMENU | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
        u32.SetWindowLong(hwnd, GWL_STYLE, style);

        defaultWindowProc = u32.SetWindowLongPtr(hwnd, GWLP_WNDPROC,
                CallbackReference.getFunctionPointer(windowProc));

        // Drop shadow + Win11 rounded corners for the frameless window.
        try {
            Dwmapi.Margins margins = new Dwmapi.Margins();
            margins.cyTopHeight = 1;
            Dwmapi.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, margins);
        } catch (Throwable ignored) {
            // DWM not available - purely cosmetic.
        }

        // Force WM_NCCALCSIZE so the native caption disappears immediately.
        u32.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
    }

    // ------------------------------------------------------------------ public API

    public void minimize() {
        User32.INSTANCE.ShowWindow(hwnd, SW_MINIMIZE);
    }

    public void toggleMaximize() {
        User32.INSTANCE.ShowWindow(hwnd, isMaximized() ? SW_RESTORE : SW_MAXIMIZE);
    }

    public boolean isMaximized() {
        return (User32.INSTANCE.GetWindowLong(hwnd, GWL_STYLE) & WS_MAXIMIZE) != 0;
    }

    /**
     * Shows the native Windows system menu (Restore / Move / Size / Minimize / Maximize / Close)
     * just below the top-left corner of the client area, like Alt+Space in a native window.
     */
    public void showSystemMenuAtCaption() {
        POINT p = new POINT(0, 0);
        User32Ex.INSTANCE.ClientToScreen(hwnd, p);
        double scaleY = stage.getOutputScaleY() > 0 ? stage.getOutputScaleY() : 1.0;
        showSystemMenu(p.x, p.y + (int) Math.round(34 * scaleY));
    }

    /** Shows the native system menu at the given screen position (physical pixels). */
    public void showSystemMenu(int screenX, int screenY) {
        long now = System.currentTimeMillis();
        if (now - lastSystemMenuTime < SYSTEM_MENU_DEBOUNCE_MS) return;
        lastSystemMenuTime = now;

        User32Ex u = User32Ex.INSTANCE;
        Pointer menu = u.GetSystemMenu(hwnd, false);
        if (menu == null) return;

        boolean maximized = isMaximized();
        u.EnableMenuItem(menu, SC_RESTORE, MF_BYCOMMAND | (maximized ? MF_ENABLED : MF_GRAYED));
        u.EnableMenuItem(menu, SC_MOVE, MF_BYCOMMAND | (maximized ? MF_GRAYED : MF_ENABLED));
        u.EnableMenuItem(menu, SC_SIZE, MF_BYCOMMAND | (maximized || !stage.isResizable() ? MF_GRAYED : MF_ENABLED));
        u.EnableMenuItem(menu, SC_MINIMIZE, MF_BYCOMMAND | MF_ENABLED);
        u.EnableMenuItem(menu, SC_MAXIMIZE, MF_BYCOMMAND | (maximized || !stage.isResizable() ? MF_GRAYED : MF_ENABLED));
        u.EnableMenuItem(menu, SC_CLOSE, MF_BYCOMMAND | MF_ENABLED);
        u.SetMenuDefaultItem(menu, SC_CLOSE, 0);

        int cmd = u.TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON, screenX, screenY, 0, hwnd, null);
        if (cmd != 0) {
            User32.INSTANCE.PostMessage(hwnd, WM_SYSCOMMAND, new WPARAM(cmd), new LPARAM(0));
        }
    }

    // ------------------------------------------------------------------ window proc

    private LRESULT windowProc(HWND h, int msg, WPARAM wParam, LPARAM lParam) {
        try {
            switch (msg) {
                case WM_NCCALCSIZE:
                    if (wParam.intValue() != 0) {
                        if (isMaximized()) {
                            WinUser.HMONITOR monitor = User32.INSTANCE.MonitorFromWindow(h, MONITOR_DEFAULTTONEAREST);
                            if (monitor != null) {
                                WinUser.MONITORINFO mi = new WinUser.MONITORINFO();
                                if (User32.INSTANCE.GetMonitorInfo(monitor, mi).booleanValue()) {
                                    RECT work = mi.rcWork;
                                    RECT mon = mi.rcMonitor;
                                    int bottom = work.bottom;
                                    // Auto-hide taskbar: work area == monitor area. Leave 1px so the
                                    // taskbar can still be revealed by moving the pointer to the edge.
                                    if (work.left == mon.left && work.top == mon.top
                                            && work.right == mon.right && work.bottom == mon.bottom) {
                                        bottom -= 1;
                                    }
                                    Pointer p = new Pointer(lParam.longValue());
                                    p.setInt(0, work.left);
                                    p.setInt(4, work.top);
                                    p.setInt(8, work.right);
                                    p.setInt(12, bottom);
                                    return new LRESULT(0);
                                }
                            }
                        }
                        if (NATIVE_OUTER_RESIZE_FRAME) {
                            // Let Windows compute the standard frame (left/right/bottom resize borders,
                            // invisible on Win10/11), then reclaim the caption area at the top.
                            Pointer p = new Pointer(lParam.longValue());
                            int originalTop = p.getInt(4);
                            LRESULT r = callDefault(h, msg, wParam, lParam);
                            p.setInt(4, originalTop);
                            return r;
                        }
                        // Client area == window rect: no native title bar or borders.
                        return new LRESULT(0);
                    }
                    break;

                case WM_NCHITTEST: {
                    int hit = hitTest(h, msg, wParam, lParam);
                    updateMaxButtonHover(hit == HTMAXBUTTON);
                    return new LRESULT(hit);
                }

                case WM_NCRBUTTONUP:
                    if (wParam.intValue() == HTCAPTION) {
                        long lp = lParam.longValue();
                        showSystemMenu((short) (lp & 0xFFFF), (short) ((lp >> 16) & 0xFFFF));
                        return new LRESULT(0);
                    }
                    break;

                case WM_SYSCOMMAND:
                    // Alt+Space arrives as SC_KEYMENU with lParam == ' '.
                    if ((wParam.intValue() & 0xFFF0) == SC_KEYMENU && lParam.longValue() == ' ') {
                        showSystemMenuAtCaption();
                        return new LRESULT(0);
                    }
                    break;

                case WM_GETMINMAXINFO: {
                    LRESULT result = callDefault(h, msg, wParam, lParam);
                    applyWorkAreaLimits(lParam);
                    return result;
                }

                case WM_SIZE: {
                    int type = wParam.intValue();
                    if (stateListener != null && type != SIZE_MINIMIZED) {
                        stateListener.onMaximizedChanged(type == SIZE_MAXIMIZED);
                    }
                    break;
                }

                case WM_NCMOUSEMOVE:
                    updateMaxButtonHover(wParam.intValue() == HTMAXBUTTON);
                    trackNonClientLeave();
                    if (wParam.intValue() == HTMAXBUTTON) {
                        return new LRESULT(0);
                    }
                    break;

                case WM_NCMOUSELEAVE:
                    trackingNcLeave = false;
                    updateMaxButtonHover(false);
                    setMaxButtonPressed(false);
                    break;

                case WM_NCLBUTTONDOWN:
                case WM_NCLBUTTONDBLCLK:
                    if (wParam.intValue() == HTMAXBUTTON) {
                        // Swallow so DefWindowProc does not paint a classic button.
                        setMaxButtonPressed(true);
                        return new LRESULT(0);
                    }
                    break;

                case WM_NCLBUTTONUP:
                    if (wParam.intValue() == HTMAXBUTTON) {
                        boolean wasPressed = maxButtonPressed;
                        setMaxButtonPressed(false);
                        if (wasPressed) {
                            if (maxButtonFeedback != null) maxButtonFeedback.fire();
                            else toggleMaximize();
                        }
                        return new LRESULT(0);
                    }
                    break;

                case WM_NCDESTROY: {
                    Pointer def = defaultWindowProc;
                    User32.INSTANCE.SetWindowLongPtr(h, GWLP_WNDPROC, def);
                    return User32.INSTANCE.CallWindowProc(def, h, msg, wParam, lParam);
                }

                default:
                    break;
            }
        } catch (Throwable t) {
            // Never let an exception escape into native code.
        }
        return callDefault(h, msg, wParam, lParam);
    }

    private LRESULT callDefault(HWND h, int msg, WPARAM wParam, LPARAM lParam) {
        return User32.INSTANCE.CallWindowProc(defaultWindowProc, h, msg, wParam, lParam);
    }

    private int hitTest(HWND h, int msg, WPARAM wParam, LPARAM lParam) {
        long lp = lParam.longValue();
        int screenX = (short) (lp & 0xFFFF);
        int screenY = (short) ((lp >> 16) & 0xFFFF);

        double scaleX = stage.getOutputScaleX() > 0 ? stage.getOutputScaleX() : 1.0;
        double scaleY = stage.getOutputScaleY() > 0 ? stage.getOutputScaleY() : 1.0;
        boolean canResize = !isMaximized() && stage.isResizable();

        // Point in client coordinates (the JavaFX scene origin is the client origin).
        POINT client = new POINT(screenX, screenY);
        User32Ex.INSTANCE.ScreenToClient(hwnd, client);
        RECT clientRect = new RECT();
        User32.INSTANCE.GetClientRect(hwnd, clientRect);

        if (canResize) {
            if (NATIVE_OUTER_RESIZE_FRAME) {
                // 1a) Left / right / bottom borders live in the native (invisible) frame.
                int def = callDefault(h, msg, wParam, lParam).intValue();
                int by = (int) Math.round(RESIZE_BORDER_DIP * scaleY);
                boolean top = client.y < by;
                if (def == HTLEFT || def == HTTOPLEFT) return (top || def == HTTOPLEFT) ? HTTOPLEFT : HTLEFT;
                if (def == HTRIGHT || def == HTTOPRIGHT) return (top || def == HTTOPRIGHT) ? HTTOPRIGHT : HTRIGHT;
                if (def == HTBOTTOM || def == HTBOTTOMLEFT || def == HTBOTTOMRIGHT) return def;
                // 1b) Top edge is inside the client (the app draws its own caption).
                if (top) {
                    int corner = (int) Math.round(RESIZE_BORDER_DIP * 2 * scaleX);
                    if (client.x < corner) return HTTOPLEFT;
                    if (client.x >= clientRect.right - corner) return HTTOPRIGHT;
                    return HTTOP;
                }
            } else {
                // 1) Legacy mode: resize bands inside the window rect.
                int bx = (int) Math.round(RESIZE_BORDER_DIP * scaleX);
                int by = (int) Math.round(RESIZE_BORDER_DIP * scaleY);
                boolean left = client.x < bx;
                boolean right = client.x >= clientRect.right - bx;
                boolean top = client.y < by;
                boolean bottom = client.y >= clientRect.bottom - by;
                if (top && left) return HTTOPLEFT;
                if (top && right) return HTTOPRIGHT;
                if (bottom && left) return HTBOTTOMLEFT;
                if (bottom && right) return HTBOTTOMRIGHT;
                if (left) return HTLEFT;
                if (right) return HTRIGHT;
                if (top) return HTTOP;
                if (bottom) return HTBOTTOM;
            }
        }

        // 2) Title bar areas (scene coordinates in DIPs).
        if (hitTester != null) {
            double sceneX = client.x / scaleX;
            double sceneY = client.y / scaleY;
            if (hitTester.isMaximizeButton(sceneX, sceneY)) return HTMAXBUTTON;
            if (hitTester.isCaption(sceneX, sceneY)) return HTCAPTION;
        }
        return HTCLIENT;
    }

    /** Restricts the maximized bounds to the work area of the current monitor (keeps taskbar visible). */
    private void applyWorkAreaLimits(LPARAM lParam) {
        Pointer ptr = new Pointer(lParam.longValue());
        MinMaxInfo info = new MinMaxInfo(ptr);

        WinUser.HMONITOR monitor = User32.INSTANCE.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
        if (monitor == null) return;
        WinUser.MONITORINFO mi = new WinUser.MONITORINFO();
        if (!User32.INSTANCE.GetMonitorInfo(monitor, mi).booleanValue()) return;

        RECT work = mi.rcWork;
        RECT mon = mi.rcMonitor;

        int borderX = User32.INSTANCE.GetSystemMetrics(32) + User32.INSTANCE.GetSystemMetrics(92); // SM_CXSIZEFRAME + SM_CXPADDEDBORDER
        int borderY = User32.INSTANCE.GetSystemMetrics(33) + User32.INSTANCE.GetSystemMetrics(92); // SM_CYSIZEFRAME + SM_CXPADDEDBORDER
        if (borderX <= 0) borderX = 8;
        if (borderY <= 0) borderY = 8;

        info.ptMaxPosition.x = work.left - mon.left - borderX;
        info.ptMaxPosition.y = work.top - mon.top - borderY;
        info.ptMaxSize.x = (work.right - work.left) + (2 * borderX);
        info.ptMaxSize.y = (work.bottom - work.top) + (2 * borderY);
        info.write();
    }

    private void updateMaxButtonHover(boolean hover) {
        if (hover == maxButtonHover) return;
        maxButtonHover = hover;
        if (!hover) setMaxButtonPressed(false);
        if (maxButtonFeedback != null) maxButtonFeedback.setHover(hover);
    }

    private void setMaxButtonPressed(boolean pressed) {
        if (pressed == maxButtonPressed) return;
        maxButtonPressed = pressed;
        if (maxButtonFeedback != null) maxButtonFeedback.setPressed(pressed);
    }

    private void trackNonClientLeave() {
        if (trackingNcLeave) return;
        TrackMouseEventStruct tme = new TrackMouseEventStruct();
        tme.cbSize = tme.size();
        tme.dwFlags = TME_LEAVE | TME_NONCLIENT;
        tme.hwndTrack = hwnd;
        tme.dwHoverTime = 0;
        trackingNcLeave = User32Ex.INSTANCE.TrackMouseEvent(tme);
    }

    // ------------------------------------------------------------------ native structs

    @Structure.FieldOrder({"ptReserved", "ptMaxSize", "ptMaxPosition", "ptMinTrackSize", "ptMaxTrackSize"})
    public static class MinMaxInfo extends Structure {
        public POINT ptReserved = new POINT();
        public POINT ptMaxSize = new POINT();
        public POINT ptMaxPosition = new POINT();
        public POINT ptMinTrackSize = new POINT();
        public POINT ptMaxTrackSize = new POINT();

        public MinMaxInfo(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"cbSize", "dwFlags", "hwndTrack", "dwHoverTime"})
    public static class TrackMouseEventStruct extends Structure {
        public int cbSize;
        public int dwFlags;
        public HWND hwndTrack;
        public int dwHoverTime;
    }

    interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean TrackMouseEvent(TrackMouseEventStruct lpEventTrack);

        boolean ScreenToClient(HWND hWnd, POINT lpPoint);

        boolean ClientToScreen(HWND hWnd, POINT lpPoint);

        Pointer GetSystemMenu(HWND hWnd, boolean bRevert);

        int EnableMenuItem(Pointer hMenu, int uIDEnableItem, int uEnable);

        boolean SetMenuDefaultItem(Pointer hMenu, int uItem, int fByPos);

        int TrackPopupMenu(Pointer hMenu, int uFlags, int x, int y, int nReserved, HWND hWnd, Pointer prcRect);
    }

    interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class, W32APIOptions.DEFAULT_OPTIONS);

        int DwmExtendFrameIntoClientArea(HWND hwnd, Margins margins);

        @Structure.FieldOrder({"cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight"})
        class Margins extends Structure implements Structure.ByReference {
            public int cxLeftWidth;
            public int cxRightWidth;
            public int cyTopHeight;
            public int cyBottomHeight;
        }
    }

    /** Convenience predicate type for callers building a {@link HitTester}. */
    public static HitTester hitTester(BiPredicate<Double, Double> caption, BiPredicate<Double, Double> maxButton) {
        return new HitTester() {
            @Override public boolean isCaption(double x, double y) { return caption.test(x, y); }
            @Override public boolean isMaximizeButton(double x, double y) { return maxButton.test(x, y); }
        };
    }
}

