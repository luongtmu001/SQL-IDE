package app.ui.dataeditor;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/**
 * SSMS-style Criteria Pane for Table Data Editor.
 * Allows adding structured multi-column filter conditions, operators, OR conditions, and sorting orders.
 */
public class CriteriaGrid extends VBox {

    private final ObservableList<CriteriaRow> criteriaList = FXCollections.observableArrayList();
    private final TableView<CriteriaRow> tableView = new TableView<>();
    private final List<String> availableColumns = new ArrayList<>();
    private final Label lblCountBadge = new Label("0 điều kiện");
    private Runnable onChanged;

    public CriteriaGrid() {
        getStyleClass().add("tde-criteria-pane");
        setPrefHeight(150);
        setMinHeight(100);

        HBox header = buildHeader();
        setupTable();

        VBox.setVgrow(tableView, Priority.ALWAYS);
        getChildren().addAll(header, tableView);

        criteriaList.addListener((ListChangeListener<CriteriaRow>) c -> {
            updateBadge();
            if (onChanged != null) onChanged.run();
        });
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public void setAvailableColumns(List<String> cols) {
        this.availableColumns.clear();
        if (cols != null) {
            this.availableColumns.addAll(cols);
        }
        setupColumns();
    }

    public ObservableList<CriteriaRow> getCriteriaList() {
        return criteriaList;
    }

    private HBox buildHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(3, 10, 3, 10));
        header.getStyleClass().add("tde-pane-header");

        FontIcon filterIcon = new FontIcon("fth-filter");
        filterIcon.setIconSize(12);
        filterIcon.setStyle("-fx-icon-color: -color-accent-fg;");

        Label lblTitle = new Label("Bộ lọc và sắp xếp");
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 0.85em; -fx-text-fill: -color-fg-default;");

        lblCountBadge.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; " +
                "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 1 5 1 5; -fx-font-size: 0.8em; -fx-text-fill: -color-fg-muted;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAdd = new Button("Thêm tiêu chí");
        btnAdd.getStyleClass().addAll("button", "small");
        FontIcon addIcon = new FontIcon("fth-plus");
        addIcon.setIconSize(11);
        btnAdd.setGraphic(addIcon);
        btnAdd.setOnAction(e -> addEmptyRow());

        Button btnClear = new Button("Xóa hết");
        btnClear.getStyleClass().addAll("button", "small");
        FontIcon clearIcon = new FontIcon("fth-trash-2");
        clearIcon.setIconSize(11);
        btnClear.setGraphic(clearIcon);
        btnClear.setOnAction(e -> criteriaList.clear());

        header.getChildren().addAll(filterIcon, lblTitle, lblCountBadge, spacer, btnAdd, btnClear);
        return header;
    }

    private void setupTable() {
        tableView.setEditable(true);
        tableView.setItems(criteriaList);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("Chưa có điều kiện lọc nào. Nhấn '+ Thêm tiêu chí' để lọc hoặc sắp xếp."));
        setupColumns();
    }

    private void setupColumns() {
        tableView.getColumns().clear();

        // 1. Row Index (#)
        TableColumn<CriteriaRow, String> colIndex = new TableColumn<>("#");
        colIndex.setPrefWidth(35);
        colIndex.setMaxWidth(45);
        colIndex.setStyle("-fx-alignment: CENTER; -fx-font-family: monospace;");
        colIndex.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(criteriaList.indexOf(data.getValue()) + 1)));

        // 2. Column
        TableColumn<CriteriaRow, String> colColumn = new TableColumn<>("Cột");
        colColumn.setPrefWidth(160);
        colColumn.setCellValueFactory(data -> data.getValue().columnProperty());
        colColumn.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>();
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setOnAction(e -> {
                    CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && combo.getValue() != null) {
                        row.setColumn(combo.getValue());
                        if (onChanged != null) onChanged.run();
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    combo.setItems(FXCollections.observableArrayList(availableColumns));
                    combo.setValue(item);
                    setGraphic(combo);
                }
            }
        });

        // 3. Operator
        TableColumn<CriteriaRow, String> colOp = new TableColumn<>("Toán tử");
        colOp.setPrefWidth(130);
        colOp.setCellValueFactory(data -> data.getValue().operatorProperty());
        colOp.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(
                    "=", "<>", ">", "<", ">=", "<=", "LIKE", "NOT LIKE", "IN", "IS NULL", "IS NOT NULL"
            ));
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setOnAction(e -> {
                    CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && combo.getValue() != null) {
                        row.setOperator(combo.getValue());
                        if (onChanged != null) onChanged.run();
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    combo.setValue(item);
                    setGraphic(combo);
                }
            }
        });

        // 4. Filter Value
        TableColumn<CriteriaRow, String> colFilter = new TableColumn<>("Giá trị");
        colFilter.setPrefWidth(180);
        colFilter.setCellValueFactory(data -> data.getValue().filterValueProperty());
        colFilter.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.setStyle("-fx-font-family: monospace;");
                tf.setOnAction(e -> commitVal());
                tf.focusedProperty().addListener((obs, oldV, newV) -> { if (!newV) commitVal(); });
            }
            private void commitVal() {
                CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                if (row != null) {
                    row.setFilterValue(tf.getText());
                    if (onChanged != null) onChanged.run();
                }
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    boolean isNullOp = row != null && ("IS NULL".equals(row.getOperator()) || "IS NOT NULL".equals(row.getOperator()));
                    tf.setDisable(isNullOp);
                    tf.setPromptText(isNullOp ? "(Không cần giá trị)" : "Nhập giá trị...");
                    tf.setText(item != null ? item : "");
                    setGraphic(tf);
                }
            }
        });

        // 5. Or Value
        TableColumn<CriteriaRow, String> colOr = new TableColumn<>("Hoặc");
        colOr.setPrefWidth(140);
        colOr.setCellValueFactory(data -> data.getValue().orValueProperty());
        colOr.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.setStyle("-fx-font-family: monospace;");
                tf.setOnAction(e -> commitVal());
                tf.focusedProperty().addListener((obs, oldV, newV) -> { if (!newV) commitVal(); });
            }
            private void commitVal() {
                CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                if (row != null) {
                    row.setOrValue(tf.getText());
                    if (onChanged != null) onChanged.run();
                }
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    boolean isNullOp = row != null && ("IS NULL".equals(row.getOperator()) || "IS NOT NULL".equals(row.getOperator()));
                    tf.setDisable(isNullOp);
                    tf.setPromptText(isNullOp ? "(Không cần)" : "(Hoặc giá trị khác)");
                    tf.setText(item != null ? item : "");
                    setGraphic(tf);
                }
            }
        });

        // 6. Sort Type
        TableColumn<CriteriaRow, String> colSort = new TableColumn<>("Sắp xếp");
        colSort.setPrefWidth(110);
        colSort.setCellValueFactory(data -> data.getValue().sortTypeProperty());
        colSort.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("None", "ASC", "DESC"));
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setOnAction(e -> {
                    CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && combo.getValue() != null) {
                        row.setSortType(combo.getValue());
                        if (onChanged != null) onChanged.run();
                    }
                });
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    combo.setValue(item != null ? item : "None");
                    setGraphic(combo);
                }
            }
        });

        // 7. Sort Order
        TableColumn<CriteriaRow, String> colOrder = new TableColumn<>("Thứ tự");
        colOrder.setPrefWidth(55);
        colOrder.setStyle("-fx-alignment: CENTER;");
        colOrder.setCellValueFactory(data -> data.getValue().sortOrderProperty());
        colOrder.setCellFactory(TextFieldTableCell.forTableColumn());
        colOrder.setOnEditCommit(e -> {
            e.getRowValue().setSortOrder(e.getNewValue());
            if (onChanged != null) onChanged.run();
        });

        // 8. Delete button
        TableColumn<CriteriaRow, Void> colDel = new TableColumn<>("Xóa");
        colDel.setPrefWidth(45);
        colDel.setMaxWidth(50);
        colDel.setStyle("-fx-alignment: CENTER;");
        colDel.setCellFactory(tc -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.getStyleClass().addAll("button", "small");
                FontIcon icon = new FontIcon("fth-x");
                icon.setIconSize(11);
                icon.setStyle("-fx-icon-color: -color-danger-fg;");
                btn.setGraphic(icon);
                btn.setTooltip(new Tooltip("Xóa điều kiện này"));
                btn.setOnAction(e -> {
                    CriteriaRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) {
                        criteriaList.remove(row);
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tableView.getColumns().addAll(List.of(colIndex, colColumn, colOp, colFilter, colOr, colSort, colOrder, colDel));
    }

    public void addEmptyRow() {
        String defaultCol = !availableColumns.isEmpty() ? availableColumns.get(0) : "";
        criteriaList.add(new CriteriaRow(defaultCol, "=", "", "", "None", ""));
    }

    private void updateBadge() {
        lblCountBadge.setText(criteriaList.size() + " điều kiện");
    }

    /**
     * Builds the SQL WHERE clause string based on current criteria.
     */
    public String buildWhereClause(boolean isPg) {
        List<String> conditions = new ArrayList<>();
        for (CriteriaRow r : criteriaList) {
            String col = r.getColumn();
            if (col == null || col.isBlank()) continue;

            String quotedCol = isPg ? ("\"" + col + "\"") : ("[" + col + "]");
            String op = r.getOperator() != null ? r.getOperator().toUpperCase() : "=";
            String val = r.getFilterValue() != null ? r.getFilterValue().trim() : "";
            String orVal = r.getOrValue() != null ? r.getOrValue().trim() : "";

            if ("IS NULL".equals(op) || "IS NOT NULL".equals(op)) {
                conditions.add(quotedCol + " " + op);
                continue;
            }

            if (val.isBlank()) continue;

            String cond1 = buildSingleCondition(quotedCol, op, val);
            if (!orVal.isBlank()) {
                String cond2 = buildSingleCondition(quotedCol, op, orVal);
                conditions.add("(" + cond1 + " OR " + cond2 + ")");
            } else {
                conditions.add(cond1);
            }
        }

        if (conditions.isEmpty()) return null;
        return String.join(" AND ", conditions);
    }

    private String buildSingleCondition(String quotedCol, String op, String val) {
        if (!val.startsWith("'") && !isNumeric(val) && !val.equalsIgnoreCase("NULL")) {
            val = "'" + val.replace("'", "''") + "'";
        }
        return quotedCol + " " + op + " " + val;
    }

    private boolean isNumeric(String s) {
        if (s == null) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Builds the SQL ORDER BY clause based on current criteria.
     */
    public String buildOrderByClause(boolean isPg) {
        List<CriteriaRow> sortRows = new ArrayList<>();
        for (CriteriaRow r : criteriaList) {
            if (r.getColumn() != null && !r.getColumn().isBlank() && r.getSortType() != null && !"None".equalsIgnoreCase(r.getSortType())) {
                sortRows.add(r);
            }
        }
        if (sortRows.isEmpty()) return null;

        sortRows.sort((a, b) -> {
            int ordA = parseOrder(a.getSortOrder());
            int ordB = parseOrder(b.getSortOrder());
            return Integer.compare(ordA, ordB);
        });

        List<String> orders = new ArrayList<>();
        for (CriteriaRow r : sortRows) {
            String quotedCol = isPg ? ("\"" + r.getColumn() + "\"") : ("[" + r.getColumn() + "]");
            orders.add(quotedCol + " " + r.getSortType().toUpperCase());
        }
        return String.join(", ", orders);
    }

    private int parseOrder(String s) {
        if (s == null || s.isBlank()) return 999;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 999;
        }
    }
}
