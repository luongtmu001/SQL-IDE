package app.ui.editor;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.prefs.Preferences;

/**
 * SCR-MAIN-003 tab strip drawn by the app (Phase 3 follow-up, user request 2026-11-05).
 * The TabPane keeps the tabs and their content; its own header is hidden.
 *
 * <ul>
 *   <li>One row (default): tabs packed from the left, a soft gradient on the right edge tells
 *       that more tabs are hidden; mouse wheel scrolls the row; the selected tab is kept visible.</li>
 *   <li>"Hiển thị tất cả tab": tabs wrap onto more rows (FlowPane).</li>
 *   <li>The actions group (format / split) sits in its own column, never over the tabs.</li>
 *   <li>The list button opens every tab (check = active) and the "show all" toggle (LLTool tabs.js).</li>
 * </ul>
 */
public final class EditorTabStrip extends VBox {

    private static final Preferences PREFS = Preferences.userNodeForPackage(EditorTabStrip.class);
    private static final String KEY_SHOW_ALL = "editor_tabs_show_all";
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    /** Shared by every strip (primary + split) so the toggle applies to both. */
    private static final BooleanProperty SHOW_ALL = new SimpleBooleanProperty(PREFS.getBoolean(KEY_SHOW_ALL, false));

    static {
        SHOW_ALL.addListener((o, a, b) -> PREFS.putBoolean(KEY_SHOW_ALL, b));
    }

    private final TabPane tabPane;
    private final Runnable onNewTab;

    private final HBox bar = new HBox();
    private final HBox rowBox = new HBox();          // one-row mode
    private final FlowPane flowBox = new FlowPane(); // show-all mode
    private final StackPane viewport = new StackPane();
    private final Region fadeRight = new Region();
    private final Button addButton = new Button();
    private final HBox actionsSlot = new HBox();
    private final Button listButton = new Button();
    private double offset = 0;

    public EditorTabStrip(TabPane tabPane, Runnable onNewTab, Node actions) {
        this.tabPane = tabPane;
        this.onNewTab = onNewTab;
        getStyleClass().add("editor-tab-strip-host");
        tabPane.getStyleClass().add("custom-strip");

        bar.getStyleClass().add("editor-tab-strip");
        bar.setAlignment(Pos.TOP_LEFT);

        rowBox.getStyleClass().add("editor-tab-row");
        rowBox.setAlignment(Pos.CENTER_LEFT);
        rowBox.setMinWidth(Region.USE_PREF_SIZE);
        flowBox.getStyleClass().add("editor-tab-row");
        flowBox.setRowValignment(javafx.geometry.VPos.CENTER);
        // no feedback between the flow width and the viewport width: the flow takes the
        // viewport width when laid out; its height is measured in layoutChildren()
        flowBox.setMinWidth(0);
        flowBox.setPrefWidth(0);
        flowBox.setPrefWrapLength(0);

        fadeRight.getStyleClass().add("editor-tab-fade");
        fadeRight.setMouseTransparent(true);
        fadeRight.setPrefWidth(36);
        fadeRight.setMaxWidth(36);
        StackPane.setAlignment(fadeRight, Pos.CENTER_RIGHT);

        viewport.getStyleClass().add("editor-tab-viewport");
        viewport.setAlignment(Pos.CENTER_LEFT);
        viewport.setMinWidth(0);
        HBox.setHgrow(viewport, Priority.ALWAYS);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        viewport.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        viewport.widthProperty().addListener((o, a, b) -> Platform.runLater(this::updateOverflow));

        addButton.setGraphic(new FontIcon("fth-plus"));
        addButton.getStyleClass().add("editor-tab-add");
        addButton.setFocusTraversable(false);
        addButton.setTooltip(new Tooltip("Tab mới (Ctrl+N)"));
        addButton.setOnAction(e -> {
            if (this.onNewTab != null) this.onNewTab.run();
        });

        actionsSlot.getStyleClass().add("editor-tab-actions");
        actionsSlot.setAlignment(Pos.CENTER_RIGHT);
        if (actions != null) actionsSlot.getChildren().add(actions);

        listButton.setGraphic(new FontIcon("fth-chevron-down"));
        listButton.getStyleClass().add("editor-tab-list-button");
        listButton.setFocusTraversable(false);
        listButton.setTooltip(new Tooltip("Danh sách tab"));
        listButton.setOnAction(e -> showTabList());

        // actions + tab list share one 34px box pinned to the first row (aligned in both modes)
        HBox rightBox = new HBox(actionsSlot, listButton);
        rightBox.getStyleClass().add("editor-tab-right");
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        rightBox.setMinHeight(ROW_HEIGHT);
        rightBox.setPrefHeight(ROW_HEIGHT);
        rightBox.setMaxHeight(ROW_HEIGHT);
        rightBox.setMinWidth(Region.USE_PREF_SIZE);
        bar.getChildren().addAll(viewport, rightBox);
        getChildren().add(bar);

        tabPane.getTabs().addListener((ListChangeListener<Tab>) c -> rebuild());
        tabPane.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            refreshSelection();
            Platform.runLater(this::revealSelected);
        });
        SHOW_ALL.addListener((o, a, b) -> rebuild());
        rebuild();
    }

    /** Wraps the TabPane: strip on top, the TabPane (header hidden) below. */
    public static VBox wrap(TabPane tabPane, Runnable onNewTab, Node actions) {
        EditorTabStrip strip = new EditorTabStrip(tabPane, onNewTab, actions);
        VBox box = new VBox(strip, tabPane);
        box.getStyleClass().add("editor-pane-with-strip");
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        box.getProperties().put(EditorTabStrip.class, strip);
        return box;
    }

    public static BooleanProperty showAllProperty() {
        return SHOW_ALL;
    }

    // ------------------------------------------------------------------ build

    private void rebuild() {
        rowBox.getChildren().clear();
        flowBox.getChildren().clear();
        Pane target = SHOW_ALL.get() ? flowBox : rowBox;
        for (Tab t : tabPane.getTabs()) {
            if (t instanceof SqlEditorTab st) target.getChildren().add(chip(st));
        }
        target.getChildren().add(addButton);
        viewport.getChildren().setAll(target);
        if (!SHOW_ALL.get()) viewport.getChildren().add(fadeRight);
        viewport.getStyleClass().remove("wrap");
        if (SHOW_ALL.get()) viewport.getStyleClass().add("wrap");
        offset = SHOW_ALL.get() ? 0 : offset;
        applyOffset();
        refreshSelection();
        updateWrapHeight();
        Platform.runLater(() -> {
            updateWrapHeight();
            updateOverflow();
            revealSelected();
        });
    }

    private Node chip(SqlEditorTab tab) {
        StackPane chip = new StackPane(tab.getHeaderNode());
        chip.getStyleClass().add("editor-strip-tab");
        if (tab.isPinned()) chip.getStyleClass().add("pinned");
        StackPane.setAlignment(tab.getHeaderNode(), Pos.CENTER_LEFT);
        chip.getProperties().put(SqlEditorTab.class, tab);
        chip.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY) {
                tabPane.getSelectionModel().select(tab);
            }
        });
        chip.setOnContextMenuRequested(e -> {
            if (tab.getContextMenu() != null) tab.getContextMenu().show(chip, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        if (tab.getTooltip() != null) Tooltip.install(chip, tab.getTooltip());
        tab.setHoverSource(chip.hoverProperty());
        chip.widthProperty().addListener((o, a, b) -> {
            if (SHOW_ALL.get()) requestLayout();
        });
        return chip;
    }

    /** One-row mode: fixed height. Show-all: the real height is set in {@link #layoutChildren()}. */
    private void updateWrapHeight() {
        if (!SHOW_ALL.get()) {
            setViewportHeight(ROW_HEIGHT);
        } else {
            measured = -1;
            requestLayout();
        }
    }

    private double measured = -1;

    private void setViewportHeight(double h) {
        viewport.setMinHeight(h);
        viewport.setPrefHeight(h);
        viewport.setMaxHeight(h);
    }

    /**
     * Measures the wrapped rows after CSS and layout have given every tab its real size, so the
     * strip is exactly as tall as its rows: no empty row when everything fits on one line,
     * no clipped second row (bugs reported 2026-11-05).
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!SHOW_ALL.get()) return;
        double w = viewport.getWidth();
        if (w <= 0) return;
        double h = Math.max(ROW_HEIGHT, Math.ceil(flowBox.prefHeight(w)));
        if (Math.abs(h - measured) > 0.5) {
            measured = h;
            setViewportHeight(h);
            Platform.runLater(() -> {
                requestLayout();
                if (getParent() != null) getParent().requestLayout();
            });
        }
    }

    private static final double ROW_HEIGHT = 34;

    private void refreshSelection() {
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        for (Node n : (SHOW_ALL.get() ? flowBox : rowBox).getChildren()) {
            Object t = n.getProperties().get(SqlEditorTab.class);
            if (t != null) n.pseudoClassStateChanged(SELECTED, t == sel);
        }
    }

    // ------------------------------------------------------------------ one-row scrolling + fade

    private void onScroll(ScrollEvent e) {
        if (SHOW_ALL.get()) return;
        double delta = e.getDeltaX() != 0 ? e.getDeltaX() : e.getDeltaY();
        offset = clamp(offset + delta);
        applyOffset();
        updateOverflow();
        e.consume();
    }

    private double maxOffset() {
        return Math.min(0, viewport.getWidth() - rowBox.prefWidth(-1));
    }

    private double clamp(double v) {
        return Math.max(maxOffset(), Math.min(0, v));
    }

    private void applyOffset() {
        rowBox.setTranslateX(SHOW_ALL.get() ? 0 : offset);
    }

    private void updateOverflow() {
        if (SHOW_ALL.get()) {
            fadeRight.setVisible(false);
            return;
        }
        offset = clamp(offset);
        applyOffset();
        boolean moreRight = rowBox.prefWidth(-1) + offset > viewport.getWidth() + 1;
        fadeRight.setVisible(moreRight);
    }

    /** Keeps the selected tab inside the visible part of the row. */
    private void revealSelected() {
        if (SHOW_ALL.get()) return;
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        for (Node n : rowBox.getChildren()) {
            if (n.getProperties().get(SqlEditorTab.class) != sel) continue;
            Bounds b = n.getBoundsInParent();
            double left = b.getMinX() + offset, right = b.getMaxX() + offset;
            double w = viewport.getWidth() - fadeRight.getPrefWidth();
            if (left < 0) offset -= left;
            else if (right > w) offset -= (right - w);
            offset = clamp(offset);
            applyOffset();
            updateOverflow();
            return;
        }
    }

    // ------------------------------------------------------------------ list menu (LLTool overflow menu)

    private void showTabList() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("editor-tab-list-menu");
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        ToggleGroup group = new ToggleGroup();
        for (Tab t : tabPane.getTabs()) {
            if (!(t instanceof SqlEditorTab st)) continue;
            RadioMenuItem mi = new RadioMenuItem(st.getTabTitle());
            mi.setToggleGroup(group);
            mi.setGraphic(st.createListIcon());
            mi.setSelected(t == sel);
            if (t == sel) mi.getStyleClass().add("active-tab-item");
            mi.setOnAction(e -> tabPane.getSelectionModel().select(t));
            menu.getItems().add(mi);
        }
        menu.getItems().add(new SeparatorMenuItem());
        CheckMenuItem showAll = new CheckMenuItem("Hiển thị tất cả tab");
        showAll.setSelected(SHOW_ALL.get());
        showAll.setOnAction(e -> SHOW_ALL.set(showAll.isSelected()));
        menu.getItems().add(showAll);
        menu.show(listButton, Side.BOTTOM, 0, 2);
    }
}
