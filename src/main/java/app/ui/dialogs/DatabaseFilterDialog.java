package app.ui.dialogs;

import app.framework.metadata.filter.DatabaseFilter;
import app.framework.metadata.filter.DesktopFilterManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.*;

/**
 * SCR-DLG-005 Lọc database (Phase 2B): {@link DialogChrome}, accent/danger classes instead of fixed colors.
 */
public class DatabaseFilterDialog {

    public static boolean show(Stage owner, String connectionId, String connectionName, List<String> allDatabases, Runnable onApplied) {
        return new DatabaseFilterDialog().display(owner, connectionId, connectionName, allDatabases, onApplied);
    }

    private boolean display(Stage owner, String connectionId, String connectionName, List<String> allDatabases, Runnable onApplied) {
        DialogChrome chrome = DialogChrome.create(owner, "Lọc database", "fth-filter")
                .subtitle(connectionName != null ? connectionName : "Kết nối");

        DatabaseFilter currentFilter = DesktopFilterManager.getInstance().getDatabaseFilter(connectionId);
        Set<String> selectedSet = new HashSet<>();
        if (currentFilter.isActive()) selectedSet.addAll(currentFilter.getSelectedDatabases());
        else selectedSet.addAll(allDatabases);
        boolean[] applied = {false};

        TextField searchField = new TextField();
        searchField.setPromptText("Tìm database…");

        Hyperlink selectAllLink = new Hyperlink("Chọn tất cả");
        selectAllLink.getStyleClass().add("dialog-link");
        Hyperlink deselectAllLink = new Hyperlink("Bỏ chọn tất cả");
        deselectAllLink.getStyleClass().add("dialog-link");
        Region selSpacer = new Region();
        HBox.setHgrow(selSpacer, Priority.ALWAYS);
        Label statsLabel = new Label();
        statsLabel.getStyleClass().add("dialog-hint");
        Runnable updateStats = () -> statsLabel.setText("Đã chọn " + selectedSet.size() + "/" + allDatabases.size());
        updateStats.run();
        HBox selectionBar = new HBox(12, selectAllLink, deselectAllLink, selSpacer, statsLabel);
        selectionBar.setAlignment(Pos.CENTER_LEFT);

        VBox checklistContent = new VBox(6);
        checklistContent.setPadding(new Insets(8));
        Map<String, CheckBox> checkBoxes = new LinkedHashMap<>();
        for (String db : allDatabases) {
            CheckBox cb = new CheckBox(db);
            cb.setSelected(selectedSet.contains(db));
            cb.setOnAction(e -> {
                if (cb.isSelected()) selectedSet.add(db);
                else selectedSet.remove(db);
                updateStats.run();
            });
            checkBoxes.put(db, cb);
            checklistContent.getChildren().add(cb);
        }
        ScrollPane scrollPane = new ScrollPane(checklistContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(240);
        scrollPane.getStyleClass().add("dialog-list-box");

        searchField.textProperty().addListener((obs, o, n) -> {
            String q = n != null ? n.trim().toLowerCase(Locale.ROOT) : "";
            checklistContent.getChildren().clear();
            for (String db : allDatabases) {
                if (q.isEmpty() || db.toLowerCase(Locale.ROOT).contains(q)) checklistContent.getChildren().add(checkBoxes.get(db));
            }
        });
        selectAllLink.setOnAction(e -> setVisible(searchField, allDatabases, checkBoxes, selectedSet, true, updateStats));
        deselectAllLink.setOnAction(e -> setVisible(searchField, allDatabases, checkBoxes, selectedSet, false, updateStats));

        Hyperlink resetBtn = new Hyperlink("Đặt lại");
        resetBtn.getStyleClass().addAll("dialog-link", "danger");
        resetBtn.setOnAction(e -> {
            selectedSet.clear();
            selectedSet.addAll(allDatabases);
            checkBoxes.values().forEach(cb -> cb.setSelected(true));
            updateStats.run();
        });

        Button cancelBtn = DialogChrome.cancel("Hủy");
        cancelBtn.setOnAction(e -> chrome.close());
        Button applyBtn = DialogChrome.primary("Áp dụng");
        applyBtn.setOnAction(e -> {
            DesktopFilterManager.getInstance().setDatabaseFilter(connectionId,
                    selectedSet.size() == allDatabases.size() ? new DatabaseFilter() : new DatabaseFilter(selectedSet));
            applied[0] = true;
            chrome.close();
            if (onApplied != null) onApplied.run();
        });

        VBox body = new VBox(10, searchField, selectionBar, scrollPane);
        chrome.content(body).footer(new Node[]{resetBtn}, cancelBtn, applyBtn).size(400, -1);
        chrome.showAndWait();
        return applied[0];
    }

    private static void setVisible(TextField search, List<String> all, Map<String, CheckBox> boxes, Set<String> selected,
                                   boolean value, Runnable updateStats) {
        String q = search.getText() != null ? search.getText().trim().toLowerCase(Locale.ROOT) : "";
        for (String db : all) {
            if (q.isEmpty() || db.toLowerCase(Locale.ROOT).contains(q)) {
                CheckBox cb = boxes.get(db);
                if (cb != null) cb.setSelected(value);
                if (value) selected.add(db);
                else selected.remove(db);
            }
        }
        updateStats.run();
    }
}
