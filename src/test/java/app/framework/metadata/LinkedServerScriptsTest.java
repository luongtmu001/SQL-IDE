package app.framework.metadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.framework.metadata.LinkedServerScripts.Info;
import app.framework.metadata.LinkedServerScripts.Mode;
import org.junit.jupiter.api.Test;

class LinkedServerScriptsTest {

    private static final Info INFO = new Info("SRV'A", "SQL Server", "SQLNCLI", "10.0.0.5", "", "", "Sales");

    @Test
    void createScriptHasAddServerAndOptions() {
        String s = LinkedServerScripts.build(INFO, Mode.CREATE);
        assertTrue(s.contains("sp_addlinkedserver"));
        assertTrue(s.contains("@server = N'SRV''A'"), "single quotes must be doubled");
        assertTrue(s.contains(",@datasrc = N'10.0.0.5'"));
        assertTrue(s.contains(",@catalog = N'Sales'"));
        assertFalse(s.contains("@location"), "empty location is omitted");
        assertTrue(s.contains("@optname = N'rpc out'"));
        assertFalse(s.contains("sp_dropserver"));
    }

    @Test
    void dropAndDropCreate() {
        String d = LinkedServerScripts.build(INFO, Mode.DROP);
        assertTrue(d.contains("sp_dropserver") && !d.contains("sp_addlinkedserver"));
        String dc = LinkedServerScripts.build(INFO, Mode.DROP_AND_CREATE);
        assertTrue(dc.indexOf("sp_dropserver") < dc.indexOf("sp_addlinkedserver"));
    }

    @Test
    void defaultsWhenInfoMissing() {
        String s = LinkedServerScripts.build(new Info("X", null, null, null, null, null, null), Mode.CREATE);
        assertTrue(s.contains("@srvproduct = N'SQL Server'") && s.contains("@provider = N'SQLNCLI'"));
    }
}
