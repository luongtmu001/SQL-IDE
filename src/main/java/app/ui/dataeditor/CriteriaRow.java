package app.ui.dataeditor;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Model representing a single filtering/sorting condition in the Criteria Pane.
 */
public class CriteriaRow {

    private final StringProperty column = new SimpleStringProperty("");
    private final StringProperty operator = new SimpleStringProperty("=");
    private final StringProperty filterValue = new SimpleStringProperty("");
    private final StringProperty orValue = new SimpleStringProperty("");
    private final StringProperty sortType = new SimpleStringProperty("None");
    private final StringProperty sortOrder = new SimpleStringProperty("");

    public CriteriaRow() {}

    public CriteriaRow(String column, String operator, String filterValue, String orValue, String sortType, String sortOrder) {
        this.column.set(column != null ? column : "");
        this.operator.set(operator != null ? operator : "=");
        this.filterValue.set(filterValue != null ? filterValue : "");
        this.orValue.set(orValue != null ? orValue : "");
        this.sortType.set(sortType != null ? sortType : "None");
        this.sortOrder.set(sortOrder != null ? sortOrder : "");
    }

    public String getColumn() { return column.get(); }
    public void setColumn(String val) { column.set(val); }
    public StringProperty columnProperty() { return column; }

    public String getOperator() { return operator.get(); }
    public void setOperator(String val) { operator.set(val); }
    public StringProperty operatorProperty() { return operator; }

    public String getFilterValue() { return filterValue.get(); }
    public void setFilterValue(String val) { filterValue.set(val); }
    public StringProperty filterValueProperty() { return filterValue; }

    public String getOrValue() { return orValue.get(); }
    public void setOrValue(String val) { orValue.set(val); }
    public StringProperty orValueProperty() { return orValue; }

    public String getSortType() { return sortType.get(); }
    public void setSortType(String val) { sortType.set(val); }
    public StringProperty sortTypeProperty() { return sortType; }

    public String getSortOrder() { return sortOrder.get(); }
    public void setSortOrder(String val) { sortOrder.set(val); }
    public StringProperty sortOrderProperty() { return sortOrder; }
}

