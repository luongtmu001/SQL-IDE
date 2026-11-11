package app.ui.components;

import javafx.scene.control.ToggleButton;
import org.kordamp.ikonli.javafx.FontIcon;

public class FilterChip extends ToggleButton {

    private final FontIcon checkIcon = new FontIcon("fth-check");
    private final FontIcon customIcon;

    public FilterChip(String text, String iconLiteral, boolean initialSelected) {
        super(text);
        getStyleClass().add("filter-chip");

        checkIcon.setIconSize(12);
        checkIcon.getStyleClass().add("filter-chip-check");

        if (iconLiteral != null && !iconLiteral.isBlank()) {
            customIcon = new FontIcon(iconLiteral);
            customIcon.setIconSize(13);
        } else {
            customIcon = null;
        }

        setSelected(initialSelected);
        updateGraphic();

        selectedProperty().addListener((obs, oldV, isSel) -> updateGraphic());
    }

    private void updateGraphic() {
        if (isSelected()) {
            setGraphic(checkIcon);
        } else if (customIcon != null) {
            setGraphic(customIcon);
        } else {
            setGraphic(null);
        }
    }
}

