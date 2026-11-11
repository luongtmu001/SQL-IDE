package app.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueryResult {

    private final String query;
    private final List<QueryResultColumn> columns;
    private final List<QueryResultRow> rows;
    private final long executionDurationMs;
    private final String timestamp;
    private final String sourceTable;

    public QueryResult(String query, List<QueryResultColumn> columns, List<QueryResultRow> rows, long executionDurationMs, String timestamp) {
        this(query, columns, rows, executionDurationMs, timestamp, null);
    }

    public QueryResult(String query, List<QueryResultColumn> columns, List<QueryResultRow> rows, long executionDurationMs, String timestamp, String sourceTable) {
        this.query = query;
        this.columns = new ArrayList<>(columns);
        this.rows = new ArrayList<>(rows);
        this.executionDurationMs = executionDurationMs;
        this.timestamp = timestamp;
        this.sourceTable = sourceTable;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public String getQuery() {
        return query;
    }

    public List<QueryResultColumn> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public List<QueryResultRow> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return columns.size();
    }
}

