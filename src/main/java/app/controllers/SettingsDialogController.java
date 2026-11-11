package app.controllers;

import app.models.IdeSettings;
import app.services.NotificationService;
import app.services.SettingsService;
import app.services.ThemeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for Settings Center Dialog (Feature 8).
 * Fully matches Python LLTool settings.json structure and visual capabilities.
 */
public class SettingsDialogController implements Initializable {

    private static final ObjectMapper MAPPER = IdeSettings.jsonMapper(); // fields only (see IdeSettings.jsonMapper)

    @FXML private VBox settingsRoot;
    @FXML private HBox titleBar;
    @FXML private TextField settingsSearchField;
    @FXML private Button btnClearSearch;

    // View Switchers & Global Actions
    @FXML private Button btnTabVisual;
    @FXML private Button btnTabJson;
    @FXML private Button btnSave;
    @FXML private Button btnCopyJson;
    @FXML private Button btnRestoreDefaults;

    // Main View Containers
    @FXML private HBox visualArea;
    @FXML private VBox jsonArea;
    @FXML private Label lblJsonStatus;
    @FXML private TextArea txtRawJson;

    // Sidebar Category Buttons
    @FXML private Button btnNavEditor;
    @FXML private Button btnNavGrid;
    @FXML private Button btnNavMessages;
    @FXML private Button btnNavAppearance;
    @FXML private Button btnNavSql;
    @FXML private Button btnNavSession;
    @FXML private Button btnNavAddons;
    @FXML private Button btnNavHistory;
    @FXML private Button btnNavLogging;
    @FXML private Button btnNavUpdates;

    // Category Sections (Right Detail Content)
    @FXML private VBox sectionEditor;
    @FXML private VBox sectionGrid;
    @FXML private VBox sectionMessages;
    @FXML private VBox sectionAppearance;
    @FXML private VBox sectionSql;
    @FXML private VBox sectionSession;
    @FXML private VBox sectionAddons;
    @FXML private VBox sectionHistory;
    @FXML private VBox sectionLogging;
    @FXML private VBox sectionUpdates;

    // SECTION 1: Editor Controls
    @FXML private ComboBox<String> cmbEditorFontFamily;
    @FXML private Spinner<Integer> spnEditorFontSize;
    @FXML private Spinner<Double> spnLineHeight;
    @FXML private Spinner<Double> spnLetterSpacing;
    @FXML private Spinner<Integer> spnTabSize;
    @FXML private CheckBox chkInsertSpaces;
    @FXML private CheckBox chkWordWrap;
    @FXML private CheckBox chkMinimap;
    @FXML private ComboBox<String> cmbKeywordCase;
    @FXML private CheckBox chkSyntaxCheck;
    @FXML private CheckBox chkIntellisense;
    @FXML private CheckBox chkIntellisenseMru;
    @FXML private CheckBox chkIntellisenseDetail;
    @FXML private RadioButton rdoDetailAlways;
    @FXML private RadioButton rdoDetailOnDemand;

    // SECTION 2: Grid Controls
    @FXML private ComboBox<String> cmbGridFontFamily;
    @FXML private Spinner<Integer> spnGridFontSize;
    @FXML private CheckBox chkShowRowNumbers;
    @FXML private ComboBox<String> cmbResultLayout;
    private static final String RESULT_LAYOUT_LIST = "Danh sách (mặc định)";
    private static final String RESULT_LAYOUT_TABS = "Theo tab";
    @FXML private TextField txtNullDisplay;

    // SECTION 3: Messages Controls
    @FXML private ComboBox<String> cmbMessagesFontFamily;
    @FXML private Spinner<Integer> spnMessagesFontSize;

    // SECTION 4: Appearance Controls
    @FXML private ComboBox<String> cmbTheme;
    @FXML private VBox importedThemesBox;
    @FXML private Button btnImportVsix;
    @FXML private Spinner<Integer> spnIconSize;
    @FXML private ComboBox<String> cmbUiFontFamily;
    @FXML private Spinner<Integer> spnUiFontSize;
    @FXML private CheckBox chkAutoHideNavbar;
    @FXML private ComboBox<String> cmbPanelStyle;
    private static final String PANEL_STYLE_ROUNDED = "Bo góc (card, có khoảng cách)";
    private static final String PANEL_STYLE_FLAT = "Phẳng (góc vuông)";

    // SECTION 5: SQL Controls
    @FXML private Spinner<Integer> spnMaxRows;
    @FXML private Spinner<Integer> spnQueryTimeout;
    @FXML private ComboBox<String> cmbSqlProtection;
    @FXML private CheckBox chkStopOnError;
    @FXML private CheckBox chkPerTabSession;
    @FXML private CheckBox chkAutoShowResults;

    // SECTION 6: Session Controls
    @FXML private CheckBox chkRestoreOnStartup;
    @FXML private CheckBox chkSaveSqlContent;
    @FXML private Spinner<Integer> spnAutoSaveInterval;
    @FXML private Spinner<Integer> spnMaxTabSize;

    // SECTION 7: Addons Controls
    @FXML private CheckBox chkBravoToolEnabled;

    // SECTION 8: History Controls
    @FXML private CheckBox chkHistoryEnabled;
    @FXML private Spinner<Integer> spnMaxEntriesPerDoc;
    @FXML private Spinner<Integer> spnMaxAgeDays;
    @FXML private Spinner<Integer> spnMaxDbSizeMb;
    @FXML private Spinner<Integer> spnMaxEntrySizeKb;

    // SECTION 9: Logging Controls
    @FXML private CheckBox chkLoggingEnabled;
    @FXML private ComboBox<String> cmbLogLevel;
    @FXML private Spinner<Integer> spnMaxLogFileSizeMb;
    @FXML private Spinner<Integer> spnMaxBackupFiles;

    // SECTION 10: Updates Controls
    @FXML private CheckBox chkCheckOnStartup;
    @FXML private TextField txtUpdateUrl;
    @FXML private Label lblInstalledVersion;

    // Window drag state
    private double xOffset = 0;
    private double yOffset = 0;
    private VBox currentActiveSection;
    private Button currentActiveBtn;
    private boolean isJsonTabActive = false;
    /** Phase 5 (Gate B 2026-11-05): VS Code style rows + unsaved-change tracking. */
    private final app.ui.settings.SettingsRowStyler styler = new app.ui.settings.SettingsRowStyler();
    private Label dirtyLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentActiveSection = sectionEditor;
        currentActiveBtn = btnNavEditor;

        setupWindowDrag();
        setupSpinners();
        setupComboBoxes();
        setupSearchFilter();
        setupJsonValidation();

        populateValues(SettingsService.getInstance().getCurrentSettings());

        // Note: setupJsonEditor() is lazy-loaded on demand when user switches to JSON tab (Perf Optimization Task 1)
        styler.restyle(getAllSections());
        styler.snapshot();
        dirtyLabel = new Label();
        dirtyLabel.getStyleClass().add("settings-dirty-label");
        if (btnSave != null && btnSave.getParent() instanceof javafx.scene.layout.Pane bar) {
            bar.getChildren().add(bar.getChildren().indexOf(btnSave), dirtyLabel);
        }
        styler.setOnDirtyCount(n -> {
            dirtyLabel.setText(n == 0 ? "" : n + " thay đổi chưa lưu");
            if (btnSave != null) {
                btnSave.getStyleClass().remove("dirty");
                if (n > 0) btnSave.getStyleClass().add("dirty");
            }
        });
    }

    /** settings.json shown in Monaco (JSON highlighting, folding, Shift+Alt+F); the TextArea stays the model. */
    private app.ui.editor.EmbeddedMonaco jsonEditor;
    private boolean jsonSyncing = false;

    private void setupJsonEditor() {
        if (jsonEditor != null) return;
        if (txtRawJson == null || !(txtRawJson.getParent() instanceof javafx.scene.layout.Pane parent)) return;
        try {
            jsonEditor = new app.ui.editor.EmbeddedMonaco("settings-json", "json", txtRawJson.getText());
        } catch (Throwable t) {
            jsonEditor = null; // WebView unavailable: keep the TextArea
            return;
        }
        var node = jsonEditor.node();
        node.getStyleClass().add("settings-json-editor");
        javafx.scene.layout.VBox.setVgrow(node, javafx.scene.layout.Priority.ALWAYS);
        int idx = parent.getChildren().indexOf(txtRawJson);
        if (idx >= 0) {
            parent.getChildren().set(idx, node);
        }
        jsonEditor.setOnUserEdit(text -> {
            jsonSyncing = true;
            txtRawJson.setText(text);
            jsonSyncing = false;
        });
        txtRawJson.textProperty().addListener((o, a, b) -> {
            if (!jsonSyncing && jsonEditor != null) jsonEditor.setText(b);
        });
    }

    public Button getSaveButton() {
        return btnSave;
    }

    public Label getDirtyLabel() {
        return dirtyLabel;
    }

    /** × / Esc / Đóng: ask before throwing away unsaved changes. */
    public boolean canClose() {
        int n = styler.dirtyCount();
        if (n == 0) return true;
        javafx.stage.Window w = settingsRoot != null && settingsRoot.getScene() != null ? settingsRoot.getScene().getWindow() : null;
        return app.ui.dialogs.ConfirmDialog.show(w, "Đóng cài đặt", "Có " + n + " thay đổi chưa lưu.",
                "Đóng mà không lưu thì các thay đổi này sẽ mất.", "Đóng không lưu", true);
    }

    private void setupWindowDrag() {
        if (false && titleBar != null) {
            titleBar.setOnMousePressed(event -> {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            });
            titleBar.setOnMouseDragged(event -> {
                if (settingsRoot != null && settingsRoot.getScene() != null) {
                    Stage stage = (Stage) settingsRoot.getScene().getWindow();
                    if (stage != null) {
                        stage.setX(event.getScreenX() - xOffset);
                        stage.setY(event.getScreenY() - yOffset);
                    }
                }
            });
        }
    }

    private void setupSpinners() {
        // Editor
        spnEditorFontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 36, 13));
        spnLineHeight.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.0, 5.0, 1.5, 0.1));
        spnLetterSpacing.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(-2.0, 10.0, 0.5, 0.5));
        spnTabSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 8, 4, 2));

        // Grid & Messages
        spnGridFontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(9, 24, 12));
        spnMessagesFontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(9, 24, 11));

        // Appearance
        spnIconSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(12, 24, 12, 2));
        spnUiFontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 24, 13));
        if (cmbPanelStyle != null) {
            cmbPanelStyle.getItems().setAll(PANEL_STYLE_ROUNDED, PANEL_STYLE_FLAT);
        }

        // SQL & Query
        spnMaxRows.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50000, 0, 500));
        spnQueryTimeout.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 600, 30, 5));

        // Session
        spnAutoSaveInterval.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 120, 3));
        spnMaxTabSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(65536, 104857600, 1048576, 65536));

        // History
        spnMaxEntriesPerDoc.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 2000, 200, 10));
        spnMaxAgeDays.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 730, 90, 5));
        spnMaxDbSizeMb.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(50, 5000, 500, 50));
        spnMaxEntrySizeKb.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(64, 10240, 1024, 64));

        // Logging
        spnMaxLogFileSizeMb.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 2));
        spnMaxBackupFiles.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 20, 5));
    }

    private void setupComboBoxes() {
        setupThemeCombo();
        renderImportedThemes();

        List<String> codeFonts = List.of("Cascadia Mono Light", "Consolas", "Cascadia Code", "Fira Code", "JetBrains Mono", "Courier New");
        cmbEditorFontFamily.setItems(FXCollections.observableArrayList(codeFonts));
        cmbMessagesFontFamily.setItems(FXCollections.observableArrayList(codeFonts));

        List<String> textFonts = List.of("Inter", "Segoe UI", "Arial", "Roboto", "Tahoma", "Calibri");
        cmbUiFontFamily.setItems(FXCollections.observableArrayList(textFonts));
        cmbGridFontFamily.setItems(FXCollections.observableArrayList(textFonts));

        // S4: then every font installed on this computer (read once in the background, cached)
        app.ui.settings.SystemFonts.load(fonts -> {
            setFontItems(cmbEditorFontFamily, app.ui.settings.SystemFonts.merge(codeFonts, fonts.monospaced()));
            setFontItems(cmbMessagesFontFamily, app.ui.settings.SystemFonts.merge(codeFonts, fonts.monospaced()));
            setFontItems(cmbUiFontFamily, app.ui.settings.SystemFonts.merge(textFonts, fonts.all()));
            setFontItems(cmbGridFontFamily, app.ui.settings.SystemFonts.merge(textFonts, fonts.all()));
        });

        cmbKeywordCase.setItems(FXCollections.observableArrayList("upper", "lower", "preserve"));
        // IntelliSense: each option only matters when the one above it is on
        ToggleGroup detailMode = new ToggleGroup();
        rdoDetailAlways.setToggleGroup(detailMode);
        rdoDetailOnDemand.setToggleGroup(detailMode);
        chkIntellisenseDetail.disableProperty().bind(chkIntellisense.selectedProperty().not());
        chkIntellisenseMru.disableProperty().bind(chkIntellisense.selectedProperty().not());
        var detailOff = chkIntellisense.selectedProperty().and(chkIntellisenseDetail.selectedProperty()).not();
        rdoDetailAlways.disableProperty().bind(detailOff);
        rdoDetailOnDemand.disableProperty().bind(detailOff);
        cmbSqlProtection.setItems(FXCollections.observableArrayList(
                "1 - Bật bảo vệ (Cảnh báo trước khi chạy)",
                "0 - Tắt bảo vệ (Không cảnh báo)"
        ));
        cmbLogLevel.setItems(FXCollections.observableArrayList("DEBUG", "INFO", "WARNING", "ERROR"));
    }

    // ------------------------------------------------------------------ T4: themes grouped, import / remove

    /** Group titles in the theme list ("Tối · Có sẵn", "Sáng · GitHub Theme"): shown, never chosen. */
    private final java.util.Set<String> themeHeaders = new java.util.HashSet<>();
    /** Label -> dark, from the last fill: a removed theme falls back to Primer of its own kind. */
    private final java.util.Map<String, Boolean> themeDark = new java.util.HashMap<>();

    private void setupThemeCombo() {
        cmbTheme.setVisibleRowCount(16);
        cmbTheme.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                boolean header = !empty && item != null && themeHeaders.contains(item);
                setText(empty ? null : item);
                setDisable(header);
                getStyleClass().remove("theme-group-header");
                if (header) getStyleClass().add("theme-group-header");
            }
        });
        // keyboard can still land on a title: keep the previous theme
        cmbTheme.valueProperty().addListener((obs, old, val) -> {
            if (val != null && themeHeaders.contains(val)) {
                javafx.application.Platform.runLater(() -> cmbTheme.setValue(old != null && !themeHeaders.contains(old) ? old : null));
                return;
            }
            if (!populating && val != null && !val.isBlank() && !val.equals(old)) {
                var def = app.services.theme.ThemeRegistry.getInstance().findOrNull(val);
                if (def != null) {
                    var stage = app.application.AppContext.getInstance().getPrimaryStage();
                    javafx.scene.Scene scene = stage != null ? stage.getScene() : (ownerWindow() != null ? ownerWindow().getScene() : null);
                    ThemeService.getInstance().apply(def, scene);
                }
            }
        });
        fillThemeCombo();
    }

    /** T1/T4: dark groups then light; built-in themes, then one group per imported VS Code package. */
    private void fillThemeCombo() {
        String value = cmbTheme.getValue();
        boolean wasDark = value == null || themeDark.getOrDefault(value, true);
        themeHeaders.clear();
        themeDark.clear();
        List<String> items = new java.util.ArrayList<>();
        for (var g : app.services.theme.ThemeRegistry.getInstance().grouped()) {
            themeHeaders.add(g.title());
            items.add(g.title());
            for (var d : g.themes()) {
                items.add(d.label());
                themeDark.put(d.label(), d.isDark());
            }
        }
        cmbTheme.setItems(FXCollections.observableArrayList(items));
        if (value != null) {
            var still = app.services.theme.ThemeRegistry.getInstance().findOrNull(value);
            if (still != null) {
                cmbTheme.setValue(still.label());
            } else { // the chosen theme was removed: Primer of the same kind, like the main window
                cmbTheme.setValue(app.services.theme.ThemeDefinition.builtIn(wasDark
                        ? ThemeService.AppTheme.PRIMER_DARK : ThemeService.AppTheme.PRIMER_LIGHT).label());
            }
        }
    }

    /** One line per imported package: name, counts, its themes (× removes one), "Xóa gói". */
    private void renderImportedThemes() {
        if (importedThemesBox == null) return;
        importedThemesBox.getChildren().clear();
        var families = app.services.theme.ThemeRegistry.getInstance().importedFamilies();
        if (families.isEmpty()) {
            Label none = new Label("Chưa có theme nào được import.");
            none.getStyleClass().add("imported-theme-empty");
            importedThemesBox.getChildren().add(none);
            return;
        }
        String currentId = ThemeService.getInstance().getCurrentDefinition().id();
        families.forEach((family, themes) -> {
            long dark = themes.stream().filter(app.services.theme.ThemeDefinition::isDark).count();
            Label name = new Label(family);
            name.getStyleClass().add("imported-theme-name");
            Label count = new Label(themes.size() + " theme · " + dark + " tối · " + (themes.size() - dark) + " sáng");
            count.getStyleClass().add("imported-theme-count");
            javafx.scene.layout.Region gap = new javafx.scene.layout.Region();
            HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
            Button removeAll = new Button("Xóa gói");
            removeAll.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fth-trash-2"));
            removeAll.getStyleClass().addAll("settings-btn-secondary", "imported-theme-remove");
            removeAll.setOnAction(e -> app.ui.theme.ThemeImportActions.remove(ownerWindow(),
                    "gói " + family + " (" + themes.size() + " theme)", themes, this::refreshThemes));
            HBox head = new HBox(8, name, count, gap, removeAll);
            head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(6, 6);
            for (var d : themes) {
                Label lbl = new Label(d.label() + (d.highContrast() ? " · HC" : ""));
                lbl.setGraphic(new org.kordamp.ikonli.javafx.FontIcon(d.isDark() ? "fth-moon" : "fth-sun"));
                Button x = new Button();
                x.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fth-x"));
                x.getStyleClass().add("imported-theme-chip-remove");
                x.setTooltip(new Tooltip("Xóa theme này"));
                x.setFocusTraversable(false);
                x.setOnAction(e -> app.ui.theme.ThemeImportActions.remove(ownerWindow(),
                        "theme " + d.label(), List.of(d), this::refreshThemes));
                HBox chip = new HBox(4, lbl, x);
                chip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                chip.getStyleClass().add("imported-theme-chip");
                if (d.id().equals(currentId)) chip.getStyleClass().add("current");
                chips.getChildren().add(chip);
            }
            VBox pkg = new VBox(6, head, chips);
            pkg.getStyleClass().add("imported-theme-package");
            importedThemesBox.getChildren().add(pkg);
        });
    }

    private void refreshThemes() {
        fillThemeCombo();
        renderImportedThemes();
    }

    @FXML
    public void handleImportVsix() {
        app.ui.theme.ThemeImportActions.importVsix(ownerWindow(), this::refreshThemes);
    }

    private javafx.stage.Window ownerWindow() {
        return settingsRoot != null && settingsRoot.getScene() != null ? settingsRoot.getScene().getWindow() : null;
    }

    /** New list, same chosen value (a saved font that is not installed stays in the list). */
    private static void setFontItems(ComboBox<String> combo, List<String> fonts) {
        if (combo == null) return;
        String value = combo.getValue();
        java.util.List<String> items = new java.util.ArrayList<>(fonts);
        if (value != null && !value.isBlank() && !items.contains(value)) items.add(0, value);
        combo.setItems(FXCollections.observableArrayList(items));
        combo.setValue(value);
        combo.setVisibleRowCount(14);
    }

    // ------------------------------------------------------------------ S4: folders (LLTool open_log_folder / open_data_folder)

    @FXML
    public void handleOpenLogFolder() {
        openFolder(app.services.LoggingService.logsDir(), "nhật ký");
    }

    @FXML
    public void handleOpenDataFolder() {
        java.nio.file.Path root = null;
        try {
            root = app.framework.bootstrap.DesktopBootstrap.getInstance().getDirectoryManager().getRootDir();
        } catch (Exception ignored) {
        }
        openFolder(root, "dữ liệu");
    }

    /** Opens the folder in the system file manager (created first if needed), off the FX thread. */
    private static void openFolder(java.nio.file.Path dir, String what) {
        if (dir == null) {
            NotificationService.getInstance().showWarning("Thư mục " + what, "Không xác định được thư mục.");
            return;
        }
        Thread.ofVirtual().name("open-folder").start(() -> {
            try {
                java.nio.file.Files.createDirectories(dir);
                if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
                    new ProcessBuilder("explorer.exe", dir.toAbsolutePath().toString()).start();
                } else {
                    boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
                    new ProcessBuilder(mac ? "open" : "xdg-open", dir.toAbsolutePath().toString()).start();
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> NotificationService.getInstance()
                        .showWarning("Thư mục " + what, "Không mở được " + dir + ": " + e.getMessage()));
            }
        });
    }

    private void setupSearchFilter() {
        if (settingsSearchField != null) {
            settingsSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
                String q = newVal != null ? newVal.trim().toLowerCase() : "";
                if (btnClearSearch != null) {
                    btnClearSearch.setVisible(!q.isEmpty());
                }
                filterSettingsRows(q);
            });
        }
    }

    @FXML
    public void handleClearSearch() {
        if (settingsSearchField != null) {
            settingsSearchField.clear();
        }
    }

    private void filterSettingsRows(String query) {
        List<VBox> sections = getAllSections();
        styler.filter(query);
        if (query.isEmpty()) {
            setActiveSection(currentActiveSection != null ? currentActiveSection : sectionEditor,
                    currentActiveBtn != null ? currentActiveBtn : btnNavEditor);
            return;
        }
        // show every section that still has a visible setting
        for (VBox sec : sections) {
            boolean any = sec.getChildren().stream()
                    .anyMatch(n -> n.getStyleClass().contains("settings-item") && n.isVisible());
            sec.setVisible(any);
            sec.setManaged(any);
        }
    }


    private void setupJsonValidation() {
        if (txtRawJson != null) {
            txtRawJson.textProperty().addListener((obs, oldVal, newVal) -> {
                validateJsonContent(newVal);
            });
        }
    }

    private boolean validateJsonContent(String json) {
        if (json == null || json.trim().isEmpty()) {
            updateJsonStatus(false, "JSON rỗng");
            return false;
        }
        try {
            MAPPER.readTree(json);
            updateJsonStatus(true, "JSON Hợp lệ");
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            int idx = msg.indexOf('\n');
            if (idx > 0) msg = msg.substring(0, idx);
            updateJsonStatus(false, "Lỗi cú pháp: " + msg);
            return false;
        }
    }

    private void updateJsonStatus(boolean valid, String text) {
        if (lblJsonStatus != null) {
            lblJsonStatus.setText(text);
            lblJsonStatus.getStyleClass().removeAll("settings-json-badge-valid", "settings-json-badge-invalid");
            if (valid) {
                lblJsonStatus.getStyleClass().add("settings-json-badge-valid");
            } else {
                lblJsonStatus.getStyleClass().add("settings-json-badge-invalid");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Tab Navigation: Visual <-> JSON
    // ─────────────────────────────────────────────────────────────

    @FXML
    public void handleSwitchToVisualTab() {
        if (isJsonTabActive) {
            // Attempt to sync JSON text back to form if valid
            String json = txtRawJson.getText();
            if (validateJsonContent(json)) {
                try {
                    IdeSettings updated = MAPPER.readValue(json, IdeSettings.class);
                    populateValues(updated);
                } catch (Exception e) {
                    NotificationService.getInstance().showWarning("Cảnh báo JSON", "Không thể phân tích JSON để cập nhật form: " + e.getMessage());
                }
            }
        }

        isJsonTabActive = false;
        visualArea.setVisible(true);
        visualArea.setManaged(true);
        jsonArea.setVisible(false);
        jsonArea.setManaged(false);

        btnTabVisual.getStyleClass().add("active");
        btnTabJson.getStyleClass().remove("active");
    }

    @FXML
    public void handleSwitchToJsonTab() {
        // Collect current values from visual form into IdeSettings and serialize
        IdeSettings current = collectValuesFromForm();
        try {
            String json = MAPPER.writeValueAsString(current);
            txtRawJson.setText(json);
            validateJsonContent(json);
        } catch (Exception e) {
            txtRawJson.setText("{}");
        }

        // Lazy initialize Monaco JSON editor on first visit
        if (jsonEditor == null) {
            setupJsonEditor();
        } else {
            jsonEditor.setText(txtRawJson.getText());
        }

        isJsonTabActive = true;
        visualArea.setVisible(false);
        visualArea.setManaged(false);
        jsonArea.setVisible(true);
        jsonArea.setManaged(true);

        btnTabJson.getStyleClass().add("active");
        btnTabVisual.getStyleClass().remove("active");
    }

    /**
     * Prepares the cached dialog controller when reopened (Perf Optimization Task 1).
     */
    public void prepareForShow() {
        if (settingsSearchField != null) {
            settingsSearchField.clear();
        }
        handleSwitchToVisualTab();
        handleNavEditor();
        populateValues(SettingsService.getInstance().getCurrentSettings());
        styler.snapshot();
    }

    // ─────────────────────────────────────────────────────────────
    // Category Navigation
    // ─────────────────────────────────────────────────────────────

    @FXML public void handleNavEditor() { setActiveSection(sectionEditor, btnNavEditor); }
    @FXML public void handleNavGrid() { setActiveSection(sectionGrid, btnNavGrid); }
    @FXML public void handleNavMessages() { setActiveSection(sectionMessages, btnNavMessages); }
    @FXML public void handleNavAppearance() { setActiveSection(sectionAppearance, btnNavAppearance); }
    @FXML public void handleNavSql() { setActiveSection(sectionSql, btnNavSql); }
    @FXML public void handleNavSession() { setActiveSection(sectionSession, btnNavSession); }
    @FXML public void handleNavAddons() { setActiveSection(sectionAddons, btnNavAddons); }
    @FXML public void handleNavHistory() { setActiveSection(sectionHistory, btnNavHistory); }
    @FXML public void handleNavLogging() { setActiveSection(sectionLogging, btnNavLogging); }
    @FXML public void handleNavUpdates() { setActiveSection(sectionUpdates, btnNavUpdates); }

    private void setActiveSection(VBox activeSection, Button activeBtn) {
        this.currentActiveSection = activeSection;
        this.currentActiveBtn = activeBtn;

        for (VBox sec : getAllSections()) {
            boolean isTarget = (sec == activeSection);
            sec.setVisible(isTarget);
            sec.setManaged(isTarget);
        }

        List<Button> navButtons = List.of(
                btnNavEditor, btnNavGrid, btnNavMessages, btnNavAppearance, btnNavSql,
                btnNavSession, btnNavAddons, btnNavHistory, btnNavLogging, btnNavUpdates
        );
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("active");
            if (btn == activeBtn) {
                btn.getStyleClass().add("active");
            }
        }
    }

    private List<VBox> getAllSections() {
        return List.of(
                sectionEditor, sectionGrid, sectionMessages, sectionAppearance, sectionSql,
                sectionSession, sectionAddons, sectionHistory, sectionLogging, sectionUpdates
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Form Population & Value Collection
    // ─────────────────────────────────────────────────────────────

    private boolean populating = false;

    public void populateValues(IdeSettings s) {
        if (s == null) return;
        populating = true;
        try {
            // 1. Editor
            IdeSettings.EditorSettings ed = s.getEditor() != null ? s.getEditor() : new IdeSettings.EditorSettings();
            cmbEditorFontFamily.setValue(ed.getFontFamily());
            spnEditorFontSize.getValueFactory().setValue(ed.getFontSize());
            spnLineHeight.getValueFactory().setValue(ed.getLineHeight());
            spnLetterSpacing.getValueFactory().setValue(ed.getLetterSpacing());
            spnTabSize.getValueFactory().setValue(ed.getTabSize());
            chkInsertSpaces.setSelected(ed.isInsertSpaces());
            chkWordWrap.setSelected(ed.isWordWrap());
            chkMinimap.setSelected(ed.isMinimap());
            cmbKeywordCase.setValue(ed.getKeywordCase());
            chkSyntaxCheck.setSelected(ed.isSyntaxCheck());
            chkIntellisense.setSelected(ed.isIntellisense());
            chkIntellisenseMru.setSelected(ed.isIntellisenseMru());
            chkIntellisenseDetail.setSelected(ed.isIntellisenseDetail());
            boolean onDemand = app.models.IdeSettings.EditorSettings.DETAIL_MODE_ON_DEMAND.equals(ed.getIntellisenseDetailMode());
            rdoDetailOnDemand.setSelected(onDemand);
            rdoDetailAlways.setSelected(!onDemand);

            // 2. Grid
            IdeSettings.GridSettings gr = s.getGrid() != null ? s.getGrid() : new IdeSettings.GridSettings();
            cmbGridFontFamily.setValue(gr.getFontFamily());
            spnGridFontSize.getValueFactory().setValue(gr.getFontSize());
            chkShowRowNumbers.setSelected(gr.isShowRowNumbers());
            txtNullDisplay.setText(gr.getNullDisplayString());
            if (cmbResultLayout != null) {
                cmbResultLayout.getItems().setAll(RESULT_LAYOUT_LIST, RESULT_LAYOUT_TABS);
                cmbResultLayout.setValue(gr.isResultTabs() ? RESULT_LAYOUT_TABS : RESULT_LAYOUT_LIST);
            }

            // 3. Messages
            IdeSettings.MessagesSettings ms = s.getMessages() != null ? s.getMessages() : new IdeSettings.MessagesSettings();
            cmbMessagesFontFamily.setValue(ms.getFontFamily());
            spnMessagesFontSize.getValueFactory().setValue(ms.getFontSize());

            // 4. Appearance
            IdeSettings.AppearanceSettings ap = s.getAppearance() != null ? s.getAppearance() : new IdeSettings.AppearanceSettings();
            String th = s.getTheme() != null && !s.getTheme().isBlank() ? s.getTheme() : "primer-dark";
            cmbTheme.setValue(app.services.theme.ThemeRegistry.getInstance().find(th).label()); // stored: id
            spnIconSize.getValueFactory().setValue(ap.getIconSize());
            cmbUiFontFamily.setValue(ap.getUiFontFamily());
            spnUiFontSize.getValueFactory().setValue(ap.getUiFontSize());
            chkAutoHideNavbar.setSelected(s.isAutoHideNavbar());
            if (cmbPanelStyle != null) {
                cmbPanelStyle.setValue("flat".equalsIgnoreCase(ap.getPanelStyle()) ? PANEL_STYLE_FLAT : PANEL_STYLE_ROUNDED);
            }
        } finally {
            populating = false;
        }

        // 5. SQL / Query
        IdeSettings.SqlSettings sql = s.getSql() != null ? s.getSql() : new IdeSettings.SqlSettings();
        spnMaxRows.getValueFactory().setValue(sql.getMaxRows());
        spnQueryTimeout.getValueFactory().setValue(sql.getTimeoutSeconds());
        cmbSqlProtection.setValue(sql.getSqlprotection() == 1
                ? "1 - Bật bảo vệ (Cảnh báo trước khi chạy)"
                : "0 - Tắt bảo vệ (Không cảnh báo)");
        chkStopOnError.setSelected(sql.isStopOnError());
        chkPerTabSession.setSelected(sql.isPerTabSession());
        chkAutoShowResults.setSelected(sql.isAutoShowResultsPanel());

        // 6. Session
        IdeSettings.SessionSettings se = s.getSession() != null ? s.getSession() : new IdeSettings.SessionSettings();
        chkRestoreOnStartup.setSelected(se.isRestoreOnStartup());
        chkSaveSqlContent.setSelected(se.isSaveSqlContent());
        spnAutoSaveInterval.getValueFactory().setValue(se.getAutoSaveIntervalSeconds());
        spnMaxTabSize.getValueFactory().setValue((int) Math.min(se.getMaxTabSizeBytes(), Integer.MAX_VALUE));

        // 7. Addons
        IdeSettings.AddonsSettings ad = s.getAddons() != null ? s.getAddons() : new IdeSettings.AddonsSettings();
        chkBravoToolEnabled.setSelected(ad.getBravo_tool() != null && ad.getBravo_tool().isEnabled());

        // 8. History
        IdeSettings.HistorySettings hi = s.getHistory() != null ? s.getHistory() : new IdeSettings.HistorySettings();
        chkHistoryEnabled.setSelected(hi.isEnabled());
        spnMaxEntriesPerDoc.getValueFactory().setValue(hi.getMaxEntriesPerDoc());
        spnMaxAgeDays.getValueFactory().setValue(hi.getMaxAgeDays());
        spnMaxDbSizeMb.getValueFactory().setValue(hi.getMaxDbSizeMb());
        spnMaxEntrySizeKb.getValueFactory().setValue(hi.getMaxEntrySizeKb());

        // 9. Logging
        IdeSettings.LoggingSettings lo = s.getLogging() != null ? s.getLogging() : new IdeSettings.LoggingSettings();
        chkLoggingEnabled.setSelected(lo.isEnabled());
        cmbLogLevel.setValue(lo.getLevel());
        spnMaxLogFileSizeMb.getValueFactory().setValue(lo.getMaxFileSizeMb());
        spnMaxBackupFiles.getValueFactory().setValue(lo.getMaxFiles());

        // 10. Updates
        IdeSettings.UpdatesSettings up = s.getUpdates() != null ? s.getUpdates() : new IdeSettings.UpdatesSettings();
        chkCheckOnStartup.setSelected(up.isCheckOnStartup());
        txtUpdateUrl.setText(up.getUpdateUrl());
        lblInstalledVersion.setText(s.getInstalledVersion() != null ? s.getInstalledVersion() : "1.2.6");

        // Sync with raw JSON textarea
        try {
            txtRawJson.setText(MAPPER.writeValueAsString(s));
            validateJsonContent(txtRawJson.getText());
        } catch (Exception ignored) {
        }
    }

    private IdeSettings collectValuesFromForm() {
        IdeSettings s = SettingsService.getInstance().getCurrentSettings().clone();

        // 1. Editor
        IdeSettings.EditorSettings ed = s.getEditor();
        ed.setFontFamily(cmbEditorFontFamily.getValue() != null ? cmbEditorFontFamily.getValue() : "Cascadia Mono Light");
        ed.setFontSize(spnEditorFontSize.getValue());
        ed.setLineHeight(spnLineHeight.getValue());
        ed.setLetterSpacing(spnLetterSpacing.getValue());
        ed.setTabSize(spnTabSize.getValue());
        ed.setInsertSpaces(chkInsertSpaces.isSelected());
        ed.setWordWrap(chkWordWrap.isSelected());
        ed.setMinimap(chkMinimap.isSelected());
        ed.setKeywordCase(cmbKeywordCase.getValue() != null ? cmbKeywordCase.getValue() : "upper");
        ed.setSyntaxCheck(chkSyntaxCheck.isSelected());
        ed.setIntellisense(chkIntellisense.isSelected());
        ed.setIntellisenseMru(chkIntellisenseMru.isSelected());
        ed.setIntellisenseDetail(chkIntellisenseDetail.isSelected());
        ed.setIntellisenseDetailMode(rdoDetailOnDemand.isSelected()
                ? app.models.IdeSettings.EditorSettings.DETAIL_MODE_ON_DEMAND
                : app.models.IdeSettings.EditorSettings.DETAIL_MODE_ALWAYS);

        // 2. Grid
        IdeSettings.GridSettings gr = s.getGrid();
        gr.setFontFamily(cmbGridFontFamily.getValue() != null ? cmbGridFontFamily.getValue() : "Inter");
        gr.setFontSize(spnGridFontSize.getValue());
        gr.setShowRowNumbers(chkShowRowNumbers.isSelected());
        if (cmbResultLayout != null) {
            gr.setResultLayout(RESULT_LAYOUT_TABS.equals(cmbResultLayout.getValue()) ? "tabs" : "list");
        }
        gr.setNullDisplayString(txtNullDisplay.getText() != null ? txtNullDisplay.getText() : "NULL");

        // 3. Messages
        IdeSettings.MessagesSettings ms = s.getMessages();
        ms.setFontFamily(cmbMessagesFontFamily.getValue() != null ? cmbMessagesFontFamily.getValue() : "Fira Code");
        ms.setFontSize(spnMessagesFontSize.getValue());

        // 4. Appearance
        // the combo shows labels; Settings keep the theme id (T1)
        String themeVal = app.services.theme.ThemeRegistry.getInstance().find(cmbTheme.getValue()).id();
        s.setTheme(themeVal);
        IdeSettings.AppearanceSettings ap = s.getAppearance();
        ap.setTheme(themeVal);
        ap.setIconSize(spnIconSize.getValue());
        ap.setUiFontFamily(cmbUiFontFamily.getValue() != null ? cmbUiFontFamily.getValue() : "Inter");
        ap.setUiFontSize(spnUiFontSize.getValue());
        ap.setAutoHideNavbar(chkAutoHideNavbar.isSelected());
        s.setAutoHideNavbar(chkAutoHideNavbar.isSelected());
        if (cmbPanelStyle != null) {
            ap.setPanelStyle(PANEL_STYLE_FLAT.equals(cmbPanelStyle.getValue()) ? "flat" : "rounded");
        }

        // 5. SQL
        IdeSettings.SqlSettings sql = s.getSql();
        sql.setMaxRows(spnMaxRows.getValue());
        sql.setTimeoutSeconds(spnQueryTimeout.getValue());
        sql.setSqlprotection(cmbSqlProtection.getValue() != null && cmbSqlProtection.getValue().startsWith("1") ? 1 : 0);
        sql.setStopOnError(chkStopOnError.isSelected());
        sql.setPerTabSession(chkPerTabSession.isSelected());
        sql.setAutoShowResultsPanel(chkAutoShowResults.isSelected());
        s.getQuery().setTimeoutSeconds(spnQueryTimeout.getValue());
        s.getQuery().setDefaultLimit(spnMaxRows.getValue());

        // 6. Session
        IdeSettings.SessionSettings se = s.getSession();
        se.setRestoreOnStartup(chkRestoreOnStartup.isSelected());
        se.setSaveSqlContent(chkSaveSqlContent.isSelected());
        se.setAutoSaveIntervalSeconds(spnAutoSaveInterval.getValue());
        se.setMaxTabSizeBytes(spnMaxTabSize.getValue().longValue());

        // 7. Addons
        if (s.getAddons().getBravo_tool() != null) {
            s.getAddons().getBravo_tool().setEnabled(chkBravoToolEnabled.isSelected());
        }

        // 8. History
        IdeSettings.HistorySettings hi = s.getHistory();
        hi.setEnabled(chkHistoryEnabled.isSelected());
        hi.setMaxEntriesPerDoc(spnMaxEntriesPerDoc.getValue());
        hi.setMaxAgeDays(spnMaxAgeDays.getValue());
        hi.setMaxDbSizeMb(spnMaxDbSizeMb.getValue());
        hi.setMaxEntrySizeKb(spnMaxEntrySizeKb.getValue());

        // 9. Logging
        IdeSettings.LoggingSettings lo = s.getLogging();
        lo.setEnabled(chkLoggingEnabled.isSelected());
        lo.setLevel(cmbLogLevel.getValue() != null ? cmbLogLevel.getValue() : "INFO");
        lo.setMaxFileSizeMb(spnMaxLogFileSizeMb.getValue());
        lo.setMaxFiles(spnMaxBackupFiles.getValue());

        // 10. Updates
        IdeSettings.UpdatesSettings up = s.getUpdates();
        up.setCheckOnStartup(chkCheckOnStartup.isSelected());
        up.setUpdateUrl(txtUpdateUrl.getText() != null ? txtUpdateUrl.getText().trim() : "");

        // Sync typography
        s.getTypography().getUi().put("family", ap.getUiFontFamily());
        s.getTypography().getUi().put("size", ap.getUiFontSize());
        s.getTypography().getEditor().put("family", ed.getFontFamily());
        s.getTypography().getEditor().put("size", ed.getFontSize());
        s.getTypography().getGrid().put("family", gr.getFontFamily());
        s.getTypography().getGrid().put("size", gr.getFontSize());
        s.getTypography().getMessages().put("family", ms.getFontFamily());
        s.getTypography().getMessages().put("size", ms.getFontSize());

        return s;
    }

    // ─────────────────────────────────────────────────────────────
    // Actions: Save, Copy JSON, Restore Defaults, Close
    // ─────────────────────────────────────────────────────────────

    @FXML
    public void handleSaveAndApply() {
        IdeSettings toSave;
        if (isJsonTabActive) {
            String json = txtRawJson.getText();
            if (!validateJsonContent(json)) {
                NotificationService.getInstance().showError("Lỗi cú pháp JSON", "Không thể lưu vì nội dung JSON không hợp lệ.");
                return;
            }
            try {
                toSave = MAPPER.readValue(json, IdeSettings.class);
            } catch (Exception e) {
                NotificationService.getInstance().showError("Lỗi cấu hình", "Lỗi phân tích JSON: " + e.getMessage());
                return;
            }
        } else {
            toSave = collectValuesFromForm();
        }

        boolean ok = SettingsService.getInstance().saveSettings(toSave);
        if (ok) {
            styler.snapshot();
            NotificationService.getInstance().showSuccess("Cài đặt (Settings)", "Đã lưu và áp dụng cấu hình thành công!");
            handleClose();
        } else {
            NotificationService.getInstance().showError("Lỗi lưu cấu hình", "Không thể ghi dữ liệu cấu hình vào file settings.json.");
        }
    }

    @FXML
    public void handleCopyJson() {
        IdeSettings s = isJsonTabActive ? collectValuesFromForm() : collectValuesFromForm();
        try {
            String jsonStr = isJsonTabActive && validateJsonContent(txtRawJson.getText())
                    ? txtRawJson.getText()
                    : MAPPER.writeValueAsString(s);
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(jsonStr);
            clipboard.setContent(content);
            NotificationService.getInstance().showSuccess("Clipboard", "Đã sao chép toàn bộ cấu hình JSON vào Clipboard!");
        } catch (Exception e) {
            NotificationService.getInstance().showError("Lỗi Clipboard", "Không thể sao chép JSON: " + e.getMessage());
        }
    }

    @FXML
    public void handleRestoreDefaults() {
        IdeSettings defaults = IdeSettings.createDefault();
        populateValues(defaults);
        NotificationService.getInstance().showInfo("Cài đặt", "Đã khôi phục các giá trị thiết lập về mặc định.");
    }

    @FXML
    public void handleClose() {
        if (!canClose()) return;
        closeWindow();
    }

    private void closeWindow() {
        if (settingsRoot != null && settingsRoot.getScene() != null) {
            Stage stage = (Stage) settingsRoot.getScene().getWindow();
            if (stage != null) {
                stage.close();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Accessors for Testing
    // ─────────────────────────────────────────────────────────────

    public ComboBox<String> getThemeComboBox() { return cmbTheme; }
    public Spinner<Integer> getEditorFontSizeSpinner() { return spnEditorFontSize; }
    public Spinner<Integer> getQueryTimeoutSpinner() { return spnQueryTimeout; }
    public TextField getSearchField() { return settingsSearchField; }
    public VBox getGeneralSection() { return sectionAppearance; } // Alias for backward compatibility in tests
    public VBox getAppearanceSection() { return sectionAppearance; }
    public VBox getEditorSection() { return sectionEditor; }
    public VBox getGridSection() { return sectionGrid; }
    public VBox getQuerySection() { return sectionSql; }
    public TextArea getRawJsonTextArea() { return txtRawJson; }
    public Button getTabVisualButton() { return btnTabVisual; }
    public Button getTabJsonButton() { return btnTabJson; }
    public boolean isJsonTabActive() { return isJsonTabActive; }
    public app.ui.editor.EmbeddedMonaco getJsonEditor() { return jsonEditor; }
}
