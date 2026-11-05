package app.framework.metadata.filter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter for databases/catalogs under a connection (Phase 6.3).
 */
public class DatabaseFilter {
    private final Set<String> selectedDatabases = new HashSet<>();
    private boolean active = false;

    public DatabaseFilter() {}

    public DatabaseFilter(Set<String> selectedDatabases) {
        if (selectedDatabases != null && !selectedDatabases.isEmpty()) {
            this.selectedDatabases.addAll(selectedDatabases);
            this.active = true;
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<String> getSelectedDatabases() {
        return Collections.unmodifiableSet(selectedDatabases);
    }

    public void setSelectedDatabases(Set<String> dbs) {
        this.selectedDatabases.clear();
        if (dbs != null) {
            this.selectedDatabases.addAll(dbs);
        }
        this.active = (dbs != null && !dbs.isEmpty());
    }

    public boolean matches(String dbName) {
        if (!active || selectedDatabases.isEmpty()) return true;
        if (dbName == null) return false;
        for (String db : selectedDatabases) {
            if (db.equalsIgnoreCase(dbName)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        selectedDatabases.clear();
        active = false;
    }
}

