package app.ui.dataeditor;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.sql.Types;
import java.util.Objects;

/**
 * Custom TableCell supporting inline editing, strongly typed parsing, NULL styling, and dirty tracking (DE-6, DE-8).
 */
public class EditableDataTableCell extends TableCell<ObservableDataRow, Object> {

    @FunctionalInterface
    public interface CellChangeListener {
        void onCellChanged(ObservableDataRow row, String columnName, Object newValue);
    }

    private final String columnName;
    private final boolean readOnly;
    private final int sqlType;
    private final String typeName;
    private final CellChangeListener changeListener;
    private TextField textField;
    private String startEditText = "";

    public EditableDataTableCell(String columnName, boolean readOnly) {
        this(columnName, readOnly, Types.OTHER, "", null);
    }

    public EditableDataTableCell(String columnName, boolean readOnly, int sqlType, String typeName, CellChangeListener changeListener) {
        this.columnName = columnName;
        this.readOnly = readOnly;
        this.sqlType = sqlType;
        this.typeName = typeName != null ? typeName : "";
        this.changeListener = changeListener;
        setAlignment(Pos.CENTER_LEFT);
        if (!readOnly) {
            setupContextMenu();
        }
    }

    private void setupContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem miNull = new MenuItem("Gán giá trị [NULL]");
        miNull.setOnAction(e -> applyValue(null));

        MenuItem miEmpty = new MenuItem("Gán chuỗi rỗng ''");
        miEmpty.setOnAction(e -> applyValue(""));

        MenuItem miRevertCell = new MenuItem("Hoàn tác ô này");
        miRevertCell.setOnAction(e -> {
            ObservableDataRow row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                Object orig = row.getOriginalValues().get(columnName);
                applyValue(orig);
            }
        });

        MenuItem miRevertRow = new MenuItem("Hoàn tác cả dòng");
        miRevertRow.setOnAction(e -> {
            ObservableDataRow row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                row.revert();
                if (changeListener != null) {
                    changeListener.onCellChanged(row, columnName, getItem());
                }
                updateCellStyles();
            }
        });

        MenuItem miDeleteRow = new MenuItem("Đánh dấu xoá dòng");
        miDeleteRow.setOnAction(e -> {
            ObservableDataRow row = getTableRow() != null ? getTableRow().getItem() : null;
            if (row != null) {
                if (row.getState() == ObservableDataRow.RowState.DELETED) {
                    row.unmarkDeleted();
                } else {
                    row.markDeleted();
                }
                if (changeListener != null) {
                    changeListener.onCellChanged(row, columnName, getItem());
                }
                updateCellStyles();
            }
        });

        menu.getItems().addAll(miNull, miEmpty, miRevertCell, new SeparatorMenuItem(), miRevertRow, miDeleteRow);
        setContextMenu(menu);
    }

    @Override
    public void startEdit() {
        if (readOnly || isEmpty()) return;
        ObservableDataRow row = getTableRow() != null ? getTableRow().getItem() : null;
        if (row != null && row.getState() == ObservableDataRow.RowState.DELETED) {
            return; // Cannot edit deleted row
        }

        super.startEdit();
        if (textField == null) {
            createTextField();
        }
        Object item = getItem();
        startEditText = item != null ? String.valueOf(item) : "";
        textField.setText(startEditText);
        textField.getStyleClass().remove("cell-invalid");
        textField.setTooltip(null);
        setText(null);
        setGraphic(textField);
        textField.selectAll();
        textField.requestFocus();
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        if (textField != null) {
            textField.getStyleClass().remove("cell-invalid");
            textField.setTooltip(null);
        }
        setText(formatText(getItem()));
        setGraphic(null);
        updateCellStyles();
    }

    @Override
    protected void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);

        if (empty) {
            setText(null);
            setGraphic(null);
            getStyleClass().removeAll("cell-dirty", "cell-null", "row-deleted", "cell-numeric");
        } else {
            if (item instanceof Number) {
                if (!getStyleClass().contains("cell-numeric")) {
                    getStyleClass().add("cell-numeric");
                }
            } else {
                getStyleClass().remove("cell-numeric");
            }

            if (isEditing()) {
                if (textField != null) {
                    textField.setText(item != null ? String.valueOf(item) : "");
                }
                setText(null);
                setGraphic(textField);
            } else {
                setText(formatText(item));
                setGraphic(null);
            }
            updateCellStyles();
        }
    }

    private void createTextField() {
        textField = new TextField(formatText(getItem()));
        textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);

        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.DIGIT0) {
                applyValue(null);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                applyEdit();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                cancelEdit();
                e.consume();
            }
        });

        textField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV && isEditing()) {
                applyEdit();
            }
        });
    }

    private boolean applyEdit() {
        if (!isEditing() || textField == null) return true;
        String text = textField.getText();
        if (getItem() == null && (text == null || text.isEmpty())) {
            cancelEdit();
            return true;
        }
        if (Objects.equals(startEditText, text)) {
            cancelEdit();
            return true;
        }
        try {
            Object parsed = CellValueParser.parse(text, sqlType, typeName);
            if (ObservableDataRow.areValuesEqual(getItem(), parsed)) {
                cancelEdit();
                return true;
            }
            applyValue(parsed);
            return true;
        } catch (Exception ex) {
            if (!textField.getStyleClass().contains("cell-invalid")) {
                textField.getStyleClass().add("cell-invalid");
            }
            textField.setTooltip(new Tooltip(ex.getMessage()));
            return false;
        }
    }

    private void applyValue(Object newVal) {
        ObservableDataRow row = getTableRow() != null ? getTableRow().getItem() : null;
        if (row != null) {
            row.setValue(columnName, newVal);
            commitEdit(newVal);
            if (changeListener != null) {
                changeListener.onCellChanged(row, columnName, newVal);
            }
            updateCellStyles();
        }
    }

    private void updateCellStyles() {
        getStyleClass().removeAll("cell-dirty", "cell-null", "row-deleted");

        ObservableDataRow row = getTableRow() != null ? getTableRow().getItem() : null;
        if (row != null) {
            if (row.getState() == ObservableDataRow.RowState.DELETED) {
                getStyleClass().add("row-deleted");
            } else if (row.isCellDirty(columnName)) {
                getStyleClass().add("cell-dirty");
            }
        }

        if (getItem() == null) {
            getStyleClass().add("cell-null");
        }
    }

    private String formatText(Object item) {
        if (item == null) {
            return "NULL";
        }
        return displayText(item);
    }

    /** Text shown for a value: floating point numbers are never written in scientific notation. */
    public static String displayText(Object item) {
        if (item instanceof Double || item instanceof Float) {
            double d = ((Number) item).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(item);
            return new java.math.BigDecimal(String.valueOf(item)).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(item);
    }
}
