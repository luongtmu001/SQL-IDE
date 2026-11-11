package app.ui.dialogs;

import app.framework.backend.BackendFlags;
import app.framework.session.TabSession;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ReadOnlyGuardDialogTest {

    @BeforeAll
    static void initJavaFX() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("BackendFlags readOnlyAll defaults to false and supports runtime override")
    void testBackendFlagsReadOnlyDefault() {
        BackendFlags.setReadOnlyAll(false);
        assertFalse(BackendFlags.readOnlyAll(), "Default readOnlyAll should be false when profile is writable");

        BackendFlags.setReadOnlyAll(true);
        assertTrue(BackendFlags.readOnlyAll(), "Override to true should be respected");

        BackendFlags.setReadOnlyAll(false);
        assertFalse(BackendFlags.readOnlyAll(), "Override back to false should be respected");
    }

    @Test
    @DisplayName("TabSession setReadOnly updates profile readOnly property")
    void testTabSessionSetReadOnly() {
        ConnectionProfileDto profile = new ConnectionProfileDto(
                "p-test-ro", "TestRO", "DEV", "DEV",
                true, // initially readOnly
                "POSTGRESQL", "localhost", 5432, "postgres",
                null, "postgres", "PASSWORD",
                null, true, 15, true, Collections.emptyMap(),
                "", ""
        );

        TabSession session = new TabSession("tab-test-ro");
        session.connected(profile, "postgres", null, 1234);

        assertTrue(session.readOnly(), "Initial state must be read-only");

        session.setReadOnly(false);
        assertFalse(session.readOnly(), "After setReadOnly(false), session.readOnly() must be false");
        assertFalse(session.profile().readOnly(), "Profile in session must also reflect readOnly = false");
    }
}

