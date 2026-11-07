package app.ui.results;

import javafx.application.Platform;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** D4: stacked result grids share the panel, the last fills, minimized ones dock at the bottom (LLTool). */
class StackedGridsPaneTest {

    @BeforeAll
    static void fx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {
        }
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch l = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(c.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                l.countDown();
            }
        });
        assertTrue(l.await(5, TimeUnit.SECONDS));
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    private static StackedGridsPane.Entry add(StackedGridsPane p) {
        VBox box = new VBox();
        Region body = new Region();
        box.getChildren().add(body);
        return p.add(box, body);
    }

    @Test
    void gridsShareTheHeightAndMinimizedOnesDockAtTheBottom() throws Exception {
        double[] r = onFx(() -> {
            StackedGridsPane p = new StackedGridsPane();
            var a = add(p);
            var b = add(p);
            var c = add(p);
            p.resize(800, 612);
            p.layout();
            double[] out = new double[8];
            out[0] = a.pane.getHeight();
            out[1] = b.pane.getHeight();
            out[2] = c.pane.getHeight();
            out[3] = c.pane.getLayoutY() + c.pane.getHeight();
            p.setMinimized(b, true);
            p.layout();
            out[4] = b.pane.getHeight();
            out[5] = b.pane.getLayoutY();
            out[6] = c.pane.getLayoutY() + c.pane.getHeight();
            out[7] = a.pane.getHeight();
            return out;
        });
        // 612 - 2 splitters * 6 = 600 → 200 each
        assertEquals(200, r[0], 0.5);
        assertEquals(200, r[1], 0.5);
        assertEquals(612, r[3], 0.5, "the last grid reaches the bottom");
        assertEquals(StackedGridsPane.HEADER_H, r[4], 0.5, "minimized = header only");
        assertEquals(612 - StackedGridsPane.HEADER_H, r[5], 0.5, "docked at the bottom");
        assertEquals(612 - StackedGridsPane.HEADER_H, r[6], 0.5, "the last open grid fills up to the docked header");
        assertEquals(200, r[7], 0.5, "the first grid keeps its height");
    }
}
