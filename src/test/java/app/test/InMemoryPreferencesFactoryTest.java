package app.test;

import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPreferencesFactoryTest {

    @Test
    void testInMemoryPreferencesIsolation() {
        Preferences prefs = Preferences.userNodeForPackage(InMemoryPreferencesFactoryTest.class).node("isolation_test");
        prefs.put("test_key", "test_val");
        assertEquals("test_val", prefs.get("test_key", null));

        prefs.remove("test_key");
        assertNull(prefs.get("test_key", null));
    }
}
