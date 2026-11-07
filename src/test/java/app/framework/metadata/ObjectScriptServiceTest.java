package app.framework.metadata;

import com.lltool.infrastructure.database.api.DatabaseType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phase 8 / D2: identifier quoting used by real "Script … as" (LLTool quote_ident). */
class ObjectScriptServiceTest {

    @Test
    void quotesPerDialect() {
        assertEquals("[dbo].[Order Details]", ObjectScriptService.qualified(DatabaseType.SQLSERVER, "dbo", "Order Details"));
        assertEquals("[a]]b]", ObjectScriptService.quote(DatabaseType.SQLSERVER, "a]b"));
        assertEquals("public.orders", ObjectScriptService.qualified(DatabaseType.POSTGRESQL, "public", "orders"));
        assertEquals("public.\"OrderLines\"", ObjectScriptService.qualified(DatabaseType.POSTGRESQL, "public", "OrderLines"));
        assertEquals("\"my \"\"x\"\"\"", ObjectScriptService.quote(DatabaseType.POSTGRESQL, "my \"x\""));
        assertEquals("orders", ObjectScriptService.qualified(DatabaseType.SQLITE, "main", "orders"));
    }
}
