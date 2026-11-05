package app.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueryResultRow {

    private final int rowNumber;
    private final List<Object> values;

    public QueryResultRow(int rowNumber, List<Object> values) {
        this.rowNumber = rowNumber;
        this.values = new ArrayList<>(values);
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public Object getValue(int columnIndex) {
        if (columnIndex >= 0 && columnIndex < values.size()) {
            return values.get(columnIndex);
        }
        return null;
    }

    public List<Object> getValues() {
        return Collections.unmodifiableList(values);
    }

    public String toCsvLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            Object val = values.get(i);
            if (val == null) {
                sb.append("NULL");
            } else {
                String str = val.toString();
                if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                    sb.append("\"").append(str.replace("\"", "\"\"")).append("\"");
                } else {
                    sb.append(str);
                }
            }
        }
        return sb.toString();
    }

    public boolean matchesQuery(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        for (Object val : values) {
            if (val != null && val.toString().toLowerCase().contains(q)) {
                return true;
            }
        }
        return false;
    }
}

