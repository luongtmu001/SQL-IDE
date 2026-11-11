package app.framework.syntax;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Syntax Check Coordinator:
 * Orchestrates the full modern syntax checking pipeline:
 * <ol>
 *   <li>Statement-level Syntax Cache (5.0s TTL, invalidated on DDL or connection change).</li>
 *   <li>Dialect Adapter execution (SqlServerSyntaxAdapter / PostgresSyntaxAdapter).</li>
 *   <li>Fast offline structural checks (quotes, comments, brackets, parentheses, SELECT).</li>
 *   <li>Semantic Analyzer layer (verifies tables, #temp, @table, CTEs, physical columns).</li>
 *   <li>Best-Practice rules (BP001 - BP006 warnings).</li>
 *   <li>Diagnostics Deduplication by (line, column, token).</li>
 * </ol>
 */
public final class SyntaxCoordinator {

    public static final double CACHE_TTL_SECONDS = 5.0;
    public static final int CACHE_MAX_SIZE = 512;

    private record CacheEntry(long timestampMs, List<SyntaxDiagnostic> diagnostics, String connectionId) {}

    private static final SyntaxCoordinator INSTANCE = new SyntaxCoordinator();

    public static SyntaxCoordinator getInstance() {
        return INSTANCE;
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, SyntaxAdapter> adapters = new HashMap<>();

    private SyntaxCoordinator() {
        registerAdapter(SqlServerSyntaxAdapter.INSTANCE);
        registerAdapter(PostgresSyntaxAdapter.INSTANCE);
    }

    public void registerAdapter(SyntaxAdapter adapter) {
        if (adapter != null) {
            adapters.put(adapter.dialect().toLowerCase(Locale.ROOT), adapter);
        }
    }

    public int invalidateCache(String connectionId) {
        if (connectionId == null) {
            int size = cache.size();
            cache.clear();
            return size;
        }
        int count = 0;
        var it = cache.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (connectionId.equals(e.getValue().connectionId()) || e.getValue().connectionId() == null) {
                it.remove();
                count++;
            }
        }
        return count;
    }

    public int getCacheSize() {
        return cache.size();
    }

    public List<SyntaxDiagnostic> validate(
            String sql,
            String dialect,
            Connection connection,
            String connectionId,
            String database,
            String schema,
            boolean isDebug,
            boolean includeBestPractice,
            Map<String, List<String>> cachedTables
    ) {
        if (sql == null || sql.isBlank()) return Collections.emptyList();

        String dLower = (dialect != null ? dialect : "sqlserver").strip().toLowerCase(Locale.ROOT);
        String cacheKey = computeCacheKey(sql, dLower, connectionId, database, schema, isDebug, includeBestPractice);
        long now = System.currentTimeMillis();

        // 1. Cache lookup
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && (now - entry.timestampMs()) <= (CACHE_TTL_SECONDS * 1000L)) {
            return new ArrayList<>(entry.diagnostics());
        }

        List<SyntaxDiagnostic> combined = new ArrayList<>();

        // 2. Structural Checks (Offline static validator)
        combined.addAll(StructuralSqlChecks.check(sql, dLower));

        // 3. Adapter Parse (Database SET PARSEONLY / EXPLAIN)
        SyntaxAdapter adapter = adapters.get(dLower);
        if (adapter != null && connection != null) {
            try {
                List<SyntaxDiagnostic> serverDiags = adapter.validate(sql, connection, database, schema, isDebug);
                combined.addAll(serverDiags);
            } catch (Exception e) {
                // If connection error or check fails, offline checks already populated
            }
        }

        // 4. Semantic Validation Layer (Physical tables, #temp, @table, CTEs)
        // If not connected to a database server (connection == null), only check normal/structural syntax.
        // Deep object/column validation is only executed when connected to the server.
        if (connection != null) {
            try {
                List<SyntaxDiagnostic> semanticDiags = SemanticAnalyzer.analyze(sql, dLower, cachedTables);
                combined.addAll(semanticDiags);
            } catch (Exception ignored) {}
        }

        // 5. Best Practice Rules (Warnings)
        if (includeBestPractice) {
            try {
                List<SyntaxDiagnostic> bpDiags = BestPracticeRules.check(sql, dLower);
                combined.addAll(bpDiags);
            } catch (Exception ignored) {}
        }

        // 6. Deduplication
        List<SyntaxDiagnostic> deduplicated = deduplicateDiagnostics(combined);

        // 7. Store in statement cache
        if (cache.size() >= CACHE_MAX_SIZE) {
            // Remove oldest entries
            var it = cache.entrySet().iterator();
            int removed = 0;
            while (it.hasNext() && removed < 64) {
                it.next();
                it.remove();
                removed++;
            }
        }
        cache.put(cacheKey, new CacheEntry(now, List.copyOf(deduplicated), connectionId));

        return deduplicated;
    }

    public static List<SyntaxDiagnostic> deduplicateDiagnostics(List<SyntaxDiagnostic> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        Map<String, SyntaxDiagnostic> seen = new LinkedHashMap<>();
        for (var d : list) {
            if (d == null) continue;
            String key = d.line() + ":" + d.column() + ":" + (d.token() != null ? d.token().toLowerCase(Locale.ROOT) : "");
            seen.putIfAbsent(key, d);
        }
        return new ArrayList<>(seen.values());
    }

    private static String computeCacheKey(String sql, String dialect, String connId, String db, String schema, boolean isDebug, boolean includeBp) {
        String raw = sql.strip() + "||" + dialect + "||" + (connId != null ? connId : "") + "||"
                + (db != null ? db : "") + "||" + (schema != null ? schema : "") + "||" + isDebug + "||" + includeBp;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}

