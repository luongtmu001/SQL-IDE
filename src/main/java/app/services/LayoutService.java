package app.services;

import app.application.AppContext;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.prefs.Preferences;

public class LayoutService {

    public enum LayoutPreset {
        DEFAULT("Default Layout", "Standard layout with 22% explorer and 65% editor"),
        EDITOR_FOCUSED("Editor Focused", "Distraction-free mode with sidebar and results hidden"),
        DB_EXPLORER_FOCUSED("Explorer Focused", "Wider 35% explorer for deep schema navigation"),
        RESULTS_MAXIMIZED("Results Maximized", "Expanded 85% results area for deep data inspection");

        private final String displayName;
        private final String description;

        LayoutPreset(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final LayoutService INSTANCE = new LayoutService();

    // Default constants
    public static final double DEFAULT_SIDEBAR_DIVIDER = 0.22;
    public static final double COLLAPSED_SIDEBAR_DIVIDER = 0.038; // Mini strip width ~46px
    public static final double DEFAULT_RESULTS_DIVIDER = 0.65;
    public static final double MAXIMIZED_RESULTS_DIVIDER = 0.15; // Editor gets 15%, Results gets 85%
    public static final double EXPLORER_FOCUSED_DIVIDER = 0.35;

    /** SCR-MAIN-001: the Explorer keeps a fixed pixel width (VS Code / LLTool behaviour). */
    public static final double DEFAULT_SIDEBAR_WIDTH_PX = 260;
    public static final double MIN_SIDEBAR_WIDTH_PX = 180;
    public static final double EXPLORER_FOCUSED_WIDTH_PX = 420;
    public static final double RAIL_WIDTH_PX = 46;
    public static final double MIN_PANEL_HEIGHT_PX = 120;

    // Reactive State Properties
    private final BooleanProperty sidebarVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty sidebarCollapsed = new SimpleBooleanProperty(false);
    private final BooleanProperty resultsVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty resultsMaximized = new SimpleBooleanProperty(false);
    private final BooleanProperty resultsFloating = new SimpleBooleanProperty(false);
    private final DoubleProperty sidebarDividerPosition = new SimpleDoubleProperty(DEFAULT_SIDEBAR_DIVIDER);
    private final DoubleProperty resultsDividerPosition = new SimpleDoubleProperty(DEFAULT_RESULTS_DIVIDER);
    private final DoubleProperty sidebarWidthPx = new SimpleDoubleProperty(DEFAULT_SIDEBAR_WIDTH_PX);
    private final StringProperty activeEditorTabTitle = new SimpleStringProperty("console_1.sql");
    private final IntegerProperty activeResultTabIndex = new SimpleIntegerProperty(0);
    private final ObjectProperty<LayoutPreset> currentPreset = new SimpleObjectProperty<>(LayoutPreset.DEFAULT);

    // Floating window state (PoC)
    private Stage floatingResultsStage;
    private Node floatingContentNode;
    private Runnable onReDockCallback;

    // Preferences key
    private final Preferences prefs = Preferences.userNodeForPackage(LayoutService.class);

    private LayoutService() {
        loadPreferences();
    }

    public static LayoutService getInstance() {
        return INSTANCE;
    }

    public BooleanProperty sidebarVisibleProperty() {
        return sidebarVisible;
    }

    public boolean isSidebarVisible() {
        return sidebarVisible.get();
    }

    public void setSidebarVisible(boolean visible) {
        this.sidebarVisible.set(visible);
    }

    public BooleanProperty sidebarCollapsedProperty() {
        return sidebarCollapsed;
    }

    public boolean isSidebarCollapsed() {
        return sidebarCollapsed.get();
    }

    public void setSidebarCollapsed(boolean collapsed) {
        this.sidebarCollapsed.set(collapsed);
    }

    public void toggleSidebarCollapse() {
        setSidebarCollapsed(!isSidebarCollapsed());
    }

    public BooleanProperty resultsVisibleProperty() {
        return resultsVisible;
    }

    public boolean isResultsVisible() {
        return resultsVisible.get();
    }

    public void setResultsVisible(boolean visible) {
        this.resultsVisible.set(visible);
    }

    public BooleanProperty resultsMaximizedProperty() {
        return resultsMaximized;
    }

    public boolean isResultsMaximized() {
        return resultsMaximized.get();
    }

    public BooleanProperty resultsFloatingProperty() {
        return resultsFloating;
    }

    public boolean isResultsFloating() {
        return resultsFloating.get();
    }

    public DoubleProperty sidebarDividerPositionProperty() {
        return sidebarDividerPosition;
    }

    public double getSidebarDividerPosition() {
        return sidebarDividerPosition.get();
    }

    public void setSidebarDividerPosition(double pos) {
        if (pos > 0.05 && pos < 0.95) {
            this.sidebarDividerPosition.set(pos);
        }
    }

    public DoubleProperty sidebarWidthPxProperty() {
        return sidebarWidthPx;
    }

    public double getSidebarWidthPx() {
        return sidebarWidthPx.get();
    }

    public void setSidebarWidthPx(double px) {
        if (Double.isFinite(px) && px >= MIN_SIDEBAR_WIDTH_PX) {
            sidebarWidthPx.set(Math.round(px));
        }
    }

    public DoubleProperty resultsDividerPositionProperty() {
        return resultsDividerPosition;
    }

    public double getResultsDividerPosition() {
        return resultsDividerPosition.get();
    }

    public void setResultsDividerPosition(double pos) {
        if (pos > 0.05 && pos < 0.95) {
            this.resultsDividerPosition.set(pos);
        }
    }

    public StringProperty activeEditorTabTitleProperty() {
        return activeEditorTabTitle;
    }

    public String getActiveEditorTabTitle() {
        return activeEditorTabTitle.get();
    }

    public void setActiveEditorTabTitle(String title) {
        if (title != null && !title.isBlank()) {
            this.activeEditorTabTitle.set(title);
        }
    }

    public IntegerProperty activeResultTabIndexProperty() {
        return activeResultTabIndex;
    }

    public int getActiveResultTabIndex() {
        return activeResultTabIndex.get();
    }

    public void setActiveResultTabIndex(int index) {
        if (index >= 0) {
            this.activeResultTabIndex.set(index);
        }
    }

    public ObjectProperty<LayoutPreset> currentPresetProperty() {
        return currentPreset;
    }

    public LayoutPreset getCurrentPreset() {
        return currentPreset.get();
    }

    // Layout Actions
    public void toggleSidebar() {
        setSidebarVisible(!isSidebarVisible());
    }

    public void toggleResults() {
        setResultsVisible(!isResultsVisible());
    }

    public void toggleMaximizeResults() {
        resultsMaximized.set(!resultsMaximized.get());
    }

    public void applyPreset(LayoutPreset preset) {
        this.currentPreset.set(preset);
        switch (preset) {
            case DEFAULT -> {
                setSidebarVisible(true);
                setSidebarCollapsed(false);
                setResultsVisible(true);
                resultsMaximized.set(false);
                setSidebarDividerPosition(DEFAULT_SIDEBAR_DIVIDER);
                setSidebarWidthPx(DEFAULT_SIDEBAR_WIDTH_PX);
                setResultsDividerPosition(DEFAULT_RESULTS_DIVIDER);
            }
            case EDITOR_FOCUSED -> {
                setSidebarVisible(false);
                setResultsVisible(false);
                resultsMaximized.set(false);
            }
            case DB_EXPLORER_FOCUSED -> {
                setSidebarVisible(true);
                setSidebarCollapsed(false);
                setResultsVisible(true);
                resultsMaximized.set(false);
                setSidebarDividerPosition(EXPLORER_FOCUSED_DIVIDER);
                setSidebarWidthPx(EXPLORER_FOCUSED_WIDTH_PX);
                setResultsDividerPosition(DEFAULT_RESULTS_DIVIDER);
            }
            case RESULTS_MAXIMIZED -> {
                setSidebarVisible(true);
                setSidebarCollapsed(false);
                setResultsVisible(true);
                resultsMaximized.set(true);
                setResultsDividerPosition(MAXIMIZED_RESULTS_DIVIDER);
            }
        }
    }

    public void resetToDefault() {
        setSidebarCollapsed(false);
        applyPreset(LayoutPreset.DEFAULT);
        savePreferences();
    }

    // Docking & Floating Window PoC
    public void openFloatingResultsWindow(Node contentNode, Runnable onDockCallback) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> openFloatingResultsWindow(contentNode, onDockCallback));
            return;
        }

        if (resultsFloating.get() || contentNode == null) return;

        // Ensure closing this floating stage doesn't shut down JavaFX platform
        Platform.setImplicitExit(false);

        this.floatingContentNode = contentNode;
        this.onReDockCallback = onDockCallback;

        floatingResultsStage = new Stage();
        floatingResultsStage.setTitle("SQL IDE — Results & Output (Floating Window)");
        floatingResultsStage.setMinWidth(480);
        floatingResultsStage.setMinHeight(300);
        floatingResultsStage.setWidth(850);
        floatingResultsStage.setHeight(520);

        // Header bar with "Re-dock to Main Window" button
        HBox headerBar = new HBox(8);
        headerBar.setAlignment(Pos.CENTER_LEFT);
        headerBar.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 6 10 6 10; -fx-border-color: transparent transparent -color-border-subtle transparent; -fx-border-width: 0 0 1 0;");

        FontIcon popIcon = new FontIcon("fth-external-link");
        Label titleLbl = new Label("Floating Results Panel (Multi-Monitor Mode)");
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button reDockBtn = new Button("Dock Back to Workspace");
        reDockBtn.setGraphic(new FontIcon("fth-layout"));
        reDockBtn.getStyleClass().addAll("accent", "small");
        reDockBtn.setTooltip(new Tooltip("Re-attach results panel to main application window"));
        reDockBtn.setOnAction(e -> reDockResults());

        headerBar.getChildren().addAll(popIcon, titleLbl, spacer, reDockBtn);

        BorderPane rootPane = new BorderPane();
        rootPane.setTop(headerBar);
        rootPane.setCenter(contentNode);

        Scene scene = new Scene(rootPane);
        ThemeService.getInstance().initializeTheme(scene);

        floatingResultsStage.setScene(scene);
        floatingResultsStage.setOnCloseRequest(e -> {
            reDockResults();
        });

        resultsFloating.set(true);
        floatingResultsStage.show();

        NotificationService.getInstance().showInfo(
            "Floating Window",
            "Results panel moved to floating window. Click 'Dock Back' to return."
        );
    }

    public void reDockResults() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::reDockResults);
            return;
        }

        if (!resultsFloating.get()) return;

        if (floatingResultsStage != null) {
            // Detach node from floating stage scene
            if (floatingResultsStage.getScene() != null && floatingResultsStage.getScene().getRoot() instanceof BorderPane bp) {
                bp.setCenter(null);
            }
            floatingResultsStage.close();
            floatingResultsStage = null;
        }

        resultsFloating.set(false);

        if (onReDockCallback != null) {
            onReDockCallback.run();
        }

        NotificationService.getInstance().showSuccess(
            "Workspace Restored",
            "Results panel successfully re-docked to main workspace."
        );
    }

    // Preferences Persistence
    public void savePreferences() {
        try {
            prefs.putBoolean("sidebar_visible", isSidebarVisible());
            prefs.putBoolean("sidebar_collapsed", isSidebarCollapsed());
            prefs.putBoolean("results_visible", isResultsVisible());
            prefs.putDouble("sidebar_divider", getSidebarDividerPosition());
            prefs.putDouble("results_divider", getResultsDividerPosition());
            prefs.putDouble("sidebar_width_px", getSidebarWidthPx());
            prefs.put("active_editor_tab", getActiveEditorTabTitle() != null ? getActiveEditorTabTitle() : "");
            prefs.putInt("active_result_tab", getActiveResultTabIndex());
        } catch (Exception ignored) {
        }
    }

    public void loadPreferences() {
        try {
            sidebarVisible.set(prefs.getBoolean("sidebar_visible", true));
            sidebarCollapsed.set(prefs.getBoolean("sidebar_collapsed", false));
            resultsVisible.set(prefs.getBoolean("results_visible", true));
            sidebarDividerPosition.set(prefs.getDouble("sidebar_divider", DEFAULT_SIDEBAR_DIVIDER));
            resultsDividerPosition.set(prefs.getDouble("results_divider", DEFAULT_RESULTS_DIVIDER));
            double px = prefs.getDouble("sidebar_width_px", DEFAULT_SIDEBAR_WIDTH_PX);
            sidebarWidthPx.set(px >= MIN_SIDEBAR_WIDTH_PX ? px : DEFAULT_SIDEBAR_WIDTH_PX);
            activeEditorTabTitle.set(prefs.get("active_editor_tab", "console_1.sql"));
            activeResultTabIndex.set(prefs.getInt("active_result_tab", 0));
        } catch (Exception ignored) {
        }
    }
}
