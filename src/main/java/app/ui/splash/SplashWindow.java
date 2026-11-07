package app.ui.splash;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * SCR-SPLASH-001: the start-up window. Only the view: {@code StartupSequence} decides what it shows and when it
 * goes away.
 * <p>
 * It is a Swing window on purpose. JavaFX has one UI thread and building the main window blocks it for seconds; on
 * that thread the splash would freeze, its minimize button would not answer and Windows would mark it "not
 * responding". On the AWT thread it stays alive whatever JavaFX is doing. All public methods may be called from any
 * thread; the callbacks of {@link #showError} and {@code onClosed} run on the AWT thread.
 */
public final class SplashWindow {

    // Not WIDTH / HEIGHT: inside the Swing classes below those would mean ImageObserver.WIDTH (1) and HEIGHT (2)
    private static final int WINDOW_WIDTH = 720;
    private static final int WINDOW_HEIGHT = 440;
    private static final int ARC = 24;
    private static final int BAR_HEIGHT = 3;
    private static final int COLUMN_LEFT = 40;
    private static final int COLUMN_WIDTH = 328;
    private static final int ENTRANCE_MILLIS = 400;
    private static final int FADE_OUT_MILLIS = 180;
    private static final int MAX_DETAIL_CHARS = 110;

    private static final String FONT_REGULAR = "Segoe UI";
    private static final String FONT_SEMIBOLD = "Segoe UI Semibold";
    private static final String FONT_MONO = "Consolas";

    private record Palette(Color bg, Color outer, Color inner, Color title, Color text, Color muted, Color status,
                           Color track, Color bar, Color edge, Color hover, Color pressed, Color icon, Color border,
                           Color error, Color errorTitle, Color errorText, Color primaryBg, Color primaryFg) {
    }

    private static final Palette DARK = new Palette(rgb(0x0B1B19), rgb(0x1F7A6E), rgb(0xA6DACB), rgb(0xF2FAF8),
            rgb(0xB4CBC6), rgb(0x8FABA4), rgb(0xE3F1EE), rgb(0x1B2E2B), rgb(0x3FD0B6), new Color(255, 255, 255, 20),
            new Color(255, 255, 255, 26), new Color(255, 255, 255, 46), rgb(0xB4CBC6), rgb(0x47635D),
            rgb(0xFF9389), rgb(0xFFB4AD), rgb(0xD3E3DF), rgb(0xA6DACB), rgb(0x0B1B19));

    private static final Palette LIGHT = new Palette(rgb(0xF1F7F6), rgb(0x1F6F66), rgb(0xA6DACB), rgb(0x123F38),
            rgb(0x3D5A54), rgb(0x4F6B65), rgb(0x123F38), rgb(0xD9E7E4), rgb(0x1F6F66), new Color(0, 0, 0, 20),
            new Color(18, 63, 56, 26), new Color(18, 63, 56, 46), rgb(0x3D5A54), rgb(0x8FABA4),
            rgb(0xB3261E), rgb(0xB3261E), rgb(0x3D5A54), rgb(0x1F6F66), Color.WHITE);

    private static Color rgb(int value) {
        return new Color(value);
    }

    private final Palette palette;
    private final JFrame frame;
    private final Canvas canvas = new Canvas();
    private final JLabel taglineLabel;
    private final JLabel driversLabel;
    private final JLabel statusLabel;
    private final JLabel counterLabel;
    private final JLabel versionLabel;
    private final JLabel errorTitleLabel;
    private final JTextArea errorDetailText = new JTextArea();
    private final FlatButton retryButton;
    private final FlatButton openLogsButton;
    private final FlatButton exitButton;

    private Runnable onRetry = () -> { };
    private Runnable onOpenLogs = () -> { };
    private Runnable onExit = () -> { };
    private boolean errorShown;
    private boolean closedByApp;

    // animation state, AWT thread only
    private long entranceStart;
    private double barWidth;
    private double barTarget;
    private final Timer ticker;

    public SplashWindow(boolean dark, String windowTitle, String appName, String version, Runnable onClosed) {
        palette = dark ? DARK : LIGHT;
        taglineLabel = label("Môi trường phát triển và quản trị cơ sở dữ liệu", FONT_REGULAR, 14, palette.text());
        driversLabel = label("PostgreSQL · SQL Server · SQLite", FONT_MONO, 12, palette.muted());
        statusLabel = label("", FONT_REGULAR, 13, palette.status());
        counterLabel = label("", FONT_MONO, 12, palette.muted());
        counterLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        versionLabel = label("Phiên bản " + version, FONT_REGULAR, 12, palette.muted());
        errorTitleLabel = label("Không khởi động được", FONT_SEMIBOLD, 14, palette.errorTitle());
        errorDetailText.setFont(new Font(FONT_REGULAR, Font.PLAIN, 13));
        errorDetailText.setForeground(palette.errorText());
        errorDetailText.setOpaque(false);
        errorDetailText.setBackground(new Color(0, 0, 0, 0));
        errorDetailText.setEditable(false);
        errorDetailText.setFocusable(false);
        errorDetailText.setLineWrap(true);
        errorDetailText.setWrapStyleWord(true);
        retryButton = new FlatButton("Thử lại", true);
        openLogsButton = new FlatButton("Mở thư mục log", false);
        exitButton = new FlatButton("Thoát", false);
        retryButton.addActionListener(e -> onRetry.run());
        openLogsButton.addActionListener(e -> onOpenLogs.run());
        exitButton.addActionListener(e -> onExit.run());
        ticker = new Timer(16, e -> tick());

        frame = new JFrame(windowTitle);
        frame.setUndecorated(true);
        frame.setBackground(new Color(0, 0, 0, 0)); // per-pixel transparency: smooth rounded corners
        frame.setAlwaysOnTop(true);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setIconImages(loadIcons());
        frame.setContentPane(canvas);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (!closedByApp) onClosed.run(); // closed from the taskbar
            }
        });

        canvas.setLayout(null);
        place(label(appName, FONT_SEMIBOLD, 44, palette.title()), COLUMN_LEFT, 40, COLUMN_WIDTH, 58);
        place(taglineLabel, COLUMN_LEFT, 102, COLUMN_WIDTH, 22);
        place(driversLabel, COLUMN_LEFT, 134, COLUMN_WIDTH, 18);
        place(statusLabel, COLUMN_LEFT, 346, COLUMN_WIDTH - 40, 20);
        place(counterLabel, COLUMN_LEFT, 346, COLUMN_WIDTH, 20);
        place(versionLabel, COLUMN_LEFT, 372, COLUMN_WIDTH, 20);
        place(errorTitleLabel, COLUMN_LEFT + 24, 270, COLUMN_WIDTH - 24, 22);
        place(errorDetailText, COLUMN_LEFT, 296, COLUMN_WIDTH, 56);
        place(retryButton, COLUMN_LEFT, 360, 76, 32);
        place(openLogsButton, COLUMN_LEFT + 84, 360, 128, 32);
        place(exitButton, COLUMN_LEFT + 220, 360, 64, 32);
        place(new MinimizeButton(), WINDOW_WIDTH - 44, 8, 36, 28);
        setErrorVisible(false);
        installDrag();
    }

    private static List<Image> loadIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 32, 48, 256}) {
            var url = SplashWindow.class.getResource("/static/icons/app-logo-" + size + ".png");
            try {
                if (url != null) icons.add(ImageIO.read(url));
            } catch (java.io.IOException ignored) {
                // an icon is not worth failing the start for
            }
        }
        return icons;
    }

    private static JLabel label(String text, String font, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(font, Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    private void place(java.awt.Component c, int x, int y, int w, int h) {
        c.setBounds(x, y, w, h);
        canvas.add(c);
    }

    private void installDrag() {
        MouseAdapter drag = new MouseAdapter() {
            private Point grab;

            @Override
            public void mousePressed(MouseEvent e) {
                grab = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (grab != null) frame.setLocation(e.getXOnScreen() - grab.x, e.getYOnScreen() - grab.y);
            }
        };
        canvas.addMouseListener(drag);
        canvas.addMouseMotionListener(drag);
    }

    public void show() {
        onAwt(() -> {
            frame.setOpacity(0f);
            frame.setVisible(true);
            entranceStart = System.nanoTime();
            ticker.start();
        });
    }

    /** Working on {@code step} of {@code total}: the bar shows the steps already finished. */
    public void showStep(int step, int total, String label) {
        onAwt(() -> {
            setErrorVisible(false);
            statusLabel.setText(label);
            counterLabel.setText(step + "/" + total);
            setBarTarget(WINDOW_WIDTH * (step - 1.0) / total);
        });
    }

    public void showReady(int total) {
        onAwt(() -> {
            setErrorVisible(false);
            statusLabel.setText("Sẵn sàng");
            counterLabel.setText(total + "/" + total);
            setBarTarget(WINDOW_WIDTH);
        });
    }

    /** A failure must not stay hidden on the taskbar: this also brings the window back. */
    public void showError(String detail, Runnable retry, Runnable openLogs, Runnable exit) {
        onAwt(() -> {
            onRetry = retry;
            onOpenLogs = openLogs;
            onExit = exit;
            String shown = detail.length() > MAX_DETAIL_CHARS ? detail.substring(0, MAX_DETAIL_CHARS) + "…" : detail;
            errorDetailText.setText(shown);
            setErrorVisible(true);
            frame.setExtendedState(Frame.NORMAL);
            canvas.repaint();
        });
    }

    private void setErrorVisible(boolean error) {
        errorShown = error;
        driversLabel.setVisible(!error);
        statusLabel.setVisible(!error);
        counterLabel.setVisible(!error);
        versionLabel.setVisible(!error);
        errorTitleLabel.setVisible(error);
        errorDetailText.setVisible(error);
        retryButton.setVisible(error);
        openLogsButton.setVisible(error);
        exitButton.setVisible(error);
    }

    public boolean isMinimized() {
        return (frame.getExtendedState() & Frame.ICONIFIED) != 0;
    }

    /** Takes the window away without reporting it as closed by the user. */
    public void closeWithFade() {
        onAwt(() -> {
            closedByApp = true;
            if (isMinimized()) {
                frame.dispose();
                ticker.stop();
                return;
            }
            long start = System.nanoTime();
            Timer fade = new Timer(16, null);
            fade.addActionListener(e -> {
                float t = (System.nanoTime() - start) / 1_000_000f / FADE_OUT_MILLIS;
                if (t >= 1f) {
                    fade.stop();
                    ticker.stop();
                    frame.dispose();
                } else {
                    frame.setOpacity(1f - t);
                }
            });
            fade.start();
        });
    }

    private static void onAwt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private void setBarTarget(double width) {
        barTarget = width;
        if (!ticker.isRunning()) ticker.start();
    }

    private double entranceProgress() {
        double t = (System.nanoTime() - entranceStart) / 1_000_000.0 / ENTRANCE_MILLIS;
        return t >= 1 ? 1 : t;
    }

    private void tick() {
        double entrance = entranceProgress();
        if (entrance < 1) frame.setOpacity((float) entrance);
        else if (frame.getOpacity() < 1f) frame.setOpacity(1f);
        barWidth += (barTarget - barWidth) * 0.18;
        boolean barSettled = Math.abs(barTarget - barWidth) < 0.5;
        if (barSettled) barWidth = barTarget;
        canvas.repaint();
        if (entrance >= 1 && barSettled) ticker.stop();
    }

    /** Background, the double L of the logo running off the right edge, the bar. */
    private final class Canvas extends JPanel {
        Canvas() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, ARC, ARC);
            g.setColor(palette.bg());
            g.fill(shape);
            g.clip(shape);

            double eased = 1 - Math.pow(1 - entranceProgress(), 3);
            int outerShift = (int) Math.round(24 * (1 - eased));
            int innerShift = (int) Math.round(48 * (1 - eased));
            g.setColor(palette.outer());
            g.fillRect(408 + outerShift, 48, 64, 344);
            g.fillRect(472 + outerShift, 328, 248, 64);
            g.setColor(palette.inner());
            g.fillRect(504 + innerShift, 48, 64, 248);
            g.fillRect(568 + innerShift, 232, 152, 64);

            g.setColor(palette.track());
            g.fillRect(0, WINDOW_HEIGHT - BAR_HEIGHT, WINDOW_WIDTH, BAR_HEIGHT);
            g.setColor(errorShown ? palette.error() : palette.bar());
            g.fillRect(0, WINDOW_HEIGHT - BAR_HEIGHT, (int) Math.round(barWidth), BAR_HEIGHT);

            if (errorShown) paintErrorIcon(g);

            g.setClip(null);
            g.setColor(palette.edge());
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, WINDOW_WIDTH - 1, WINDOW_HEIGHT - 1, ARC, ARC));
            g.dispose();
        }

        private void paintErrorIcon(Graphics2D g) {
            g.setColor(palette.error());
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double x = COLUMN_LEFT;
            double y = 273;
            g.draw(new Ellipse2D.Double(x + 1.5, y + 1.5, 13, 13));
            g.draw(new Line2D.Double(x + 8, y + 4.75, x + 8, y + 8.5));
            g.draw(new Line2D.Double(x + 8, y + 11.25, x + 8, y + 11.26));
        }
    }

    private abstract class BaseButton extends JButton {
        BaseButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        Graphics2D smooth(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            return g;
        }

        void paintState(Graphics2D g) {
            if (getModel().isPressed()) {
                g.setColor(palette.pressed());
            } else if (getModel().isRollover()) {
                g.setColor(palette.hover());
            } else {
                return;
            }
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        }
    }

    private final class FlatButton extends BaseButton {
        private final boolean primary;

        FlatButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(new Font(primary ? FONT_SEMIBOLD : FONT_REGULAR, Font.PLAIN, 13));
            setForeground(primary ? palette.primaryFg() : palette.status());
            setMargin(new java.awt.Insets(0, 0, 0, 0));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            if (primary) {
                g.setColor(palette.primaryBg());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            } else {
                g.setColor(palette.border());
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            paintState(g);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private final class MinimizeButton extends BaseButton {
        MinimizeButton() {
            super("");
            setToolTipText("Thu nhỏ");
            getAccessibleContext().setAccessibleName("Thu nhỏ cửa sổ khởi động");
            addActionListener(e -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            paintState(g);
            g.setColor(palette.icon());
            g.fillRect(getWidth() / 2 - 5, getHeight() / 2, 10, 1);
            g.dispose();
        }
    }
}
