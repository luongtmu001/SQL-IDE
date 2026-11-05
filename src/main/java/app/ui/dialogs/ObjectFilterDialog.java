package app.ui.dialogs;

import app.framework.metadata.filter.DesktopFilterManager;
import app.framework.metadata.filter.ObjectFilter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * SCR-DLG-006 Lọc đối tượng (bảng, view, function, procedure) — Phase 2B, {@link DialogChrome}.
 */
public class ObjectFilterDialog {

    public static boolean show(Stage owner, String connectionId, String catalog, String schema, String objectType, Runnable onApplied) {
        ObjectFilterDialog dialog = new ObjectFilterDialog();
        return dialog.display(owner, connectionId, catalog, schema, objectType, onApplied);
    }

    private boolean display(Stage owner, String connectionId, String catalog, String schema, String objectType, Runnable onApplied) {
        ObjectFilter currentFilter = DesktopFilterManager.getInstance().getObjectFilter(connectionId, catalog, schema, objectType);
        String scope = (catalog != null ? catalog : "") + (schema != null && !schema.isBlank() ? " · " + schema : "");
        DialogChrome chrome = DialogChrome.create(owner, "Lọc " + formatObjectTypeName(objectType), "fth-filter")
                .subtitle(scope);
        boolean[] applied = {false};

        HBox columnHeaders = new HBox(8);
        Label fieldHeader = new Label("Trường");
        fieldHeader.setPrefWidth(100);
        Label operatorHeader = new Label("Toán tử");
        operatorHeader.setPrefWidth(125);
        Label valueHeader = new Label("Giá trị");
        for (Label l : List.of(fieldHeader, operatorHeader, valueHeader)) l.getStyleClass().add("dialog-section");
        columnHeaders.getChildren().addAll(fieldHeader, operatorHeader, valueHeader);

        VBox conditionsList = new VBox(6);
        List<ConditionRow> conditionRows = new ArrayList<>();
        Runnable createRow = () -> {
            ConditionRow row = new ConditionRow(conditionsList, conditionRows);
            conditionRows.add(row);
            conditionsList.getChildren().add(row.getNode());
        };
        if (currentFilter.isActive() && !currentFilter.getConditions().isEmpty()) {
            for (ObjectFilter.Condition c : currentFilter.getConditions()) {
                ConditionRow row = new ConditionRow(conditionsList, conditionRows, c);
                conditionRows.add(row);
                conditionsList.getChildren().add(row.getNode());
            }
        } else {
            createRow.run();
        }

        Button addConditionBtn = DialogChrome.secondary("Thêm điều kiện");
        FontIcon plus = new FontIcon("fth-plus");
        plus.setIconSize(12);
        addConditionBtn.setGraphic(plus);
        addConditionBtn.setOnAction(e -> createRow.run());
        Region tbSpacer = new Region();
        HBox.setHgrow(tbSpacer, Priority.ALWAYS);
        Label logicLabel = new Label("Kết hợp:");
        logicLabel.getStyleClass().add("dialog-hint");
        ComboBox<ObjectFilter.Logic> logicCombo = new ComboBox<>();
        logicCombo.getItems().addAll(ObjectFilter.Logic.AND, ObjectFilter.Logic.OR);
        logicCombo.setValue(currentFilter.getLogic() != null ? currentFilter.getLogic() : ObjectFilter.Logic.AND);
        logicCombo.setPrefWidth(90);
        HBox actionToolbar = new HBox(10, addConditionBtn, tbSpacer, logicLabel, logicCombo);
        actionToolbar.setAlignment(Pos.CENTER_LEFT);
        actionToolbar.setPadding(new Insets(4, 0, 0, 0));

        Hyperlink clearBtn = new Hyperlink("Xóa bộ lọc");
        clearBtn.getStyleClass().addAll("dialog-link", "danger");
        clearBtn.setOnAction(e -> {
            DesktopFilterManager.getInstance().clearObjectFilter(connectionId, catalog, schema, objectType);
            applied[0] = true;
            chrome.close();
            if (onApplied != null) onApplied.run();
        });
        Button cancelBtn = DialogChrome.cancel("Hủy");
        cancelBtn.setOnAction(e -> chrome.close());
        Button applyBtn = DialogChrome.primary("Áp dụng");
        applyBtn.setOnAction(e -> {
            List<ObjectFilter.Condition> conditions = new ArrayList<>();
            for (ConditionRow cr : conditionRows) {
                var c = cr.getCondition();
                if (c != null && !c.value().isBlank()) conditions.add(c);
            }
            if (conditions.isEmpty()) {
                DesktopFilterManager.getInstance().clearObjectFilter(connectionId, catalog, schema, objectType);
            } else {
                DesktopFilterManager.getInstance().setObjectFilter(connectionId, catalog, schema, objectType,
                        new ObjectFilter(conditions, logicCombo.getValue()));
            }
            applied[0] = true;
            chrome.close();
            if (onApplied != null) onApplied.run();
        });

        VBox body = new VBox(6, columnHeaders, conditionsList, actionToolbar);
        chrome.content(body).footer(new Node[]{clearBtn}, cancelBtn, applyBtn).size(480, -1);
        chrome.showAndWait();
        return applied[0];
    }

    private static String formatObjectTypeName(String objectType) {
        if (objectType == null) return "đối tượng";
        return switch (objectType.toUpperCase()) {
            case "TABLE", "TABLES" -> "bảng";
            case "VIEW", "VIEWS" -> "view";
            case "FUNCTION", "FUNCTIONS" -> "function";
            case "PROCEDURE", "PROCEDURES" -> "procedure";
            default -> objectType.toLowerCase().replace('_', ' ');
        };
    }

    private static class ConditionRow {
        private final HBox row = new HBox(8);
        private final ComboBox<String> fieldCombo = new ComboBox<>();
        private final ComboBox<ObjectFilter.Operator> operatorCombo = new ComboBox<>();
        private final TextField valueField = new TextField();

        public ConditionRow(VBox parentContainer, List<ConditionRow> allRows) {
            this(parentContainer, allRows, new ObjectFilter.Condition("name", ObjectFilter.Operator.CONTAINS, ""));
        }

        public ConditionRow(VBox parentContainer, List<ConditionRow> allRows, ObjectFilter.Condition initial) {
            row.setAlignment(Pos.CENTER_LEFT);

            fieldCombo.getItems().addAll("Name", "Schema");  // values map to filter fields
            fieldCombo.setValue("schema".equalsIgnoreCase(initial.field()) ? "Schema" : "Name");
            fieldCombo.setPrefWidth(100);

            operatorCombo.getItems().addAll(ObjectFilter.Operator.values());
            operatorCombo.setValue(initial.operator() != null ? initial.operator() : ObjectFilter.Operator.CONTAINS);
            operatorCombo.setPrefWidth(125);
            operatorCombo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(ObjectFilter.Operator item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });
            operatorCombo.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(ObjectFilter.Operator item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getDisplayName());
                }
            });

            valueField.setText(initial.value());
            valueField.setPromptText("Nội dung lọc…");
            HBox.setHgrow(valueField, Priority.ALWAYS);

            Button removeBtn = new Button();
            removeBtn.getStyleClass().addAll("dialog-icon-button", "danger");
            removeBtn.setTooltip(new Tooltip("Xóa điều kiện"));
            FontIcon removeIcon = new FontIcon("fth-x");
            removeIcon.setIconSize(12);
            removeBtn.setGraphic(removeIcon);
            removeBtn.setOnAction(e -> {
                if (allRows.size() > 1) {
                    allRows.remove(this);
                    parentContainer.getChildren().remove(row);
                } else {
                    valueField.clear();
                }
            });

            row.getChildren().addAll(fieldCombo, operatorCombo, valueField, removeBtn);
        }

        public HBox getNode() {
            return row;
        }

        public ObjectFilter.Condition getCondition() {
            String val = valueField.getText() != null ? valueField.getText().trim() : "";
            return new ObjectFilter.Condition(fieldCombo.getValue().toLowerCase(), operatorCombo.getValue(), val);
        }
    }
}

