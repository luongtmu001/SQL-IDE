package app.ui.components;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * SCR-MAIN-001 card panels (approved 2026-11-05: keep cards, fix clipped corners).
 *
 * <p>JavaFX paints a Region's border <em>below</em> its children, so square child backgrounds
 * (tab headers, tree, grid, WebView) used to cover the rounded border at the corners.
 * This helper (1) clips the card and all its children to a rounded rectangle and
 * (2) draws the border on a mouse-transparent, unmanaged overlay kept as the last child,
 * so the 1px outline is always painted on top and stays continuous around the corners.</p>
 */
public final class RoundedCard {

    public static final String OVERLAY_STYLE_CLASS = "card-border-overlay";
    private static final String INSTALLED_KEY = "app.roundedCard.installed";

    private RoundedCard() {
    }

    /** Rounded clip only (no outline) — e.g. the coloured status bar. */
    public static void clip(Region region, double radius) {
        if (region == null) return;
        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        region.setClip(clip);
    }

    /** Changes the corner radius of a clip installed by {@link #install} or {@link #clip} (0 = square). */
    public static void setRadius(Region region, double radius) {
        if (region != null && region.getClip() instanceof Rectangle r) {
            r.setArcWidth(radius * 2);
            r.setArcHeight(radius * 2);
        }
    }

    public static void install(Pane card, double radius) {
        if (card == null || card.getProperties().containsKey(INSTALLED_KEY)) {
            return;
        }
        card.getProperties().put(INSTALLED_KEY, Boolean.TRUE);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        card.setClip(clip);

        Region overlay = new Region();
        overlay.getStyleClass().add(OVERLAY_STYLE_CLASS);
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        overlay.setFocusTraversable(false);
        Runnable resize = () -> overlay.resizeRelocate(0, 0, card.getWidth(), card.getHeight());
        card.widthProperty().addListener((o, a, b) -> resize.run());
        card.heightProperty().addListener((o, a, b) -> resize.run());
        card.getChildren().add(overlay);
        resize.run();

        // Keep the overlay painted last when children are added later.
        card.getChildren().addListener((ListChangeListener<Node>) change -> {
            var children = card.getChildren();
            if (!children.isEmpty() && children.get(children.size() - 1) != overlay) {
                javafx.application.Platform.runLater(() -> {
                    if (children.remove(overlay)) {
                        children.add(overlay);
                    }
                });
            }
        });
    }
}
