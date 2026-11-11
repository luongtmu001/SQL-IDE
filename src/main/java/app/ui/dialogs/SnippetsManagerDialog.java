package app.ui.dialogs;

import app.models.SqlSnippet;
import app.services.NotificationService;
import app.services.SettingsService;
import app.services.SnippetService;
import app.ui.components.IconButton;
import app.ui.components.SearchField;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.*;

/**
 * SQL Snippets Manager Dialog (Phase 2 - Feature 9).
 * Provides full management of SQL template snippets compatible with Red Gate SQL Prompt 10.
 * Automatically expands variables ($table$, $column$) and focuses/selects the first variable.
 */
public final class SnippetsManagerDialog {

    private final SnippetService snippetService = SnippetService.getInstance();
    private DialogChrome chrome;

    private final ObservableList<SqlSnippet> masterList = FXCollections.observableArrayList();
    private final FilteredList<SqlSnippet> filteredList = new FilteredList<>(masterList, p -> true);
    private final ListView<SqlSnippet> listView = new ListView<>(filteredList);

    private final SearchField searchField = new SearchField("Tìm prefix, mô tả, nhãn...");
    private String selectedDbFilter = "all";

    // Form controls
    private final TextField prefixField = new TextField();
    private final ComboBox<String> dbTypeBox = new ComboBox<>();
    private final CheckBox pinnedBox = new CheckBox("Ghim ưu tiên ⭐");
    private final TextField descField = new TextField();
    private final TextField tagsField = new TextField();
    private final TextArea bodyArea = new TextArea();

    private SqlSnippet activeSnippet = null;
    private final Button deleteBtn = DialogChrome.danger("Xóa");

    private SnippetsManagerDialog() {
    }

    public static void show(Window owner) {
        new SnippetsManagerDialog().open(owner);
    }

    private void open(Window owner) {
        chrome = DialogChrome.create(owner, "SQL Snippets Manager", "fth-code")
                .subtitle("Quản lý mẫu câu lệnh SQL (Red Gate Prompt Compatible)");

        Node content = buildContent();

        // Footer buttons
        Button importBtn = DialogChrome.secondary("Nhập JSON");
        importBtn.setGraphic(new FontIcon("fth-upload"));
        importBtn.setOnAction(e -> handleImport());

        Button exportBtn = DialogChrome.secondary("Xuất JSON");
        exportBtn.setGraphic(new FontIcon("fth-download"));
        exportBtn.setOnAction(e -> handleExport());

        Button resetBtn = DialogChrome.secondary("Đặt lại mặc định");
        resetBtn.setOnAction(e -> handleReset());

        deleteBtn.setOnAction(e -> handleDelete());
        deleteBtn.setDisable(true);

        Button cancelBtn = DialogChrome.cancel("Đóng");
        cancelBtn.setOnAction(e -> chrome.close());

        Button saveBtn = DialogChrome.primary("Lưu Snippet");
        saveBtn.setGraphic(new FontIcon("fth-check"));
        saveBtn.setOnAction(e -> handleSave());

        chrome.content(content)
                .footer(new Node[]{importBtn, exportBtn, resetBtn}, deleteBtn, cancelBtn, saveBtn)
                .size(800, 540);

        loadSnippets();
        chrome.showAndWait();
    }

    private Node buildContent() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("snippet-dialog-content");

        // Load CSS
        try {
            var css = getClass().getResource("/css/snippets-manager.css");
            if (css != null) root.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}

        // ─────────────────────────────────────────────────────────────
        // Left Column: Master List (290px)
        // ─────────────────────────────────────────────────────────────
        VBox leftCol = new VBox(6);
        leftCol.setPrefWidth(290);
        leftCol.setMinWidth(250);
        leftCol.getStyleClass().add("snippet-master");
        leftCol.setPadding(new Insets(8));

        // Search Field
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Target DB Filter Chips
        HBox chipBar = new HBox(4);
        chipBar.setAlignment(Pos.CENTER_LEFT);
        chipBar.getStyleClass().add("snippet-chip-bar");

        ToggleGroup chipGroup = new ToggleGroup();
        ToggleButton chipAll = createChip("Tất cả", "all", chipGroup, true);
        ToggleButton chipMssql = createChip("SQL Server", "sqlserver", chipGroup, false);
        ToggleButton chipPg = createChip("PostgreSQL", "postgresql", chipGroup, false);
        chipBar.getChildren().addAll(chipAll, chipMssql, chipPg);

        chipGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedDbFilter = (String) newVal.getUserData();
            } else {
                chipAll.setSelected(true);
                selectedDbFilter = "all";
            }
            applyFilters();
        });

        // List View setup
        listView.getStyleClass().add("snippet-list-view");
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setCellFactory(lv -> new SnippetListCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                bindToForm(newVal);
            }
        });

        // New snippet quick button
        Button newSnippetBtn = new Button("+ Tạo snippet mới");
        newSnippetBtn.getStyleClass().add("btn-subtle");
        newSnippetBtn.setMaxWidth(Double.MAX_VALUE);
        newSnippetBtn.setOnAction(e -> createNewSnippet());

        leftCol.getChildren().addAll(searchField, chipBar, listView, newSnippetBtn);
        root.setLeft(leftCol);

        // ─────────────────────────────────────────────────────────────
        // Right Column: Detail Form
        // ─────────────────────────────────────────────────────────────
        VBox rightCol = new VBox(8);
        rightCol.getStyleClass().add("snippet-detail");
        rightCol.setPadding(new Insets(12, 14, 12, 14));
        HBox.setHgrow(rightCol, Priority.ALWAYS);

        // Row 1: Prefix, DB Type, Pinned
        HBox row1 = new HBox(10);
        row1.setAlignment(Pos.CENTER_LEFT);

        VBox prefixBox = new VBox(3);
        Label prefixLbl = new Label("Prefix viết tắt (gõ + Tab):");
        prefixLbl.getStyleClass().add("snippet-field-label");
        prefixField.setPromptText("ví dụ: ssf");
        prefixField.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-weight: bold;");
        prefixBox.getChildren().addAll(prefixLbl, prefixField);
        HBox.setHgrow(prefixBox, Priority.ALWAYS);

        VBox dbBox = new VBox(3);
        Label dbLbl = new Label("Mục tiêu CSDL:");
        dbLbl.getStyleClass().add("snippet-field-label");
        dbTypeBox.getItems().addAll("all", "sqlserver", "postgresql");
        dbTypeBox.setValue("all");
        dbTypeBox.setPrefWidth(160);
        dbBox.getChildren().addAll(dbLbl, dbTypeBox);

        VBox pinBox = new VBox(3);
        Label pinLbl = new Label(" ");
        pinBox.getChildren().addAll(pinLbl, pinnedBox);

        row1.getChildren().addAll(prefixBox, dbBox, pinBox);

        // Row 2: Description & Tags
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);

        VBox descBox = new VBox(3);
        Label descLbl = new Label("Mô tả tóm tắt:");
        descLbl.getStyleClass().add("snippet-field-label");
        descField.setPromptText("ví dụ: SELECT * FROM fragment");
        descBox.getChildren().addAll(descLbl, descField);
        HBox.setHgrow(descBox, Priority.ALWAYS);

        VBox tagsBox = new VBox(3);
        Label tagsLbl = new Label("Nhãn / Tags (cách nhau bởi dấu phẩy):");
        tagsLbl.getStyleClass().add("snippet-field-label");
        tagsField.setPromptText("ví dụ: select, dml");
        tagsField.setPrefWidth(220);
        tagsBox.getChildren().addAll(tagsLbl, tagsField);

        row2.getChildren().addAll(descBox, tagsBox);

        // Row 3: Body Template
        VBox bodyBox = new VBox(4);
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        HBox bodyHeader = new HBox();
        Label bodyLbl = new Label("Nội dung Template (Red Gate placeholder $param$):");
        bodyLbl.getStyleClass().add("snippet-field-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label syntaxHint = new Label("Biến: $table$, $column$, $SELECTIONSTART$");
        syntaxHint.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
        bodyHeader.getChildren().addAll(bodyLbl, spacer, syntaxHint);

        bodyArea.getStyleClass().add("snippet-body-area");
        bodyArea.setWrapText(false);
        bodyArea.setPromptText("Nhập câu lệnh SQL template tại đây...\nví dụ: SELECT * FROM $table$ WHERE $column$ = $value$;");
        VBox.setVgrow(bodyArea, Priority.ALWAYS);

        bodyBox.getChildren().addAll(bodyHeader, bodyArea);

        // Row 4: Helper bar
        HBox helperBar = new HBox(6);
        helperBar.getStyleClass().add("snippet-helper-box");
        helperBar.setAlignment(Pos.CENTER_LEFT);

        FontIcon infoIcon = new FontIcon("fth-info");
        infoIcon.setStyle("-fx-icon-color: -color-accent-fg;");
        Label helperText = new Label("Khi gõ prefix + nhấn Tab trong editor, code tự động bung ra và bôi đen biến đầu tiên ($var$) để bạn gõ đè ngay lập tức. Bấm Tab để nhảy tiếp.");
        helperText.getStyleClass().add("snippet-helper-text");
        helperText.setWrapText(true);
        HBox.setHgrow(helperText, Priority.ALWAYS);

        helperBar.getChildren().addAll(infoIcon, helperText);

        rightCol.getChildren().addAll(row1, row2, bodyBox, helperBar);
        root.setCenter(rightCol);

        return root;
    }

    // ─────────────────────────────────────────────────────────────
    // Logic & Event Handlers
    // ─────────────────────────────────────────────────────────────

    private void loadSnippets() {
        masterList.setAll(snippetService.getAllSnippets());
        if (!filteredList.isEmpty()) {
            listView.getSelectionModel().select(0);
        } else {
            createNewSnippet();
        }
    }

    private void applyFilters() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        filteredList.setPredicate(s -> {
            // Check DB filter
            if (!"all".equalsIgnoreCase(selectedDbFilter)) {
                String db = s.getDbType() == null ? "all" : s.getDbType().toLowerCase();
                if (!"all".equals(db) && !db.equalsIgnoreCase(selectedDbFilter)) {
                    return false;
                }
            }
            // Check text query
            if (query.isEmpty()) return true;
            boolean matchPrefix = s.getPrefix().toLowerCase().contains(query);
            boolean matchDesc = s.getDescription().toLowerCase().contains(query);
            boolean matchTag = s.getTags().stream().anyMatch(t -> t.toLowerCase().contains(query));
            boolean matchBody = s.getBody().toLowerCase().contains(query);
            return matchPrefix || matchDesc || matchTag || matchBody;
        });
    }

    private void bindToForm(SqlSnippet s) {
        this.activeSnippet = s;
        prefixField.setText(s.getPrefix());
        dbTypeBox.setValue(s.getDbType() != null ? s.getDbType() : "all");
        pinnedBox.setSelected(s.isPinned());
        descField.setText(s.getDescription());
        tagsField.setText(String.join(", ", s.getTags()));
        bodyArea.setText(s.getBody());
        deleteBtn.setDisable(false);
    }

    private void createNewSnippet() {
        this.activeSnippet = new SqlSnippet();
        prefixField.setText("");
        dbTypeBox.setValue("all");
        pinnedBox.setSelected(false);
        descField.setText("");
        tagsField.setText("");
        bodyArea.setText("");
        prefixField.requestFocus();
        deleteBtn.setDisable(true);
        listView.getSelectionModel().clearSelection();
    }

    private void handleSave() {
        String prefix = prefixField.getText() == null ? "" : prefixField.getText().trim();
        if (prefix.isBlank()) {
            NotificationService.getInstance().showWarning("Thiếu thông tin", "Prefix viết tắt không được để trống!");
            prefixField.requestFocus();
            return;
        }

        String body = bodyArea.getText() == null ? "" : bodyArea.getText();
        if (body.isBlank()) {
            NotificationService.getInstance().showWarning("Thiếu thông tin", "Nội dung template không được để trống!");
            bodyArea.requestFocus();
            return;
        }

        SqlSnippet snippetToSave = activeSnippet != null ? activeSnippet : new SqlSnippet();
        snippetToSave.setPrefix(prefix);
        snippetToSave.setDescription(descField.getText() != null ? descField.getText().trim() : "");
        snippetToSave.setBody(body);
        snippetToSave.setDbType(dbTypeBox.getValue() != null ? dbTypeBox.getValue() : "all");
        snippetToSave.setPinned(pinnedBox.isSelected());

        List<String> tags = new ArrayList<>();
        String rawTags = tagsField.getText();
        if (rawTags != null && !rawTags.isBlank()) {
            for (String t : rawTags.split(",")) {
                if (!t.trim().isEmpty()) tags.add(t.trim());
            }
        }
        snippetToSave.setTags(tags);

        boolean ok = snippetService.saveSnippet(snippetToSave);
        if (ok) {
            NotificationService.getInstance().showSuccess("Đã lưu", "Snippet [" + prefix + "] đã được lưu thành công.");
            loadSnippets();
            // Select the saved snippet
            for (int i = 0; i < filteredList.size(); i++) {
                if (filteredList.get(i).getId().equals(snippetToSave.getId())) {
                    listView.getSelectionModel().select(i);
                    break;
                }
            }
        } else {
            NotificationService.getInstance().showError("Lỗi", "Không thể lưu snippet vào file cấu hình.");
        }
    }

    private void handleDelete() {
        if (activeSnippet == null) return;
        boolean confirm = ConfirmDialog.show(chrome.stage(), "Xác nhận xóa Snippet",
                "Bạn có chắc chắn muốn xóa snippet [" + activeSnippet.getPrefix() + "]?",
                "Hành động này không thể hoàn tác.", "Xóa", true);

        if (confirm) {
            boolean ok = snippetService.deleteSnippet(activeSnippet.getId());
            if (ok) {
                NotificationService.getInstance().showInfo("Đã xóa", "Đã xóa snippet [" + activeSnippet.getPrefix() + "].");
                loadSnippets();
            }
        }
    }

    private void handleReset() {
        boolean confirm = ConfirmDialog.show(chrome.stage(), "Khôi phục Snippets mặc định",
                "Bạn có chắc muốn đặt lại danh sách snippet về mặc định ban đầu?",
                "Mọi thay đổi tùy biến có thể bị ghi đè.", "Đặt lại", true);

        if (confirm) {
            snippetService.resetToDefaults();
            loadSnippets();
            NotificationService.getInstance().showSuccess("Hoàn tất", "Đã khôi phục các snippet mẫu mặc định.");
        }
    }

    private void handleExport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Xuất danh sách SQL Snippets ra JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        chooser.setInitialFileName("snippets.json");
        File file = chooser.showSaveDialog(chrome.stage());
        if (file != null) {
            boolean ok = snippetService.exportSnippets(file);
            if (ok) {
                NotificationService.getInstance().showSuccess("Xuất thành công", "Đã lưu snippets vào " + file.getName());
            } else {
                NotificationService.getInstance().showError("Lỗi", "Không thể xuất file snippets.");
            }
        }
    }

    private void handleImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Nhập danh sách SQL Snippets từ JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(chrome.stage());
        if (file != null) {
            boolean ok = snippetService.importSnippets(file);
            if (ok) {
                NotificationService.getInstance().showSuccess("Nhập thành công", "Đã nạp snippets từ " + file.getName());
                loadSnippets();
            } else {
                NotificationService.getInstance().showError("Lỗi", "File không hợp lệ hoặc không thể nạp snippets.");
            }
        }
    }

    private ToggleButton createChip(String text, String userData, ToggleGroup group, boolean selected) {
        ToggleButton b = new ToggleButton(text);
        b.setUserData(userData);
        b.setToggleGroup(group);
        b.setSelected(selected);
        b.getStyleClass().add("snippet-chip");
        return b;
    }

    // ─────────────────────────────────────────────────────────────
    // Custom List Cell
    // ─────────────────────────────────────────────────────────────

    private class SnippetListCell extends ListCell<SqlSnippet> {
        private final BorderPane root = new BorderPane();
        private final Label prefixLbl = new Label();
        private final Label descLbl = new Label();
        private final Label badgeLbl = new Label();
        private final Label starLbl = new Label("⭐");

        public SnippetListCell() {
            root.getStyleClass().add("snippet-list-cell");

            prefixLbl.getStyleClass().add("snippet-cell-prefix");
            descLbl.getStyleClass().add("snippet-cell-desc");
            badgeLbl.getStyleClass().add("snippet-cell-badge");

            starLbl.setOnMouseClicked(e -> {
                e.consume();
                SqlSnippet item = getItem();
                if (item != null) {
                    snippetService.togglePin(item.getId());
                    loadSnippets();
                }
            });

            HBox top = new HBox(6, prefixLbl, badgeLbl);
            top.setAlignment(Pos.CENTER_LEFT);

            VBox textCol = new VBox(2, top, descLbl);
            root.setCenter(textCol);
            root.setRight(starLbl);
            BorderPane.setAlignment(starLbl, Pos.CENTER);
        }

        @Override
        protected void updateItem(SqlSnippet item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                prefixLbl.setText(item.getPrefix());
                descLbl.setText(item.getDescription());
                badgeLbl.setText(item.getDbType() != null ? item.getDbType() : "all");

                if (item.isPinned()) {
                    starLbl.setText("⭐");
                    starLbl.getStyleClass().setAll("snippet-star-active");
                } else {
                    starLbl.setText("☆");
                    starLbl.getStyleClass().setAll("snippet-star-inactive");
                }

                setGraphic(root);
            }
        }
    }
}
