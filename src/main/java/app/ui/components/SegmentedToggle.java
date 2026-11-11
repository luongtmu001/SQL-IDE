package app.ui.components;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.Map;

public class SegmentedToggle<T> extends HBox {

    private final ToggleGroup group = new ToggleGroup();
    private final Map<T, ToggleButton> buttons = new LinkedHashMap<>();
    private final ObjectProperty<T> selectedValue = new SimpleObjectProperty<>();

    public SegmentedToggle() {
        super(0);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("segmented-toggle-group");
        setPadding(new Insets(1));

        selectedValue.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && buttons.containsKey(newVal)) {
                ToggleButton btn = buttons.get(newVal);
                if (!btn.isSelected()) {
                    btn.setSelected(true);
                }
            }
        });
    }

    public void addItem(T value, String text, String iconLiteral) {
        ToggleButton btn = new ToggleButton(text);
        btn.getStyleClass().add("segmented-toggle-button");
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            FontIcon icon = new FontIcon(iconLiteral);
            icon.setIconSize(13);
            btn.setGraphic(icon);
        }
        btn.setToggleGroup(group);

        btn.setOnAction(e -> {
            if (!btn.isSelected()) {
                // Prevent unselecting the currently selected item
                btn.setSelected(true);
            } else {
                selectedValue.set(value);
            }
        });

        buttons.put(value, btn);
        getChildren().add(btn);

        if (buttons.size() == 1) {
            btn.setSelected(true);
            selectedValue.set(value);
        }
    }

    public void select(T value) {
        if (buttons.containsKey(value)) {
            buttons.get(value).setSelected(true);
            selectedValue.set(value);
        }
    }

    public T getSelectedValue() {
        return selectedValue.get();
    }

    public ObjectProperty<T> selectedValueProperty() {
        return selectedValue;
    }

    public ToggleButton getButton(T value) {
        return buttons.get(value);
    }
}

