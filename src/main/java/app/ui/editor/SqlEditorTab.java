package app.ui.editor;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.function.Consumer;

public class SqlEditorTab extends Tab {

    private final String documentId;
    private final SqlCodeArea codeArea;
    private String originalContent;
    private String currentContent;
    private String language = "sql";
    private final BooleanProperty isDirty = new SimpleBooleanProperty(false);
    private final BooleanProperty isPinned = new SimpleBooleanProperty(false);
    private Path filePath;
    private String connectionName;
    private String databaseName;
    private String schemaName;
    private TabContentType tabContentType = TabContentType.SQL_EDITOR;

    public enum TabContentType {
        SQL_EDITOR,
        TABLE_DATA_EDITOR,
        TABLE_DESIGNER,
        SCHEMA_COMPARE,
        DEPENDENCY_GRAPH,
        OBJECT_SEARCH,
        SQL_VISUALIZER,
        VIEW_PANEL
    }

    public TabContentType getTabContentType() {
        return tabContentType;
    }

    public void setTabContentType(TabContentType tabContentType) {
        this.tabContentType = tabContentType;
    }

    public boolean isSqlTextTab() {
        return tabContentType == TabContentType.SQL_EDITOR && !isCustomContent();
    }

    // ---- header (Phase 3, Gate B 2026-11-05: VS Code style)
    private final HBox headerLayout = new HBox(5);
    private final Region connectionDot = new Region();
    private final StackPane iconSlot = new StackPane();
    private final javafx.scene.image.ImageView connectionLogo = new javafx.scene.image.ImageView();
    private final FontIcon fileIcon = new FontIcon("fth-file-text");
    private javafx.beans.value.ObservableBooleanValue hoverSource;
    private final Label titleLabel = new Label();
    private final StackPane trailingSlot = new StackPane();
    private final Label dirtyIndicator = new Label("●");
    private final Button closeButton = new Button();
    private final Button pinButton = new Button();
    private final MenuItem togglePin = new MenuItem("Ghim tab");

    // ---- content: banner (not connected) above the editor node
    private final VBox contentBox = new VBox();
    private final HBox banner = new HBox(8);
    private final Label bannerText = new Label();
    private final FontIcon bannerIcon = new FontIcon("mdi2p-power-plug-off-outline");
    private final Button bannerConnect = new Button("Kết nối");
    private final StackPane editorSlot = new StackPane();
    private boolean bannerDismissed = false;

    /** Banner actions, set once by the EditorController. */
    public interface BannerActions {
        void connect(SqlEditorTab tab);
        void changeConnection(SqlEditorTab tab);
    }

    private static BannerActions bannerActions;

    public static void setBannerActions(BannerActions actions) {
        bannerActions = actions;
    }

    public SqlEditorTab(String title, String initialContent, Path filePath, Consumer<SqlEditorTabAction> actionHandler) {
        this(title, initialContent, filePath, null, null, null, actionHandler);
    }

    public SqlEditorTab(String title, String initialContent, Path filePath,
                        String connectionName, String databaseName, String schemaName,
                        Consumer<SqlEditorTabAction> actionHandler) {
        this.documentId = "doc_" + java.util.UUID.randomUUID().toString().replace("-", "");
        this.originalContent = initialContent != null ? initialContent : "";
        this.currentContent = this.originalContent;
        this.filePath = filePath;
        this.connectionName = connectionName;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.codeArea = new SqlCodeArea(this.originalContent);

        setText(""); // custom graphic header
        setClosable(false); // own close button in the header (dirty dot <-> x on hover)
        setupContent();
        setEditorNode(codeArea);

        setupHeader(title, actionHandler);
        setupDirtyTracking();
        setupContextMenu(actionHandler);
    }

    // ------------------------------------------------------------------ content + banner

    private void setupContent() {
        contentBox.getStyleClass().add("editor-tab-content");
        banner.getStyleClass().add("editor-connection-banner");
        banner.setAlignment(Pos.CENTER_LEFT);
        bannerIcon.getStyleClass().add("banner-icon");
        bannerText.setMinWidth(0);
        bannerText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bannerText, Priority.ALWAYS);
        bannerConnect.getStyleClass().addAll("banner-button", "accent");
        bannerConnect.setOnAction(e -> {
            if (bannerActions != null) bannerActions.connect(this);
        });
        Button change = new Button("Đổi kết nối");
        change.getStyleClass().add("banner-button");
        change.setOnAction(e -> {
            if (bannerActions != null) bannerActions.changeConnection(this);
        });
        Button dismiss = new Button();
        dismiss.setGraphic(new FontIcon("fth-x"));
        dismiss.getStyleClass().add("banner-close");
        dismiss.setTooltip(new Tooltip("Ẩn thông báo"));
        dismiss.setOnAction(e -> {
            bannerDismissed = true;
            showBanner(false);
        });
        banner.getChildren().addAll(bannerIcon, bannerText, bannerConnect, change, dismiss);
        showBanner(false);
        VBox.setVgrow(editorSlot, Priority.ALWAYS);
        contentBox.getChildren().addAll(banner, editorSlot);
        setContent(contentBox);
    }

    private void showBanner(boolean visible) {
        banner.setVisible(visible);
        banner.setManaged(visible);
    }

    private Node customContent;
    private String customKey;

    /** Turns this tab into a view tab (no SQL editor): {@code key} identifies it so the same view is opened once. */
    public void setCustomContent(String key, Node view) {
        this.customKey = key;
        this.customContent = view;
        editorSlot.getChildren().setAll(view);
    }

    public boolean isCustomContent() {
        return customContent != null;
    }

    public String getCustomKey() {
        return customKey;
    }

    /** Puts the editor (shared Monaco host or the fallback code area) under the banner; null detaches it. */
    public void setEditorNode(Node node) {
        if (customContent != null) { // a view tab (e.g. Permissions): the editor never replaces it
            if (editorSlot.getChildren().size() != 1 || editorSlot.getChildren().get(0) != customContent) {
                editorSlot.getChildren().setAll(customContent);
            }
            return;
        }
        if (node == codeArea && codeAreaStale) syncCodeArea();
        if (node == null) {
            editorSlot.getChildren().clear();
        } else if (editorSlot.getChildren().size() != 1 || editorSlot.getChildren().get(0) != node) {
            editorSlot.getChildren().setAll(node);
        }
    }

    public Node getEditorNode() {
        return editorSlot.getChildren().isEmpty() ? null : editorSlot.getChildren().get(0);
    }

    /**
     * Shows "Chưa kết nối" when this tab points to a connection that is not connected
     * (LLTool tabs.js banner). {@code connected == null}: the tab has no connection.
     */
    public void updateConnectionBanner(String connection, String database, Boolean connected) {
        updateConnectionBanner(connection, database,
                connected == null ? BannerState.HIDDEN : (connected ? BannerState.HIDDEN : BannerState.DISCONNECTED), null);
    }

    /** What the banner says (LLTool tabs.js: disconnected / reconnecting / lost). */
    public enum BannerState { HIDDEN, DISCONNECTED, CONNECTING, LOST }

    private static final javafx.css.PseudoClass PC_LOST = javafx.css.PseudoClass.getPseudoClass("lost");
    private BannerState bannerState = BannerState.HIDDEN;

    public void updateConnectionBanner(String connection, String database, BannerState state, String error) {
        if (state != bannerState) {
            bannerDismissed = false; // a new situation shows again even if the previous one was dismissed
            bannerState = state;
        }
        if (state == BannerState.HIDDEN || bannerDismissed) {
            showBanner(false);
            return;
        }
        String target = connection + (database != null && !database.isBlank() ? " (" + database + ")" : "");
        banner.pseudoClassStateChanged(PC_LOST, state == BannerState.LOST);
        switch (state) {
            case LOST -> {
                bannerIcon.setIconLiteral("mdi2a-alert-outline");
                bannerText.setText("Mất kết nối: tab này trỏ tới " + target + " nhưng kết nối đã bị ngắt."
                        + (error != null && !error.isBlank() ? " (" + error + ")" : ""));
                bannerConnect.setText("Kết nối lại");
                bannerConnect.setDisable(false);
            }
            case CONNECTING -> {
                bannerIcon.setIconLiteral("mdi2p-power-plug-outline");
                bannerText.setText("Đang kết nối tới " + target + "…");
                bannerConnect.setText("Kết nối");
                bannerConnect.setDisable(true);
            }
            default -> {
                bannerIcon.setIconLiteral("mdi2p-power-plug-off-outline");
                bannerText.setText("Chưa kết nối: tab này trỏ tới " + target + " nhưng chưa được kết nối.");
                bannerConnect.setText("Kết nối");
                bannerConnect.setDisable(false);
            }
        }
        showBanner(true);
    }

    /** Called when the connection becomes connected or changes, so a dismissed banner can return later. */
    public void resetBannerDismissed() {
        bannerDismissed = false;
    }

    // ------------------------------------------------------------------ header

    private void setupHeader(String title, Consumer<SqlEditorTabAction> actionHandler) {
        headerLayout.setAlignment(Pos.CENTER_LEFT);
        headerLayout.getStyleClass().add("editor-tab-header");

        connectionDot.getStyleClass().add("editor-tab-conn-dot");
        connectionDot.setMinSize(8, 8);
        connectionDot.setPrefSize(8, 8);
        connectionDot.setMaxSize(8, 8);
        showDot(false);

        titleLabel.setText(title);
        titleLabel.getStyleClass().add("editor-tab-title");
        titleLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        titleLabel.setPadding(javafx.geometry.Insets.EMPTY);
        titleLabel.setMaxWidth(160);
        setTooltip(new Tooltip(title));

        dirtyIndicator.getStyleClass().add("editor-tab-dirty");
        closeButton.setGraphic(new FontIcon("fth-x"));
        closeButton.getStyleClass().add("editor-tab-close");
        closeButton.setFocusTraversable(false);
        closeButton.setTooltip(new Tooltip("Đóng (Ctrl+W)"));
        closeButton.setOnAction(e -> {
            if (actionHandler != null) actionHandler.accept(new SqlEditorTabAction(SqlEditorTabAction.Type.CLOSE, this));
        });
        pinButton.setGraphic(new FontIcon("mdi2p-pin-outline"));
        pinButton.getStyleClass().add("editor-tab-pin");
        pinButton.setFocusTraversable(false);
        pinButton.setTooltip(new Tooltip("Bỏ ghim"));
        pinButton.setOnAction(e -> setPinned(false));

        trailingSlot.getStyleClass().add("editor-tab-trailing");
        trailingSlot.setMinSize(16, 16);
        trailingSlot.setPrefSize(16, 16);
        trailingSlot.setMaxSize(16, 16);
        trailingSlot.getChildren().addAll(dirtyIndicator, closeButton, pinButton);

        connectionLogo.setFitWidth(14);
        connectionLogo.setFitHeight(14);
        connectionLogo.setPreserveRatio(true);
        fileIcon.getStyleClass().add("editor-tab-file-icon");
        iconSlot.setMinSize(16, 16);
        iconSlot.setPrefSize(16, 16);
        iconSlot.setMaxSize(16, 16);
        iconSlot.getChildren().setAll(fileIcon);

        // user request 2026-11-05: [connection icon] title [status dot] [x / ● / pin]
        headerLayout.getChildren().addAll(iconSlot, titleLabel, connectionDot, trailingSlot);
        // the header is shown by EditorTabStrip, not by the TabPane

        // middle click closes (VS Code)
        headerLayout.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.MIDDLE && actionHandler != null && !isPinned.get()) {
                actionHandler.accept(new SqlEditorTabAction(SqlEditorTabAction.Type.CLOSE, this));
            }
        });

        headerLayout.hoverProperty().addListener((o, a, b) -> updateTrailing());
        hoverSource = headerLayout.hoverProperty();
        selectedProperty().addListener((o, a, b) -> updateTrailing());
        isDirty.addListener((o, a, b) -> updateTrailing());
        isPinned.addListener((obs, oldVal, pinned) -> {
            togglePin.setText(pinned ? "Bỏ ghim tab" : "Ghim tab");
            getStyleClass().remove("pinned");
            if (pinned) getStyleClass().add("pinned");
            updateTrailing();
        });
        updateTrailing();
    }

    /** pinned: pin icon · hover / selected: × · dirty: ● · otherwise empty (slot keeps its width). */
    private void updateTrailing() {
        boolean pinned = isPinned.get();
        boolean hover = hoverSource != null ? hoverSource.get() : headerLayout.isHover();
        boolean hot = hover || isSelected();
        boolean showClose = !pinned && hot;
        boolean showDirty = !pinned && !hot && isDirty.get();
        pinButton.setVisible(pinned);
        closeButton.setVisible(showClose);
        dirtyIndicator.setVisible(showDirty);
        // selected + dirty: VS Code keeps the dot until the tab is hovered
        if (!pinned && isSelected() && isDirty.get() && !hover) {
            closeButton.setVisible(false);
            dirtyIndicator.setVisible(true);
        }
    }

    /** The whole tab header (icon, title, status dot, close slot) for {@link EditorTabStrip}. */
    public Node getHeaderNode() {
        return headerLayout;
    }

    /** The strip chip drives hover (× on hover) because it is larger than the header. */
    public void setHoverSource(javafx.beans.value.ObservableBooleanValue source) {
        hoverSource = source;
        source.addListener((o, a, b) -> updateTrailing());
        updateTrailing();
    }

    /** Logo of the tab's connection (DB type); null shows the plain file icon. */
    public void setConnectionIcon(javafx.scene.image.Image logo) {
        if (logo == null) {
            iconSlot.getChildren().setAll(fileIcon);
        } else {
            connectionLogo.setImage(logo);
            iconSlot.getChildren().setAll(connectionLogo);
        }
    }

    public void setFileIconLiteral(String iconLiteral) {
        if (iconLiteral != null) {
            fileIcon.setIconLiteral(iconLiteral);
        }
    }

    /** Copy of the tab icon for the tab list menu. */
    public Node createListIcon() {
        if (iconSlot.getChildren().contains(connectionLogo) && connectionLogo.getImage() != null) {
            javafx.scene.image.ImageView v = new javafx.scene.image.ImageView(connectionLogo.getImage());
            v.setFitWidth(14);
            v.setFitHeight(14);
            v.setPreserveRatio(true);
            return v;
        }
        return new FontIcon("fth-file-text");
    }

    private javafx.scene.control.Tooltip sessionTip;

    /** Phase 8 / D1: tooltip of the status dot with this tab's own session (state, database, PID). */
    public void setSessionTooltip(String text) {
        if (text == null) {
            if (sessionTip != null) javafx.scene.control.Tooltip.uninstall(connectionDot, sessionTip);
            return;
        }
        if (sessionTip == null) {
            sessionTip = new javafx.scene.control.Tooltip();
            javafx.scene.control.Tooltip.install(connectionDot, sessionTip);
        }
        sessionTip.setText(text);
    }

    /** Status dot after the title: connection colour when connected, grey when not; null hides it. */
    public void setConnectionColor(String hex) {
        if (hex == null) {
            showDot(false);
        } else {
            connectionDot.setStyle("-fx-background-color: " + hex + ";");
            showDot(true);
        }
    }

    private void showDot(boolean v) {
        connectionDot.setVisible(v);
        connectionDot.setManaged(v);
    }

    private void setupDirtyTracking() {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            EDITS.incrementAndGet();
            this.currentContent = newText;
            isDirty.set(!newText.equals(originalContent));
        });
    }

    // ------------------------------------------------------------------ context menu (LLTool tabs.js)

    private void setupContextMenu(Consumer<SqlEditorTabAction> h) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("editor-tab-menu");

        MenuItem save = item("Lưu", "fth-save", "Ctrl+S", SqlEditorTabAction.Type.SAVE, h);
        MenuItem close = item("Đóng", "fth-x", "Ctrl+W", SqlEditorTabAction.Type.CLOSE, h);
        MenuItem closeOthers = item("Đóng các tab khác", null, null, SqlEditorTabAction.Type.CLOSE_OTHERS, h);
        MenuItem closeRight = item("Đóng các tab bên phải", null, null, SqlEditorTabAction.Type.CLOSE_RIGHT, h);
        MenuItem closeAll = item("Đóng tất cả", null, null, SqlEditorTabAction.Type.CLOSE_ALL, h);

        togglePin.setGraphic(new FontIcon("mdi2p-pin-outline"));
        togglePin.setOnAction(e -> setPinned(!isPinned.get()));

        MenuItem duplicate = item("Nhân bản tab", "fth-copy", null, SqlEditorTabAction.Type.DUPLICATE, h);
        MenuItem rename = new MenuItem("Đổi tên tab…");
        rename.setGraphic(new FontIcon("fth-edit-2"));
        rename.setOnAction(e -> promptRename());
        MenuItem copyTitle = new MenuItem("Sao chép tiêu đề");
        copyTitle.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(getTabTitle());
            Clipboard.getSystemClipboard().setContent(c);
        });

        Menu split = new Menu("Chia editor");
        split.setGraphic(new FontIcon("fth-columns"));
        split.getItems().addAll(
                item("Sang phải", null, null, SqlEditorTabAction.Type.SPLIT_RIGHT, h),
                item("Sang trái", null, null, SqlEditorTabAction.Type.SPLIT_LEFT, h),
                item("Xuống dưới", null, null, SqlEditorTabAction.Type.SPLIT_DOWN, h),
                item("Lên trên", null, null, SqlEditorTabAction.Type.SPLIT_UP, h),
                new SeparatorMenuItem(),
                item("Đóng chế độ chia", null, null, SqlEditorTabAction.Type.CLOSE_SPLIT, h));

        MenuItem compare = deferred("So sánh với…");
        MenuItem compareDisk = deferred("So sánh với file trên đĩa…");

        menu.getItems().addAll(
                save,
                new SeparatorMenuItem(),
                close, closeOthers, closeRight, closeAll,
                new SeparatorMenuItem(),
                togglePin, duplicate, rename, copyTitle,
                new SeparatorMenuItem(),
                split,
                new SeparatorMenuItem(),
                compare, compareDisk);
        menu.setOnShowing(e -> {
            boolean pinned = isPinned.get();
            close.setDisable(pinned);
        });
        setContextMenu(menu);
    }

    private MenuItem item(String text, String icon, String accel, SqlEditorTabAction.Type type, Consumer<SqlEditorTabAction> h) {
        MenuItem mi = new MenuItem(text);
        if (icon != null) mi.setGraphic(new FontIcon(icon));
        if (accel != null) mi.setAccelerator(javafx.scene.input.KeyCombination.keyCombination(accel.replace("Ctrl", "Shortcut")));
        mi.setOnAction(e -> {
            if (h != null) h.accept(new SqlEditorTabAction(type, this));
        });
        return mi;
    }

    private static MenuItem deferred(String text) {
        MenuItem mi = new MenuItem(text + " (chưa hỗ trợ)");
        mi.setDisable(true);
        mi.getStyleClass().add("deferred-item");
        return mi;
    }

    private void promptRename() {
        javafx.stage.Window owner = getTabPane() != null && getTabPane().getScene() != null ? getTabPane().getScene().getWindow() : null;
        app.ui.dialogs.TextPromptDialog.show(owner, "Đổi tên tab", "fth-edit-2", "Tên tab", titleLabel.getText(), "Đổi tên")
                .ifPresent(this::setTabTitle);
    }

    public void setTabTitle(String title) {
        EDITS.incrementAndGet();
        titleLabel.setText(title);
        if (getTooltip() != null) getTooltip().setText(title); else setTooltip(new Tooltip(title));
    }

    public void markSaved() {
        this.originalContent = this.currentContent != null ? this.currentContent : codeArea.getText();
        this.isDirty.set(false);
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCurrentContent() {
        return currentContent != null ? currentContent : codeArea.getText();
    }

    /**
     * Bumped on every change a tab snapshot holds (text, title, file, connection, pinned): the periodic
     * auto-save skips building the snapshot when nothing changed (performance rules: no repeated work).
     */
    private static final java.util.concurrent.atomic.AtomicLong EDITS = new java.util.concurrent.atomic.AtomicLong();

    public static long editCount() {
        return EDITS.get();
    }

    public void setCurrentContent(String content) {
        EDITS.incrementAndGet();
        this.currentContent = content != null ? content : "";
        // Monaco sends the whole text on every keystroke. The fallback CodeArea re-highlights the whole text on
        // each change, so it is only updated while it is the visible editor; otherwise once, when it is needed.
        if (editorSlot.getChildren().contains(codeArea)) {
            syncCodeArea();
        } else {
            codeAreaStale = true;
        }
        isDirty.set(!this.currentContent.equals(originalContent));
    }

    /** True when {@link #currentContent} changed while the CodeArea was not shown. */
    private boolean codeAreaStale;

    private void syncCodeArea() {
        codeAreaStale = false;
        if (!codeArea.getText().equals(this.currentContent)) {
            codeArea.replaceText(this.currentContent);
        }
    }

    public SqlCodeArea getCodeArea() {
        if (codeAreaStale) syncCodeArea();
        return codeArea;
    }

    public String getTabTitle() {
        return titleLabel.getText();
    }

    public boolean isDirty() {
        return isDirty.get();
    }

    public BooleanProperty dirtyProperty() {
        return isDirty;
    }

    public boolean isPinned() {
        return isPinned.get();
    }

    public void setPinned(boolean pinned) {
        EDITS.incrementAndGet();
        this.isPinned.set(pinned);
    }

    public BooleanProperty pinnedProperty() {
        return isPinned;
    }

    public Path getFilePath() {
        return filePath;
    }

    public void setFilePath(Path filePath) {
        EDITS.incrementAndGet();
        this.filePath = filePath;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        EDITS.incrementAndGet();
        this.connectionName = connectionName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        EDITS.incrementAndGet();
        this.databaseName = databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        EDITS.incrementAndGet();
        this.schemaName = schemaName;
    }

    public static class SqlEditorTabAction {
        public enum Type {
            CLOSE,
            CLOSE_OTHERS,
            CLOSE_ALL,
            CLOSE_UNMODIFIED,
            SPLIT_RIGHT,
            SPLIT_DOWN,
            SAVE,
            CLOSE_RIGHT,
            DUPLICATE,
            SPLIT_LEFT,
            SPLIT_UP,
            CLOSE_SPLIT
        }

        private final Type type;
        private final SqlEditorTab tab;

        public SqlEditorTabAction(Type type, SqlEditorTab tab) {
            this.type = type;
            this.tab = tab;
        }

        public Type getType() {
            return type;
        }

        public SqlEditorTab getTab() {
            return tab;
        }
    }
}

