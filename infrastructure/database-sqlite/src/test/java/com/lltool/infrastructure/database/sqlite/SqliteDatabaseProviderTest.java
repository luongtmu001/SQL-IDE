package com.lltool.infrastructure.database.sqlite;

import com.lltool.core.common.result.Result;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseConnectionConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseProviderTest {

    @Test
    @DisplayName("SqliteProvider connects to in-memory database and executes query")
    void testConnectAndQuery() throws Exception {
        SqliteDatabaseProvider provider = new SqliteDatabaseProvider();
        DatabaseConnectionConfig config = DatabaseConnectionConfig.sqlite("sqlite-mem", "In-Memory Test", ":memory:");

        Result<DatabaseConnection> connRes = provider.connect(config, "");
        assertTrue(connRes.isSuccess());

        try (DatabaseConnection conn = connRes.getDataOrThrow()) {
            assertTrue(conn.isValid(2));
            try (Statement stmt = conn.getRawJdbcConnection().createStatement()) {
                stmt.execute("CREATE TABLE test_table (id INTEGER PRIMARY KEY, name TEXT)");
                stmt.execute("INSERT INTO test_table (id, name) VALUES (1, 'Alpha')");

                try (ResultSet rs = stmt.executeQuery("SELECT name FROM test_table WHERE id = 1")) {
                    assertTrue(rs.next());
                    assertEquals("Alpha", rs.getString("name"));
                }
            }
        }
    }
}

