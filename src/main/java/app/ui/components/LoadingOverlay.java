package app.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * DLG-014 loading overlay, shared by any panel (results, explorer, dialogs).
 * Dims the host, shows a small card with spinner, message, elapsed time and "Hủy".
 * Appears only after {@value #SHOW_DELAY_MS} ms so quick operations never flash.
 * The card follows R-UI-01 through the {@code .loading-card} rules in shape / shape-flat css.
 */
public final class LoadingOverlay {

    private static final long SHOW_DELAY_MS = 150;

    private final VBox host;
    private final Label message = new Label();
    private final Label elapsed = new Label("00:00");
    private final Hyperlink cancel = new Hyperlink("Hủy");
    private final Timeline ticker;
    private final PauseTransition delay = new PauseTransition(Duration.millis(SHOW_DELAY_MS));
    private long startedAt;
    private Runnable onCancel;

    private LoadingOverlay(VBox host) {
        this.host = host;
        host.getChildren().clear();
        host.getStyleClass().add("loading-overlay");
        host.setAlignment(Pos.CENTER);
        host.setPickOnBounds(true);

        ProgressIndicator spin = new ProgressIndicator();
        spin.getStyleClass().add("loading-spinner");
        message.getStyleClass().add("loading-message");
        elapsed.getStyleClass().add("loading-elapsed");
        cancel.getStyleClass().add("loading-cancel");
        cancel.setFocusTraversable(false);
        cancel.setOnAction(e -> {
            Runnable r = onCancel;
            cancel.setDisable(true);
            message.setText("Đang hủy…");
            if (r != null) r.run();
        });
        HBox meta = new HBox(8, elapsed, cancel);
        meta.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(2, message, meta);
        HBox card = new HBox(12, spin, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("loading-card");
        card.setMaxSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);
        host.getChildren().add(card);

        ticker = new Timeline(new KeyFrame(Duration.millis(500), e -> updateElapsed()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        delay.setOnFinished(e -> {
            host.setVisible(true);
            host.setManaged(true);
        });
        setShown(false);
    }

    /** Turns an existing (FXML) overlay box into a loading overlay. */
    public static LoadingOverlay attach(VBox host) {
        return new LoadingOverlay(host);
    }

    /** Adds a new overlay layer on top of {@code parent}. */
    public static LoadingOverlay over(StackPane parent) {
        VBox box = new VBox();
        parent.getChildren().add(box);
        return new LoadingOverlay(box);
    }

    /** @param cancelAction null hides the "Hủy" link */
    public void show(String text, Runnable cancelAction) {
        this.onCancel = cancelAction;
        message.setText(text != null ? text : "Đang xử lý…");
        cancel.setDisable(false);
        cancel.setVisible(cancelAction != null);
        cancel.setManaged(cancelAction != null);
        startedAt = System.currentTimeMillis();
        updateElapsed();
        ticker.playFromStart();
        if (!host.isVisible()) delay.playFromStart();
    }

    public void hide() {
        onCancel = null;
        delay.stop();
        ticker.stop();
        setShown(false);
    }

    public void setMessage(String text) {
        if (message != null) {
            message.setText(text != null ? text : "");
        }
    }

    public boolean isShowing() {
        return host.isVisible() || delay.getStatus() == javafx.animation.Animation.Status.RUNNING;
    }

    private void setShown(boolean shown) {
        host.setVisible(shown);
        host.setManaged(shown);
    }

    private void updateElapsed() {
        long s = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
        elapsed.setText(String.format("%02d:%02d", s / 60, s % 60));
    }
}
