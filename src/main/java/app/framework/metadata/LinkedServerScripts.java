package app.framework.metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds CREATE / DROP scripts for a SQL Server linked server, same text as LLTool's
 * {@code get_linked_server_script}; single quotes inside values are doubled (LLTool did not).
 */
public final class LinkedServerScripts {

    /** Row of {@code sys.servers}. */
    public record Info(String name, String product, String provider, String dataSource,
                       String location, String providerString, String catalog) { }

    public enum Mode { CREATE, DROP, DROP_AND_CREATE }

    private LinkedServerScripts() { }

    public static String build(Info info, Mode mode) {
        String name = nz(info.name());
        return switch (mode) {
            case DROP -> drop(name);
            case CREATE -> create(info);
            case DROP_AND_CREATE -> drop(name) + "\n" + create(info);
        };
    }

    private static String drop(String name) {
        String q = q(name);
        return "-- =========================================================================\n"
                + "-- Script: Drop Linked Server [" + name + "]\n"
                + "-- =========================================================================\n"
                + "USE [master]\nGO\n\n"
                + "IF EXISTS (SELECT 1 FROM sys.servers WHERE name = N'" + q + "' AND is_linked = 1)\nBEGIN\n"
                + "    EXEC master.dbo.sp_dropserver @server = N'" + q + "', @droplogins = 'droplogins';\n"
                + "    PRINT 'Linked Server [" + name + "] dropped successfully.';\nEND\nGO\n";
    }

    private static String create(Info i) {
        String name = nz(i.name());
        String q = q(name);
        List<String> l = new ArrayList<>();
        l.add("-- =========================================================================");
        l.add("-- Script: Create Linked Server [" + name + "]");
        l.add("-- =========================================================================");
        l.add("USE [master]");
        l.add("GO");
        l.add("");
        l.add("EXEC master.dbo.sp_addlinkedserver");
        l.add("    @server = N'" + q + "',");
        l.add("    @srvproduct = N'" + q(or(i.product(), "SQL Server")) + "',");
        l.add("    @provider = N'" + q(or(i.provider(), "SQLNCLI")) + "'");
        if (!nz(i.dataSource()).isEmpty()) l.add("    ,@datasrc = N'" + q(i.dataSource()) + "'");
        if (!nz(i.location()).isEmpty()) l.add("    ,@location = N'" + q(i.location()) + "'");
        if (!nz(i.providerString()).isEmpty()) l.add("    ,@provstr = N'" + q(i.providerString()) + "'");
        if (!nz(i.catalog()).isEmpty()) l.add("    ,@catalog = N'" + q(i.catalog()) + "'");
        l.add("GO");
        l.add("");
        for (String opt : new String[]{"data access", "rpc", "rpc out", "use remote collation"}) {
            l.add("EXEC master.dbo.sp_serveroption @server = N'" + q + "', @optname = N'" + opt + "', @optvalue = N'true';");
            l.add("GO");
        }
        return String.join("\n", l);
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String or(String s, String d) { return s == null || s.isBlank() ? d : s; }
    private static String q(String s) { return nz(s).replace("'", "''"); }
}
