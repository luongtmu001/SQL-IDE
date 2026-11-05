package app.ui.components;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable Breadcrumb Bar Component (Phase 5).
 * Displays hierarchical navigation path (e.g. Server > Database > Schema > Table).
 */
public class BreadcrumbBar extends HBox {

    public record Crumb(String text, Object data) {}

    private final ObservableList<Crumb> crumbs = FXCollections.observableArrayList();
    private Consumer<Crumb> onCrumbClicked;

    public BreadcrumbBar() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4);
        getStyleClass().add("breadcrumb-bar");
    }

    public void pushItem(String text, Runnable onClick) {
        crumbs.add(new Crumb(text, onClick));
        rebuild();
    }

    public void popItem() {
        if (!crumbs.isEmpty()) {
            crumbs.remove(crumbs.size() - 1);
            rebuild();
        }
    }

    public Crumb peekItem() {
        if (crumbs.isEmpty()) return null;
        return crumbs.get(crumbs.size() - 1);
    }

    public int size() {
        return crumbs.size();
    }

    public void clear() {
        crumbs.clear();
        getChildren().clear();
    }

    public void setCrumbs(List<Crumb> newCrumbs) {
        crumbs.setAll(newCrumbs);
        rebuild();
    }

    public void setOnCrumbClicked(Consumer<Crumb> handler) {
        this.onCrumbClicked = handler;
    }

    private void rebuild() {
        getChildren().clear();
        for (int i = 0; i < crumbs.size(); i++) {
            Crumb crumb = crumbs.get(i);
            boolean isLast = (i == crumbs.size() - 1);

            if (isLast) {
                Label currentLabel = new Label(crumb.text());
                currentLabel.getStyleClass().add("breadcrumb-current");
                getChildren().add(currentLabel);
            } else {
                Hyperlink link = new Hyperlink(crumb.text());
                link.getStyleClass().add("breadcrumb-item");
                link.setOnAction(e -> {
                    if (crumb.data() instanceof Runnable r) {
                        r.run();
                    }
                    if (onCrumbClicked != null) {
                        onCrumbClicked.accept(crumb);
                    }
                });
                getChildren().add(link);

                FontIcon separator = new FontIcon("fth-chevron-right");
                separator.getStyleClass().add("breadcrumb-separator");
                getChildren().add(separator);
            }
        }
    }
}

