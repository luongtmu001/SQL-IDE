package app.ui.components;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * Reusable Card Container (Phase 5).
 * Encapsulates 8px rounded borders, card styling, and automatic scene-graph clipping
 * ensuring child controls cannot bleed over the card's rounded corners.
 */
public class CardPane extends VBox {

    private final Rectangle clipShape = new Rectangle();

    public CardPane() {
        this(8.0);
    }

    public CardPane(double cornerRadius) {
        getStyleClass().add("card-pane");

        // Set up automatic scene-graph clipping matching cornerRadius
        double arc = cornerRadius * 2.0;
        clipShape.setArcWidth(arc);
        clipShape.setArcHeight(arc);
        clipShape.widthProperty().bind(widthProperty());
        clipShape.heightProperty().bind(heightProperty());
        setClip(clipShape);
    }

    public CardPane(Node... children) {
        this();
        getChildren().addAll(children);
    }
}

