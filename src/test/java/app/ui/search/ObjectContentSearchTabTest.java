package app.ui.search;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ObjectContentSearchTab Component Tests")
class ObjectContentSearchTabTest {

    @BeforeAll
    static void initJavaFX() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException ignored) {
            Platform.setImplicitExit(false);
            latch.countDown();
        }
        assertTrue(latch.await(20, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("ObjectContentSearchTab initializes controls and toolbar cleanly")
    void testTabInitialization() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ObjectContentSearchTab> tabRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ConnectionProfileDto profile = new ConnectionProfileDto(
                        UUID.randomUUID().toString(),
                        "Test Search Conn",
                        "Default",
                        "Dev",
                        false,
                        "SQLSERVER",
                        "localhost",
                        1433,
                        "master",
                        null,
                        "sa",
                        "SQL_PASSWORD",
                        null,
                        false,
                        30,
                        true,
                        Collections.emptyMap(),
                        null,
                        null
                );

                ObjectContentSearchTab tab = new ObjectContentSearchTab(profile, "master", "dbo", a -> {});
                tabRef.set(tab);

                assertNotNull(tab.getSearchField());
                assertNotNull(tab.getBtnMatchCase());
                assertNotNull(tab.getBtnWholeWord());
                assertNotNull(tab.getBtnRegex());
                assertNotNull(tab.getHitTreeView());
                assertNotNull(tab.getOccurrencesTable());
                assertNotNull(tab.getBtnToggleOccurrences());
                assertNotNull(tab.getDetailSplitPane());
                assertNotNull(tab.getDdlEditor());

                // Default toggles are unselected
                assertFalse(tab.getBtnMatchCase().isSelected());
                assertFalse(tab.getBtnWholeWord().isSelected());
                assertFalse(tab.getBtnRegex().isSelected());
                assertTrue(tab.getBtnToggleOccurrences().isSelected());

                // Toggle them
                tab.getBtnMatchCase().setSelected(true);
                assertTrue(tab.getBtnMatchCase().isSelected());

                tab.getBtnRegex().setSelected(true);
                assertTrue(tab.getBtnRegex().isSelected());

                // Occurrences panel toggle
                tab.getBtnToggleOccurrences().setSelected(false);
                tab.getBtnToggleOccurrences().fire();

            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
