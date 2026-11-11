package app.ui.dependency;

import app.ui.components.ZoomableGraphPane;
import com.lltool.core.domain.dependency.*;
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
import javafx.scene.shape.Polygon;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.Consumer;

/**
 * Interactive JavaFX Canvas for Dependency Graphs.
 * Inherits zoom, pan, dragging, fit, and export from ZoomableGraphPane.
 */
public class DependencyGraphCanvas extends ZoomableGraphPane {

    private static final PseudoClass HIGHLIGHTED_PSEUDO = PseudoClass.getPseudoClass("highlighted");

    private DependencyGraphModel currentModel;
    private final ObjectProperty<DependencyNode> selectedNode = new SimpleObjectProperty<>();
    private final Map<String, VBox> nodeCardMap = new HashMap<>();
    private final Map<DependencyEdge, EdgeVisual> edgeVisualMap = new HashMap<>();

    private Consumer<DependencyNode> onNodeSelectedListener;

    public DependencyGraphCanvas() {
        getStyleClass().add("graph-canvas-container");
        setOnBackgroundClicked(() -> selectNode(null));

        scale.xProperty().addListener((obs, oldV, newV) -> {
            boolean showLabels = newV.doubleValue() >= 0.7;
            for (EdgeVisual ev : edgeVisualMap.values()) {
                ev.labelNode.setVisible(showLabels && ev.edge.getLabel() != null && !ev.edge.getLabel().isBlank());
            }
        });
    }

    public void setModel(DependencyGraphModel model) {
        this.currentModel = model;
        clearCanvas();

        if (model == null || model.getNodeCount() == 0) {
            return;
        }

        // 1. Build node visual cards
        for (DependencyNode node : model.getNodes().values()) {
            VBox card = createNodeCard(node);
            nodeCardMap.put(node.getId(), card);
            nodesPane.getChildren().add(card);

            card.setLayoutX(node.getX());
            card.setLayoutY(node.getY());
        }

        // 2. Build edge visuals with distributed anchor indexing
        Map<String, List<DependencyEdge>> outgoing = new HashMap<>();
        Map<String, List<DependencyEdge>> incoming = new HashMap<>();
        for (DependencyEdge edge : model.getEdges()) {
            outgoing.computeIfAbsent(edge.getSourceId(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.getTargetId(), k -> new ArrayList<>()).add(edge);
        }

        for (DependencyEdge edge : model.getEdges()) {
            VBox srcCard = nodeCardMap.get(edge.getSourceId());
            VBox tgtCard = nodeCardMap.get(edge.getTargetId());
            if (srcCard != null && tgtCard != null) {
                int outIndex = outgoing.get(edge.getSourceId()).indexOf(edge);
                int outTotal = outgoing.get(edge.getSourceId()).size();
                int inIndex = incoming.get(edge.getTargetId()).indexOf(edge);
                int inTotal = incoming.get(edge.getTargetId()).size();

                EdgeVisual visual = new EdgeVisual(edge, srcCard, tgtCard, outIndex, outTotal, inIndex, inTotal);
                edgeVisualMap.put(edge, visual);
                edgesGroup.getChildren().addAll(visual.path, visual.arrowHead, visual.labelNode);
                visual.update();
            }
        }

        // Auto select root and fit
        if (model.getRootNode() != null) {
            selectNode(model.getRootNode());
        }
        fitToScreen();
    }

    private void clearCanvas() {
        nodesPane.getChildren().clear();
        edgesGroup.getChildren().clear();
        nodeCardMap.clear();
        edgeVisualMap.clear();
        selectedNode.set(null);
    }

    private VBox createNodeCard(DependencyNode node) {
        VBox card = new VBox(4);
        card.getStyleClass().add("graph-node-card");
        card.setMinWidth(app.ui.components.UiMetrics.em(15.38));
        card.setMaxWidth(app.ui.components.UiMetrics.em(21.54));

        if (node.isRoot()) {
            card.getStyleClass().add("root-node");
        }

        // Header: Badge + Metadata tag
        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);

        Label badge = new Label(node.isRoot() ? "Gốc" : node.getType().name());
        badge.getStyleClass().add("node-badge");
        if (node.isRoot()) {
            badge.getStyleClass().add("node-badge-root");
        } else {
            switch (node.getType()) {
                case TABLE -> badge.getStyleClass().add("node-badge-table");
                case VIEW, MATERIALIZED_VIEW -> badge.getStyleClass().add("node-badge-view");
                case PROCEDURE, FUNCTION -> badge.getStyleClass().add("node-badge-proc");
                case TRIGGER -> badge.getStyleClass().add("node-badge-trigger");
                default -> badge.getStyleClass().add("node-badge-table");
            }
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tag = new Label();
        tag.getStyleClass().add("node-subtitle");
        if (node.getRowCount() != null) {
            tag.setText(String.format("%,d rows", node.getRowCount()));
        } else if (node.isRoot()) {
            int dependents = currentModel != null ? currentModel.getOutgoingEdges(node.getId()).size() : 0;
            tag.setText(dependents + " liên kết");
        }
        header.getChildren().addAll(badge, spacer, tag);

        // Body: Icon + Title (with ellipsis and tooltip)
        HBox body = new HBox(6);
        body.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = app.ui.components.Icons.of(node.getType().getIconLiteral(), app.ui.components.IconScale.MD);
        if (node.isRoot()) icon.getStyleClass().add("icon-accent");

        Label title = new Label(node.getName());
        title.getStyleClass().add("node-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMaxWidth(app.ui.components.UiMetrics.em(16.92));
        title.setTooltip(new Tooltip(node.getName()));
        body.getChildren().addAll(icon, title);

        // Subtitle / Schema
        Label schemaLbl = new Label(node.isColumn() ? "Cột: " + node.getColumnName() : "Schema: " + node.getSchema());
        schemaLbl.getStyleClass().add("node-subtitle");
        schemaLbl.setTextOverrun(OverrunStyle.ELLIPSIS);
        schemaLbl.setMaxWidth(app.ui.components.UiMetrics.em(18.46));

        card.getChildren().addAll(header, body, schemaLbl);

        // Drag node behavior via ZoomableGraphPane
        makeNodeDraggable(card, () -> {
            node.setX(card.getLayoutX());
            node.setY(card.getLayoutY());
            updateConnectedEdges(node.getId());
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

    public void selectNode(DependencyNode node) {
        selectedNode.set(node);

        // Reset highlight states
        for (VBox c : nodeCardMap.values()) {
            c.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, false);
        }
        for (EdgeVisual ev : edgeVisualMap.values()) {
            ev.setHighlighted(false);
        }

        if (node == null) {
            if (onNodeSelectedListener != null) {
                onNodeSelectedListener.accept(null);
            }
            return;
        }

        // Highlight selected node card
        VBox selectedCard = nodeCardMap.get(node.getId());
        if (selectedCard != null) {
            selectedCard.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, true);
        }

        // Highlight incident edges and neighbor nodes
        if (currentModel != null) {
            Set<String> connectedNodeIds = new HashSet<>();
            connectedNodeIds.add(node.getId());

            for (Map.Entry<DependencyEdge, EdgeVisual> entry : edgeVisualMap.entrySet()) {
                DependencyEdge edge = entry.getKey();
                if (edge.getSourceId().equals(node.getId()) || edge.getTargetId().equals(node.getId())) {
                    entry.getValue().setHighlighted(true);
                    connectedNodeIds.add(edge.getSourceId());
                    connectedNodeIds.add(edge.getTargetId());
                }
            }

            for (String nId : connectedNodeIds) {
                VBox card = nodeCardMap.get(nId);
                if (card != null) {
                    card.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, true);
                }
            }
        }

        if (onNodeSelectedListener != null) {
            onNodeSelectedListener.accept(node);
        }
    }

    private void updateConnectedEdges(String nodeId) {
        for (Map.Entry<DependencyEdge, EdgeVisual> entry : edgeVisualMap.entrySet()) {
            DependencyEdge edge = entry.getKey();
            if (edge.getSourceId().equals(nodeId) || edge.getTargetId().equals(nodeId)) {
                entry.getValue().update();
            }
        }
    }

    public void setOnNodeSelected(Consumer<DependencyNode> listener) {
        this.onNodeSelectedListener = listener;
    }

    // Helper visual class for directed curved edge with distributed anchors
    private static class EdgeVisual {
        final DependencyEdge edge;
        final VBox sourceCard;
        final VBox targetCard;
        final int outIndex, outTotal;
        final int inIndex, inTotal;
        final CubicCurve path = new CubicCurve();
        final Polygon arrowHead = new Polygon();
        final Label labelNode = new Label();

        EdgeVisual(DependencyEdge edge, VBox sourceCard, VBox targetCard,
                   int outIndex, int outTotal, int inIndex, int inTotal) {
            this.edge = edge;
            this.sourceCard = sourceCard;
            this.targetCard = targetCard;
            this.outIndex = outIndex;
            this.outTotal = Math.max(1, outTotal);
            this.inIndex = inIndex;
            this.inTotal = Math.max(1, inTotal);

            path.getStyleClass().add("graph-edge");

            arrowHead.getPoints().addAll(
                    0.0, 0.0,
                    -10.0, -5.0,
                    -10.0, 5.0
            );
            arrowHead.getStyleClass().add("graph-edge-arrow");

            labelNode.setText(edge.getLabel());
            labelNode.getStyleClass().add("edge-label");
        }

        void setHighlighted(boolean highlighted) {
            path.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, highlighted);
            arrowHead.pseudoClassStateChanged(HIGHLIGHTED_PSEUDO, highlighted);
        }

        void update() {
            double srcCardW = sourceCard.getWidth() > 0 ? sourceCard.getWidth() : 200;
            double srcCardH = sourceCard.getHeight() > 0 ? sourceCard.getHeight() : 80;
            double tgtCardH = targetCard.getHeight() > 0 ? targetCard.getHeight() : 80;

            // Distributed anchor points along source right and target left
            double srcAnchorY = (srcCardH * (outIndex + 1.0)) / (outTotal + 1.0);
            double tgtAnchorY = (tgtCardH * (inIndex + 1.0)) / (inTotal + 1.0);

            double srcX = sourceCard.getLayoutX() + srcCardW;
            double srcY = sourceCard.getLayoutY() + srcAnchorY;

            double tgtX = targetCard.getLayoutX();
            double tgtY = targetCard.getLayoutY() + tgtAnchorY;

            path.setStartX(srcX);
            path.setStartY(srcY);

            double dx = Math.abs(tgtX - srcX) * 0.5;
            path.setControlX1(srcX + dx);
            path.setControlY1(srcY);
            path.setControlX2(tgtX - dx);
            path.setControlY2(tgtY);

            path.setEndX(tgtX);
            path.setEndY(tgtY);

            // Arrow at target
            arrowHead.setLayoutX(tgtX);
            arrowHead.setLayoutY(tgtY);

            double angle = Math.toDegrees(Math.atan2(tgtY - path.getControlY2(), tgtX - path.getControlX2()));
            arrowHead.setRotate(angle);

            // Midpoint at t = 0.5 along cubic bezier
            // B(0.5) = 0.125 * P0 + 0.375 * P1 + 0.375 * P2 + 0.125 * P3
            double midX = 0.125 * srcX + 0.375 * (srcX + dx) + 0.375 * (tgtX - dx) + 0.125 * tgtX;
            double midY = 0.125 * srcY + 0.375 * srcY + 0.375 * tgtY + 0.125 * tgtY;

            labelNode.setLayoutX(midX - 25);
            labelNode.setLayoutY(midY - 10);
        }
    }
}
