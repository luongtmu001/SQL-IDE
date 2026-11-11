package app.ui.components;

import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Common icon factory conforming to R-UI-15 and H-2 of 07-ui-consistency-rules.md:
 * - icon size follows "Giao diện/Theme → Kích thước biểu tượng" through UiMetrics
 * - four scale levels: SM, MD, LG, XL
 */
public final class Icons {

    /** Key under which an icon keeps its own size listener alive (see {@link #bind}). */
    private static final Object SIZE_LISTENER_KEY = new Object();

    private Icons() {}

    /** Creates a FontIcon with the default MD scale (base icon size). */
    public static FontIcon of(String literal) {
        return of(literal, IconScale.MD);
    }

    /** Creates a FontIcon whose size follows the user's icon-size setting at the given scale. */
    public static FontIcon of(String literal, IconScale scale) {
        FontIcon icon = new FontIcon();
        if (literal != null && !literal.isBlank()) {
            icon.setIconLiteral(literal);
        }
        bind(icon, scale);
        return icon;
    }

    /**
     * Makes an existing FontIcon follow the icon-size setting.
     *
     * The shared binding in UiMetrics is static, so it must not hold the icon strongly: the icon owns
     * its listener (stored in its properties map) and the binding only gets a weak wrapper. When the
     * icon is dropped (closed tab, recycled cell) it can be collected.
     */
    public static FontIcon bind(FontIcon icon, IconScale scale) {
        if (icon == null) return null;
        IntegerBinding binding = UiMetrics.bindingFor(scale != null ? scale : IconScale.MD);
        icon.setIconSize(binding.get());
        ChangeListener<Number> listener = (obs, oldV, newV) -> {
            if (newV != null) icon.setIconSize(newV.intValue());
        };
        icon.getProperties().put(SIZE_LISTENER_KEY, listener); // replaces (and frees) a previous one
        binding.addListener(new WeakChangeListener<>(listener));
        return icon;
    }
}
