package app.ui.results;

import app.ui.results.GridFilterState.ColumnFilter;
import app.ui.results.GridFilterState.Op;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/**
 * Column filter (user mockup 2026-11-05): condition + checklist of distinct values with counts;
 * the search box filters the checklist as you type. Opens under the column's filter button.
 */
public final class ColumnFilterPopup {

    private static final int MAX_VALUES = 10_000;

    /** One checklist entry. */
    private static final class Item {
        final String key;
        final String text;
        final int count;
        final BooleanProperty checked = new SimpleBooleanProperty(true);

        Item(String key, String text, int count) {
            this.key = key;
            this.text = text;
            this.count = count;
        }
    }

    private ColumnFilterPopup() {
    }

    /**
     * @param nullText how NULL is displayed in the grid (setting grid.nullDisplayString)
     * @param onApplied called after the filter changed (to restyle the header button)
     */
    public static void show(Node anchor, GridFilterState state, int colIndex, String colName,
                            String nullText, Runnable onApplied) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        ColumnFilter current = state.get(colIndex);

        // ---- header
        FontIcon icon = new FontIcon("fth-filter");
        icon.getStyleClass().add("col-filter-title-icon");
        Label title = new Label("Lọc: " + colName);
        title.getStyleClass().add("col-filter-title");
        title.setMinWidth(0);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button close = new Button();
        close.setGraphic(new FontIcon("fth-x"));
        close.getStyleClass().add("col-filter-close");
        close.setOnAction(e -> popup.hide());
        HBox header = new HBox(6, icon, title, sp, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("col-filter-header");

        // ---- condition
        Label condLbl = new Label("LỌC THEO ĐIỀU KIỆN");
        condLbl.getStyleClass().add("col-filter-section");
        ComboBox<Op> opBox = new ComboBox<>(FXCollections.observableArrayList(Op.values()));
        opBox.setMaxWidth(Double.MAX_VALUE);
        opBox.setValue(current != null && current.op() != null ? current.op() : Op.NONE);
        TextField valueField = new TextField(current != null && current.value() != null ? current.value() : "");
        valueField.setPromptText("Giá trị");
        Runnable syncValue = () -> {
            boolean need = opBox.getValue() != null && opBox.getValue().needsValue;
            valueField.setVisible(need);
            valueField.setManaged(need);
        };
        opBox.valueProperty().addListener((o, a, b) -> {
            syncValue.run();
            if (b != null && b.needsValue) Platform.runLater(valueField::requestFocus);
        });
        syncValue.run();

        // ---- value checklist
        Label listLbl = new Label("LỌC THEO DANH SÁCH GIÁ TRỊ");
        listLbl.getStyleClass().add("col-filter-section");
        TextField search = new TextField();
        search.setPromptText("Tìm kiếm trong danh sách…");

        ObservableList<Item> items = FXCollections.observableArrayList();
        Set<String> allowed = current != null ? current.allowed() : null;
        var distinct = state.distinct(colIndex, MAX_VALUES);
        for (var e : distinct) {
            String text = GridFilterState.NULL_KEY.equals(e.getKey()) ? "(" + (nullText != null ? nullText : "NULL") + ")" : e.getKey();
            Item it = new Item(e.getKey(), text.isEmpty() ? "(trống)" : text, e.getValue());
            it.checked.set(allowed == null || allowed.contains(e.getKey()));
            items.add(it);
        }
        FilteredList<Item> visible = new FilteredList<>(items, i -> true);

        CheckBox all = new CheckBox("(Chọn tất cả)");
        all.getStyleClass().add("col-filter-all");
        Label counter = new Label();
        counter.getStyleClass().add("col-filter-counter");
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        HBox allRow = new HBox(6, all, sp2, counter);
        allRow.setAlignment(Pos.CENTER_LEFT);
        allRow.getStyleClass().add("col-filter-all-row");

        boolean[] syncing = {false};
        Runnable refreshAll = () -> {
            long shown = visible.size();
            long checked = visible.stream().filter(i -> i.checked.get()).count();
            syncing[0] = true;
            all.setIndeterminate(checked > 0 && checked < shown);
            all.setSelected(shown > 0 && checked == shown);
            syncing[0] = false;
            counter.setText(checked + "/" + shown);
        };
        all.selectedProperty().addListener((o, a, b) -> {
            if (syncing[0]) return;
            all.setIndeterminate(false);
            visible.forEach(i -> i.checked.set(b));
            refreshAll.run();
        });
        items.forEach(i -> i.checked.addListener((o, a, b) -> refreshAll.run()));

        ListView<Item> list = new ListView<>(visible);
        list.getStyleClass().add("col-filter-list");
        list.setPrefHeight(220);
        list.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox cb = new CheckBox();
            private final Label count = new Label();
            private final HBox row;
            private Item bound;

            {
                Region s = new Region();
                HBox.setHgrow(s, Priority.ALWAYS);
                count.getStyleClass().add("col-filter-count");
                cb.setMinWidth(0);
                cb.setMaxWidth(Double.MAX_VALUE);
                row = new HBox(6, cb, s, count);
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (bound != null) cb.selectedProperty().unbindBidirectional(bound.checked);
                bound = null;
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                bound = item;
                cb.setText(item.text);
                cb.selectedProperty().bindBidirectional(item.checked);
                count.setText(String.format("%,d", item.count));
                setText(null);
                setGraphic(row);
            }
        });

        // typing filters the checklist below (user request)
        search.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.trim().toLowerCase(Locale.ROOT);
            visible.setPredicate(i -> q.isEmpty() || i.text.toLowerCase(Locale.ROOT).contains(q));
            refreshAll.run();
        });
        refreshAll.run();

        Label limitNote = new Label("Chỉ hiện " + String.format("%,d", MAX_VALUES) + " giá trị đầu.");
        limitNote.getStyleClass().add("col-filter-note");
        boolean limited = distinct.size() >= MAX_VALUES;
        limitNote.setVisible(limited);
        limitNote.setManaged(limited);

        // ---- footer
        Button clear = new Button("Xóa lọc");
        clear.getStyleClass().add("col-filter-button");
        Button cancel = new Button("Hủy");
        cancel.getStyleClass().add("col-filter-button");
        Button apply = new Button("Áp dụng");
        apply.getStyleClass().addAll("col-filter-button", "accent");
        apply.setDefaultButton(true);
        Region sp3 = new Region();
        HBox.setHgrow(sp3, Priority.ALWAYS);
        HBox footer = new HBox(6, clear, sp3, cancel, apply);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("col-filter-footer");

        clear.setOnAction(e -> {
            state.set(colIndex, null);
            popup.hide();
            if (onApplied != null) onApplied.run();
        });
        cancel.setOnAction(e -> popup.hide());
        apply.setOnAction(e -> {
            Op op = opBox.getValue() != null ? opBox.getValue() : Op.NONE;
            String val = valueField.getText() != null ? valueField.getText().trim() : "";
            if (op.needsValue && val.isEmpty()) op = Op.NONE;
            // with an active search, only the visible ticked values count (Excel behaviour)
            boolean searching = search.getText() != null && !search.getText().isBlank();
            Set<String> keep = new HashSet<>();
            for (Item i : (searching ? visible : items)) if (i.checked.get()) keep.add(i.key);
            Set<String> allowedSet = (!searching && keep.size() == items.size() && !limited) ? null : keep;
            state.set(colIndex, new ColumnFilter(op, val, allowedSet));
            popup.hide();
            if (onApplied != null) onApplied.run();
        });
        valueField.setOnAction(apply.getOnAction());

        VBox body = new VBox(6, condLbl, opBox, valueField, listLbl, search, allRow, list, limitNote);
        body.getStyleClass().add("col-filter-body");
        VBox root = new VBox(header, body, footer);
        // "root": the AtlantaFX colour variables and app fonts resolve inside the popup window
        root.getStyleClass().addAll("root", "col-filter-popup");
        root.setPrefWidth(300);
        root.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) popup.hide();
        });
        popup.getContent().add(root);

        if (anchor.getScene() != null) {
            popup.getScene().getStylesheets().setAll(anchor.getScene().getStylesheets());
            if (anchor.getScene().getRoot().getStyleClass().contains("flat-panels")
                    || app.services.ThemeService.isFlat()) {
                root.getStyleClass().add("flat");
            }
        }
        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, b.getMinX() - 8, b.getMaxY() + 4);
        Platform.runLater(search::requestFocus);
    }
}
