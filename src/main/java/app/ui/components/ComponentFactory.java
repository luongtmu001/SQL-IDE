package app.ui.components;

import javafx.scene.Node;

/**
 * Centralized Component Factory (Phase 5).
 * One-stop factory for constructing design-system compliant UI controls.
 */
public final class ComponentFactory {

    private ComponentFactory() {}

    public static AppButton primaryButton(String text, String iconLiteral) {
        return new AppButton(text, iconLiteral, AppButton.Variant.PRIMARY);
    }

    public static AppButton successButton(String text, String iconLiteral) {
        return new AppButton(text, iconLiteral, AppButton.Variant.SUCCESS);
    }

    public static AppButton dangerButton(String text, String iconLiteral) {
        return new AppButton(text, iconLiteral, AppButton.Variant.DANGER);
    }

    public static IconButton iconButton(String iconLiteral, String tooltip) {
        return new IconButton(iconLiteral, tooltip);
    }

    public static SearchField searchField(String promptText) {
        return new SearchField(promptText);
    }

    public static StatusIndicator statusIndicator(StatusIndicator.Status status) {
        return new StatusIndicator(status);
    }

    public static CardPane cardPane(Node... children) {
        return new CardPane(children);
    }

    public static Badge badge(String text, Badge.Variant variant) {
        return new Badge(text, variant);
    }

    public static EmptyState emptyState(String iconLiteral, String title, String description) {
        return new EmptyState(iconLiteral, title, description);
    }

    public static LoadingView loadingView(String message) {
        return new LoadingView(message);
    }

    public static BreadcrumbBar breadcrumbBar() {
        return new BreadcrumbBar();
    }
}

