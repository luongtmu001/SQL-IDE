package app.models;

public class QueryResultColumn {

    private final String name;
    private final String dataType;

    public QueryResultColumn(String name, String dataType) {
        this.name = name;
        this.dataType = dataType;
    }

    public String getName() {
        return name;
    }

    public String getDataType() {
        return dataType;
    }

    public String getHeaderLabel() {
        return dataType != null && !dataType.isBlank() ? (name + " [" + dataType + "]") : name;
    }

    @Override
    public String toString() {
        return getHeaderLabel();
    }
}

