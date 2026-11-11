package app.ui.components;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Reusable interactive graph canvas with pan, zoom, auto-fit, and node dragging.
 * Handles viewport clipping, unmanaged content layout, centered zoom, and scale-aware dragging.
 */
public class ZoomableGraphPane extends Pane {

    public record FitResult(double scale, double translateX, double translateY) {}

    protected final Group contentGroup = new Group();
    protected final Group backgroundGroup = new Group();
    protected final Group edgesGroup = new Group();
    protected final Pane nodesPane = new Pane();

    protected final Translate translate = new Translate(0, 0);
    protected final Scale scale = new Scale(1.0, 1.0, 0, 0);

    private final DoubleProperty zoomProperty = new SimpleDoubleProperty(1.0);
    private final StringProperty zoomPercentProperty = new SimpleStringProperty("100%");

    private double dragStartX;
    private double dragStartY;
    private boolean isPanning = false;

    private Runnable onBackgroundClicked;

    public ZoomableGraphPane() {
        getStyleClass().add("zoomable-graph-pane");

        // 1. Content is positioned purely by transform
        contentGroup.setManaged(false);
        contentGroup.getTransforms().addAll(translate, scale);
        contentGroup.getChildren().addAll(backgroundGroup, edgesGroup, nodesPane);
        getChildren().add(contentGroup);

        // 2. Viewport Clip
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        setCursor(Cursor.DEFAULT);
        initInteractions();
    }

    private void initInteractions() {
        setOnMouseEntered(e -> {
            if (!isPanning && isBackgroundTarget(e.getTarget())) {
                setCursor(Cursor.OPEN_HAND);
            }
        });

        setOnMouseMoved(e -> {
            if (!isPanning) {
                if (isBackgroundTarget(e.getTarget())) {
                    setCursor(Cursor.OPEN_HAND);
                } else {
                    setCursor(Cursor.DEFAULT);
                }
            }
        });

        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.MIDDLE) {
                if (isBackgroundTarget(e.getTarget())) {
                    isPanning = true;
                    setCursor(Cursor.CLOSED_HAND);
                    dragStartX = e.getX() - translate.getX();
                    dragStartY = e.getY() - translate.getY();
                    if (onBackgroundClicked != null) {
                        onBackgroundClicked.run();
                    }
                }
            }
        });

        setOnMouseDragged(e -> {
            if (isPanning) {
                translate.setX(e.getX() - dragStartX);
                translate.setY(e.getY() - dragStartY);
            }
        });

        setOnMouseReleased(e -> {
            if (isPanning) {
                isPanning = false;
                setCursor(isBackgroundTarget(e.getTarget()) ? Cursor.OPEN_HAND : Cursor.DEFAULT);
            }
        });

        // Zoom via mouse wheel around cursor
        setOnScroll((ScrollEvent e) -> {
            double delta = e.getDeltaY();
            if (delta == 0) return;

            double factor = (delta > 0) ? 1.12 : (1.0 / 1.12);
            zoomAt(factor, e.getX(), e.getY());
            e.consume();
        });
    }

    private boolean isBackgroundTarget(Object target) {
        return target == this || target == contentGroup || target == backgroundGroup || target == nodesPane || target == edgesGroup;
    }

    public void setOnBackgroundClicked(Runnable listener) {
        this.onBackgroundClicked = listener;
    }

    public void zoomIn() {
        double pivotX = getWidth() > 0 ? getWidth() / 2.0 : 400.0;
        double pivotY = getHeight() > 0 ? getHeight() / 2.0 : 300.0;
        zoomAt(1.2, pivotX, pivotY);
    }

    public void zoomOut() {
        double pivotX = getWidth() > 0 ? getWidth() / 2.0 : 400.0;
        double pivotY = getHeight() > 0 ? getHeight() / 2.0 : 300.0;
        zoomAt(1.0 / 1.2, pivotX, pivotY);
    }

    public void resetZoom() {
        scale.setX(1.0);
        scale.setY(1.0);
        translate.setX(0);
        translate.setY(0);
        updateZoomProperties();
    }

    public void zoomAt(double factor, double pivotX, double pivotY) {
        double newScale = scale.getX() * factor;
        if (newScale >= 0.25 && newScale <= 3.0) {
            double dx = (pivotX - translate.getX()) * (1 - factor);
            double dy = (pivotY - translate.getY()) * (1 - factor);

            translate.setX(translate.getX() + dx);
            translate.setY(translate.getY() + dy);

            scale.setX(newScale);
            scale.setY(newScale);
            updateZoomProperties();
        }
    }

    private void updateZoomProperties() {
        zoomProperty.set(scale.getX());
        zoomPercentProperty.set((int) Math.round(scale.getX() * 100) + "%");
    }

    public DoubleProperty zoomProperty() {
        return zoomProperty;
    }

    public StringProperty zoomPercentProperty() {
        return zoomPercentProperty;
    }

    public Scale getScaleTransform() {
        return scale;
    }

    public Translate getTranslateTransform() {
        return translate;
    }

    /**
     * Pure calculation for fit-to-screen to enable headless unit testing.
     */
    public static FitResult calculateFit(double viewW, double viewH, double minX, double minY, double maxX, double maxY, int nodeCount) {
        if (viewW <= 0) viewW = 800;
        if (viewH <= 0) viewH = 600;

        double padding = 40.0;
        double graphW = (maxX - minX) + (padding * 2.0);
        double graphH = (maxY - minY) + (padding * 2.0);

        if (graphW <= 0) graphW = 200;
        if (graphH <= 0) graphH = 200;

        double s = Math.min(viewW / graphW, viewH / graphH);

        // Do not zoom out below 0.6 when <= 30 nodes; do not scale above 1.0
        if (nodeCount <= 30) {
            s = Math.max(0.6, Math.min(1.0, s));
        } else {
            s = Math.max(0.25, Math.min(1.0, s));
        }

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;

        double tx = (viewW / 2.0) - (centerX * s);
        double ty = (viewH / 2.0) - (centerY * s);

        return new FitResult(s, tx, ty);
    }

    /**
     * Fits the graph to the current viewport after layout passes.
     */
    public void fitToScreen() {
        applyCss();
        layout();

        if (nodesPane.getChildren().isEmpty()) return;

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        int count = 0;
        for (Node n : nodesPane.getChildren()) {
            double x = n.getLayoutX();
            double y = n.getLayoutY();
            double w = n instanceof Region r && r.getWidth() > 0 ? r.getWidth() : (n.getBoundsInParent().getWidth() > 0 ? n.getBoundsInParent().getWidth() : 220);
            double h = n instanceof Region r && r.getHeight() > 0 ? r.getHeight() : (n.getBoundsInParent().getHeight() > 0 ? n.getBoundsInParent().getHeight() : 80);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + w);
            maxY = Math.max(maxY, y + h);
            count++;
        }

        double viewW = getWidth() > 0 ? getWidth() : (getPrefWidth() > 0 ? getPrefWidth() : 900);
        double viewH = getHeight() > 0 ? getHeight() : (getPrefHeight() > 0 ? getPrefHeight() : 600);

        FitResult fit = calculateFit(viewW, viewH, minX, minY, maxX, maxY, count);
        scale.setX(fit.scale());
        scale.setY(fit.scale());
        translate.setX(fit.translateX());
        translate.setY(fit.translateY());
        updateZoomProperties();
    }

    /**
     * Attaches dragging behavior to a node card respecting current zoom level.
     */
    public void makeNodeDraggable(Node nodeCard, Runnable onDrag) {
        class DragContext {
            double startX, startY;
        }
        final DragContext ctx = new DragContext();

        nodeCard.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                ctx.startX = nodeCard.getLayoutX() - (e.getSceneX() / scale.getX());
                ctx.startY = nodeCard.getLayoutY() - (e.getSceneY() / scale.getY());
                nodeCard.toFront();
                e.consume();
            }
        });

        nodeCard.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double newX = (e.getSceneX() / scale.getX()) + ctx.startX;
                double newY = (e.getSceneY() / scale.getY()) + ctx.startY;
                nodeCard.setLayoutX(newX);
                nodeCard.setLayoutY(newY);
                if (onDrag != null) onDrag.run();
                e.consume();
            }
        });
    }

    /**
     * Exports the graph scene to a PNG file.
     */
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
}

