package app.framework.query;

import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 8 / D3: turns the {@link SQLWarning} chain of a statement into plain lines for the Messages tab.
 * SQL Server delivers {@code PRINT} and low-severity {@code RAISERROR} this way; PostgreSQL delivers
 * {@code RAISE NOTICE / WARNING} this way.
 */
public final class SqlWarningMessages {

    private SqlWarningMessages() {
    }

    public static List<String> collect(SQLWarning first) {
        List<String> out = new ArrayList<>();
        int guard = 0; // a driver bug must not loop forever
        for (SQLWarning w = first; w != null && guard < 10_000; w = w.getNextWarning(), guard++) {
            String m = w.getMessage();
            if (m != null && !m.isBlank()) {
                out.add(m.strip());
            }
        }
        return out;
    }
}
