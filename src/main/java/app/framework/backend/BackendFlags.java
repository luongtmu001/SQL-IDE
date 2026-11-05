package app.framework.backend;

/**
 * Phase 8 (Gate D, approved 2026-11-05): which areas talk to real databases.
 *
 * <ul>
 *   <li>{@code -Dsqlide.backend=mock} switches every area back to the UI prototype's mock data.</li>
 *   <li>{@code -Dsqlide.backend.<area>=mock|real} overrides one area
 *       ({@code connections}, {@code metadata}, {@code query}, {@code plan}).</li>
 *   <li>Environment variable {@code SQLIDE_BACKEND=mock} does the same (used by {@code run-ui-mock.cmd}).</li>
 *   <li>The legacy {@code -Dsqlide.mockMetadata=true} still means "everything mock".</li>
 * </ul>
 * Default: real. {@link #readOnlyAll()} (default true, user decision 2026-11-05) makes every
 * connection read-only whatever its profile says.
 */
public final class BackendFlags {

    public enum Area { CONNECTIONS, METADATA, QUERY, PLAN }

    private BackendFlags() {
    }

    public static boolean real(Area area) {
        if ("true".equalsIgnoreCase(System.getProperty("sqlide.mockMetadata"))) return false;
        String env = System.getenv("SQLIDE_BACKEND");   // run-ui-mock.cmd sets SQLIDE_BACKEND=mock
        String global = System.getProperty("sqlide.backend", env != null && !env.isBlank() ? env : "real");
        String own = System.getProperty("sqlide.backend." + area.name().toLowerCase(), global);
        return !"mock".equalsIgnoreCase(own.trim());
    }

    public static boolean mock(Area area) {
        return !real(area);
    }

    /** All connections are read-only (DML / DDL blocked, JDBC read-only, PostgreSQL READ ONLY session). */
    public static boolean readOnlyAll() {
        return !"false".equalsIgnoreCase(System.getProperty("sqlide.readOnly", "true"));
    }
}
