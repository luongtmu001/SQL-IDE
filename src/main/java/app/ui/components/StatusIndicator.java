package app.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

public class StatusIndicator extends HBox {

    public enum Status {
        ONLINE("online", "Connected / Active"),
        BUSY("busy", "Working / Executing"),
        OFFLINE("offline", "Disconnected / Offline"),
        IDLE("idle", "Idle / Ready");

        private final String cssClass;
        private final String defaultDescription;

        Status(String cssClass, String defaultDescription) {
            this.cssClass = cssClass;
            this.defaultDescription = defaultDescription;
        }

        public String getCssClass() {
            return cssClass;
        }

        public String getDefaultDescription() {
            return defaultDescription;
        }
    }

    private final Region dot = new Region();
    private final Label textLabel = new Label();
    private final Tooltip tooltip = new Tooltip();
    private Status currentStatus = Status.ONLINE;

    public StatusIndicator() {
        this(Status.ONLINE, null);
    }

    public StatusIndicator(Status status) {
        this(status, null);
    }

    public StatusIndicator(Status status, String labelText) {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("status-indicator");

        dot.getStyleClass().add("status-dot");
        textLabel.getStyleClass().add("status-indicator-label");

        Tooltip.install(this, tooltip);

        getChildren().add(dot);
        if (labelText != null && !labelText.isBlank()) {
            textLabel.setText(labelText);
            getChildren().add(textLabel);
        }

        setStatus(status);
    }

    public void setStatus(Status status) {
        setStatus(status, status.getDefaultDescription());
    }

    public void setStatus(Status status, String tooltipOrText) {
        this.currentStatus = status;

        dot.getStyleClass().removeAll("online", "busy", "offline", "idle");
        dot.getStyleClass().add(status.getCssClass());

        tooltip.setText(tooltipOrText);
        if (!getChildren().contains(textLabel) && textLabel.getText() != null && !textLabel.getText().isBlank()) {
            getChildren().add(textLabel);
        }
    }

    public void setText(String text) {
        textLabel.setText(text);
        if (text != null && !text.isBlank() && !getChildren().contains(textLabel)) {
            getChildren().add(textLabel);
        } else if ((text == null || text.isBlank()) && getChildren().contains(textLabel)) {
            getChildren().remove(textLabel);
        }
    }

    public Status getCurrentStatus() {
        return currentStatus;
    }

    public Status getStatus() {
        return currentStatus;
    }
}

