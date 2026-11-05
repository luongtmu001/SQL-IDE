package app.ui.components;

import app.services.ThemeService;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * DLG-011 (Gate B 2026-11-05): self-drawn toast stack, bottom-right of the owner window.
 * Follows R-UI-01 (rounded card or square when {@code appearance.panelStyle = flat}).
 * At most {@value #MAX_TOASTS} toasts; info/success hide after 4 s, warnings after 6 s,
 * errors stay until closed. Hovering a toast pauses its timer.
 */
public final class ToastHost {

    public enum Level { INFO, SUCCESS, WARNING, ERROR }

    /** Link-style action shown under the message ("Chi tiết", "Thử lại", …). */
    public record Action(String text, Runnable handler) {
    }

    private static final int MAX_TOASTS = 3;
    private static final double WIDTH = 340;
    private static final double MARGIN_RIGHT = 16;
    private static final double MARGIN_BOTTOM = 34; // clears the 22px status bar
    private static final Map<Window, ToastHost> HOSTS = new WeakHashMap<>();

    private final Window owner;
    private final Popup popup = new Popup();
    private final VBox stack = new VBox(6);

    private ToastHost(Window owner) {
        this.owner = owner;
        stack.getStyleClass().add("toast-stack");
        stack.setFillWidth(true);
        stack.setPrefWidth(WIDTH);
        stack.setMaxWidth(WIDTH);
        popup.getContent().add(stack);
        popup.setAutoHide(false);
        popup.setAutoFix(false);
        popup.setHideOnEscape(false);
        stack.heightProperty().addListener((o, a, b) -> reposition());
        owner.xProperty().addListener((o, a, b) -> reposition());
        owner.yProperty().addListener((o, a, b) -> reposition());
        owner.widthProperty().addListener((o, a, b) -> reposition());
        owner.heightProperty().addListener((o, a, b) -> reposition());
    }

    public static ToastHost of(Window owner) {
        return HOSTS.computeIfAbsent(owner, ToastHost::new);
    }

    public void show(Level level, String title, String message, List<Action> actions) {
        refreshStyles();
        while (stack.getChildren().size() >= MAX_TOASTS) {
            stack.getChildren().remove(0);
        }
        Node toast = build(level, title, message, actions != null ? actions : List.of());
        toast.setOpacity(0);
        stack.getChildren().add(toast);
        if (!popup.isShowing()) {
            popup.show(owner, owner.getX(), owner.getY());
        }
        stack.applyCss();
        stack.layout();
        reposition();
        FadeTransition in = new FadeTransition(Duration.millis(140), toast);
        in.setToValue(1);
        in.play();
    }

    // ------------------------------------------------------------------ toast node

    private Node build(Level level, String title, String message, List<Action> actions) {
        HBox toast = new HBox(10);
        toast.getStyleClass().addAll("toast", "toast-" + level.name().toLowerCase());
        toast.setAlignment(Pos.TOP_LEFT);
        toast.setPrefWidth(WIDTH);
        toast.setMaxWidth(WIDTH);

        FontIcon icon = new FontIcon(switch (level) {
            case SUCCESS -> "fth-check-circle";
            case WARNING -> "fth-alert-triangle";
            case ERROR -> "fth-alert-circle";
            default -> "fth-info";
        });
        icon.getStyleClass().add("toast-icon");

        VBox text = new VBox(2);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMinWidth(0);
        if (title != null && !title.isBlank()) {
            Label t = new Label(title);
            t.getStyleClass().add("toast-title");
            t.setWrapText(true);
            text.getChildren().add(t);
        }
        if (message != null && !message.isBlank()) {
            Label m = new Label(message);
            m.getStyleClass().add("toast-message");
            m.setWrapText(true);
            m.setMaxHeight(54); // ~3 lines; the full text stays available through "Chi tiết"
            text.getChildren().add(m);
        }
        if (!actions.isEmpty()) {
            HBox links = new HBox(10);
            links.getStyleClass().add("toast-actions");
            for (Action a : actions) {
                Hyperlink link = new Hyperlink(a.text());
                link.getStyleClass().add("toast-action");
                link.setFocusTraversable(false);
                link.setOnAction(e -> {
                    dismiss(toast);
                    if (a.handler() != null) a.handler().run();
                });
                links.getChildren().add(link);
            }
            text.getChildren().add(links);
        }

        Button close = new Button();
        close.setGraphic(new FontIcon("fth-x"));
        close.getStyleClass().add("toast-close");
        close.setFocusTraversable(false);
        close.setOnAction(e -> dismiss(toast));

        toast.getChildren().addAll(icon, text, close);

        if (level != Level.ERROR) {
            PauseTransition timer = new PauseTransition(Duration.seconds(level == Level.WARNING ? 6 : 4));
            timer.setOnFinished(e -> dismiss(toast));
            toast.setOnMouseEntered(e -> timer.pause());
            toast.setOnMouseExited(e -> timer.play());
            timer.play();
        }
        return toast;
    }

    private void dismiss(Node toast) {
        if (!stack.getChildren().contains(toast)) return;
        FadeTransition out = new FadeTransition(Duration.millis(120), toast);
        out.setToValue(0);
        out.setOnFinished(e -> {
            stack.getChildren().remove(toast);
            if (stack.getChildren().isEmpty()) {
                popup.hide();
            } else {
                reposition();
            }
        });
        out.play();
    }

    // ------------------------------------------------------------------ layout / styles

    private void reposition() {
        if (!popup.isShowing()) return;
        double h = stack.getHeight() > 0 ? stack.getHeight() : stack.prefHeight(WIDTH);
        popup.setX(owner.getX() + owner.getWidth() - WIDTH - MARGIN_RIGHT);
        popup.setY(owner.getY() + owner.getHeight() - h - MARGIN_BOTTOM);
    }

    /** Copies the owner's sheets (theme + R-UI-01 shape) so toasts always match the current setting. */
    private void refreshStyles() {
        Scene ownerScene = owner.getScene();
        Scene scene = popup.getScene();
        if (scene == null) return;
        List<String> sheets = new ArrayList<>();
        if (ownerScene != null) sheets.addAll(ownerScene.getStylesheets());
        var toastCss = ToastHost.class.getResource("/css/toast.css");
        if (toastCss != null) {
            sheets.remove(toastCss.toExternalForm());
            sheets.add(toastCss.toExternalForm());
        }
        if (!scene.getStylesheets().equals(sheets)) {
            scene.getStylesheets().setAll(sheets);
        }
        stack.getStyleClass().remove("flat");
        if (ThemeService.isFlat()) stack.getStyleClass().add("flat");
    }
}
