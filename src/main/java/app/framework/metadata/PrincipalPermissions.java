package app.framework.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data of the "Permissions" tab of a SQL Server database user / PostgreSQL role (LLTool
 * {@code database_users}), plus the CREATE / DROP scripts built from it.
 */
public final class PrincipalPermissions {

    /** Role the principal belongs to. {@code adminOption} is PostgreSQL only. */
    public record Membership(String role, String type, boolean admin) { }

    /** One GRANT / DENY. {@code scope} = database | schema | object | cluster. {@code source} = DIRECT | ROLE. */
    public record Permission(String scope, String permission, String state, String source, String viaRole,
                             String objectType, String schema, String object, String grantor) { }

    public final boolean postgres;
    public final String name;
    public final String database;
    /** Label -> value, in the order shown on the Overview tab. */
    public final Map<String, String> overview = new LinkedHashMap<>();
    /** Raw attributes used by the scripts (type_desc, login_name, default_schema, can_login, ...). */
    public final Map<String, Object> attrs = new LinkedHashMap<>();
    public final List<Membership> memberships = new ArrayList<>();
    public final List<Permission> permissions = new ArrayList<>();

    public PrincipalPermissions(boolean postgres, String name, String database) {
        this.postgres = postgres;
        this.name = name;
        this.database = database;
    }

    // ------------------------------------------------------------------ scripts (same text as LLTool)

    public static String quoteSqlServer(String s) {
        if (s == null || s.isBlank()) return "";
        String t = s.strip();
        if (t.startsWith("[") && t.endsWith("]") && t.length() >= 2) t = t.substring(1, t.length() - 1).replace("]]", "]");
        return "[" + t.replace("]", "]]") + "]";
    }

    public static String quotePostgres(String s) {
        if (s == null || s.isBlank()) return "";
        String t = s.strip();
        if (t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2) t = t.substring(1, t.length() - 1).replace("\"\"", "\"");
        return "\"" + t.replace("\"", "\"\"") + "\"";
    }

    private static String qualified(boolean pg, String a, String b) {
        java.util.function.UnaryOperator<String> q = pg ? PrincipalPermissions::quotePostgres : PrincipalPermissions::quoteSqlServer;
        String x = a == null || a.isBlank() ? "" : q.apply(a);
        String y = b == null || b.isBlank() ? "" : q.apply(b);
        return x.isEmpty() ? y : y.isEmpty() ? x : x + "." + y;
    }

    /** CREATE script; {@code full} adds role memberships and DIRECT permissions. */
    public String createScript(String connectionName, boolean full, String timestamp) {
        return postgres ? createPg(connectionName, full, timestamp) : createSqlServer(connectionName, full, timestamp);
    }

    public String dropScript(String connectionName, String timestamp) {
        return postgres ? dropPg(connectionName, timestamp) : dropSqlServer(connectionName, timestamp);
    }

    private String str(String key) {
        Object o = attrs.get(key);
        return o == null ? null : String.valueOf(o);
    }

    private boolean flag(String key, boolean def) {
        Object o = attrs.get(key);
        return o instanceof Boolean b ? b : def;
    }

    private String createSqlServer(String conn, boolean full, String ts) {
        String user = quoteSqlServer(name);
        String schema = quoteSqlServer(str("default_schema") != null && !str("default_schema").isBlank() ? str("default_schema") : "dbo");
        String type = str("type_desc") != null ? str("type_desc").toUpperCase() : "SQL_USER";
        String auth = str("authentication_type") != null ? str("authentication_type").toUpperCase() : "";
        String login = str("login_name");
        List<String> l = new ArrayList<>();
        l.add(headerSqlServer("CREATE USER", conn, ts));
        l.add("USE " + quoteSqlServer(database) + ";");
        l.add("GO\n");
        if (type.equals("SQL_USER") || type.equals("WINDOWS_USER") || type.equals("WINDOWS_GROUP") || type.equals("EXTERNAL_USER")) {
            if (login != null && !login.isBlank()) {
                l.add("CREATE USER " + user + " FOR LOGIN " + quoteSqlServer(login));
            } else {
                l.add("CREATE USER " + user + " WITHOUT LOGIN");
            }
            l.add("    WITH DEFAULT_SCHEMA = " + schema + ";");
            l.add("GO\n");
        } else if (auth.contains("CONTAINED") || auth.contains("DATABASE")) {
            l.add("CREATE USER " + user + " WITH DEFAULT_SCHEMA = " + schema + ";");
            l.add("GO\n");
        } else {
            l.add("CREATE USER " + user + " WITHOUT LOGIN");
            l.add("    WITH DEFAULT_SCHEMA = " + schema + ";");
            l.add("GO\n");
        }
        if (full) {
            if (!memberships.isEmpty()) {
                l.add("/* ── Role Memberships ── */");
                for (Membership m : memberships) {
                    if (m.role() != null && !m.role().equalsIgnoreCase("public")) {
                        l.add("ALTER ROLE " + quoteSqlServer(m.role()) + " ADD MEMBER " + user + ";");
                    }
                }
                l.add("GO\n");
            }
            if (!permissions.isEmpty()) {
                l.add("/* ── Granted Permissions ── */");
                for (Permission p : permissions) {
                    if (!"DIRECT".equals(p.source())) continue;
                    String st = p.state() != null ? p.state() : "GRANT";
                    if ("database".equals(p.scope())) {
                        l.add(st + " " + p.permission() + " TO " + user + ";");
                    } else if ("schema".equals(p.scope()) && p.schema() != null) {
                        l.add(st + " " + p.permission() + " ON SCHEMA::" + quoteSqlServer(p.schema()) + " TO " + user + ";");
                    } else if ("object".equals(p.scope()) && p.object() != null) {
                        l.add(st + " " + p.permission() + " ON " + qualified(false, p.schema(), p.object()) + " TO " + user + ";");
                    }
                }
                l.add("GO\n");
            }
        }
        return String.join("\n", l);
    }

    private String dropSqlServer(String conn, String ts) {
        List<String> l = new ArrayList<>();
        l.add(headerSqlServer("DROP USER", conn, ts));
        l.add("USE " + quoteSqlServer(database) + ";");
        l.add("GO\n");
        l.add("/* CẢNH BÁO: Kiểm tra kỹ dependency hoặc schema do user sở hữu trước khi thực thi. */");
        l.add("IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'" + name.replace("'", "''") + "')");
        l.add("BEGIN");
        l.add("    DROP USER " + quoteSqlServer(name) + ";");
        l.add("END");
        l.add("GO\n");
        return String.join("\n", l);
    }

    private String headerSqlServer(String action, String conn, String ts) {
        return "/****** Object: User [" + name + "]   Script Date: " + ts + " ******/\n"
                + "/* Engine: Microsoft SQL Server" + (conn != null && !conn.isBlank() ? " (" + conn + ")" : "") + " */\n"
                + "/* Database: [" + database + "] */\n"
                + "/* Action: " + action + " */\n";
    }

    private String headerPg(String action, String conn, String ts) {
        return "-- ==========================================================\n"
                + "-- Object: Role \"" + name + "\"   Script Date: " + ts + "\n"
                + "-- Engine: PostgreSQL" + (conn != null && !conn.isBlank() ? " (" + conn + ")" : "") + "\n"
                + "-- Action: " + action + "\n"
                + "-- ==========================================================\n";
    }

    private String createPg(String conn, boolean full, String ts) {
        String role = quotePostgres(name);
        List<String> l = new ArrayList<>();
        l.add(headerPg("CREATE ROLE", conn, ts));
        List<String> a = new ArrayList<>();
        a.add(flag("can_login", false) ? "LOGIN" : "NOLOGIN");
        a.add(flag("is_superuser", false) ? "SUPERUSER" : "NOSUPERUSER");
        a.add(flag("can_create_db", false) ? "CREATEDB" : "NOCREATEDB");
        a.add(flag("can_create_role", false) ? "CREATEROLE" : "NOCREATEROLE");
        a.add(flag("inherit_privileges", true) ? "INHERIT" : "NOINHERIT");
        a.add(flag("can_replicate", false) ? "REPLICATION" : "NOREPLICATION");
        String limit = str("connection_limit");
        if (limit != null && !limit.equals("-1")) a.add("CONNECTION LIMIT " + limit);
        l.add("CREATE ROLE " + role + " WITH");
        for (int i = 0; i < a.size(); i++) l.add("    " + a.get(i) + (i == a.size() - 1 ? ";\n" : ""));
        l.add("-- Lưu ý: Mật khẩu (PASSWORD) không được xuất tự động vì lý do an toàn.\n"
                + "-- Nếu cần thiết lập mật khẩu, hãy chạy:\n"
                + "-- ALTER ROLE " + role + " WITH PASSWORD 'your_password_here';\n");
        if (full) {
            if (!memberships.isEmpty()) {
                l.add("-- ── Granted Roles (Memberships) ──");
                for (Membership m : memberships) {
                    if (m.role() != null) l.add("GRANT " + quotePostgres(m.role()) + " TO " + role + (m.admin() ? " WITH ADMIN OPTION" : "") + ";");
                }
                l.add("");
            }
            if (!permissions.isEmpty()) {
                l.add("-- ── Object & Schema Permissions ──");
                for (Permission p : permissions) {
                    if (!"DIRECT".equals(p.source())) continue;
                    String st = p.state() != null ? p.state() : "GRANT";
                    if ("database".equals(p.scope()) && p.object() != null) {
                        l.add(st + " " + p.permission() + " ON DATABASE " + quotePostgres(p.object()) + " TO " + role + ";");
                    } else if ("schema".equals(p.scope()) && p.schema() != null) {
                        l.add(st + " " + p.permission() + " ON SCHEMA " + quotePostgres(p.schema()) + " TO " + role + ";");
                    } else if ("object".equals(p.scope()) && p.object() != null) {
                        l.add(st + " " + p.permission() + " ON " + qualified(true, p.schema(), p.object()) + " TO " + role + ";");
                    }
                }
                l.add("");
            }
        }
        return String.join("\n", l);
    }

    private String dropPg(String conn, String ts) {
        String role = quotePostgres(name);
        return headerPg("DROP ROLE", conn, ts) + "\n"
                + "-- CẢNH BÁO AN TOÀN:\n"
                + "-- Nếu Role đang sở hữu đối tượng hoặc có quyền phụ thuộc, lệnh DROP ROLE sẽ báo lỗi.\n"
                + "-- Trước khi DROP, xem xét chuyển giao hoặc thu hồi quyền sở hữu nếu cần thiết:\n"
                + "--   REASSIGN OWNED BY " + role + " TO \"new_owner\";\n"
                + "--   DROP OWNED BY " + role + ";\n\n"
                + "DROP ROLE IF EXISTS " + role + ";\n";
    }
}
