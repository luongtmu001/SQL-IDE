package app.ui.visualizer;

import app.ui.components.ZoomableGraphPane;
import com.lltool.core.domain.visualizer.*;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Interactive JavaFX Canvas for Visual SQL Execution / Query Flow.
 * Inherits zoom, pan, dragging, fit, and export from ZoomableGraphPane.
 */
public class SqlVisualizerCanvas extends ZoomableGraphPane {

    private static final PseudoClass HIGHLIGHTED_PSEUDO = PseudoClass.getPseudoClass("highlighted");

    private QueryFlowGraph currentGraph;
    private final ObjectProperty<QueryFlowNode> selectedNode = new SimpleObjectProperty<>();
    private final Map<String, VBox> nodeCardMap = new HashMap<>();
    private final Map<QueryFlowEdge, EdgeVisual> edgeVisualMap = new HashMap<>();

    private Consumer<QueryFlowNode> onNodeSelectedListener;

    public SqlVisualizerCanvas() {
        getStyleClass().add("qf-canvas-container");
        setOnBackgroundClicked(() -> selectNode(null));

        scale.xProperty().addListener((obs, oldV, newV) -> {
            boolean showLabels = newV.doubleValue() >= 0.7;
            for (EdgeVisual ev : edgeVisualMap.values()) {
                if (ev.label != null) {
                    ev.label.setVisible(showLabels && ev.edge.getLabel() != null && !ev.edge.getLabel().isBlank());
                }
            }
        });
    }

    public void setGraph(QueryFlowGraph graph) {
        this.currentGraph = graph;
        clearCanvas();

        if (graph == null || graph.getNodeCount() == 0) {
            return;
        }

        // 1. Render Stage Column Headers and Vertical Guides
        renderStageGuides(graph);

        // 2. Render Node Cards
        for (QueryFlowNode node : graph.getNodes().values()) {
            VBox card = createNodeCard(node);
            nodeCardMap.put(node.getId(), card);
            nodesPane.getChildren().add(card);
        }

        // 3. Render Connecting Stream Edges
        for (QueryFlowEdge edge : graph.getEdges()) {
            EdgeVisual visual = createEdgeVisual(edge);
            if (visual != null) {
                edgeVisualMap.put(edge, visual);
                edgesGroup.getChildren().addAll(visual.path, visual.arrowHead);
                if (visual.label != null) {
                    edgesGroup.getChildren().add(visual.label);
                }
            }
        }

        nodesPane.layout();
        updateAllEdgePositions();

        // Auto select first node
        if (!graph.getNodes().isEmpty()) {
            QueryFlowNode first = graph.getNodes().values().iterator().next();
            selectNode(first);
        }

        fitToScreen();
    }

    private void renderStageGuides(QueryFlowGraph graph) {
        List<String> stages = graph.getStageTitles();
        double stageWidth = 260.0;
        double originX = 80.0;

        for (int i = 0; i < stages.size(); i++) {
            double stageX = originX + (i * stageWidth);

            // Stage pill header
            Label stageHeader = new Label(stages.get(i));
            stageHeader.getStyleClass().add("qf-stage-pill");
            stageHeader.setLayoutX(stageX);
            stageHeader.setLayoutY(50.0);
            backgroundGroup.getChildren().add(stageHeader);

            // Vertical divider line between stages
            if (i > 0) {
                Line divider = new Line(stageX - 30, 40, stageX - 30, 520);
                divider.getStyleClass().add("stage-divider");
                divider.getStrokeDashArray().addAll(4.0, 4.0);
                backgroundGroup.getChildren().add(divider);
            }
        }
    }

    private VBox createNodeCard(QueryFlowNode node) {
        VBox card = new VBox(6);
        card.getStyleClass().add("qf-node-card");
        card.setMinWidth(200);
        card.setMaxWidth(280);

        // Styling class according to node type
        switch (node.getType()) {
            case SOURCE_TABLE, JOIN_TABLE -> card.getStyleClass().add("node-source");
            case JOIN -> card.getStyleClass().add("node-join");
            case FILTER -> card.getStyleClass().add("node-filter");
            case AGGREGATE -> card.getStyleClass().add("node-aggregate");
            case SORT -> card.getStyleClass().add("node-sort");
            case OUTPUT -> card.getStyleClass().add("node-output");
            default -> card.getStyleClass().add("node-source");
        }

        // Top Row: Step badge + Icon + Operator Title
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label stepBadge = new Label(String.valueOf(node.getStepIndex()));
        stepBadge.getStyleClass().add("qf-step-badge");

        FontIcon icon = new FontIcon(getNodeIconLiteral(node.getType()));
        icon.setIconSize(15);
        icon.getStyleClass().add("qf-node-icon");

        Label titleLabel = new Label(node.getTitle());
        titleLabel.getStyleClass().add("qf-node-title");
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setMaxWidth(200);
        titleLabel.setTooltip(new Tooltip(node.getTitle()));

        topRow.getChildren().addAll(stepBadge, icon, titleLabel);

        // Subtitle / Expression body
        Label subtitleLabel = new Label(node.getSubtitle());
        subtitleLabel.getStyleClass().add("qf-node-subtitle");
        subtitleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        subtitleLabel.setMaxWidth(240);
        subtitleLabel.setTooltip(new Tooltip(node.getSubtitle()));

        card.getChildren().addAll(topRow, subtitleLabel);

        // Bottom Metrics Badge
        if (node.getEstimatedCost() != null || node.getRowCount() != null) {
            HBox metaRow = new HBox(6);
            metaRow.setAlignment(Pos.CENTER_LEFT);

            if (node.getEstimatedCost() != null) {
                Label costBadge = new Label("Chi phí: " + node.getEstimatedCost());
                costBadge.getStyleClass().add("qf-cost-badge");
                metaRow.getChildren().add(costBadge);
            }

            if (node.getRowCount() != null) {
                Label rowLabel = new Label(String.format("%,d dòng", node.getRowCount()));
                rowLabel.setStyle("-fx-font-size: 0.72em; -fx-text-fill: -color-fg-muted;");
                metaRow.getChildren().add(rowLabel);
            }

            card.getChildren().add(metaRow);
        }

        card.setLayoutX(node.getX());
        card.setLayoutY(node.getY());

        // Node dragging via ZoomableGraphPane
        makeNodeDraggable(card, () -> {
            node.setX(card.getLayoutX());
            node.setY(card.getLayoutY());
            updateAllEdgePositions();
        });

        // Click to select
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selectNode(node);
                e.consume();
            }
        });

        return card;
    }

    private String getNodeIconLiteral(QueryNodeType type) {
        return switch (type) {
            case SOURCE_TABLE, JOIN_TABLE -> "fth-database";
            case JOIN -> "fth-git-merge";
            case FILTER -> "fth-filter";
            case AGGREGATE -> "fth-pie-chart";
            case SORT -> "fth-bar-chart-2";
            case OUTPUT -> "fth-check-circle";
            case CTE, SUBQUERY -> "fth-code";
            default -> "fth-box";
        };
    }

    private EdgeVisual createEdgeVisual(QueryFlowEdge edge) {
        VBox sourceCard = nodeCardMap.get(edge.getFromNodeId());
        VBox targetCard = nodeCardMap.get(edge.getToNodeId());
        if (sourceCard == null || targetCard == null) return null;

        CubicCurve curve = new CubicCurve();
        curve.getStyleClass().add("graph-edge");

        Polygon arrow = new Polygon();
        arrow.getStyleClass().add("graph-edge-arrow");

        Label label = null;
        if (edge.getLabel() != null && !edge.getLabel().isBlank()) {
            label = new Label(edge.getLabel());
            label.getStyleClass().add("edge-label");
        }

        return new EdgeVisual(edge, curve, arrow, label);
    }

    private void updateAllEdgePositions() {
        for (Map.Entry<QueryFlowEdge, EdgeVisual> entry : edgeVisualMap.entrySet()) {
            QueryFlowEdge edge = entry.getKey();
            EdgeVisual visual = entry.getValue();

            VBox srcCard = nodeCardMap.get(edge.getFromNodeId());
            VBox tgtCard = nodeCardMap.get(edge.getToNodeId());
            if (srcCard == null || tgtCard == null) continue;

            double srcW = srcCard.getWidth() > 0 ? srcCard.getWidth() : (srcCard.getBoundsInParent().getWidth() > 0 ? srcCard.getBoundsInParent().getWidth() : 200);
            double srcH = srcCard.getHeight() > 0 ? srcCard.getHeight() : (srcCard.getBoundsInParent().getHeight() > 0 ? srcCard.getBoundsInParent().getHeight() : 80);
            double tgtH = tgtCard.getHeight() > 0 ? tgtCard.getHeight() : (tgtCard.getBoundsInParent().getHeight() > 0 ? tgtCard.getBoundsInParent().getHeight() : 80);

            double startX = srcCard.getLayoutX() + srcW;
            double startY = srcCard.getLayoutY() + (srcH / 2.0);

            double endX = tgtCard.getLayoutX();
            double endY = tgtCard.getLayoutY() + (tgtH / 2.0);

            visual.path.setStartX(startX);
            visual.path.setStartY(startY);
            visual.path.setEndX(endX);
            visual.path.setEndY(endY);

            double dx = Math.abs(endX - startX) * 0.5;
            visual.path.setControlX1(startX + dx);
            visual.path.setControlY1(startY);
            visual.path.setControlX2(endX - dx);
            visual.path.setControlY2(endY);

            // Arrow at target
            double arrowSize = 6.0;
            visual.arrowHead.getPoints().setAll(
                    endX, endY,
                    endX - arrowSize * 1.5, endY - arrowSize,
                    endX - arrowSize * 1.5, endY + arrowSize
            );

            // Position label at t = 0.5 of cubic curve
            if (visual.label != null) {
                double midX = 0.125 * startX + 0.375 * (startX + dx) + 0.375 * (endX - dx) + 0.125 * endX;
                double midY = 0.125 * startY + 0.375 * startY + 0.375 * endY + 0.125 * endY;
                visual.label.setLayoutX(midX - 20);
                visual.label.setLayoutY(midY - 10);
            }
        }
    }

    public void selectNode(QueryFlowNode node) {
        selectedNode.set(node);

        for (Map.Entry<String, VBox> entry : nodeCardMap.entrySet()) {
            VBox card = entry.getValue();
            if (node != null && entry.getKey().equals(node.getId())) {
                if (!card.getStyleClass().contains("selected")) {
                    card.getStyleClass().add("selected");
                }
            } else {
                card.getStyleClass().remove("selected");
            }
        }

        for (Map.Entry<QueryFlowEdge, EdgeVisual> entry : edgeVisualMap.entrySet()) {
            QueryFlowEdge edge = entry.getKey();
            EdgeVisual visual = entry.getValue();

            boolean isConnected = node != null && (edge.getFromNodeId().equals(node.getId()) || edge.getToNodeId().equals(node.getId()));
            visual.path.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, isConnected);
            visual.arrowHead.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, isConnected);
        }

        if (onNodeSelectedListener != null) {
            onNodeSelectedListener.accept(node);
        }
    }

    @Override
    public boolean exportToPng(File file) {
        return super.exportToPng(file);
    }

    public void clearCanvas() {
        backgroundGroup.getChildren().clear();
        edgesGroup.getChildren().clear();
        nodesPane.getChildren().clear();
        nodeCardMap.clear();
        edgeVisualMap.clear();
        selectedNode.set(null);
    }

    public void setOnNodeSelectedListener(Consumer<QueryFlowNode> listener) {
        this.onNodeSelectedListener = listener;
    }

    public QueryFlowNode getSelectedNode() {
        return selectedNode.get();
    }

    public Map<String, VBox> getNodeCardMap() {
        return Collections.unmodifiableMap(nodeCardMap);
    }

    private static class EdgeVisual {
        final QueryFlowEdge edge;
        final CubicCurve path;
        final Polygon arrowHead;
        final Label label;

        EdgeVisual(QueryFlowEdge edge, CubicCurve path, Polygon arrowHead, Label label) {
            this.edge = edge;
            this.path = path;
            this.arrowHead = arrowHead;
            this.label = label;
        }
    }
}
