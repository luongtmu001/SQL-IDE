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
        setPrefHeight(app.ui.components.UiMetrics.em(11.5));
        setMinHeight(app.ui.components.UiMetrics.em(7.5));

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

        FontIcon filterIcon = app.ui.components.Icons.of("fth-filter", app.ui.components.IconScale.SM);
        filterIcon.getStyleClass().add("icon-accent");

        Label lblTitle = new Label("Bộ lọc và sắp xếp");
        lblTitle.getStyleClass().add("criteria-header-title");

        lblCountBadge.getStyleClass().add("criteria-count-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAdd = new Button("Thêm tiêu chí");
        btnAdd.getStyleClass().add("button");
        btnAdd.setGraphic(app.ui.components.Icons.of("fth-plus", app.ui.components.IconScale.SM));
        btnAdd.setOnAction(e -> addEmptyRow());

        Button btnClear = new Button("Xóa hết");
        btnClear.getStyleClass().add("button");
        btnClear.setGraphic(app.ui.components.Icons.of("fth-trash-2", app.ui.components.IconScale.SM));
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
        colIndex.setPrefWidth(app.ui.components.UiMetrics.em(2.69));
        colIndex.setMaxWidth(app.ui.components.UiMetrics.em(3.46));
        colIndex.getStyleClass().add("criteria-col-index");
        colIndex.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(criteriaList.indexOf(data.getValue()) + 1)));

        // 2. Column
        TableColumn<CriteriaRow, String> colColumn = new TableColumn<>("Cột");
        colColumn.setPrefWidth(app.ui.components.UiMetrics.em(12.31));
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
        colOp.setPrefWidth(app.ui.components.UiMetrics.em(10));
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
        colFilter.setPrefWidth(app.ui.components.UiMetrics.em(13.85));
        colFilter.setCellValueFactory(data -> data.getValue().filterValueProperty());
        colFilter.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.getStyleClass().add("code-inline");
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
        colOr.setPrefWidth(app.ui.components.UiMetrics.em(10.77));
        colOr.setCellValueFactory(data -> data.getValue().orValueProperty());
        colOr.setCellFactory(tc -> new TableCell<>() {
            private final TextField tf = new TextField();
            {
                tf.setMaxWidth(Double.MAX_VALUE);
                tf.getStyleClass().add("code-inline");
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
        colSort.setPrefWidth(app.ui.components.UiMetrics.em(8.46));
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
        colOrder.setPrefWidth(app.ui.components.UiMetrics.em(4.23));
        colOrder.getStyleClass().add("criteria-col-center");
        colOrder.setCellValueFactory(data -> data.getValue().sortOrderProperty());
        colOrder.setCellFactory(TextFieldTableCell.forTableColumn());
        colOrder.setOnEditCommit(e -> {
            e.getRowValue().setSortOrder(e.getNewValue());
            if (onChanged != null) onChanged.run();
        });

        // 8. Delete button
        TableColumn<CriteriaRow, Void> colDel = new TableColumn<>("Xóa");
        colDel.setPrefWidth(app.ui.components.UiMetrics.em(3.46));
        colDel.setMaxWidth(app.ui.components.UiMetrics.em(3.85));
        colDel.getStyleClass().add("criteria-col-center");
        colDel.setCellFactory(tc -> new TableCell<>() {
            private final Button btn = new Button();
            {
                btn.getStyleClass().add("button");
                FontIcon icon = app.ui.components.Icons.of("fth-x", app.ui.components.IconScale.SM);
                icon.getStyleClass().add("icon-danger");
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

    public record WhereClause(String sql, List<Object> params, String previewSql) {}

    /**
     * Builds the SQL WHERE clause string based on current criteria (DE-9).
     */
    public String buildWhereClause(boolean isPg) {
        WhereClause wc = buildWhere(null, isPg);
        return wc != null ? wc.previewSql() : null;
    }

    public WhereClause buildWhere(com.lltool.core.domain.tableeditor.model.TableEditorMetadata metadata, boolean isPg) {
        List<String> conditions = new ArrayList<>();
        List<String> previewConditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (CriteriaRow r : criteriaList) {
            String col = r.getColumn();
            if (col == null || col.isBlank()) continue;

            String quotedCol = com.lltool.core.application.tableeditor.TableDataEditorService.quoteIdent(col, isPg);
            String op = r.getOperator() != null ? r.getOperator().trim().toUpperCase() : "=";
            String val = r.getFilterValue() != null ? r.getFilterValue().trim() : "";
            String orVal = r.getOrValue() != null ? r.getOrValue().trim() : "";

            if ("IS NULL".equals(op) || "IS NOT NULL".equals(op)) {
                conditions.add(quotedCol + " " + op);
                previewConditions.add(quotedCol + " " + op);
                continue;
            }

            if (val.isBlank()) continue;

            var c1 = buildSingleCondition(quotedCol, op, val, isPg);
            if (!orVal.isBlank()) {
                var c2 = buildSingleCondition(quotedCol, op, orVal, isPg);
                conditions.add("(" + c1.sql() + " OR " + c2.sql() + ")");
                previewConditions.add("(" + c1.preview() + " OR " + c2.preview() + ")");
                params.addAll(c1.params());
                params.addAll(c2.params());
            } else {
                conditions.add(c1.sql());
                previewConditions.add(c1.preview());
                params.addAll(c1.params());
            }
        }

        if (conditions.isEmpty()) return null;
        return new WhereClause(String.join(" AND ", conditions), params, String.join(" AND ", previewConditions));
    }

    private record CondResult(String sql, String preview, List<Object> params) {}

    private CondResult buildSingleCondition(String quotedCol, String op, String val, boolean isPg) {
        if ("IN".equals(op) || "NOT IN".equals(op)) {
            String clean = val;
            if (clean.startsWith("(") && clean.endsWith(")")) {
                clean = clean.substring(1, clean.length() - 1).trim();
            }
            String[] tokens = clean.split(",");
            List<String> formatted = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            List<Object> params = new ArrayList<>();

            for (String tok : tokens) {
                String t = tok.trim();
                if (t.isEmpty()) continue;
                placeholders.add("?");
                if (isStrictNumeric(t)) {
                    formatted.add(t);
                    params.add(t.contains(".") ? (Number) Double.valueOf(t) : (Number) Long.valueOf(t));
                } else {
                    String str = stripQuotes(t);
                    formatted.add("'" + str.replace("'", "''") + "'");
                    params.add(str);
                }
            }
            if (placeholders.isEmpty()) {
                return new CondResult("1 = 1", "1 = 1", List.of());
            }
            String sql = quotedCol + " " + op + " (" + String.join(", ", placeholders) + ")";
            String preview = quotedCol + " " + op + " (" + String.join(", ", formatted) + ")";
            return new CondResult(sql, preview, params);
        }

        if ("LIKE".equals(op) || "NOT LIKE".equals(op)) {
            String str = stripQuotes(val);
            if (!str.contains("%") && !str.contains("_")) {
                str = "%" + str + "%";
            }
            String sql = quotedCol + " " + op + " ?";
            String preview = quotedCol + " " + op + " '" + str.replace("'", "''") + "'";
            return new CondResult(sql, preview, List.of(str));
        }

        if ("NULL".equalsIgnoreCase(val)) {
            if ("=".equals(op)) {
                return new CondResult(quotedCol + " IS NULL", quotedCol + " IS NULL", List.of());
            } else if ("!=".equals(op) || "<>".equals(op)) {
                return new CondResult(quotedCol + " IS NOT NULL", quotedCol + " IS NOT NULL", List.of());
            }
        }

        if (isStrictNumeric(val)) {
            Object num = val.contains(".") ? (Number) Double.valueOf(val) : (Number) Long.valueOf(val);
            return new CondResult(quotedCol + " " + op + " ?", quotedCol + " " + op + " " + val, List.of(num));
        }

        String str = stripQuotes(val);
        String previewVal = "'" + str.replace("'", "''") + "'";
        return new CondResult(quotedCol + " " + op + " ?", quotedCol + " " + op + " " + previewVal, List.of(str));
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        String t = s.trim();
        if ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith("\"") && t.endsWith("\""))) {
            if (t.length() >= 2) {
                return t.substring(1, t.length() - 1);
            }
        }
        return t;
    }

    private static boolean isStrictNumeric(String s) {
        if (s == null || s.isBlank()) return false;
        return s.trim().matches("^[+-]?\\d+(\\.\\d+)?$");
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
