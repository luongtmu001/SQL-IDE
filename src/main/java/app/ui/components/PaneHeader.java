package app.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

public class PaneHeader extends HBox {

    private final FontIcon iconNode = new FontIcon();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final HBox rightBox = new HBox(6);
    private Badge badgeNode;

    public PaneHeader(String title, String iconLiteral) {
        this(title, iconLiteral, null);
    }

    public PaneHeader(String title, String iconLiteral, String subtitle) {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        setMinHeight(28);
        setPrefHeight(28);
        setMaxHeight(28);
        setPadding(new Insets(2, 10, 2, 10));
        getStyleClass().add("pane-header");

        if (iconLiteral != null && !iconLiteral.isBlank()) {
            iconNode.setIconLiteral(iconLiteral);
            iconNode.setIconSize(13);
            iconNode.getStyleClass().add("pane-header-icon");
            getChildren().add(iconNode);
        }

        titleLabel.setText(title);
        titleLabel.getStyleClass().add("pane-header-title");
        getChildren().add(titleLabel);

        subtitleLabel.getStyleClass().add("pane-header-subtitle");
        if (subtitle != null && !subtitle.isBlank()) {
            subtitleLabel.setText(subtitle);
            getChildren().add(subtitleLabel);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);

        rightBox.setAlignment(Pos.CENTER_RIGHT);
        getChildren().add(rightBox);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public String getTitle() {
        return titleLabel.getText();
    }

    public void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle != null ? subtitle : "");
        if (subtitle != null && !subtitle.isBlank()) {
            if (!getChildren().contains(subtitleLabel)) {
                int idx = getChildren().indexOf(titleLabel);
                getChildren().add(idx + 1, subtitleLabel);
            }
        } else {
            getChildren().remove(subtitleLabel);
        }
    }

    public String getSubtitle() {
        return subtitleLabel.getText();
    }

    public void setBadge(Badge badge) {
        if (badgeNode != null) {
            getChildren().remove(badgeNode);
        }
        badgeNode = badge;
        if (badgeNode != null) {
            int idx = getChildren().indexOf(titleLabel);
            getChildren().add(idx + 1, badgeNode);
        }
    }

    public Badge getBadge() {
        return badgeNode;
    }

    public void addAction(Node actionNode) {
        rightBox.getChildren().add(actionNode);
    }

    public void setRightNode(Node node) {
        rightBox.getChildren().clear();
        if (node != null) {
            rightBox.getChildren().add(node);
        }
    }

    public HBox getRightBox() {
        return rightBox;
    }

    public FontIcon getIconNode() {
        return iconNode;
    }

    public void setIconLiteral(String iconLiteral) {
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            iconNode.setIconLiteral(iconLiteral);
            if (!getChildren().contains(iconNode)) {
                getChildren().add(0, iconNode);
            }
        }
    }
}

