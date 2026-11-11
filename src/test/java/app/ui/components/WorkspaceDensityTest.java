package app.ui.components;

import app.services.ThemeService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R-UI-14 / R-UI-12: workspace controls have the IDE density and scale with the UI font size. */
class WorkspaceDensityTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    private record Sizes(double button, double header, double toolbar) {}

    private static Sizes measure(int fontPx) throws Exception {
        AtomicReference<Sizes> ref = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                WorkspaceToolbar toolbar = new WorkspaceToolbar();
                Button btn = new AppButton("Lưu", "fth-save");
                toolbar.addLeft(btn);
                PaneHeader header = new PaneHeader("Dữ liệu", "fth-grid");
                VBox root = new VBox(toolbar, header);
                root.getStyleClass().add("workspace-root");
                Scene scene = new Scene(root, 800, 300);
                ThemeService.getInstance().applyThemeToScene(scene);
                root.setStyle("-fx-font-size: " + fontPx + "px;");
                root.applyCss();
                root.layout();
                ref.set(new Sizes(btn.getHeight(), header.getHeight(), toolbar.getHeight()));
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        assertNull(err.get(), () -> "measure failed: " + err.get());
        return ref.get();
    }

    @Test
    void controlsHaveIdeDensityAtDefaultFontSize() throws Exception {
        Sizes s = measure(13);
        assertEquals(24, s.button(), 3, "toolbar button height at 13px");
        assertEquals(28, s.header(), 2, "pane header height at 13px");
        assertEquals(32, s.toolbar(), 5, "toolbar height at 13px");
    }

    @Test
    void controlsGrowWithTheUiFontSize() throws Exception {
        Sizes small = measure(13);
        Sizes large = measure(18);
        assertTrue(large.button() > small.button() + 5, "button must grow with the font: " + small.button() + " -> " + large.button());
        assertTrue(large.header() > small.header() + 5, "pane header must grow with the font: " + small.header() + " -> " + large.header());
        assertTrue(large.toolbar() > small.toolbar() + 5, "toolbar must grow with the font: " + small.toolbar() + " -> " + large.toolbar());
    }

    @Test
    void iconsFollowTheIconSizeSettingAndAreNotRetained() throws Exception {
        int before = UiMetrics.getBaseIconSize();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                var md = Icons.of("fth-save");
                var sm = Icons.of("fth-save", IconScale.SM);
                UiMetrics.setBaseIconSize(20);
                assertEquals(20, md.getIconSize());
                assertEquals(18, sm.getIconSize());

                // the shared binding must not keep icons alive (they live in recycled cells and closed tabs)
                java.lang.ref.WeakReference<Object> weak = new java.lang.ref.WeakReference<>(Icons.of("fth-x"));
                for (int i = 0; i < 20 && weak.get() != null; i++) {
                    System.gc();
                    Thread.sleep(50);
                }
                assertNull(weak.get(), "an icon created by Icons.of must be collectable");
            } catch (Throwable t) {
                err.set(t);
            } finally {
                UiMetrics.setBaseIconSize(before);
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS));
        assertNull(err.get(), () -> "icon check failed: " + err.get());
    }
}
