package com.lltool.infrastructure.database.postgresql;

import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostgresTableDesignReaderTest {

    private final PostgresTableDesignReader reader = new PostgresTableDesignReader();

    @Test
    @DisplayName("Should return failure when connection is null")
    void testNullConnection() {
        Result<TableDesign> result = reader.readTableDesign(null, "postgres", "public", "customers");
        assertTrue(result.isFailure());
        assertEquals("NULL_CONNECTION", result.getError().get().code());
    }

    @Test
    @DisplayName("Should return failure when table name is null or blank")
    void testBlankTable() {
        Result<TableDesign> result = reader.readTableDesign(null, "postgres", "public", "");
        assertTrue(result.isFailure());
        assertEquals("NULL_CONNECTION", result.getError().get().code());
    }
}

