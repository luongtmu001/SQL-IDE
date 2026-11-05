package app.ui.dialogs;

import app.services.SettingsService;
import app.services.ThemeService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Shared window chrome for all SQL IDE dialogs (Phase 2B, Gate B 2026-11-05, Q4 = a).
 *
 * <p>Custom 34px title bar (icon, title, optional subtitle, close), rounded 8px window with
 * shadow — or square corners when Settings ▸ Giao diện ▸ Kiểu khung = Phẳng. Drag by the title
 * bar, Esc closes, modal to the owner. Content and footer are supplied by each dialog.</p>
 */
public final class DialogChrome {

    private static final double SHADOW = 14;

    private final Stage stage = new Stage();
    private final VBox shell = new VBox();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final FontIcon titleIcon = new FontIcon();
    private double dragX;
    private double dragY;
    /** Returns false to keep the dialog open (e.g. unsaved changes); used by × and Esc. */
    private java.util.function.BooleanSupplier closeGuard;

    private DialogChrome(Window owner, String title, String iconLiteral) {
        boolean flat = SettingsService.getInstance().getCurrentSettings() != null
                && SettingsService.getInstance().getCurrentSettings().isFlatPanels();

        stage.initStyle(StageStyle.TRANSPARENT);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle(title);

        // ----- title bar
        titleIcon.setIconLiteral(iconLiteral != null ? iconLiteral : "fth-square");
        titleIcon.getStyleClass().add("dialog-title-icon");
        titleLabel.setText(title);
        titleLabel.getStyleClass().add("dialog-title");
        titleLabel.setMinWidth(0);
        subtitleLabel.getStyleClass().add("dialog-subtitle");
        subtitleLabel.setMinWidth(0);
        subtitleLabel.setVisible(false);
        subtitleLabel.setManaged(false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button();
        close.setGraphic(new FontIcon("fth-x"));
        close.getStyleClass().add("dialog-close-button");
        close.setFocusTraversable(false);
        close.setOnAction(e -> requestClose());

        HBox titleBar = new HBox(8, titleIcon, titleLabel, subtitleLabel, spacer, close);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.getStyleClass().add("dialog-titlebar");
        titleBar.setOnMousePressed(e -> {
            dragX = e.getScreenX() - stage.getX();
            dragY = e.getScreenY() - stage.getY();
        });
        titleBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragX);
            stage.setY(e.getScreenY() - dragY);
        });

        shell.getStyleClass().add("dialog-shell");
        if (flat) shell.getStyleClass().add("flat");
        shell.getChildren().add(titleBar);

        // clip children to the rounded shell so headers/footers never cover the corners
        Rectangle clip = new Rectangle();
        double r = flat ? 0 : 8;
        clip.setArcWidth(r * 2);
        clip.setArcHeight(r * 2);
        clip.widthProperty().bind(shell.widthProperty());
        clip.heightProperty().bind(shell.heightProperty());

        StackPane clipHost = new StackPane(shell);
        clipHost.getStyleClass().add("dialog-clip-host");
        shell.setClip(clip);

        StackPane root = new StackPane(clipHost);
        root.setPadding(new Insets(SHADOW));
        root.getStyleClass().add("dialog-root");
        if (flat) root.getStyleClass().add("flat");
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        ThemeService.getInstance().initializeTheme(scene);
        var css = DialogChrome.class.getResource("/css/dialogs.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        ThemeService.getInstance().applyShape(scene); // R-UI-01 sheets stay last
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                requestClose();
                e.consume();
            }
        });
        stage.setScene(scene);
    }

    public static DialogChrome create(Window owner, String title, String iconLiteral) {
        return new DialogChrome(owner, title, iconLiteral);
    }

    public DialogChrome subtitle(String text) {
        boolean has = text != null && !text.isBlank();
        subtitleLabel.setText(has ? text : "");
        subtitleLabel.setVisible(has);
        subtitleLabel.setManaged(has);
        return this;
    }

    public DialogChrome title(String text) {
        titleLabel.setText(text);
        stage.setTitle(text);
        return this;
    }

    public DialogChrome iconStyle(String styleClass) {
        titleIcon.getStyleClass().add(styleClass);
        return this;
    }

    /** Body of the dialog (gets the {@code dialog-body} padding). */
    public DialogChrome content(Node content) {
        VBox body = new VBox(content);
        body.getStyleClass().add("dialog-body");
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);
        shell.getChildren().add(body);
        return this;
    }

    /** Body without padding (e.g. split layouts that manage their own spacing). */
    public DialogChrome rawContent(Node content) {
        VBox.setVgrow(content, Priority.ALWAYS);
        shell.getChildren().add(content);
        return this;
    }

    /** Footer row: {@code left} nodes, flexible gap, then {@code right} nodes (primary last). */
    public DialogChrome footer(Node[] left, Node... right) {
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("dialog-footer");
        if (left != null) footer.getChildren().addAll(left);
        footer.getChildren().add(gap);
        footer.getChildren().addAll(right);
        shell.getChildren().add(footer);
        return this;
    }

    public DialogChrome size(double width, double height) {
        shell.setPrefWidth(width);
        shell.setMinWidth(width);
        if (height > 0) {
            shell.setPrefHeight(height);
            shell.setMinHeight(height);
        }
        return this;
    }

    public DialogChrome onCloseRequest(java.util.function.BooleanSupplier guard) {
        this.closeGuard = guard;
        return this;
    }

    /** Close through the guard (title bar ×, Esc). */
    public void requestClose() {
        if (closeGuard == null || closeGuard.getAsBoolean()) stage.close();
    }

    public Stage stage() {
        return stage;
    }

    public void close() {
        stage.close();
    }

    public void showAndWait() {
        stage.sizeToScene();
        Window owner = stage.getOwner();
        if (owner != null) {
            stage.setOnShown(e -> {
                stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
                stage.setY(owner.getY() + Math.max(40, (owner.getHeight() - stage.getHeight()) / 3));
            });
        } else {
            stage.centerOnScreen();
        }
        stage.showAndWait();
    }

    // ------------------------------------------------------------------ buttons

    public static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("dialog-button", "accent");
        b.setDefaultButton(true);
        return b;
    }

    public static Button secondary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("dialog-button");
        return b;
    }

    public static Button danger(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("dialog-button", "danger");
        b.setDefaultButton(true);
        return b;
    }

    public static Button cancel(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("dialog-button");
        b.setCancelButton(true);
        return b;
    }
}
