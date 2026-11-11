package app.ui.dependency;

import com.lltool.core.domain.dependency.*;
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
 * Interactive JavaFX Canvas for Dependency Graphs.
 * Supports zoom, pan, dragging nodes, bezier edge routing, arrow markers, and PNG export.
 */
public class DependencyGraphCanvas extends StackPane {

    private static final PseudoClass HIGHLIGHTED_PSEUDO = PseudoClass.getPseudoClass("highlighted");

    private final Group contentGroup = new Group();
    private final Group edgesGroup = new Group();
    private final Pane nodesPane = new Pane();

    private final Translate translate = new Translate(0, 0);
    private final Scale scale = new Scale(1.0, 1.0, 0, 0);

    private double dragStartX;
    private double dragStartY;

    private DependencyGraphModel currentModel;
    private final ObjectProperty<DependencyNode> selectedNode = new SimpleObjectProperty<>();
    private final Map<String, VBox> nodeCardMap = new HashMap<>();
    private final Map<DependencyEdge, EdgeVisual> edgeVisualMap = new HashMap<>();

    private Consumer<DependencyNode> onNodeSelectedListener;

    public DependencyGraphCanvas() {
        getStyleClass().add("graph-canvas-container");
        setAlignment(Pos.CENTER);

        // Transform chain
        contentGroup.getTransforms().addAll(translate, scale);
        contentGroup.getChildren().addAll(edgesGroup, nodesPane);
        getChildren().add(contentGroup);

        initCanvasInteractions();
    }

    private void initCanvasInteractions() {
        // Pan on background drag
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

                // Adjust translate so mouse point stays fixed
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

            // Set initial position
            card.setLayoutX(node.getX());
            card.setLayoutY(node.getY());
        }

        // 2. Build edge visuals
        for (DependencyEdge edge : model.getEdges()) {
            VBox srcCard = nodeCardMap.get(edge.getSourceId());
            VBox tgtCard = nodeCardMap.get(edge.getTargetId());
            if (srcCard != null && tgtCard != null) {
                EdgeVisual visual = new EdgeVisual(edge, srcCard, tgtCard);
                edgeVisualMap.put(edge, visual);
                edgesGroup.getChildren().addAll(visual.path, visual.arrowHead, visual.labelNode);
                visual.update();
            }
        }

        // Select root by default
        if (model.getRootNode() != null) {
            selectNode(model.getRootNode());
            fitToScreen();
        }
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

        // Body: Icon + Title
        HBox body = new HBox(6);
        body.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(node.getType().getIconLiteral());
        icon.setIconSize(13);
        if (node.isRoot()) icon.getStyleClass().add("icon-accent");

        Label title = new Label(node.getName());
        title.getStyleClass().add("node-title");
        body.getChildren().addAll(icon, title);

        // Subtitle / Schema
        Label schemaLbl = new Label(node.isColumn() ? "Cột: " + node.getColumnName() : "Schema: " + node.getSchema());
        schemaLbl.getStyleClass().add("node-subtitle");

        card.getChildren().addAll(header, body, schemaLbl);

        // Drag node behavior
        final Delta dragDelta = new Delta();
        card.setOnMousePressed(e -> {
            dragDelta.x = card.getLayoutX() - e.getSceneX();
            dragDelta.y = card.getLayoutY() - e.getSceneY();
            selectNode(node);
            card.toFront();
            e.consume();
        });

        card.setOnMouseDragged(e -> {
            double newX = e.getSceneX() + dragDelta.x;
            double newY = e.getSceneY() + dragDelta.y;
            card.setLayoutX(newX);
            card.setLayoutY(newY);
            node.setX(newX);
            node.setY(newY);
            updateConnectedEdges(node.getId());
            e.consume();
        });

        return card;
    }

    public void selectNode(DependencyNode node) {
        if (node == null) return;
        selectedNode.set(node);

        // Highlight card
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

        // Highlight connected edges
        for (Map.Entry<DependencyEdge, EdgeVisual> entry : edgeVisualMap.entrySet()) {
            DependencyEdge edge = entry.getKey();
            EdgeVisual visual = entry.getValue();
            boolean connected = edge.getSourceId().equals(node.getId()) || edge.getTargetId().equals(node.getId());
            visual.setHighlighted(connected);
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

    public void zoomIn() {
        double newScale = Math.min(3.0, scale.getX() * 1.2);
        scale.setX(newScale);
        scale.setY(newScale);
    }

    public void zoomOut() {
        double newScale = Math.max(0.25, scale.getX() / 1.2);
        scale.setX(newScale);
        scale.setY(newScale);
    }

    public void resetZoom() {
        scale.setX(1.0);
        scale.setY(1.0);
        translate.setX(0);
        translate.setY(0);
    }

    public void fitToScreen() {
        if (nodeCardMap.isEmpty()) return;

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        for (VBox card : nodeCardMap.values()) {
            double x = card.getLayoutX();
            double y = card.getLayoutY();
            double w = card.getWidth() > 0 ? card.getWidth() : 200;
            double h = card.getHeight() > 0 ? card.getHeight() : 80;

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + w);
            maxY = Math.max(maxY, y + h);
        }

        double graphW = maxX - minX + 160;
        double graphH = maxY - minY + 160;
        double viewW = getWidth() > 0 ? getWidth() : 800;
        double viewH = getHeight() > 0 ? getHeight() : 600;

        double scaleFactor = Math.min(1.2, Math.min(viewW / graphW, viewH / graphH));
        scale.setX(scaleFactor);
        scale.setY(scaleFactor);

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        translate.setX((viewW / 2.0) - (centerX * scaleFactor));
        translate.setY((viewH / 2.0) - (centerY * scaleFactor));
    }

    public boolean exportToPng(File targetFile) {
        try {
            SnapshotParameters params = new SnapshotParameters();
            Paint bgPaint = getBackground() != null && !getBackground().getFills().isEmpty()
                    ? getBackground().getFills().get(0).getFill()
                    : Color.TRANSPARENT;
            params.setFill(bgPaint);
            WritableImage fxImage = contentGroup.snapshot(params, null);
            int width = (int) fxImage.getWidth();
            int height = (int) fxImage.getHeight();
            if (width <= 0 || height <= 0) return false;

            BufferedImage bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            PixelReader reader = fxImage.getPixelReader();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    bImage.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            return ImageIO.write(bImage, "png", targetFile);
        } catch (Exception e) {
            return false;
        }
    }

    // Helper visual class for directed curved edge
    private static class EdgeVisual {
        final DependencyEdge edge;
        final VBox sourceCard;
        final VBox targetCard;
        final CubicCurve path = new CubicCurve();
        final Polygon arrowHead = new Polygon();
        final Label labelNode = new Label();

        EdgeVisual(DependencyEdge edge, VBox sourceCard, VBox targetCard) {
            this.edge = edge;
            this.sourceCard = sourceCard;
            this.targetCard = targetCard;

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
            double srcX = sourceCard.getLayoutX() + (sourceCard.getWidth() > 0 ? sourceCard.getWidth() : 200);
            double srcY = sourceCard.getLayoutY() + ((sourceCard.getHeight() > 0 ? sourceCard.getHeight() : 80) / 2.0);

            double tgtX = targetCard.getLayoutX();
            double tgtY = targetCard.getLayoutY() + ((targetCard.getHeight() > 0 ? targetCard.getHeight() : 80) / 2.0);

            path.setStartX(srcX);
            path.setStartY(srcY);

            double dx = Math.abs(tgtX - srcX) * 0.5;
            path.setControlX1(srcX + dx);
            path.setControlY1(srcY);
            path.setControlX2(tgtX - dx);
            path.setControlY2(tgtY);

            path.setEndX(tgtX);
            path.setEndY(tgtY);

            // Position and rotate arrow at target
            arrowHead.setLayoutX(tgtX);
            arrowHead.setLayoutY(tgtY);

            double angle = Math.toDegrees(Math.atan2(tgtY - path.getControlY2(), tgtX - path.getControlX2()));
            arrowHead.setRotate(angle);

            // Position label at midpoint
            double midX = (srcX + tgtX) / 2.0;
            double midY = (srcY + tgtY) / 2.0 - 12;
            labelNode.setLayoutX(midX - 30);
            labelNode.setLayoutY(midY);
        }
    }

    private static class Delta {
        double x, y;
    }
}
