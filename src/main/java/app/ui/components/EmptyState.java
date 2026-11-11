package app.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

public class EmptyState extends VBox {

    private final FontIcon icon = new FontIcon();
    private final Label titleLabel = new Label();
    private final Label messageLabel = new Label();
    private AppButton actionButton;

    public EmptyState() {
        this("fth-inbox", "No Data Available", "There are currently no items to display.");
    }

    public EmptyState(String iconLiteral, String title, String message) {
        super(8);
        setAlignment(Pos.CENTER);
        getStyleClass().add("empty-state-view");

        icon.setIconLiteral(iconLiteral);
        icon.setIconSize(36);
        icon.getStyleClass().add("empty-state-icon");

        titleLabel.setText(title);
        titleLabel.getStyleClass().add("empty-state-title");

        messageLabel.setText(message);
        messageLabel.getStyleClass().add("empty-state-desc");
        messageLabel.setWrapText(true);

        getChildren().addAll(icon, titleLabel, messageLabel);
    }

    public void setActionButton(String text, String buttonIcon, Runnable onAction) {
        if (actionButton != null) {
            getChildren().remove(actionButton);
        }
        actionButton = new AppButton(text, buttonIcon, AppButton.Variant.PRIMARY, AppButton.Size.SMALL);
        actionButton.setOnAction(e -> {
            if (onAction != null) {
                onAction.run();
            }
        });
        getChildren().add(actionButton);
    }

    public void setIcon(String iconLiteral) {
        icon.setIconLiteral(iconLiteral);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
    }

    public static EmptyState error(String title, String technicalDetails, Runnable onRetry) {
        EmptyState es = new EmptyState("fth-alert-triangle", title != null ? title : "Đã xảy ra lỗi", "");
        es.icon.getStyleClass().add("icon-warning");

        if (technicalDetails != null && !technicalDetails.isBlank()) {
            javafx.scene.control.TitledPane detailsPane = new javafx.scene.control.TitledPane();
            detailsPane.setText("Chi tiết");
            detailsPane.setExpanded(false);
            Label detailsLabel = new Label(technicalDetails);
            detailsLabel.setWrapText(true);
            detailsLabel.getStyleClass().add("empty-state-tech-desc");
            detailsPane.setContent(detailsLabel);
            detailsPane.setMaxWidth(380);
            es.getChildren().add(detailsPane);
        }

        if (onRetry != null) {
            es.setActionButton("Thử lại", "fth-rotate-cw", onRetry);
        }

        return es;
    }
}

