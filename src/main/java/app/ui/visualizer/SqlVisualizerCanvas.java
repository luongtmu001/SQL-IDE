package app.ui.visualizer;

import com.lltool.core.domain.visualizer.*;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Interactive JavaFX Canvas for SQL Visualizer & Query Flow (Phase 3 - Slice 6).
 * Renders pipeline stage columns, operator cards, bezier data stream arrows,
 * and handles zoom, pan, node selection, and PNG export.
 */
public class SqlVisualizerCanvas extends StackPane {

    private static final PseudoClass HIGHLIGHTED_PSEUDO = PseudoClass.getPseudoClass("highlighted");

    private final Group contentGroup = new Group();
    private final Group backgroundGroup = new Group();
    private final Group edgesGroup = new Group();
    private final Pane nodesPane = new Pane();

    private final Translate translate = new Translate(0, 0);
    private final Scale scale = new Scale(1.0, 1.0, 0, 0);

    private double dragStartX;
    private double dragStartY;

    private QueryFlowGraph currentGraph;
    private final ObjectProperty<QueryFlowNode> selectedNode = new SimpleObjectProperty<>();
    private final Map<String, VBox> nodeCardMap = new HashMap<>();
    private final Map<QueryFlowEdge, EdgeVisual> edgeVisualMap = new HashMap<>();

    private Consumer<QueryFlowNode> onNodeSelectedListener;

    public SqlVisualizerCanvas() {
        getStyleClass().add("qf-canvas-container");
        setAlignment(Pos.CENTER);

        // Assemble scene graph
        contentGroup.getTransforms().addAll(translate, scale);
        contentGroup.getChildren().addAll(backgroundGroup, edgesGroup, nodesPane);
        getChildren().add(contentGroup);

        initCanvasInteractions();
    }

    private void initCanvasInteractions() {
        // Pan canvas on background drag
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.MIDDLE) {
                if (e.getTarget() == this || e.getTarget() == contentGroup || e.getTarget() == nodesPane) {
                    dragStartX = e.getX() - translate.getX();
                    dragStartY = e.getY() - translate.getY();
                }
            }
        });

        setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.MIDDLE) {
                if (e.getTarget() == this || e.getTarget() == contentGroup || e.getTarget() == nodesPane) {
                    translate.setX(e.getX() - dragStartX);
                    translate.setY(e.getY() - dragStartY);
                }
            }
        });

        // Zoom with mouse wheel centered at cursor
        setOnScroll((ScrollEvent e) -> {
            double delta = e.getDeltaY();
            if (delta == 0) return;

            double factor = (delta > 0) ? 1.12 : (1.0 / 1.12);
            double newScale = scale.getX() * factor;

            if (newScale >= 0.25 && newScale <= 3.0) {
                double mouseX = e.getX();
                double mouseY = e.getY();

                double dx = (mouseX - translate.getX()) * (1 - factor);
                double dy = (mouseY - translate.getY()) * (1 - factor);

                translate.setX(translate.getX() + dx);
                translate.setY(translate.getY() + dy);

                scale.setX(newScale);
                scale.setY(newScale);
            }
            e.consume();
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

        // Layout pass to anchor edge curves to actual card positions
        nodesPane.layout();
        updateAllEdgePositions();

        // Auto select first node
        if (!graph.getNodes().isEmpty()) {
            QueryFlowNode first = graph.getNodes().values().iterator().next();
            selectNode(first);
        }
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

        topRow.getChildren().addAll(stepBadge, icon, titleLabel);

        // Subtitle / Expression body
        Label subtitleLabel = new Label(node.getSubtitle());
        subtitleLabel.getStyleClass().add("qf-node-subtitle");
        subtitleLabel.setMaxWidth(200);

        card.getChildren().addAll(topRow, subtitleLabel);

        // Bottom Metrics Badge (Estimated Cost / Row Count)
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

        // Position on Canvas
        card.setLayoutX(node.getX());
        card.setLayoutY(node.getY());

        // Interaction: Click to select
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selectNode(node);
                e.consume();
            }
        });

        // Draggable card
        final double[] dragDelta = new double[2];
        card.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragDelta[0] = card.getLayoutX() - e.getSceneX();
                dragDelta[1] = card.getLayoutY() - e.getSceneY();
                e.consume();
            }
        });

        card.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double newX = e.getSceneX() + dragDelta[0];
                double newY = e.getSceneY() + dragDelta[1];
                card.setLayoutX(newX);
                card.setLayoutY(newY);
                node.setX(newX);
                node.setY(newY);
                updateAllEdgePositions();
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
            label.setStyle("-fx-font-size: 0.72em; -fx-text-fill: -color-fg-muted; -fx-background-color: -ide-editor-bg; -fx-padding: 1 4; -fx-background-radius: 3;");
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

            double srcW = srcCard.getBoundsInParent().getWidth();
            double srcH = srcCard.getBoundsInParent().getHeight();
            double tgtH = tgtCard.getBoundsInParent().getHeight();

            double startX = srcCard.getLayoutX() + (srcW > 0 ? srcW : 200);
            double startY = srcCard.getLayoutY() + (srcH > 0 ? srcH / 2.0 : 40);

            double endX = tgtCard.getLayoutX();
            double endY = tgtCard.getLayoutY() + (tgtH > 0 ? tgtH / 2.0 : 40);

            visual.path.setStartX(startX);
            visual.path.setStartY(startY);
            visual.path.setEndX(endX);
            visual.path.setEndY(endY);

            double dx = Math.abs(endX - startX) * 0.5;
            visual.path.setControlX1(startX + dx);
            visual.path.setControlY1(startY);
            visual.path.setControlX2(endX - dx);
            visual.path.setControlY2(endY);

            // Update arrow at target
            double arrowSize = 6.0;
            visual.arrowHead.getPoints().setAll(
                    endX, endY,
                    endX - arrowSize * 1.5, endY - arrowSize,
                    endX - arrowSize * 1.5, endY + arrowSize
            );

            // Position label at center of curve
            if (visual.label != null) {
                double midX = (startX + endX) / 2.0 - 20;
                double midY = (startY + endY) / 2.0 - 10;
                visual.label.setLayoutX(midX);
                visual.label.setLayoutY(midY);
            }
        }
    }

    public void selectNode(QueryFlowNode node) {
        if (node == null) return;
        selectedNode.set(node);

        // Update card styles
        for (Map.Entry<String, VBox> entry : nodeCardMap.entrySet()) {
            VBox card = entry.getValue();
            if (entry.getKey().equals(node.getId())) {
                if (!card.getStyleClass().contains("selected")) {
                    card.getStyleClass().add("selected");
                }
            } else {
                card.getStyleClass().remove("selected");
            }
        }

        // Highlight connected stream edges
        for (Map.Entry<QueryFlowEdge, EdgeVisual> entry : edgeVisualMap.entrySet()) {
            QueryFlowEdge edge = entry.getKey();
            EdgeVisual visual = entry.getValue();

            boolean isConnected = edge.getFromNodeId().equals(node.getId()) || edge.getToNodeId().equals(node.getId());
            visual.path.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, isConnected);
            visual.arrowHead.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, isConnected);
        }

        if (onNodeSelectedListener != null) {
            onNodeSelectedListener.accept(node);
        }
    }

    public void zoomIn() {
        scale.setX(Math.min(3.0, scale.getX() * 1.2));
        scale.setY(Math.min(3.0, scale.getY() * 1.2));
    }

    public void zoomOut() {
        scale.setX(Math.max(0.25, scale.getX() / 1.2));
        scale.setY(Math.max(0.25, scale.getY() / 1.2));
    }

    public void resetZoom() {
        scale.setX(1.0);
        scale.setY(1.0);
        translate.setX(0);
        translate.setY(0);
    }

    public void fitToScreen() {
        if (nodesPane.getChildren().isEmpty()) {
            resetZoom();
            return;
        }

        Bounds bounds = contentGroup.getBoundsInLocal();
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0 || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            resetZoom();
            return;
        }

        double factorX = (w - 60) / bounds.getWidth();
        double factorY = (h - 60) / bounds.getHeight();
        double fitScale = Math.min(Math.min(factorX, factorY), 1.2);

        scale.setX(fitScale);
        scale.setY(fitScale);

        translate.setX((w - bounds.getWidth() * fitScale) / 2.0 - bounds.getMinX() * fitScale);
        translate.setY((h - bounds.getHeight() * fitScale) / 2.0 - bounds.getMinY() * fitScale);
    }

    public void exportToPng(File file) throws IOException {
        SnapshotParameters params = new SnapshotParameters();
        Paint bgPaint = getBackground() != null && !getBackground().getFills().isEmpty()
                ? getBackground().getFills().get(0).getFill()
                : Color.TRANSPARENT;
        params.setFill(bgPaint);

        WritableImage fxImage = contentGroup.snapshot(params, null);

        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }

        ImageIO.write(bufferedImage, "png", file);
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
