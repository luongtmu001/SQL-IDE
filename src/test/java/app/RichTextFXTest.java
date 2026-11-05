package app;

import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RichTextFXTest {

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testRichTextFXAvailability() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                CodeArea codeArea = new CodeArea("SELECT * FROM users;");
                assertNotNull(codeArea);
                codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));

                StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
                builder.add(Collections.singleton("sql-keyword"), 6);
                builder.add(Collections.emptyList(), 14);
                StyleSpans<Collection<String>> spans = builder.create();
                assertNotNull(spans);
                success.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(success.get());
    }
}
