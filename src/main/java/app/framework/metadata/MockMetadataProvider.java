package app.framework.metadata;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.metadata.ColumnMetadata;
import com.lltool.infrastructure.database.api.metadata.ConstraintMetadata;
import com.lltool.infrastructure.database.api.metadata.IndexMetadata;
import com.lltool.infrastructure.database.api.metadata.KeyMetadata;
import com.lltool.infrastructure.database.api.metadata.RoutineMetadata;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;
import com.lltool.infrastructure.database.api.metadata.TriggerMetadata;
import com.lltool.infrastructure.database.api.metadata.UserTypeMetadata;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Prototype metadata (SCR-MAIN-002, Gate B 2026-11-05, Q1 = a).
 *
 * <p>While the UI prototype is being built, expanding a connection in the Object Explorer must
 * never open a real database connection. {@link DesktopMetadataService} asks this class first;
 * when {@link #isEnabled()} is true every fetch is answered with deterministic mock data after a
 * short delay (so the "connecting / loading" states stay visible).</p>
 *
 * <p>Gate D (real backend integration) = start the app with {@code -Dsqlide.mockMetadata=false}
 * or call {@link #setEnabled(boolean)}.</p>
 */
public final class MockMetadataProvider {

    /** Phase 8 (Gate D 2026-11-05): real metadata by default; see {@link app.framework.backend.BackendFlags}. */
    private static volatile boolean enabled =
            app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.METADATA);

    private static final long DELAY_MS = 280;

    private MockMetadataProvider() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** Delivers {@code data} to {@code callback} on the FX thread after a short simulated latency. */
    public static <T> void deliver(Supplier<List<T>> data, Consumer<List<T>> callback) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            List<T> result;
            try {
                result = data.get();
            } catch (RuntimeException e) {
                result = List.of();
            }
            List<T> finalResult = result;
            Platform.runLater(() -> callback.accept(finalResult));
        }, "mock-metadata");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ dialect helpers

    private static String dialect(ConnectionProfileDto p) {
        String t = p != null && p.databaseType() != null ? p.databaseType().toUpperCase() : "POSTGRESQL";
        if (t.contains("SQLSERVER") || t.contains("MSSQL")) return "MSSQL";
        if (t.contains("SQLITE")) return "SQLITE";
        return "PG";
    }

    // ------------------------------------------------------------------ data

    public static List<String> catalogs(ConnectionProfileDto p) {
        List<String> list = new ArrayList<>();
        String def = p != null && p.databaseName() != null && !p.databaseName().isBlank() ? p.databaseName() : null;
        switch (dialect(p)) {
            case "MSSQL" -> list.addAll(List.of("master", "B10_BachDang_XM", "B10_PhucLoi", "BravoSetup", "msdb"));
            case "SQLITE" -> list.add("main");
            default -> list.addAll(List.of("postgres", "B10_PhucLoi", "staging_db", "analytics"));
        }
        if (def != null && !list.contains(def)) list.add(0, def);
        return list;
    }

    public static List<String> schemas(ConnectionProfileDto p, String catalog) {
        return switch (dialect(p)) {
            case "MSSQL" -> List.of("dbo", "sales", "audit");
            case "SQLITE" -> List.of("main");
            default -> List.of("public", "auth", "audit_logs");
        };
    }

    private static final String[][] TABLES = {
            {"customers", "12450"}, {"orders", "89210"}, {"order_items", "245100"}, {"products", "3200"},
            {"categories", "48"}, {"employees", "310"}, {"invoices", "56020"}, {"payments", "54001"},
            {"b30stockledgersales", "1204500"}, {"b30generalledgersales", "980200"}, {"users", "1450"},
            {"audit_events", "3400000"}};

    public static List<TableMetadata> tables(ConnectionProfileDto p, String catalog, String schema) {
        List<TableMetadata> list = new ArrayList<>();
        int count = "audit".equals(schema) || "audit_logs".equals(schema) || "auth".equals(schema) || "sales".equals(schema) ? 3 : TABLES.length;
        for (int i = 0; i < count; i++) {
            list.add(new TableMetadata(TABLES[i][0], schema, catalog, "TABLE", Long.parseLong(TABLES[i][1]), null));
        }
        return list;
    }

    public static List<TableMetadata> views(ConnectionProfileDto p, String catalog, String schema) {
        return List.of(
                new TableMetadata("v_customer_orders", schema, catalog, "VIEW", 0L, null),
                new TableMetadata("v_monthly_revenue", schema, catalog, "VIEW", 0L, null),
                new TableMetadata("v_stock_balance", schema, catalog, "VIEW", 0L, null),
                new TableMetadata("v_active_users", schema, catalog, "VIEW", 0L, null));
    }

    public static List<TableMetadata> materializedViews(ConnectionProfileDto p, String catalog, String schema) {
        return List.of(new TableMetadata("mv_daily_sales", schema, catalog, "MATERIALIZED VIEW", 0L, null));
    }

    public static List<RoutineMetadata> routines(ConnectionProfileDto p, String catalog, String schema) {
        boolean mssql = "MSSQL".equals(dialect(p));
        return List.of(
                new RoutineMetadata("usp_close_period", schema, catalog, "PROCEDURE", null),
                new RoutineMetadata("usp_recalc_stock", schema, catalog, "PROCEDURE", null),
                new RoutineMetadata("usp_kct_def", schema, catalog, "PROCEDURE", null),
                new RoutineMetadata(mssql ? "ufn_kcd_interest" : "fn_kcd_interest", schema, catalog, "FUNCTION", "numeric"),
                new RoutineMetadata("fn_format_code", schema, catalog, "FUNCTION", mssql ? "nvarchar" : "text"),
                new RoutineMetadata("fn_get_balance", schema, catalog, "FUNCTION", "numeric"));
    }

    public static List<UserTypeMetadata> userTypes(ConnectionProfileDto p, String catalog, String schema) {
        return List.of(
                new UserTypeMetadata("t_money", schema, catalog, "numeric(18,2)", null),
                new UserTypeMetadata("t_code", schema, catalog, "varchar(32)", null));
    }

    public static List<ColumnMetadata> columns(ConnectionProfileDto p, String catalog, String schema, String table) {
        String t = table != null ? table : "table";
        String single = t.endsWith("s") ? t.substring(0, t.length() - 1) : t;
        List<ColumnMetadata> cols = new ArrayList<>();
        cols.add(new ColumnMetadata("id", "integer", 10, false, true, false, null, 1));
        cols.add(new ColumnMetadata(single + "_code", "varchar", 32, false, false, false, null, 2));
        cols.add(new ColumnMetadata("name", "varchar", 255, true, false, false, null, 3));
        if (!"customers".equals(t)) {
            cols.add(new ColumnMetadata("customer_id", "integer", 10, true, false, true, null, 4));
        }
        cols.add(new ColumnMetadata("amount", "numeric", 18, true, false, false, "0", cols.size() + 1));
        cols.add(new ColumnMetadata("status", "varchar", 20, true, false, false, "'active'", cols.size() + 1));
        cols.add(new ColumnMetadata("created_at", "timestamp", 0, false, false, false, "now()", cols.size() + 1));
        return cols;
    }

    public static List<KeyMetadata> keys(ConnectionProfileDto p, String catalog, String schema, String table) {
        List<KeyMetadata> keys = new ArrayList<>();
        keys.add(new KeyMetadata("pk_" + table, "PRIMARY KEY", table, List.of("id")));
        if (!"customers".equals(table)) {
            keys.add(new KeyMetadata("fk_" + table + "_customer", "FOREIGN KEY", table, List.of("customer_id"),
                    "customers", List.of("id")));
        }
        return keys;
    }

    public static List<ConstraintMetadata> constraints(ConnectionProfileDto p, String catalog, String schema, String table) {
        return List.of(new ConstraintMetadata("ck_" + table + "_amount", "CHECK", table, "amount >= 0"));
    }

    public static List<IndexMetadata> indexes(ConnectionProfileDto p, String catalog, String schema, String table) {
        String t = table != null ? table : "orders";
        return List.of(
                new IndexMetadata("pk_" + t, t, schema, true, List.of("id")),
                new IndexMetadata("ix_" + t + "_created_at", t, schema, false, List.of("created_at")));
    }

    public static List<TriggerMetadata> triggers(ConnectionProfileDto p, String catalog, String schema, String table) {
        String t = table != null ? table : "orders";
        return List.of(new TriggerMetadata("trg_" + t + "_audit", t, schema, "INSERT, UPDATE", "AFTER"));
    }
}
