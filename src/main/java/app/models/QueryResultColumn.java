package app.models;

public class QueryResultColumn {

    private final String name;
    private final String dataType;
    private final String tableName;

    public QueryResultColumn(String name, String dataType) {
        this(name, dataType, null);
    }

    public QueryResultColumn(String name, String dataType, String tableName) {
        this.name = name;
        this.dataType = dataType;
        this.tableName = tableName;
    }

    public String getName() {
        return name;
    }

    public String getDataType() {
        return dataType;
    }

    public String getTableName() {
        return tableName;
    }

    public String getHeaderLabel() {
        return dataType != null && !dataType.isBlank() ? (name + " [" + dataType + "]") : name;
    }

    @Override
    public String toString() {
        return getHeaderLabel();
    }
}

