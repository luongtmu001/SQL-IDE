package app.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class WorkspaceFooter extends HBox {

    private final HBox leftBox = new HBox(8);
    private final HBox rightBox = new HBox(8);
    private final Badge sampleDataBadge = Badge.warning("DỮ LIỆU MẪU");

    public WorkspaceFooter() {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        setMinHeight(24);
        setPrefHeight(24);
        setMaxHeight(24);
        setPadding(new Insets(2, 12, 2, 12));
        getStyleClass().add("workspace-footer");

        leftBox.setAlignment(Pos.CENTER_LEFT);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        sampleDataBadge.setVisible(false);
        sampleDataBadge.setManaged(false);

        getChildren().addAll(leftBox, spacer, sampleDataBadge, rightBox);
    }

    public void addSection(Node node) {
        if (!leftBox.getChildren().isEmpty()) {
            Separator sep = new Separator(Orientation.VERTICAL);
            sep.getStyleClass().add("footer-separator");
            leftBox.getChildren().add(sep);
        }
        leftBox.getChildren().add(node);
    }

    public void addSection(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("footer-label");
        addSection(lbl);
    }

    public void setRightNode(Node node) {
        rightBox.getChildren().clear();
        if (node != null) {
            rightBox.getChildren().add(node);
        }
    }

    public void setSampleDataVisible(boolean visible) {
        sampleDataBadge.setVisible(visible);
        sampleDataBadge.setManaged(visible);
    }

    public boolean isSampleDataVisible() {
        return sampleDataBadge.isVisible();
    }

    public HBox getLeftBox() {
        return leftBox;
    }

    public HBox getRightBox() {
        return rightBox;
    }
}

