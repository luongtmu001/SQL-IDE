package app.ui.editor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Phase 0 measurements in the JavaFX WebView: loads the pages of src/test/intellisense-perf from its server, the same
 * pages Edge loads. Results go to target/perf. Needs a display and the server; not part of "mvn test":
 *
 * <pre>
 * node src/test/intellisense-perf/server.js
 * mvnw -Dtest=IntellisensePerfIT test
 * </pre>
 */
class IntellisensePerfIT {

    private static final String BASE = System.getProperty("perf.base", "http://127.0.0.1:8765");

    /** Must stay referenced: WebEngine only holds it weakly. */
    public static final class Sink {
        volatile int length;

        /** Stands in for BridgeCallback.onContentChanged: only the crossing itself is measured. */
        public void sink(String id, String text) {
            length = text.length();
        }
    }

    private static final Sink SINK = new Sink();
    private static WebEngine engine;
    private static Stage stage;

    private static <T> T fx(Callable<T> c) throws Exception {
        FutureTask<T> task = new FutureTask<>(c);
        Platform.runLater(task);
        return task.get(60, TimeUnit.SECONDS);
    }

    private static void runPage(String url, long maxMs) throws Exception {
        fx(() -> {
            engine.executeScript("window.__benchDone = false");   // the previous page's flag until the new page is loaded
            engine.load(BASE + url);
            return null;
        });
        long end = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < end) {
            boolean done;
            try {
                done = Boolean.TRUE.equals(fx(() -> engine.executeScript("window.__benchDone === true")));
            } catch (Exception busy) {
                done = false;
            }
            if (done) {
                Thread.sleep(500);
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out: " + url);
    }

    @Test
    void measure() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(20, TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
        fx(() -> {
            WebView view = new WebView();
            engine = view.getEngine();
            stage = new Stage();
            stage.setScene(new Scene(view, 1100, 620));
            stage.setX(-3000);
            stage.setY(40);
            stage.show();
            engine.getLoadWorker().stateProperty().addListener((o, a, s) -> {
                if (s == Worker.State.SUCCEEDED) {
                    ((JSObject) engine.executeScript("window")).setMember("__javaPerf", SINK);
                }
            });
            return null;
        });
        // -Dperf.pages=engine / editor: only that part
        String pages = System.getProperty("perf.pages", "engine,editor");
        if (pages.contains("engine")) {
            runPage("/bench/engine.html?src=py&browser=webkit", 600_000);
            runPage("/bench/engine.html?src=java&browser=webkit", 600_000);
        }
        if (pages.contains("editor")) runPage("/bench/editor?browser=webkit", 900_000);
        if (pages.contains("breakdown")) runPage("/bench/editor?browser=webkit&mode=breakdown", 900_000);
        if (pages.contains("worker")) runPage("/bench/worker.html?browser=webkit", 150_000);
        if (pages.contains("focus")) runPage("/bench/editor?browser=webkit&mode=focus", 500_000);
        if (pages.contains("listpaint")) runPage("/bench/editor?browser=webkit&mode=focus&part=list", 200_000);
        fx(() -> {
            stage.close();
            return null;
        });
    }
}
