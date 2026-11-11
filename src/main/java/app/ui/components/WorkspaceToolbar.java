package app.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

public class WorkspaceToolbar extends HBox {

    public enum Priority {
        HIGH,
        LOW
    }

    private final HBox leftBox = new HBox(6);
    private final HBox centerBox = new HBox(6);
    private final HBox rightBox = new HBox(6);
    private final Region spacerLeft = new Region();
    private final Region spacerRight = new Region();

    private final MenuButton overflowMenu = new MenuButton();
    private final Map<Node, Priority> nodePriorities = new LinkedHashMap<>();
    private final Map<Node, ItemOrigin> nodeOrigins = new LinkedHashMap<>();
    private final List<Node> hiddenLowPriorityNodes = new ArrayList<>();

    private double collapseThreshold = 950.0;

    private enum ItemOrigin { LEFT, RIGHT }

    public WorkspaceToolbar() {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(4, 10, 4, 10));
        setMinHeight(36);
        setPrefHeight(36);
        setMaxHeight(36);
        getStyleClass().add("workspace-toolbar");

        leftBox.setAlignment(Pos.CENTER_LEFT);
        centerBox.setAlignment(Pos.CENTER);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        HBox.setHgrow(spacerLeft, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(spacerRight, javafx.scene.layout.Priority.ALWAYS);

        overflowMenu.setGraphic(new FontIcon("fth-more-horizontal"));
        overflowMenu.getStyleClass().addAll("button-subtle", "toolbar-overflow-menu");
        overflowMenu.setVisible(false);
        overflowMenu.setManaged(false);

        getChildren().addAll(leftBox, spacerLeft, centerBox, spacerRight, rightBox, overflowMenu);

        widthProperty().addListener((obs, oldW, newW) -> updateOverflow(newW.doubleValue()));
    }

    public void addLeft(Node node) {
        addLeft(node, Priority.HIGH);
    }

    public void addLeft(Node node, Priority priority) {
        nodePriorities.put(node, priority);
        nodeOrigins.put(node, ItemOrigin.LEFT);
        leftBox.getChildren().add(node);
    }

    public void addCenter(Node node) {
        centerBox.getChildren().add(node);
    }

    public void addRight(Node node) {
        addRight(node, Priority.HIGH);
    }

    public void addRight(Node node, Priority priority) {
        nodePriorities.put(node, priority);
        nodeOrigins.put(node, ItemOrigin.RIGHT);
        rightBox.getChildren().add(node);
    }

    public void setCollapseThreshold(double threshold) {
        this.collapseThreshold = threshold;
    }

    public double getCollapseThreshold() {
        return collapseThreshold;
    }

    public void updateOverflow(double width) {
        if (width <= 0) return;

        boolean shouldCollapse = width < collapseThreshold;

        if (shouldCollapse && hiddenLowPriorityNodes.isEmpty()) {
            overflowMenu.getItems().clear();
            for (Map.Entry<Node, Priority> entry : nodePriorities.entrySet()) {
                if (entry.getValue() == Priority.LOW) {
                    Node node = entry.getKey();
                    ItemOrigin origin = nodeOrigins.get(node);
                    if (origin == ItemOrigin.LEFT) {
                        leftBox.getChildren().remove(node);
                    } else if (origin == ItemOrigin.RIGHT) {
                        rightBox.getChildren().remove(node);
                    }
                    hiddenLowPriorityNodes.add(node);

                    MenuItem item = createMenuItemFromNode(node);
                    if (item != null) {
                        overflowMenu.getItems().add(item);
                    }
                }
            }

            if (!hiddenLowPriorityNodes.isEmpty()) {
                overflowMenu.setVisible(true);
                overflowMenu.setManaged(true);
            }
        } else if (!shouldCollapse && !hiddenLowPriorityNodes.isEmpty()) {
            for (Node node : hiddenLowPriorityNodes) {
                ItemOrigin origin = nodeOrigins.get(node);
                if (origin == ItemOrigin.LEFT) {
                    leftBox.getChildren().add(node);
                } else if (origin == ItemOrigin.RIGHT) {
                    rightBox.getChildren().add(node);
                }
            }
            hiddenLowPriorityNodes.clear();
            overflowMenu.getItems().clear();
            overflowMenu.setVisible(false);
            overflowMenu.setManaged(false);
        }
    }

    private MenuItem createMenuItemFromNode(Node node) {
        if (node instanceof Button btn) {
            MenuItem mi = new MenuItem(btn.getText(), btn.getGraphic());
            mi.setOnAction(btn.getOnAction());
            return mi;
        } else if (node instanceof Labeled labeled) {
            return new MenuItem(labeled.getText(), labeled.getGraphic());
        }
        return null;
    }

    public HBox getLeftBox() {
        return leftBox;
    }

    public HBox getCenterBox() {
        return centerBox;
    }

    public HBox getRightBox() {
        return rightBox;
    }

    public MenuButton getOverflowMenu() {
        return overflowMenu;
    }

    public List<Node> getHiddenLowPriorityNodes() {
        return Collections.unmodifiableList(hiddenLowPriorityNodes);
    }
}

