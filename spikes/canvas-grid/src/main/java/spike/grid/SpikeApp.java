package spike.grid;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.Map;

/**
 * S1 demo window: pick a scenario (K1–K4) and a grid (Canvas / TableView); the status line shows how long
 * the data took to build and the grid to appear. Same data object for both grids.
 */
public class SpikeApp extends Application {

    private final Map<SpikeData.Scenario, SpikeData> cache = new EnumMap<>(SpikeData.Scenario.class);
    private final StackPane center = new StackPane();
    private final Label status = new Label();
    private final ComboBox<SpikeData.Scenario> scenario = new ComboBox<>();
    private final ToggleGroup kind = new ToggleGroup();
    private final ToggleButton canvasBtn = new ToggleButton("Canvas");
    private final ToggleButton tableBtn = new ToggleButton("TableView");

    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        scenario.getItems().setAll(SpikeData.Scenario.values());
        scenario.getSelectionModel().select(SpikeData.Scenario.K1);
        canvasBtn.setToggleGroup(kind);
        tableBtn.setToggleGroup(kind);
        canvasBtn.setSelected(true);
        status.getStyleClass().add("spike-status");

        HBox bar = new HBox(new Label("Dữ liệu:"), scenario, canvasBtn, tableBtn, status);
        bar.getStyleClass().add("spike-toolbar");
        BorderPane root = new BorderPane(center);
        root.setTop(bar);

        scenario.setOnAction(e -> show());
        kind.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true); else show();
        });

        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add(SpikeApp.class.getResource("/spike/spike.css").toExternalForm());
        stage.setTitle("Canvas grid spike (S1) — SQL-IDE D4");
        stage.setScene(scene);
        stage.show();
        show();
    }

    private void show() {
        SpikeData.Scenario s = scenario.getValue();
        long t0 = System.nanoTime();
        SpikeData data = cache.computeIfAbsent(s, SpikeData::of);
        long built = System.nanoTime();
        Node grid = canvasBtn.isSelected() ? canvasGrid(data) : TableViewGrid.build(data);
        center.getChildren().setAll(grid);
        center.applyCss();
        center.layout();
        long shown = System.nanoTime();
        status.setText(String.format("%s · %s · dữ liệu %d ms · hiện %d ms", s.label,
                canvasBtn.isSelected() ? "Canvas" : "TableView", (built - t0) / 1_000_000, (shown - built) / 1_000_000));
    }

    static CanvasGrid canvasGrid(SpikeData data) {
        CanvasGrid g = new CanvasGrid();
        g.setData(data.columns, data.rows);
        return g;
    }
}
