package app.ui.dependency;

import app.ui.components.AppButton;
import app.ui.components.IconButton;
import app.ui.editor.EmbeddedMonaco;
import com.lltool.core.domain.dependency.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Inspector drawer for Dependency Graph (Phase 3 - Slice 5).
 * Contains 3 tabs: Node Properties, DDL Definition, and Suggested Migration Steps.
 */
public class DependencyInspectorPanel extends VBox {

    private final TabPane tabPane = new TabPane();
    private final Label lblHeaderTitle = new Label("Chi tiết đối tượng");
    private final Tooltip headerTooltip = new Tooltip();
    private final Label lblHeaderTypeBadge = new Label("TABLE");

    // Properties Tab Controls
    private final Label lblPropFullName = new Label("-");
    private final Label lblPropSchema = new Label("-");
    private final Label lblPropType = new Label("-");
    private final Label lblPropRole = new Label("-");
    private final Label lblPropInbound = new Label("0");
    private final Label lblPropOutbound = new Label("0");
    private final Label lblPropRowCount = new Label("-");
    private final Label lblPropDescription = new Label("-");

    // Definition Tab Controls
    private EmbeddedMonaco monacoViewer;
    private final StackPane editorContainer = new StackPane();
    private String currentDdl = "";

    // Suggested Steps Tab Controls
    private final VBox stepsContainer = new VBox(8);
    private final ScrollPane stepsScrollPane = new ScrollPane(stepsContainer);
    private final AppButton btnOpenFullScript = new AppButton("Mở toàn bộ kịch bản trong Tab SQL", "fth-terminal", AppButton.Variant.PRIMARY);

    private Consumer<String> openInSqlTabHandler;

    public DependencyInspectorPanel() {
        getStyleClass().add("dep-inspector");
        setPrefWidth(380);
        setMinWidth(320);

        buildHeader();
        buildTabs();
    }

    private void buildHeader() {
        HBox header = new HBox(8);
        header.getStyleClass().add("dep-inspector-header");

        FontIcon icon = new FontIcon("fth-info");
        icon.setIconSize(13);

        lblHeaderTitle.getStyleClass().add("node-title");
        lblHeaderTitle.setMaxWidth(200);
        lblHeaderTitle.setTooltip(headerTooltip);
        lblHeaderTypeBadge.getStyleClass().addAll("node-badge", "node-badge-table");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(icon, lblHeaderTitle, spacer, lblHeaderTypeBadge);
        getChildren().add(header);
    }

    private void buildTabs() {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1: Thuộc tính
        Tab tabProps = new Tab("Thuộc tính", buildPropertiesContent());
        tabProps.setGraphic(new FontIcon("fth-sliders"));

        // Tab 2: DDL
        Tab tabDdl = new Tab("DDL", buildDefinitionContent());
        tabDdl.setGraphic(new FontIcon("fth-code"));

        // Tab 3: Các bước
        Tab tabSteps = new Tab("Các bước", buildStepsContent());
        tabSteps.setGraphic(new FontIcon("fth-check-circle"));

        tabPane.getTabs().addAll(tabProps, tabDdl, tabSteps);
        getChildren().add(tabPane);
    }

    private VBox buildPropertiesContent() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        VBox card = new VBox(6);
        card.getStyleClass().add("property-group-card");

        card.getChildren().addAll(
                createPropertyRow("Tên đối tượng:", lblPropFullName),
                createPropertyRow("Lược đồ:", lblPropSchema),
                createPropertyRow("Loại đối tượng:", lblPropType),
                createPropertyRow("Vai trò sơ đồ:", lblPropRole),
                new Separator(),
                createPropertyRow("Liên kết đến:", lblPropInbound),
                createPropertyRow("Liên kết ra:", lblPropOutbound),
                createPropertyRow("Số dòng:", lblPropRowCount)
        );

        VBox descCard = new VBox(4);
        descCard.getStyleClass().add("property-group-card");
        Label descHeader = new Label("Ghi chú & Phân tích tác động:");
        descHeader.getStyleClass().add("node-subtitle");
        lblPropDescription.setWrapText(true);
        lblPropDescription.getStyleClass().add("property-val");
        descCard.getChildren().addAll(descHeader, lblPropDescription);

        box.getChildren().addAll(card, descCard);
        return box;
    }

    private HBox createPropertyRow(String key, Label val) {
        HBox row = new HBox(8);
        row.getStyleClass().add("property-row");
        Label keyLbl = new Label(key);
        keyLbl.getStyleClass().add("property-key");
        val.getStyleClass().add("property-val");
        row.getChildren().addAll(keyLbl, val);
        return row;
    }

    private VBox buildDefinitionContent() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));

        HBox toolbar = new HBox(6);
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        AppButton btnCopy = new AppButton("Sao chép DDL", "fth-copy", AppButton.Variant.DEFAULT);
        btnCopy.setOnAction(e -> {
            if (!currentDdl.isBlank()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(currentDdl);
                Clipboard.getSystemClipboard().setContent(content);
            }
        });

        AppButton btnOpenTab = new AppButton("Mở trong Tab SQL", "fth-external-link", AppButton.Variant.DEFAULT);
        btnOpenTab.setOnAction(e -> {
            if (openInSqlTabHandler != null && !currentDdl.isBlank()) {
                openInSqlTabHandler.accept(currentDdl);
            }
        });

        toolbar.getChildren().addAll(btnCopy, btnOpenTab);

        monacoViewer = new EmbeddedMonaco("dep_inspector_" + System.currentTimeMillis(), "sql", "-- DDL Definition");
        monacoViewer.setReadOnly(true);
        editorContainer.getChildren().add(monacoViewer.node());
        VBox.setVgrow(editorContainer, Priority.ALWAYS);

        box.getChildren().addAll(toolbar, editorContainer);
        return box;
    }

    private VBox buildStepsContent() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));

        stepsScrollPane.setFitToWidth(true);
        stepsScrollPane.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(stepsScrollPane, Priority.ALWAYS);

        stepsContainer.setPadding(new Insets(4));

        btnOpenFullScript.setMaxWidth(Double.MAX_VALUE);
        btnOpenFullScript.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append("-- ====================================================================\n");
            sb.append("-- Safe Migration Script (Generated by Dependency Graph Impact Analysis)\n");
            sb.append("-- ====================================================================\n\n");
            for (javafx.scene.Node node : stepsContainer.getChildren()) {
                if (node instanceof VBox card && card.getUserData() instanceof SuggestedStep step) {
                    sb.append("-- Bước ").append(step.getStepNumber()).append(": ").append(step.getDescription()).append("\n");
                    sb.append(step.getSqlScript()).append("\n\n");
                }
            }
            if (openInSqlTabHandler != null && sb.length() > 0) {
                openInSqlTabHandler.accept(sb.toString());
            }
        });

        box.getChildren().addAll(stepsScrollPane, btnOpenFullScript);
        return box;
    }

    public void inspectNode(DependencyNode node, DependencyGraphModel model) {
        if (node == null) return;

        Platform.runLater(() -> {
            lblHeaderTitle.setText(node.getName());
            headerTooltip.setText(node.getName() + " (" + node.getDisplayName() + ")");
            lblHeaderTypeBadge.setText(node.getType().name());

            lblPropFullName.setText(node.getDisplayName());
            lblPropSchema.setText(node.getSchema());
            lblPropType.setText(node.getType().getDisplayName());
            lblPropRole.setText(node.isRoot() ? "Root Target (Gốc)" : (node.getLayer() < 0 ? "Dependency (Nguồn)" : "Dependent (Phụ thuộc)"));

            int inbound = model != null ? model.getIncomingEdges(node.getId()).size() : 0;
            int outbound = model != null ? model.getOutgoingEdges(node.getId()).size() : 0;
            lblPropInbound.setText(String.valueOf(inbound));
            lblPropOutbound.setText(String.valueOf(outbound));

            lblPropRowCount.setText(node.getRowCount() != null ? String.format("%,d dòng", node.getRowCount()) : "Không xác định");
            lblPropDescription.setText(node.getDescription() != null && !node.getDescription().isBlank() ? node.getDescription() : "Không có mô tả bổ sung.");

            // Update DDL
            currentDdl = node.getDefinitionSql() != null ? node.getDefinitionSql() : "-- Không có DDL cho đối tượng này";
            if (monacoViewer != null) {
                monacoViewer.setText(currentDdl);
            }

            // Update Suggested Steps
            updateStepsList(model != null ? model.getSuggestedSteps() : List.of());
        });
    }

    private void updateStepsList(List<SuggestedStep> steps) {
        stepsContainer.getChildren().clear();
        if (steps == null || steps.isEmpty()) {
            Label emptyLbl = new Label("Không có bước đề xuất nào.");
            emptyLbl.getStyleClass().add("node-subtitle");
            stepsContainer.getChildren().add(emptyLbl);
            return;
        }

        for (SuggestedStep step : steps) {
            VBox card = new VBox(4);
            card.getStyleClass().add("step-card");
            card.setUserData(step);

            HBox header = new HBox(6);
            header.setAlignment(Pos.CENTER_LEFT);

            Label numBadge = new Label("Bước " + step.getStepNumber());
            numBadge.getStyleClass().add("step-number-badge");

            Label title = new Label(step.getActionType() + ": " + step.getTargetObject());
            title.getStyleClass().add("step-action-title");

            header.getChildren().addAll(numBadge, title);

            Label desc = new Label(step.getDescription());
            desc.getStyleClass().add("node-subtitle");
            desc.setWrapText(true);

            Label sql = new Label(step.getSqlScript());
            sql.getStyleClass().add("step-sql-snippet");
            sql.setWrapText(true);

            card.getChildren().addAll(header, desc, sql);
            stepsContainer.getChildren().add(card);
        }
    }

    public void setOpenInSqlTabHandler(Consumer<String> handler) {
        this.openInSqlTabHandler = handler;
    }
}
