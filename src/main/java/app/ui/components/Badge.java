package app.ui.components;

import javafx.scene.control.Label;

/**
 * Reusable Badge Component (Phase 5).
 * Displays short status labels, PK/FK badges, or row counts with semantic colors.
 */
public class Badge extends Label {

    public enum Variant {
        INFO("badge-info"),
        SUCCESS("badge-success"),
        WARNING("badge-warning"),
        DANGER("badge-danger"),
        PRIMARY("badge-primary"),
        NEUTRAL("badge-neutral");

        private final String cssClass;

        Variant(String cssClass) {
            this.cssClass = cssClass;
        }

        public String getCssClass() {
            return cssClass;
        }
    }

    private Variant variant;

    public Badge(String text, Variant variant) {
        super(text);
        this.variant = variant;
        getStyleClass().addAll("badge", variant.getCssClass());
    }

    public Variant getVariant() {
        return variant;
    }

    public void setVariant(Variant newVariant) {
        if (this.variant != null) {
            getStyleClass().remove(this.variant.getCssClass());
        }
        this.variant = newVariant;
        getStyleClass().add(newVariant.getCssClass());
    }

    public static Badge info(String text) {
        return new Badge(text, Variant.INFO);
    }

    public static Badge success(String text) {
        return new Badge(text, Variant.SUCCESS);
    }

    public static Badge warning(String text) {
        return new Badge(text, Variant.WARNING);
    }

    public static Badge danger(String text) {
        return new Badge(text, Variant.DANGER);
    }

    public static Badge neutral(String text) {
        return new Badge(text, Variant.NEUTRAL);
    }
}

