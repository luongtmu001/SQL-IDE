package app.framework.metadata;

import app.framework.metadata.PrincipalPermissions.Membership;
import app.framework.metadata.PrincipalPermissions.Permission;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.application.Platform;
import com.lltool.infrastructure.database.api.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.function.Consumer;

/**
 * Reads properties, role memberships and permissions of a SQL Server database user or a PostgreSQL role
 * (queries of LLTool's {@code sqlserver_provider.py} / {@code postgres_provider.py}); read-only.
 */
public final class PrincipalPermissionsService {

    private static final PrincipalPermissionsService INSTANCE = new PrincipalPermissionsService();

    private PrincipalPermissionsService() { }

    public static PrincipalPermissionsService getInstance() {
        return INSTANCE;
    }

    public static boolean isPostgres(ConnectionProfileDto p) {
        return p != null && p.databaseType() != null && p.databaseType().toUpperCase().contains("POSTGRE");
    }

    /** Loads in the background; callbacks run on the FX thread. Never cached (the tab has a Refresh button). */
    public void loadAsync(ConnectionProfileDto profile, String database, String name,
                          Consumer<PrincipalPermissions> onDone, Consumer<String> onError) {
        Thread.ofVirtual().name("principal-permissions").start(() -> {
            try {
                PrincipalPermissions r = load(profile, database, name);
                Platform.runLater(() -> onDone.accept(r));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                Platform.runLater(() -> onError.accept(msg));
            }
        });
    }

    public PrincipalPermissions load(ConnectionProfileDto profile, String database, String name) throws Exception {
        boolean pg = isPostgres(profile);
        String db = database != null && !database.isBlank() ? database
                : (profile.databaseName() != null && !profile.databaseName().isBlank() ? profile.databaseName() : (pg ? "postgres" : "master"));
        PrincipalPermissions r = new PrincipalPermissions(pg, name, db);
        try (DatabaseConnection dc = DesktopMetadataService.getInstance().explorerConnection(profile, db)) {
            Connection c = dc.getRawJdbcConnection();
            if (pg) loadPostgres(c, r); else loadSqlServer(c, r);
        }
        return r;
    }

    // ------------------------------------------------------------------ SQL Server

    private static final String SQLSERVER_DIRECT = """
            SELECT dp.permission_name, dp.state_desc, dp.class_desc, SCHEMA_NAME(o.schema_id), o.name, o.type_desc,
                   s.name, USER_NAME(dp.grantor_principal_id)
            FROM sys.database_permissions AS dp
            JOIN sys.database_principals AS p ON dp.grantee_principal_id = p.principal_id
            LEFT JOIN sys.all_objects AS o ON dp.major_id = o.object_id AND dp.class = 1
            LEFT JOIN sys.schemas AS s ON dp.major_id = s.schema_id AND dp.class = 3
            WHERE p.name = ?
            ORDER BY dp.class_desc, dp.permission_name""";

    private void loadSqlServer(Connection c, PrincipalPermissions r) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT dp.principal_id, dp.name, dp.type_desc, dp.authentication_type_desc, dp.default_schema_name, "
                        + "CONVERT(varchar(19), dp.create_date, 120), CONVERT(varchar(19), dp.modify_date, 120), SUSER_SNAME(dp.sid) "
                        + "FROM sys.database_principals dp WHERE dp.name = ?")) {
            ps.setString(1, r.name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String type = rs.getString(3);
                    String auth = rs.getString(4);
                    String defSchema = rs.getString(5);
                    String login = rs.getString(8);
                    r.attrs.put("type_desc", type);
                    r.attrs.put("authentication_type", auth);
                    r.attrs.put("default_schema", defSchema);
                    r.attrs.put("login_name", login);
                    r.overview.put("User Name", r.name);
                    r.overview.put("Principal Type", type != null ? type : "SQL_USER");
                    r.overview.put("Server Login Mapping", login != null ? login : "(None / Without Login)");
                    r.overview.put("Default Schema", defSchema != null ? defSchema : "dbo");
                    r.overview.put("Authentication Type", auth != null ? auth : "INSTANCE");
                    r.overview.put("Create Date", rs.getString(6) != null ? rs.getString(6) : "--");
                    r.overview.put("Modify Date", rs.getString(7) != null ? rs.getString(7) : "--");
                } else {
                    r.overview.put("User Name", r.name);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.name, r.type_desc, r.is_fixed_role FROM sys.database_role_members rm "
                        + "JOIN sys.database_principals r ON rm.role_principal_id = r.principal_id "
                        + "JOIN sys.database_principals m ON rm.member_principal_id = m.principal_id "
                        + "WHERE m.name = ? ORDER BY r.name")) {
            ps.setString(1, r.name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String t = rs.getString(2);
                    r.memberships.add(new Membership(rs.getString(1), t != null ? t : "DATABASE_ROLE", false));
                }
            }
        }
        try {
            readSqlServerPermissions(c, r, r.name, "DIRECT", null);
        } catch (Exception e) {
            r.permissions.add(new Permission("database", "METADATA_UNAVAILABLE", "OTHER", "UNKNOWN", null, null, null, null, null));
            r.overview.put("Cảnh báo", "Không đủ quyền đọc sys.database_permissions: " + e.getMessage());
        }
        for (Membership m : r.memberships) {
            try {
                readSqlServerPermissions(c, r, m.role(), "ROLE", m.role());
            } catch (Exception ignored) {
                // role permissions are best effort (LLTool logs at debug level)
            }
        }
    }

    private void readSqlServerPermissions(Connection c, PrincipalPermissions r, String principal, String source, String viaRole)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(SQLSERVER_DIRECT)) {
            ps.setString(1, principal);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cls = rs.getString(3) != null ? rs.getString(3).toUpperCase() : "";
                    String scope = "database";
                    String schema = null, object = null, objType = null;
                    if (cls.contains("SCHEMA")) {
                        scope = "schema";
                        schema = rs.getString(7);
                    } else if (cls.contains("OBJECT")) {
                        scope = "object";
                        schema = rs.getString(4);
                        object = rs.getString(5);
                        objType = rs.getString(6) != null ? rs.getString(6) : "OBJECT";
                    }
                    String state = rs.getString(2) != null ? rs.getString(2) : "GRANT";
                    r.permissions.add(new Permission(scope, rs.getString(1), state, source, viaRole, objType, schema, object, rs.getString(8)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ PostgreSQL

    private void loadPostgres(Connection c, PrincipalPermissions r) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT rolsuper, rolinherit, rolcreaterole, rolcreatedb, rolcanlogin, rolreplication, rolconnlimit "
                        + "FROM pg_catalog.pg_roles WHERE rolname = ?")) {
            ps.setString(1, r.name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    r.attrs.put("is_superuser", rs.getBoolean(1));
                    r.attrs.put("inherit_privileges", rs.getBoolean(2));
                    r.attrs.put("can_create_role", rs.getBoolean(3));
                    r.attrs.put("can_create_db", rs.getBoolean(4));
                    r.attrs.put("can_login", rs.getBoolean(5));
                    r.attrs.put("can_replicate", rs.getBoolean(6));
                    r.attrs.put("connection_limit", rs.getInt(7));
                    r.overview.put("Role Name", r.name);
                    r.overview.put("Can Login", rs.getBoolean(5) ? "✓ YES (User Account)" : "✗ NO (Role Only)");
                    r.overview.put("Superuser", rs.getBoolean(1) ? "✓ YES (Superuser)" : "NO");
                    r.overview.put("Can Create DB", rs.getBoolean(4) ? "YES" : "NO");
                    r.overview.put("Can Create Role", rs.getBoolean(3) ? "YES" : "NO");
                    r.overview.put("Inherit Privileges", rs.getBoolean(2) ? "YES" : "NO");
                    r.overview.put("Connection Limit", rs.getInt(7) == -1 ? "-1 (Unlimited)" : String.valueOf(rs.getInt(7)));
                } else {
                    r.overview.put("Role Name", r.name);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.rolname, m.admin_option FROM pg_catalog.pg_auth_members m "
                        + "JOIN pg_catalog.pg_roles r ON m.roleid = r.oid JOIN pg_catalog.pg_roles u ON m.member = u.oid "
                        + "WHERE u.rolname = ? ORDER BY r.rolname")) {
            ps.setString(1, r.name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.memberships.add(new Membership(rs.getString(1), "ROLE", rs.getBoolean(2)));
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p, has_database_privilege(?, ?, p) FROM unnest(ARRAY['CONNECT','CREATE','TEMPORARY']) AS p")) {
            ps.setString(1, r.name);
            ps.setString(2, r.database);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getBoolean(2)) {
                        r.permissions.add(new Permission("database", rs.getString(1), "GRANT", "DIRECT", null, "DATABASE", null, r.database, null));
                    }
                }
            }
        } catch (Exception ignored) {
            // role or database not visible: no database-level rows (LLTool ignores the error too)
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT n.nspname, p, has_schema_privilege(?, n.oid, p) FROM pg_catalog.pg_namespace n "
                        + "CROSS JOIN unnest(ARRAY['USAGE','CREATE']) AS p "
                        + "WHERE n.nspname NOT LIKE 'pg_temp_%' AND n.nspname NOT LIKE 'pg_toast%' ORDER BY n.nspname, p")) {
            ps.setString(1, r.name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getBoolean(3)) {
                        r.permissions.add(new Permission("schema", rs.getString(2), "GRANT", "DIRECT", null, "SCHEMA",
                                rs.getString(1), rs.getString(1), null));
                    }
                }
            }
        } catch (Exception ignored) {
            // see above
        }
        for (Membership m : r.memberships) {
            r.permissions.add(new Permission("cluster", "MEMBER OF", "GRANT", "ROLE", m.role(), "ROLE", null, m.role(), null));
        }
    }
}
