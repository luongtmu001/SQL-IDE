package app.ui.components;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Button;
import org.kordamp.ikonli.javafx.FontIcon;

public class AppButton extends Button {

    public enum Variant {
        DEFAULT,
        PRIMARY,
        SUCCESS,
        DANGER,
        WARNING,
        FLAT,
        OUTLINE
    }

    public enum Size {
        SMALL,
        MEDIUM,
        LARGE
    }

    private Variant variant = Variant.DEFAULT;
    private Size size = Size.MEDIUM;

    public AppButton() {
        super();
        initStyle();
    }

    public AppButton(String text) {
        super(text);
        initStyle();
    }

    public AppButton(String text, String iconLiteral) {
        super(text);
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            setGraphic(new FontIcon(iconLiteral));
        }
        initStyle();
    }

    public AppButton(String text, String iconLiteral, Variant variant) {
        super(text);
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            setGraphic(new FontIcon(iconLiteral));
        }
        this.variant = variant;
        initStyle();
    }

    public AppButton(String text, String iconLiteral, Variant variant, Size size) {
        super(text);
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            setGraphic(new FontIcon(iconLiteral));
        }
        this.variant = variant;
        this.size = size;
        initStyle();
    }

    public void setVariant(Variant variant) {
        this.variant = variant;
        updateClasses();
    }

    public Variant getVariant() {
        return variant;
    }

    public void setButtonSize(Size size) {
        this.size = size;
        updateClasses();
    }

    public Size getButtonSize() {
        return size;
    }

    public void setIcon(String iconLiteral) {
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            setGraphic(new FontIcon(iconLiteral));
        } else {
            setGraphic(null);
        }
    }

    private void initStyle() {
        getStyleClass().add("app-button");
        updateClasses();
    }

    private void updateClasses() {
        getStyleClass().removeAll(
            Styles.ACCENT, Styles.SUCCESS, Styles.DANGER, Styles.WARNING,
            Styles.FLAT, Styles.BUTTON_OUTLINED,
            Styles.SMALL, Styles.MEDIUM, Styles.LARGE
        );

        switch (variant) {
            case PRIMARY -> getStyleClass().add(Styles.ACCENT);
            case SUCCESS -> getStyleClass().add(Styles.SUCCESS);
            case DANGER -> getStyleClass().add(Styles.DANGER);
            case WARNING -> getStyleClass().add(Styles.WARNING);
            case FLAT -> getStyleClass().add(Styles.FLAT);
            case OUTLINE -> getStyleClass().add(Styles.BUTTON_OUTLINED);
            case DEFAULT -> {}
        }

        switch (size) {
            case SMALL -> getStyleClass().add(Styles.SMALL);
            case MEDIUM -> getStyleClass().add(Styles.MEDIUM);
            case LARGE -> getStyleClass().add(Styles.LARGE);
        }
    }
}

