package app.test;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * In-memory implementation of {@link PreferencesFactory} for unit and integration tests.
 * Keeps all test preferences inside JVM memory and completely prevents reading or writing
 * to the Windows Registry (HKCU\Software\JavaSoft\Prefs) or user home preferences.
 */
public class InMemoryPreferencesFactory implements PreferencesFactory {

    private static final InMemoryPreferences SYSTEM_ROOT = new InMemoryPreferences(null, "");
    private static final InMemoryPreferences USER_ROOT = new InMemoryPreferences(null, "");

    public InMemoryPreferencesFactory() {
    }

    @Override
    public Preferences systemRoot() {
        return SYSTEM_ROOT;
    }

    @Override
    public Preferences userRoot() {
        return USER_ROOT;
    }

    /**
     * Clears all system and user preferences from memory.
     */
    public static void reset() {
        SYSTEM_ROOT.clearAll();
        USER_ROOT.clearAll();
    }

    public static class InMemoryPreferences extends AbstractPreferences {
        private final Map<String, String> map = new HashMap<>();
        private final Map<String, InMemoryPreferences> children = new HashMap<>();

        public InMemoryPreferences(AbstractPreferences parent, String name) {
            super(parent, name);
        }

        public synchronized void clearAll() {
            map.clear();
            for (InMemoryPreferences child : children.values()) {
                child.clearAll();
            }
            children.clear();
        }

        @Override
        protected synchronized void putSpi(String key, String value) {
            map.put(key, value);
        }

        @Override
        protected synchronized String getSpi(String key) {
            return map.get(key);
        }

        @Override
        protected synchronized void removeSpi(String key) {
            map.remove(key);
        }

        @Override
        protected synchronized void removeNodeSpi() throws BackingStoreException {
            map.clear();
            children.clear();
        }

        @Override
        protected synchronized String[] keysSpi() throws BackingStoreException {
            return map.keySet().toArray(new String[0]);
        }

        @Override
        protected synchronized String[] childrenNamesSpi() throws BackingStoreException {
            return children.keySet().toArray(new String[0]);
        }

        @Override
        protected synchronized AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, n -> new InMemoryPreferences(this, n));
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }
    }
}

