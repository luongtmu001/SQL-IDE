package spike.grid;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** S1 check: both grids open each scenario and show the data; pictures go to target/spike-*.png. */
class SpikeSmokeTest {

    private static final Path REPORT = Path.of("target", "spike-check.txt");

    @BeforeAll
    static void init() throws Exception {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, "=== S1 check " + java.time.LocalDateTime.now() + System.lineSeparator());
        CountDownLatch l = new CountDownLatch(1);
        try {
            Platform.startup(l::countDown);
        } catch (IllegalStateException e) {
            l.countDown();
        }
        Platform.setImplicitExit(false);
        assertTrue(l.await(5, TimeUnit.SECONDS));
    }

    private static void report(String s) throws Exception {
        Files.writeString(REPORT, s + System.lineSeparator(), StandardOpenOption.APPEND);
    }

    private static <T> T fx(Callable<T> c) throws Exception {
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
        assertTrue(l.await(180, TimeUnit.SECONDS));
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    private static void png(Parent node, String name) throws Exception {
        WritableImage img = node.snapshot(new SnapshotParameters(), null);
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var pr = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
        ImageIO.write(bi, "png", new File("target/" + name));
    }

    private String open(SpikeData.Scenario s, boolean canvas) throws Exception {
        SpikeData data = SpikeData.of(s);
        return fx(() -> {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            StackPane root = new StackPane();
            Scene scene = new Scene(root, 1200, 700);
            scene.getStylesheets().add(SpikeApp.class.getResource("/spike/spike.css").toExternalForm());
            Stage st = new Stage();
            st.setScene(scene);
            st.setX(-4000);
            st.show();
            long t0 = System.nanoTime();
            root.getChildren().setAll(canvas ? SpikeApp.canvasGrid(data) : TableViewGrid.build(data));
            root.applyCss();
            root.layout();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String base = "spike-" + s.name() + "-" + (canvas ? "canvas" : "tableview");
            png(root, base + ".png");
            if (canvas && s.rows > 0) {
                CanvasGrid g = (CanvasGrid) root.getChildren().get(0);
                g.verticalBar().setValue(g.verticalBar().getMax() / 2);
                g.horizontalBar().setValue(g.horizontalBar().getMax() / 2);
                g.paint();
                png(root, base + "-scrolled.png");
            }
            st.close();
            return String.format("%s %-9s show %5d ms", s.name(), canvas ? "Canvas" : "TableView", ms);
        });
    }

    @Test
    void bothGridsOpenEveryScenario() throws Exception {
        for (SpikeData.Scenario s : new SpikeData.Scenario[]{SpikeData.Scenario.K1, SpikeData.Scenario.K2, SpikeData.Scenario.K3}) {
            report(open(s, true));
            report(open(s, false));
        }
        assertTrue(new File("target/spike-K1-canvas.png").exists());
    }
}
