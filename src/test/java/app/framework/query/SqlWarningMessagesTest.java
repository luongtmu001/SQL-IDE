package app.framework.query;

import org.junit.jupiter.api.Test;

import java.sql.SQLWarning;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlWarningMessagesTest {

    @Test
    void nullChainGivesEmptyList() {
        assertTrue(SqlWarningMessages.collect(null).isEmpty());
    }

    @Test
    void collectsWholeChainAndSkipsBlank() {
        SQLWarning a = new SQLWarning("  hello ");
        a.setNextWarning(new SQLWarning(" "));
        a.getNextWarning().setNextWarning(new SQLWarning("world"));
        assertEquals(List.of("hello", "world"), SqlWarningMessages.collect(a));
    }

    @Test
    void queryOptionsCapRules() {
        assertEquals(10_000, QueryOptions.legacy().effectiveRowCap());
        assertEquals(500, new QueryOptions(500, 0).effectiveRowCap());
        assertEquals(Integer.MAX_VALUE, new QueryOptions(0, 0).effectiveRowCap());
        assertTrue(new QueryOptions(0, 0).isUnlimited());
        assertEquals(0, new QueryOptions(-5, -1).maxRows());
        assertEquals(0, new QueryOptions(-5, -1).timeoutSeconds());
    }
}
