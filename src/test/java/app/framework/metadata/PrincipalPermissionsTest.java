package app.framework.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.framework.metadata.PrincipalPermissions.Membership;
import app.framework.metadata.PrincipalPermissions.Permission;
import org.junit.jupiter.api.Test;

class PrincipalPermissionsTest {

    private static final String TS = "2026-11-06 00:00:00 UTC";

    private static PrincipalPermissions sqlServerUser() {
        PrincipalPermissions p = new PrincipalPermissions(false, "app]user", "Sales");
        p.attrs.put("type_desc", "SQL_USER");
        p.attrs.put("login_name", "app_login");
        p.attrs.put("default_schema", "sales");
        p.memberships.add(new Membership("db_datareader", "DATABASE_ROLE", false));
        p.memberships.add(new Membership("public", "DATABASE_ROLE", false));
        p.permissions.add(new Permission("database", "CONNECT", "GRANT", "DIRECT", null, null, null, null, "dbo"));
        p.permissions.add(new Permission("object", "SELECT", "DENY", "DIRECT", null, "USER_TABLE", "sales", "Orders", "dbo"));
        p.permissions.add(new Permission("database", "SELECT", "GRANT", "ROLE", "db_datareader", null, null, null, "dbo"));
        return p;
    }

    @Test
    void sqlServerCreateFull() {
        String s = sqlServerUser().createScript("Local", true, TS);
        assertTrue(s.contains("USE [Sales];"));
        assertTrue(s.contains("CREATE USER [app]]user] FOR LOGIN [app_login]"), "closing bracket is doubled");
        assertTrue(s.contains("WITH DEFAULT_SCHEMA = [sales];"));
        assertTrue(s.contains("ALTER ROLE [db_datareader] ADD MEMBER [app]]user];"));
        assertFalse(s.contains("ALTER ROLE [public]"), "public is skipped");
        assertTrue(s.contains("GRANT CONNECT TO [app]]user];"));
        assertTrue(s.contains("DENY SELECT ON [sales].[Orders] TO [app]]user];"));
        assertFalse(s.contains("GRANT SELECT TO"), "role-inherited permissions are not scripted");
    }

    @Test
    void sqlServerCreateWithoutLoginAndDrop() {
        PrincipalPermissions p = new PrincipalPermissions(false, "u", "Db");
        p.attrs.put("type_desc", "SQL_USER");
        String c = p.createScript("", false, TS);
        assertTrue(c.contains("WITHOUT LOGIN") && c.contains("[dbo]"));
        String d = p.dropScript("", TS);
        assertTrue(d.contains("IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'u')"));
        assertTrue(d.contains("DROP USER [u];"));
    }

    @Test
    void postgresCreateAndDrop() {
        PrincipalPermissions p = new PrincipalPermissions(true, "app_user", "db1");
        p.attrs.put("can_login", true);
        p.attrs.put("can_create_db", true);
        p.attrs.put("inherit_privileges", true);
        p.attrs.put("connection_limit", 5);
        p.memberships.add(new Membership("readers", "ROLE", true));
        p.permissions.add(new Permission("schema", "USAGE", "GRANT", "DIRECT", null, "SCHEMA", "public", "public", null));
        String s = p.createScript("PG", true, TS);
        assertTrue(s.contains("CREATE ROLE \"app_user\" WITH"));
        assertTrue(s.contains("    LOGIN") && s.contains("    NOSUPERUSER") && s.contains("    CREATEDB"));
        assertTrue(s.contains("CONNECTION LIMIT 5;"));
        assertTrue(s.contains("GRANT \"readers\" TO \"app_user\" WITH ADMIN OPTION;"));
        assertTrue(s.contains("GRANT USAGE ON SCHEMA \"public\" TO \"app_user\";"));
        String d = p.dropScript("PG", TS);
        assertTrue(d.contains("DROP ROLE IF EXISTS \"app_user\";"));
    }
}
