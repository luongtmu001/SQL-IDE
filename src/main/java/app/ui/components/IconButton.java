package app.ui.components;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

public class IconButton extends Button {

    private final FontIcon fontIcon = new FontIcon();

    public IconButton() {
        super();
        init();
    }

    public IconButton(String iconLiteral) {
        this(iconLiteral, null);
    }

    public IconButton(String iconLiteral, String tooltipText) {
        super();
        init();
        setIcon(iconLiteral);
        if (tooltipText != null && !tooltipText.isBlank()) {
            setTooltip(new Tooltip(tooltipText));
        }
    }

    private void init() {
        setGraphic(fontIcon);
        getStyleClass().addAll("icon-button", Styles.BUTTON_ICON, Styles.FLAT);
    }

    public void setIcon(String iconLiteral) {
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            fontIcon.setIconLiteral(iconLiteral);
            setGraphic(fontIcon);
        } else {
            setGraphic(null);
        }
    }

    public String getIcon() {
        return fontIcon.getIconLiteral();
    }
}

