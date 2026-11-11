package app.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toolbar of a workspace tab: left / center / right groups on one row.
 *
 * R-UI-13.8: labels are never cut with an ellipsis. When the row does not fit, buttons move into the
 * "…" menu - low-priority ones first (last added first), then the remaining plain buttons.
 * The decision is taken from the measured width of the content, not from a fixed threshold.
 */
public class WorkspaceToolbar extends HBox {

    public enum Priority {
        HIGH,
        LOW
    }

    private enum ItemOrigin { LEFT, RIGHT }

    private final HBox leftBox = new HBox();
    private final HBox centerBox = new HBox();
    private final HBox rightBox = new HBox();
    private final Region spacerLeft = new Region();
    private final Region spacerRight = new Region();

    private final MenuButton overflowMenu = new MenuButton();
    private final Map<Node, Priority> nodePriorities = new LinkedHashMap<>();
    private final Map<Node, ItemOrigin> nodeOrigins = new LinkedHashMap<>();
    /** Nodes currently shown in the "…" menu, in the order they were hidden. */
    private final List<Node> hiddenNodes = new ArrayList<>();
    private boolean updating;

    public WorkspaceToolbar() {
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("workspace-toolbar");

        for (HBox box : List.of(leftBox, centerBox, rightBox)) {
            box.getStyleClass().add("workspace-toolbar-group");
            box.setMinWidth(Region.USE_PREF_SIZE);
        }
        leftBox.setAlignment(Pos.CENTER_LEFT);
        centerBox.setAlignment(Pos.CENTER);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        HBox.setHgrow(spacerLeft, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(spacerRight, javafx.scene.layout.Priority.ALWAYS);
        spacerLeft.setMinWidth(0);
        spacerRight.setMinWidth(0);

        overflowMenu.setGraphic(Icons.of("fth-more-horizontal", IconScale.MD));
        overflowMenu.getStyleClass().addAll("button-subtle", "toolbar-overflow-menu");
        overflowMenu.setMinWidth(Region.USE_PREF_SIZE);
        overflowMenu.setVisible(false);
        overflowMenu.setManaged(false);

        getChildren().addAll(leftBox, spacerLeft, centerBox, spacerRight, rightBox, overflowMenu);
        setMinWidth(0);

        widthProperty().addListener((obs, oldW, newW) -> updateOverflow(newW.doubleValue()));
    }

    public void addLeft(Node node) {
        addLeft(node, Priority.HIGH);
    }

    public void addLeft(Node node, Priority priority) {
        register(node, priority, ItemOrigin.LEFT);
        leftBox.getChildren().add(node);
    }

    public void addCenter(Node node) {
        keepLabelWhole(node);
        centerBox.getChildren().add(node);
    }

    public void addRight(Node node) {
        addRight(node, Priority.HIGH);
    }

    public void addRight(Node node, Priority priority) {
        register(node, priority, ItemOrigin.RIGHT);
        rightBox.getChildren().add(node);
    }

    private void register(Node node, Priority priority, ItemOrigin origin) {
        keepLabelWhole(node);
        nodePriorities.put(node, priority);
        nodeOrigins.put(node, origin);
    }

    /** A labelled control never shrinks below its text (no "Lư…"). */
    private static void keepLabelWhole(Node node) {
        if (node instanceof Labeled labeled) {
            labeled.setMinWidth(Region.USE_PREF_SIZE);
        }
    }

    /** Kept for source compatibility; overflow is now measured, the threshold is not used. */
    @Deprecated
    public void setCollapseThreshold(double threshold) {
    }

    /** Re-evaluates which nodes fit in {@code width} and moves the rest into the "…" menu. */
    public void updateOverflow(double width) {
        if (width <= 0 || updating) return;
        updating = true;
        try {
            // start from the full toolbar, then hide candidates until it fits
            hiddenNodes.clear();
            rebuildBoxes();
            List<Node> candidates = overflowCandidates();
            int next = 0;
            while (requiredWidth() > width && next < candidates.size()) {
                hiddenNodes.add(candidates.get(next++));
                rebuildBoxes();
            }
            rebuildMenu();
        } finally {
            updating = false;
        }
    }

    /** Low-priority nodes first (last added goes first), then other plain buttons except the primary one. */
    private List<Node> overflowCandidates() {
        List<Node> low = new ArrayList<>();
        List<Node> high = new ArrayList<>();
        for (Map.Entry<Node, Priority> e : nodePriorities.entrySet()) {
            Node n = e.getKey();
            if (!canMoveToMenu(n)) continue;
            if (e.getValue() == Priority.LOW) {
                low.add(n);
            } else if (!n.getStyleClass().contains("accent")) {
                high.add(n);
            }
        }
        Collections.reverse(low);
        Collections.reverse(high);
        low.addAll(high);
        return low;
    }

    private static boolean canMoveToMenu(Node n) {
        if (n instanceof MenuButton) return false;
        if (!(n instanceof ButtonBase b)) return false;
        boolean hasText = b.getText() != null && !b.getText().isBlank();
        boolean hasTooltip = b.getTooltip() != null && b.getTooltip().getText() != null && !b.getTooltip().getText().isBlank();
        return hasText || hasTooltip;
    }

    private void rebuildBoxes() {
        List<Node> left = new ArrayList<>();
        List<Node> right = new ArrayList<>();
        for (Map.Entry<Node, ItemOrigin> e : nodeOrigins.entrySet()) {
            if (hiddenNodes.contains(e.getKey())) continue;
            (e.getValue() == ItemOrigin.LEFT ? left : right).add(e.getKey());
        }
        if (!leftBox.getChildren().equals(left)) leftBox.getChildren().setAll(left);
        if (!rightBox.getChildren().equals(right)) rightBox.getChildren().setAll(right);
        boolean showMenu = !hiddenNodes.isEmpty();
        overflowMenu.setVisible(showMenu);
        overflowMenu.setManaged(showMenu);
    }

    private double requiredWidth() {
        Insets in = getInsets();
        double gap = getSpacing();
        double w = in.getLeft() + in.getRight();
        int parts = 0;
        for (Node n : List.of(leftBox, centerBox, rightBox)) {
            if (n instanceof HBox box && box.getChildren().isEmpty()) continue;
            w += n.prefWidth(-1);
            parts++;
        }
        if (overflowMenu.isManaged()) {
            w += overflowMenu.prefWidth(-1);
            parts++;
        }
        // the two spacers are children too, so spacing applies between every pair
        return w + gap * Math.max(0, parts + 1);
    }

    private void rebuildMenu() {
        overflowMenu.getItems().clear();
        // show menu entries in toolbar order, not in the order they were hidden
        for (Node node : nodePriorities.keySet()) {
            if (!hiddenNodes.contains(node)) continue;
            MenuItem item = createMenuItem(node);
            if (item != null) overflowMenu.getItems().add(item);
        }
    }

    private MenuItem createMenuItem(Node node) {
        if (!(node instanceof ButtonBase btn)) return null;
        MenuItem mi;
        if (btn instanceof ToggleButton toggle) {
            CheckMenuItem check = new CheckMenuItem();
            check.setSelected(toggle.isSelected());
            check.setOnAction(e -> toggle.fire());
            toggle.selectedProperty().addListener((o, a, b) -> check.setSelected(b));
            mi = check;
        } else {
            mi = new MenuItem();
            mi.setOnAction(e -> btn.fire());
        }
        String text = btn.getText();
        if (text == null || text.isBlank()) {
            mi.setText(btn.getTooltip() != null ? btn.getTooltip().getText() : "");
        } else {
            mi.textProperty().bind(btn.textProperty());
        }
        // a Node has one parent: give the menu its own icon instead of taking the button's
        if (btn.getGraphic() instanceof FontIcon fi && fi.getIconLiteral() != null && !(mi instanceof CheckMenuItem)) {
            mi.setGraphic(Icons.of(fi.getIconLiteral()));
        }
        mi.disableProperty().bind(btn.disableProperty());
        return mi;
    }

    public HBox getLeftBox() {
        return leftBox;
    }

    public HBox getCenterBox() {
        return centerBox;
    }

    public HBox getRightBox() {
        return rightBox;
    }

    public MenuButton getOverflowMenu() {
        return overflowMenu;
    }

    /** Nodes currently moved into the "…" menu. */
    public List<Node> getHiddenLowPriorityNodes() {
        return Collections.unmodifiableList(hiddenNodes);
    }
}
