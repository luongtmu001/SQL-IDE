package com.lltool.infrastructure.database.api.metadata;

import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.infrastructure.database.api.DatabaseConnection;

/**
 * Service interface for reading comprehensive table design metadata from a database.
 * Extracts column lengths, scales, identity properties, sequences, computed formulas,
 * defaults, collations, comments, and full constraint graphs (indexes, FKs, checks, uniques, triggers).
 */
public interface TableDesignReader {

    Result<TableDesign> readTableDesign(DatabaseConnection connection, String catalog, String schema, String table);
}

