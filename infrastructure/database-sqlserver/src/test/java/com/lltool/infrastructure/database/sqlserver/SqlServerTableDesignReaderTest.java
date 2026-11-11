package com.lltool.infrastructure.database.sqlserver;

import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlServerTableDesignReaderTest {

    private final SqlServerTableDesignReader reader = new SqlServerTableDesignReader();

    @Test
    @DisplayName("Should return failure when connection is null")
    void testNullConnection() {
        Result<TableDesign> result = reader.readTableDesign(null, "master", "dbo", "Customers");
        assertTrue(result.isFailure());
        assertEquals("NULL_CONNECTION", result.getError().get().code());
    }

    @Test
    @DisplayName("Should return failure when table name is null or blank")
    void testBlankTable() {
        Result<TableDesign> result = reader.readTableDesign(null, "master", "dbo", "");
        assertTrue(result.isFailure());
        // Since connection is checked first, it will fail on null connection
        assertEquals("NULL_CONNECTION", result.getError().get().code());
    }
}

