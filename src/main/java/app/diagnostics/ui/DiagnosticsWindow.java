package app.diagnostics.ui;

import app.diagnostics.advanced.DeadlockDetector;
import app.diagnostics.advanced.FlightRecorderManager;
import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsLevel;
import app.diagnostics.core.DiagnosticsManager;
import app.diagnostics.errors.ErrorRecord;
import app.diagnostics.errors.ExceptionCollector;
import app.diagnostics.integrations.DatabaseDiagnostics;
import app.diagnostics.integrations.JavaFxDiagnostics;
import app.diagnostics.integrations.WebViewDiagnostics;
import app.diagnostics.level.DebugLevelManager;
import app.diagnostics.monitoring.SystemMetricsSnapshot;
import app.diagnostics.monitoring.SystemMonitor;
import app.diagnostics.startup.StartupProfiler;
import app.diagnostics.startup.StartupReport;
import app.diagnostics.startup.StartupTask;
import app.diagnostics.storage.DiagnosticsExporter;
import app.diagnostics.tasks.TaskRecord;
import app.diagnostics.tasks.TaskTracker;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Standalone Diagnostics Center Window.
 * <p>
 * Completely decoupled from MainWindow.
 */
public final class DiagnosticsWindow {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private static Stage windowStage;
    private final SystemMonitor systemMonitor;
    private final DiagnosticsExporter exporter;

    private Label statusBadge;
    private CheckBox debugToggle;
    private ComboBox<DiagnosticsLevel> levelCombo;
    private StackPane contentArea;

    public DiagnosticsWindow() {
        this.systemMonitor = new SystemMonitor(60);
        this.exporter = new DiagnosticsExporter();
    }

    public static synchronized void showWindow() {
        Platform.runLater(() -> {
            if (windowStage != null) {
                windowStage.toFront();
                windowStage.requestFocus();
                return;
            }

            DiagnosticsWindow diagWin = new DiagnosticsWindow();
            Stage stage = new Stage();
            stage.setTitle("LLTool - Application Diagnostics & Debug Center");
            stage.setWidth(1020);
            stage.setHeight(680);
            stage.setMinWidth(800);
            stage.setMinHeight(500);

            Scene scene = new Scene(diagWin.buildRootNode());
            stage.setScene(scene);

            stage.setOnCloseRequest(e -> {
                diagWin.shutdown();
                windowStage = null;
            });

            windowStage = stage;
            stage.show();
        });
    }

    /**
     * Installs global keyboard shortcuts on the provided Scene to open Diagnostics:
     * <ul>
     *     <li>{@code Ctrl + Shift + D} (or {@code Cmd + Shift + D} on macOS)</li>
     *     <li>{@code Ctrl + Alt + D}</li>
     * </ul>
     */
    public static void installGlobalShortcut(Scene scene) {
        if (scene == null) return;
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() && event.getCode() == javafx.scene.input.KeyCode.D) {
                if (event.isShiftDown() || event.isAltDown()) {
                    showWindow();
                    event.consume();
                }
            }
        });
    }

    public Parent buildRootNode() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #cccccc; -fx-font-family: 'Segoe UI', sans-serif;");

        contentArea = new StackPane();
        contentArea.setPadding(new Insets(15));

        root.setTop(buildHeader());
        root.setLeft(buildSidebar());

        contentArea.getChildren().setAll(buildOverviewView());
        root.setCenter(contentArea);

        return root;
    }

    private Node buildHeader() {
        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setStyle("-fx-background-color: #252526; -fx-border-color: #333333; -fx-border-width: 0 0 1 0;");

        Label title = new Label("Diagnostics & Debug Center");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        statusBadge = new Label("ONLINE");
        statusBadge.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: #ffffff; -fx-padding: 3 8; -fx-font-weight: bold; -fx-background-radius: 4; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        debugToggle = new CheckBox("Debug Mode");
        debugToggle.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
        debugToggle.setSelected(DebugLevelManager.getInstance().isDebugModeActive());
        debugToggle.setOnAction(e -> {
            boolean active = debugToggle.isSelected();
            if (active) {
                DebugLevelManager.getInstance().enableDebugMode(levelCombo.getValue());
            } else {
                DebugLevelManager.getInstance().disableDebugMode();
            }
        });

        Label levelLabel = new Label("Level:");
        levelLabel.setStyle("-fx-text-fill: #aaaaaa;");

        levelCombo = new ComboBox<>(FXCollections.observableArrayList(
                DiagnosticsLevel.BASIC, DiagnosticsLevel.BALANCED, DiagnosticsLevel.ADVANCED
        ));
        levelCombo.setValue(DebugLevelManager.getInstance().getCurrentLevel() == DiagnosticsLevel.OFF
                ? DiagnosticsLevel.BASIC
                : DebugLevelManager.getInstance().getCurrentLevel());
        levelCombo.setOnAction(e -> {
            if (debugToggle.isSelected()) {
                DebugLevelManager.getInstance().setLevel(levelCombo.getValue());
            }
        });

        Button exportBtn = new Button("Export Bundle");
        exportBtn.setStyle("-fx-background-color: #007acc; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 4;");
        exportBtn.setOnAction(e -> handleExportBundle());

        header.getChildren().addAll(title, statusBadge, spacer, debugToggle, levelLabel, levelCombo, exportBtn);
        return header;
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(210);
        sidebar.setPadding(new Insets(10));
        sidebar.setStyle("-fx-background-color: #252526; -fx-border-color: #333333; -fx-border-width: 0 1 0 0;");

        String[] navItems = {
                "Overview",
                "Startup Analysis",
                "Performance",
                "Running Tasks",
                "Error Monitor",
                "WebView Diagnostics",
                "Database Diagnostics",
                "Event Logs"
        };

        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < navItems.length; i++) {
            String name = navItems[i];
            ToggleButton btn = new ToggleButton(name);
            btn.setToggleGroup(group);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setPadding(new Insets(8, 12, 8, 12));
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 13px; -fx-cursor: hand;");

            btn.selectedProperty().addListener((obs, oldV, selected) -> {
                if (selected) {
                    btn.setStyle("-fx-background-color: #37373d; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 13px; -fx-background-radius: 4;");
                    switchView(name);
                } else {
                    btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 13px;");
                }
            });

            if (i == 0) {
                btn.setSelected(true);
            }

            sidebar.getChildren().add(btn);
        }

        return sidebar;
    }

    private void switchView(String viewName) {
        if (contentArea == null) return;
        contentArea.getChildren().clear();
        Node view = switch (viewName) {
            case "Startup Analysis" -> buildStartupView();
            case "Performance" -> buildPerformanceView();
            case "Running Tasks" -> buildTasksView();
            case "Error Monitor" -> buildErrorsView();
            case "WebView Diagnostics" -> buildWebViewView();
            case "Database Diagnostics" -> buildDatabaseView();
            case "Event Logs" -> buildLogsView();
            default -> buildOverviewView();
        };
        contentArea.getChildren().add(view);
    }

    private Node buildOverviewView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("System & Environment Overview"));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);

        SystemMetricsSnapshot snap = systemMonitor.captureSnapshot();
        addGridRow(grid, 0, "Operating System", System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        addGridRow(grid, 1, "Java Runtime", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        addGridRow(grid, 2, "Diagnostics Session", DiagnosticsManager.getInstance().getCurrentSession() != null
                ? DiagnosticsManager.getInstance().getCurrentSession().getSessionId() : "N/A");
        addGridRow(grid, 3, "Debug Mode", DebugLevelManager.getInstance().isDebugModeActive() ? "ACTIVE" : "OFF");
        addGridRow(grid, 4, "Active Level", DebugLevelManager.getInstance().getCurrentLevel().getDisplayName());
        addGridRow(grid, 5, "Total Live Threads", String.valueOf(snap.liveThreadCount()));
        addGridRow(grid, 6, "Total Startup Tasks", String.valueOf(StartupProfiler.getInstance().getTasks().size()));

        box.getChildren().add(grid);
        return box;
    }

    private Node buildStartupView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("Startup Timeline & Bottleneck Analysis"));

        StartupReport report = StartupProfiler.getInstance().generateReport();

        HBox stats = new HBox(20);
        stats.getChildren().addAll(
                createStatCard("Total Startup Duration", report.totalStartupDurationMs() + " ms"),
                createStatCard("JVM Bootstrap", report.jvmBootstrapDurationMs() + " ms"),
                createStatCard("Completed Tasks", String.valueOf(report.tasks().size())),
                createStatCard("Bottlenecks (>=250ms)", String.valueOf(report.getBottlenecks().size()))
        );
        box.getChildren().add(stats);

        TableView<StartupTask> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<StartupTask, String> nameCol = new TableColumn<>("Task Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<StartupTask, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().durationMs() + " ms"));

        TableColumn<StartupTask, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));

        TableColumn<StartupTask, String> threadCol = new TableColumn<>("Thread");
        threadCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().threadName()));

        table.getColumns().addAll(nameCol, durCol, statusCol, threadCol);
        table.setItems(FXCollections.observableArrayList(report.tasks()));

        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().add(table);
        return box;
    }

    private Node buildPerformanceView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("Real-time Performance & JVM Telemetry"));

        SystemMetricsSnapshot snap = systemMonitor.captureSnapshot();

        HBox cards = new HBox(15);
        cards.getChildren().addAll(
                createStatCard("Process CPU", String.format("%.1f %%", snap.getProcessCpuPercentage())),
                createStatCard("System CPU", String.format("%.1f %%", snap.getSystemCpuPercentage())),
                createStatCard("Heap Memory", String.format("%.1f MB / %.1f MB (%.1f%%)",
                        snap.getHeapUsedMb(), snap.getHeapMaxMb(), snap.getHeapUsagePercentage())),
                createStatCard("Threads", String.format("%d live / %d peak", snap.liveThreadCount(), snap.peakThreadCount()))
        );
        box.getChildren().add(cards);

        HBox actions = new HBox(10);
        Button refreshBtn = new Button("Refresh Metrics");
        refreshBtn.setOnAction(e -> switchView("Performance"));

        Button deadlockBtn = new Button("Check Deadlocks");
        deadlockBtn.setOnAction(e -> {
            var dl = DeadlockDetector.getInstance().detectDeadlocks();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Deadlock Detection");
            alert.setHeaderText(dl.deadlockDetected() ? "Deadlock Detected!" : "No Deadlocks Detected");
            alert.setContentText("Active deadlocked threads: " + dl.deadlockedThreads().size());
            alert.showAndWait();
        });

        actions.getChildren().addAll(refreshBtn, deadlockBtn);
        box.getChildren().add(actions);
        return box;
    }

    private Node buildTasksView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("Asynchronous & Running Tasks"));

        TableView<TaskRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<TaskRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<TaskRecord, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category()));

        TableColumn<TaskRecord, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status().name()));

        TableColumn<TaskRecord, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().durationMs() + " ms"));

        table.getColumns().addAll(nameCol, catCol, statusCol, durCol);
        table.setItems(FXCollections.observableArrayList(TaskTracker.getInstance().getCompletedTasks()));

        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().add(table);
        return box;
    }

    private Node buildErrorsView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("Runtime Exceptions & Error Monitor"));

        TableView<ErrorRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<ErrorRecord, String> countCol = new TableColumn<>("Count");
        countCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOccurrenceCount())));

        TableColumn<ErrorRecord, String> classCol = new TableColumn<>("Class");
        classCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getExceptionClass()));

        TableColumn<ErrorRecord, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMessage()));

        TableColumn<ErrorRecord, String> threadCol = new TableColumn<>("Thread");
        threadCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getThreadName()));

        table.getColumns().addAll(countCol, classCol, msgCol, threadCol);
        table.setItems(FXCollections.observableArrayList(ExceptionCollector.getInstance().getErrors()));

        TextArea stackView = new TextArea();
        stackView.setEditable(false);
        stackView.setPrefHeight(180);
        stackView.setPromptText("Select an error to view stack trace...");

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
            if (selected != null) {
                stackView.setText(selected.getStackTrace());
            }
        });

        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().addAll(table, stackView);
        return box;
    }

    private Node buildWebViewView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("Monaco WebView & JS Diagnostics"));

        Label subtitle = new Label("JavaScript Console Errors:");
        subtitle.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold;");

        TableView<WebViewDiagnostics.JsErrorRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<WebViewDiagnostics.JsErrorRecord, String> srcCol = new TableColumn<>("Source");
        srcCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sourceId() + ":" + c.getValue().lineNumber()));

        TableColumn<WebViewDiagnostics.JsErrorRecord, String> msgCol = new TableColumn<>("Error Message");
        msgCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().message()));

        table.getColumns().addAll(srcCol, msgCol);
        table.setItems(FXCollections.observableArrayList(WebViewDiagnostics.getInstance().getJsErrors()));

        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().addAll(subtitle, table);
        return box;
    }

    private Node buildDatabaseView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("Database Operations (Sanitized)"));

        TableView<DatabaseDiagnostics.QueryMetric> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<DatabaseDiagnostics.QueryMetric, String> sqlCol = new TableColumn<>("Sanitized Query");
        sqlCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sanitizedSql()));

        TableColumn<DatabaseDiagnostics.QueryMetric, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().durationMs() + " ms"));

        TableColumn<DatabaseDiagnostics.QueryMetric, String> rowsCol = new TableColumn<>("Rows");
        rowsCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().rowsAffected())));

        table.getColumns().addAll(sqlCol, durCol, rowsCol);
        table.setItems(FXCollections.observableArrayList(DatabaseDiagnostics.getInstance().getQueryMetrics()));

        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().add(table);
        return box;
    }

    private Node buildLogsView() {
        VBox box = new VBox(15);
        box.getChildren().add(createSectionHeader("Diagnostic Event Stream"));

        TableView<DiagnosticsEvent> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<DiagnosticsEvent, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(TIME_FMT.format(c.getValue().getTimestamp())));

        TableColumn<DiagnosticsEvent, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSeverity().name()));

        TableColumn<DiagnosticsEvent, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));

        TableColumn<DiagnosticsEvent, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        TableColumn<DiagnosticsEvent, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMessage()));

        table.getColumns().addAll(timeCol, sevCol, catCol, nameCol, msgCol);

        var session = DiagnosticsManager.getInstance().getCurrentSession();
        if (session != null) {
            table.setItems(FXCollections.observableArrayList(session.getEvents()));
        }

        VBox.setVgrow(table, Priority.ALWAYS);
        box.getChildren().add(table);
        return box;
    }

    private void handleExportBundle() {
        try {
            Path bundle = exporter.createDiagnosticsBundle(null);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Diagnostics Bundle Created");
            alert.setHeaderText("Export Successful!");
            alert.setContentText("Bundle created at:\n" + bundle.toAbsolutePath());
            alert.showAndWait();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export Failed");
            alert.setContentText("Failed to create diagnostics bundle: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    private Label createSectionHeader(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #ffffff; -fx-padding: 0 0 6 0;");
        return label;
    }

    private Node createStatCard(String labelText, String valueText) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.setStyle("-fx-background-color: #252526; -fx-border-color: #333333; -fx-border-radius: 4; -fx-background-radius: 4;");

        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");

        Label value = new Label(valueText);
        value.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");

        card.getChildren().addAll(label, value);
        return card;
    }

    private void addGridRow(GridPane grid, int row, String key, String val) {
        Label k = new Label(key + ":");
        k.setStyle("-fx-text-fill: #888888; -fx-font-weight: bold;");
        Label v = new Label(val);
        v.setStyle("-fx-text-fill: #ffffff;");
        grid.add(k, 0, row);
        grid.add(v, 1, row);
    }

    public void shutdown() {
        systemMonitor.stop();
    }
}
