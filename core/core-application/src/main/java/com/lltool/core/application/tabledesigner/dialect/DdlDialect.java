package com.lltool.core.application.tabledesigner.dialect;

import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;

/**
 * Strategy interface defining database dialect-specific DDL generation rules.
 */
public interface DdlDialect {

    String engine();

    String quoteIdentifier(String identifier);

    String formatTableName(String schema, String table);

    String formatColumnType(ColumnDesign column);

    String formatDefaultValue(String defaultValue, String dataType, boolean isExpression);

    String formatColumnDefinition(ColumnDesign column, boolean isCreate);

    String generateCreateTable(TableDesign design);

    String generateTableComment(String schema, String table, String comment, boolean isDiff);

    String generateColumnComment(String schema, String table, String column, String comment, boolean isDiff);

    String escapeLiteral(String literal);
}

