package app.ui.components;

import app.application.AppContext;
import app.controllers.MainController;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.framework.metadata.ObjectScriptService;
import app.framework.metadata.SqlScriptGenerator;
import app.framework.state.GlobalDesktopState;
import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.services.ConnectionColorService;
import app.services.ExplorerConnectionState;
import app.services.ExplorerConnectionState.Status;
import app.services.ExplorerGroupService;
import app.services.NotificationService;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.metadata.ColumnMetadata;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Object Explorer row (SCR-MAIN-002, Gate B 2026-11-05).
 *
 * <p>Layout: [colour dot] [icon] name  description … [count badge] [filter] [⋯].
 * Groups are folders, connections show their DB-type icon, status and colour; context menus
 * follow LLTool's {@code context-menu.js} registry (Q4 = a); features outside the migration scope
 * are shown disabled with "(chưa hỗ trợ)" (Q3 = b).</p>
 */
public class DatabaseTreeCell extends TreeCell<DatabaseNode> {

    /** Operations that need the Explorer controller (dialogs, profile updates, reload). */
    public interface ExplorerActions {
        void newConnection(String groupPath);
        void newGroup(String parentPath);
        void renameGroup(DatabaseNode group);
        void deleteGroup(DatabaseNode group);
        void editConnection(DatabaseNode connection);
        void duplicateConnection(DatabaseNode connection);
        void deleteConnection(DatabaseNode connection);
        void disconnect(TreeItem<DatabaseNode> connectionItem);
        void filterDatabases(DatabaseNode connection);
        void filterObjects(DatabaseNode category);
        void moveConnection(String connectionId, String targetGroupPath);
        void moveGroup(String groupPath, String targetParentPath);
        void reloadAll();
    }

    public static final DataFormat EXPLORER_ITEM = new DataFormat("application/x-sqlide-explorer-item");
    public static final String DEFERRED_SUFFIX = " (chưa hỗ trợ)";

    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");
    private static final Map<String, Image> IMAGE_CACHE = new HashMap<>();
    /** Not-connected connection icon: no colour. */
    private static final javafx.scene.effect.ColorAdjust GREYSCALE = new javafx.scene.effect.ColorAdjust(0, -1, 0.15, 0);

    private final HBox cellLayout = new HBox(5);
    /**
     * Row actions (reconnect, filter, refresh, connect, ⋯) float over the right end of the row on hover / selection
     * instead of taking space in it, so a name is only shortened while the pointer is on its row (LLTool parity).
     */
    private final HBox actionBar = new HBox(2);
    private final StackPane rowRoot = new StackPane();
    /** Non-interactive "a filter is active" mark that stays in the row when the action bar is hidden. */
    private final FontIcon filterIndicator = new FontIcon("fth-filter");
    private final StackPane iconBox = new StackPane();
    private final FontIcon icon = new FontIcon();
    private final ImageView imageIcon = new ImageView();
    private final Label nameLabel = new Label();
    /** Name split in before / match / after while an Explorer search is active. */
    private final HBox highlightBox = new HBox();
    /** Current Explorer search text (lower case, empty = none); matched text of names is highlighted. */
    private static volatile String searchQuery = "";

    public static void setSearchQuery(String q) {
        searchQuery = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
    }
    /** Small spinner in the icon slot of the "Đang tải…" child row shown under a node while it loads. */
    private final javafx.scene.control.ProgressIndicator loadingSpinner = new javafx.scene.control.ProgressIndicator();
    /** Connection row actions (LLTool): reconnect, refresh metadata, disconnect / connect. */
    private final Button reconnectButton = new Button();
    private final Button refreshButton = new Button();
    private final Button disconnectButton = new Button();
    private final Button connectButton = new Button();
    /** One reusable menu per cell: built when it opens, not on every updateItem. */
    private final ContextMenu cellMenu = new ContextMenu();
    private final Label descLabel = new Label();
    private final Label badgeLabel = new Label();
    private final Pane spacer = new Pane();
    private final Label countLabel = new Label();
    private final Button filterButton = new Button();
    private final Button moreButton = new Button();
    private final Tooltip tooltip = new Tooltip();

    // ---- appearance.iconSize (S3): size of the row icon, shared by every Explorer cell
    private static volatile int iconSize = 13;
    private int appliedIconSize = -1;

    /** Settings: Explorer icon size in px (10–24); the tree is refreshed by its owner after a change. */
    public static void setIconSize(int px) {
        iconSize = px < 10 || px > 24 ? 13 : px;
    }

    /** Only when the size changed for this cell: an inline style beats the stylesheet's 13px. */
    private void applyIconSize() {
        int px = iconSize;
        if (px == appliedIconSize) return;
        appliedIconSize = px;
        icon.setStyle("-fx-icon-size: " + px + "px;");
        imageIcon.setFitWidth(px + 1);
        imageIcon.setFitHeight(px + 1);
        double box = Math.max(16, px + 3);
        iconBox.setMinSize(box, box);
        iconBox.setPrefSize(box, box);
        iconBox.setMaxSize(box, box);
    }
    /** One tooltip per cell, text changed in updateItem (a new Tooltip per update is a popup window per scroll step). */
    private final Tooltip filterTip = new Tooltip();

    private final Consumer<DatabaseNode> onActionConsumer;
    private final ExplorerActions actions;
    /** Read-only list rendering (Connection Manager): same look as the Explorer, no menu / drag / ⋯. */
    private final boolean listMode;

    /** Cell for lists that must look exactly like the Object Explorer (e.g. "Kết nối đã lưu"). */
    public static DatabaseTreeCell forList() {
        return new DatabaseTreeCell(null, null, true);
    }

    public DatabaseTreeCell(Consumer<DatabaseNode> onActionConsumer) {
        this(onActionConsumer, null);
    }

    public DatabaseTreeCell(Consumer<DatabaseNode> onActionConsumer, ExplorerActions actions) {
        this(onActionConsumer, actions, false);
    }

    private DatabaseTreeCell(Consumer<DatabaseNode> onActionConsumer, ExplorerActions actions, boolean listMode) {
        this.onActionConsumer = onActionConsumer;
        this.actions = actions;
        this.listMode = listMode;
        getStyleClass().add("explorer-cell");

        cellLayout.setAlignment(Pos.CENTER_LEFT);
        cellLayout.setMinWidth(0);
        cellLayout.getStyleClass().add("explorer-cell-layout");

        icon.setIconSize(13);
        imageIcon.setFitWidth(14);
        imageIcon.setFitHeight(14);
        imageIcon.setPreserveRatio(true);
        iconBox.setMinSize(16, 16);
        iconBox.setPrefSize(16, 16);
        iconBox.setMaxSize(16, 16);
        iconBox.getChildren().addAll(icon, imageIcon);

        nameLabel.getStyleClass().add("tree-cell-name");
        nameLabel.setMinWidth(24);
        descLabel.getStyleClass().add("tree-cell-desc");
        descLabel.setMinWidth(0);
        badgeLabel.getStyleClass().add("tree-cell-badge");
        badgeLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spacer.setMinWidth(0);

        countLabel.getStyleClass().add("explorer-count-badge");
        countLabel.setMinWidth(40);
        countLabel.setAlignment(Pos.CENTER);

        filterButton.getStyleClass().addAll("explorer-row-button", "explorer-filter-button");
        filterButton.setGraphic(new FontIcon("fth-filter"));
        filterButton.setTooltip(filterTip);
        filterButton.setFocusTraversable(false);
        filterButton.visibleProperty().bind(filterButton.managedProperty());
        filterButton.setOnAction(e -> {
            if (actions == null || getItem() == null) return;
            if (getItem().getType() == DatabaseNodeType.CONNECTION) actions.filterDatabases(getItem());
            else actions.filterObjects(getItem());
        });

        moreButton.getStyleClass().addAll("explorer-row-button", "explorer-more-button");
        moreButton.setGraphic(new FontIcon("fth-more-horizontal"));
        moreButton.setFocusTraversable(false);
        moreButton.visibleProperty().bind(moreButton.managedProperty());
        moreButton.setTooltip(new Tooltip("Thao tác"));
        moreButton.setOnAction(e -> {
            ContextMenu menu = getContextMenu();
            if (menu != null) menu.show(moreButton, Side.BOTTOM, 0, 0);
        });

        loadingSpinner.setPrefSize(12, 12);
        loadingSpinner.setMinSize(12, 12);
        loadingSpinner.setMaxSize(12, 12);
        loadingSpinner.setMouseTransparent(true);
        loadingSpinner.getStyleClass().add("explorer-loading-spinner");
        loadingSpinner.setVisible(false);
        iconBox.getChildren().add(loadingSpinner);

        setupConnectionButton(reconnectButton, "fth-rotate-cw", null, "Reconnect (Kết nối lại)", this::reconnectNode);
        setupConnectionButton(refreshButton, "fth-refresh-cw", null, "Refresh Metadata", this::reloadCurrentNode);
        setupConnectionButton(disconnectButton, "fth-power", Color.web("#d6a100"), "Disconnect", () -> {
            if (actions != null) actions.disconnect(getTreeItem());
        });
        setupConnectionButton(connectButton, "fth-zap", Color.web("#2e9e5b"), "Connect (Kết nối)", this::connectNode);

        cellMenu.getStyleClass().add("explorer-context-menu");
        cellMenu.getItems().add(new MenuItem("…")); // never empty, so the first right click opens it
        cellMenu.setOnShowing(e -> {
            DatabaseNode it = getItem();
            if (it != null) cellMenu.getItems().setAll(createContextMenu(it).getItems());
        });

        filterIndicator.getStyleClass().add("explorer-filter-indicator");
        filterIndicator.setIconSize(11);
        filterIndicator.setMouseTransparent(true);
        filterIndicator.setVisible(false);
        filterIndicator.setManaged(false);
        cellLayout.getChildren().addAll(iconBox, nameLabel, badgeLabel, descLabel, spacer,
                countLabel, filterIndicator);

        actionBar.setAlignment(Pos.CENTER_RIGHT);
        actionBar.getStyleClass().add("explorer-action-bar");
        actionBar.setMaxWidth(Region.USE_PREF_SIZE);
        actionBar.setMaxHeight(Double.MAX_VALUE); // background covers the full row height
        actionBar.getChildren().addAll(reconnectButton, filterButton, refreshButton, disconnectButton, connectButton, moreButton);
        StackPane.setAlignment(actionBar, Pos.CENTER_RIGHT);
        rowRoot.setMinWidth(0);
        rowRoot.getChildren().addAll(cellLayout, actionBar);
        if (listMode) {
            actionBar.setVisible(false);
            actionBar.setManaged(false);
        } else {
            // only while the pointer is on the row (LLTool): a selected / just-connected row keeps its full name
            actionBar.visibleProperty().bind(hoverProperty());
        }

        tooltip.setShowDelay(Duration.millis(450));

        if (listMode) {
            moreButton.visibleProperty().unbind();
            moreButton.setVisible(false);
            moreButton.setManaged(false);
        } else {
            setupDragAndDrop();
            setupDoubleClickHandler();
        }
    }

    private void setupConnectionButton(Button b, String icon, Color color, String tip, Runnable action) {
        b.getStyleClass().addAll("explorer-row-button", "explorer-conn-button");
        FontIcon fi = new FontIcon(icon);
        if (color != null) fi.setIconColor(color);
        b.setGraphic(fi);
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> {
            e.consume();
            action.run();
        });
        b.setManaged(false);
        b.visibleProperty().bind(b.managedProperty()); // an unmanaged button would still be drawn at the bar's origin
    }

    private void reconnectNode() {
        TreeItem<DatabaseNode> ti = getTreeItem();
        if (ti == null || actions == null) return;
        actions.disconnect(ti);
        javafx.application.Platform.runLater(() -> {
            ti.setExpanded(true);
            if (ti.getValue() != null) GlobalDesktopState.getInstance().setActiveConnection(ti.getValue().getName());
        });
    }

    private void connectNode() {
        TreeItem<DatabaseNode> ti = getTreeItem();
        if (ti == null) return;
        ti.setExpanded(true);
        if (ti.getValue() != null) GlobalDesktopState.getInstance().setActiveConnection(ti.getValue().getName());
    }

    /** Never wider than the viewport: no horizontal scrolling, long names are ellipsised. */
    @Override
    protected double computePrefWidth(double height) {
        return 0;
    }

    @Override
    protected void updateItem(DatabaseNode item, boolean empty) {
        super.updateItem(item, empty);
        pseudoClassStateChanged(DROP_TARGET, false);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setContextMenu(null);
            setTooltip(null);
            return;
        }

        DatabaseNodeType type = item.getType();
        nameLabel.setText(item.getName());
        nameLabel.getStyleClass().removeAll("conn-connected", "conn-error", "group-name", "loading-name");
        iconBox.setOpacity(1.0);
        applyIconSize();

        // ----- icon
        boolean useImage = false;
        if (type == DatabaseNodeType.CONNECTION) {
            Image img = connectionImage(item);
            if (img != null) {
                imageIcon.setImage(img);
                useImage = true;
            } else {
                icon.setIconLiteral("mdi2d-database-outline");
            }
        } else if (type == DatabaseNodeType.GROUP) {
            icon.setIconLiteral(getTreeItem() != null && getTreeItem().isExpanded()
                    ? "mdi2f-folder-open-outline" : "mdi2f-folder-outline");
        } else {
            icon.setIconLiteral(item.getEffectiveIconLiteral());
        }
        imageIcon.setVisible(useImage);
        icon.setVisible(!useImage);
        applyIconStyling(item);
        boolean loadingRow = type == DatabaseNodeType.LOADING && item.getName() != null && item.getName().startsWith("Đang tải");
        loadingSpinner.setVisible(loadingRow);
        if (loadingRow) {
            icon.setVisible(false);
            imageIcon.setVisible(false);
        }

        // ----- connection status = icon colour (user request 2026-11-05: no status dot)
        //       not connected / connecting -> grey icon; connected -> original icon colour
        boolean isConn = type == DatabaseNodeType.CONNECTION;
        icon.getStyleClass().remove("conn-off");
        imageIcon.setEffect(null);
        if (isConn) {
            Status status = ExplorerConnectionState.getInstance().get(item.getConnectionId());
            if (status != Status.CONNECTED) {
                imageIcon.setEffect(GREYSCALE);
                icon.getStyleClass().add("conn-off");
            }
        }

        // ----- PK / FK badge
        if (item.isPrimaryKey() || item.isForeignKey()) {
            badgeLabel.setText(item.isPrimaryKey() ? "PK" : "FK");
            show(badgeLabel, true);
        } else {
            show(badgeLabel, false);
        }

        // ----- description (muted, right after the name)
        String desc = null;
        if (isConn) {
            desc = dbTypeLabel(item);
            Status st = ExplorerConnectionState.getInstance().get(item.getConnectionId());
            switch (st) {
                case CONNECTED -> nameLabel.getStyleClass().add("conn-connected");
                case CONNECTING -> desc = "đang kết nối…";
                case ERROR -> nameLabel.getStyleClass().add("conn-error");
                default -> { }
            }
        } else if (type == DatabaseNodeType.GROUP) {
            nameLabel.getStyleClass().add("group-name");
        } else if (type == DatabaseNodeType.LOADING) {
            nameLabel.getStyleClass().add("loading-name");
        } else if (!isCategory(type) && type != DatabaseNodeType.DATABASE && type != DatabaseNodeType.SCHEMA
                && item.getSubText() != null && !item.getSubText().isBlank()) {
            desc = item.getSubText();
        }
        descLabel.setText(desc != null ? desc : "");
        show(descLabel, desc != null && !desc.isBlank());
        applyNameHighlight(item);

        // ----- count badge + filter button for categories
        boolean filterable = isFilterableCategory(type) || type == DatabaseNodeType.DATABASE;
        String sub = isCategory(type) ? item.getSubText() : null;
        boolean filtered = sub != null && sub.contains("/");
        if (type == DatabaseNodeType.DATABASE) {
            filtered = app.framework.metadata.filter.DesktopFilterManager.getInstance()
                    .hasSchemaFilter(item.getConnectionId() != null ? item.getConnectionId()
                            : (item.getConnectionProfile() != null ? item.getConnectionProfile().id() : null), item.getName());
        }
        if (sub != null && !sub.isBlank()) {
            countLabel.setText(filtered ? sub.replace("/", " / ") : sub);
            show(countLabel, true);
        } else {
            show(countLabel, false);
        }
        countLabel.getStyleClass().remove("filtered");
        if (filtered) countLabel.getStyleClass().add("filtered");

        boolean filterActive = false;
        if (filterable && actions != null) {
            filterButton.setManaged(true);
            filterButton.getStyleClass().remove("active");
            if (filtered) {
                filterButton.getStyleClass().add("active");
                filterActive = true;
            }
            filterTip.setText(type == DatabaseNodeType.DATABASE
                    ? "Lọc schema" + (filtered ? " (đang lọc)" : "")
                    : "Lọc " + item.getName() + (filtered ? " (đang lọc)" : ""));
        } else {
            filterButton.setManaged(false);
        }

        // ----- connection row buttons (LLTool): reconnect | filter databases | refresh | disconnect  (or connect)
        boolean connRow = type == DatabaseNodeType.CONNECTION && actions != null && !listMode;
        boolean connected = connRow && ExplorerConnectionState.getInstance().get(item.getConnectionId()) == Status.CONNECTED;
        for (Button b : new Button[]{reconnectButton, refreshButton, disconnectButton}) {
            b.setManaged(connected);
        }
        connectButton.setManaged(connRow && !connected);
        if (connRow) {
            boolean dbFiltered = app.framework.metadata.filter.DesktopFilterManager.getInstance()
                    .getDatabaseFilter(item.getConnectionId()).isActive();
            filterButton.getStyleClass().remove("active");
            filterButton.setManaged(connected);
            if (connected && dbFiltered) {
                filterButton.getStyleClass().add("active");
                filterActive = true;
            }
            filterTip.setText("Filter Databases" + (dbFiltered ? " (đang lọc)" : ""));
        }
        // a connection / category row whose filter is on keeps a small mark, since the action bar is hidden until hover
        filterIndicator.setVisible(filterActive && !listMode);
        filterIndicator.setManaged(filterActive && !listMode);

        boolean hasMenu = !listMode && type != DatabaseNodeType.LOADING;
        moreButton.setManaged(hasMenu);

        setText(null);
        setGraphic(rowRoot);
        setContextMenu(hasMenu ? cellMenu : null);
        updateTooltip(item);
    }

    // ------------------------------------------------------------------ icons

    private static Image connectionImage(DatabaseNode item) {
        String t = dbType(item);
        String path = t.contains("POSTGRE") ? "/static/icons/postgresql.png"
                : (t.contains("SQLSERVER") || t.contains("MSSQL")) ? "/static/icons/sqlserver.png"
                : t.contains("SQLITE") ? "/static/icons/sqlite.png" : null;
        if (path == null) return null;
        return IMAGE_CACHE.computeIfAbsent(path, p -> {
            var url = DatabaseTreeCell.class.getResource(p);
            return url != null ? new Image(url.toExternalForm(), 14, 14, true, true) : null;
        });
    }

    private static String dbType(DatabaseNode item) {
        ConnectionProfileDto p = item != null ? item.getConnectionProfile() : null;
        return p != null && p.databaseType() != null ? p.databaseType().toUpperCase() : "";
    }

    private static String dbTypeLabel(DatabaseNode item) {
        String t = dbType(item);
        if (t.contains("POSTGRE")) return "PostgreSQL";
        if (t.contains("SQLSERVER") || t.contains("MSSQL")) return "SQL Server";
        if (t.contains("SQLITE")) return "SQLite";
        if (t.contains("MYSQL")) return "MySQL";
        return t.isEmpty() ? null : t;
    }

    private static String connectionKey(DatabaseNode item) {
        return item.getConnectionId() != null ? item.getConnectionId() : item.getName();
    }

    private void applyIconStyling(DatabaseNode item) {
        icon.getStyleClass().removeIf(c -> c.startsWith("icon-"));
        String cls = switch (item.getType()) {
            case GROUP -> "icon-group";
            case CONNECTION -> "icon-connection";
            case DATABASE -> "icon-database";
            case SCHEMA -> "icon-cat-folder";
            case CATEGORY_TABLES -> "icon-cat-tables";
            case TABLE -> "icon-table";
            case CATEGORY_COLUMNS, CATEGORY_KEYS, CATEGORY_CONSTRAINTS -> "icon-cat-folder";
            case KEY -> "icon-key";
            case CONSTRAINT -> "icon-constraint";
            case CATEGORY_VIEWS -> "icon-cat-views";
            case VIEW -> "icon-view";
            case CATEGORY_MATERIALIZED_VIEWS, MATERIALIZED_VIEW -> "icon-materialized-view";
            case CATEGORY_FUNCTIONS -> "icon-cat-funcs";
            case FUNCTION -> "icon-func";
            case CATEGORY_PROCEDURES -> "icon-cat-procs";
            case PROCEDURE -> "icon-proc";
            case CATEGORY_INDEXES -> item.getTableMetadata() != null ? "icon-cat-folder" : "icon-index";
            case INDEX -> "icon-index";
            case CATEGORY_TRIGGERS -> item.getTableMetadata() != null ? "icon-cat-folder" : "icon-trigger";
            case TRIGGER -> "icon-trigger";
            case CATEGORY_SEQUENCES, SEQUENCE -> "icon-sequence";
            case CATEGORY_USER_TYPES, USER_TYPE -> "icon-user-type";
            case ROLES_USERS -> "icon-roles";
            case LINKED_SERVER, SERVER_OBJECTS -> "icon-cat-folder";
            case COLUMN -> columnIconClass(item);
            default -> null;
        };
        if (cls != null) icon.getStyleClass().add(cls);
    }

    private static String columnIconClass(DatabaseNode item) {
        if (item.isPrimaryKey()) return "icon-pk";
        if (item.isForeignKey()) return "icon-fk";
        String sub = item.getSubText() != null ? item.getSubText().toLowerCase() : "";
        if (sub.contains("int") || sub.contains("numeric") || sub.contains("decimal") || sub.contains("float")
                || sub.contains("double") || sub.contains("real")) return "icon-col-num";
        if (sub.contains("char") || sub.contains("text")) return "icon-col-text";
        if (sub.contains("time") || sub.contains("date")) return "icon-col-date";
        return "icon-column";
    }

    // ------------------------------------------------------------------ tooltip

    private void updateTooltip(DatabaseNode item) {
        String text = null;
        if (item.getType() == DatabaseNodeType.CONNECTION) {
            ConnectionProfileDto p = item.getConnectionProfile();
            StringBuilder sb = new StringBuilder(item.getName());
            String typeLabel = dbTypeLabel(item);
            if (typeLabel != null) sb.append("  (").append(typeLabel).append(")");
            if (p != null) {
                if (p.host() != null && !p.host().isBlank()) {
                    sb.append("\nMáy chủ: ").append(p.host()).append(p.port() > 0 ? ":" + p.port() : "");
                } else if (p.sqliteFilePath() != null && !p.sqliteFilePath().isBlank()) {
                    sb.append("\nTệp: ").append(p.sqliteFilePath());
                }
                if (p.databaseName() != null && !p.databaseName().isBlank()) sb.append("\nDatabase: ").append(p.databaseName());
                if (p.username() != null && !p.username().isBlank()) sb.append("\nUser: ").append(p.username());
                if (p.environment() != null && !p.environment().isBlank()) sb.append("\nMôi trường: ").append(p.environment());
                if (p.readOnly()) sb.append("\nChỉ đọc");
                if (p.group() != null && !p.group().isBlank()) sb.append("\nNhóm: ").append(p.group());
            }
            Status st = ExplorerConnectionState.getInstance().get(item.getConnectionId());
            sb.append("\nTrạng thái: ").append(switch (st) {
                case CONNECTED -> "đã kết nối" + (MockMetadataProvider.isEnabled() ? " (dữ liệu mẫu)" : "");
                case CONNECTING -> "đang kết nối…";
                case ERROR -> "lỗi — " + ExplorerConnectionState.getInstance().getError(item.getConnectionId());
                default -> "chưa kết nối";
            });
            text = sb.toString();
        } else if (item.getType() == DatabaseNodeType.GROUP) {
            text = "Nhóm: " + (item.getGroupPath() != null ? item.getGroupPath() : item.getName());
        } else if (item.getName() != null && item.getName().length() > 28) {
            text = item.getName();
        }
        if (text != null) {
            tooltip.setText(text);
            setTooltip(tooltip);
        } else {
            setTooltip(null);
        }
    }

    // ------------------------------------------------------------------ drag & drop

    private void setupDragAndDrop() {
        setOnDragDetected(event -> {
            DatabaseNode item = getItem();
            if (item == null) return;
            ClipboardContent content = new ClipboardContent();
            Dragboard db;
            if (item.getType() == DatabaseNodeType.CONNECTION && actions != null) {
                db = startDragAndDrop(TransferMode.MOVE);
                content.put(EXPLORER_ITEM, "conn:" + item.getConnectionId());
                content.putString(item.getName());
            } else if (item.getType() == DatabaseNodeType.GROUP && actions != null) {
                db = startDragAndDrop(TransferMode.MOVE);
                content.put(EXPLORER_ITEM, "group:" + item.getGroupPath());
                content.putString(item.getName());
            } else if (item.getType() == DatabaseNodeType.TABLE) {
                db = startDragAndDrop(TransferMode.COPY);
                content.putString(SqlScriptGenerator.generateSelect(resolveDialect(item), resolveTableMetadata(item), resolveColumns(item)));
            } else {
                db = startDragAndDrop(TransferMode.COPY);
                content.putString(item.getName());
            }
            db.setContent(content);
            event.consume();
        });

        setOnDragOver(event -> {
            String target = dropTargetGroup();
            Object payload = event.getDragboard().getContent(EXPLORER_ITEM);
            if (target != null && payload instanceof String s && event.getGestureSource() != this && canDrop(s, target)) {
                event.acceptTransferModes(TransferMode.MOVE);
                pseudoClassStateChanged(DROP_TARGET, true);
                event.consume();
            }
        });
        setOnDragExited(event -> pseudoClassStateChanged(DROP_TARGET, false));
        setOnDragDropped(event -> {
            String target = dropTargetGroup();
            Object payload = event.getDragboard().getContent(EXPLORER_ITEM);
            boolean done = false;
            if (target != null && payload instanceof String s && actions != null && canDrop(s, target)) {
                if (s.startsWith("conn:")) {
                    actions.moveConnection(s.substring(5), target);
                } else if (s.startsWith("group:")) {
                    actions.moveGroup(s.substring(6), target);
                }
                done = true;
            }
            pseudoClassStateChanged(DROP_TARGET, false);
            event.setDropCompleted(done);
            event.consume();
        });
    }

    /** Group a drop on this row goes to: the group itself, or the group of a connection row. */
    private String dropTargetGroup() {
        DatabaseNode item = getItem();
        if (item == null || actions == null) return null;
        if (item.getType() == DatabaseNodeType.GROUP) return item.getGroupPath();
        if (item.getType() == DatabaseNodeType.CONNECTION) {
            return item.getGroupPath() != null ? item.getGroupPath() : "";
        }
        return null;
    }

    private static boolean canDrop(String payload, String targetGroup) {
        if (payload.startsWith("group:")) {
            String source = payload.substring(6);
            return !ExplorerGroupService.isSameOrBelow(targetGroup, source)
                    && !ExplorerGroupService.parentOf(source).equalsIgnoreCase(ExplorerGroupService.normalize(targetGroup));
        }
        return payload.startsWith("conn:");
    }

    private boolean clickedOnArrowOrButton(javafx.scene.input.MouseEvent event) {
        Object t = event.getTarget();
        javafx.scene.Node disclosure = getDisclosureNode();
        for (javafx.scene.Node n = t instanceof javafx.scene.Node nn ? nn : null; n != null && n != this; n = n.getParent()) {
            if (n == disclosure || n instanceof javafx.scene.control.ButtonBase) return true;
        }
        return false;
    }

    private void setupDoubleClickHandler() {
        setOnMouseClicked(event -> {
            // one click on the row opens / closes it (not only the arrow); arrow and buttons keep their own behaviour
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY && event.getClickCount() == 1
                    && !event.isControlDown() && !event.isShiftDown() && getItem() != null
                    && getItem().getType() != DatabaseNodeType.LOADING) {
                TreeItem<DatabaseNode> ti = getTreeItem();
                if (ti != null && !ti.isLeaf() && !clickedOnArrowOrButton(event)) {
                    ti.setExpanded(!ti.isExpanded());
                }
            }
            if (event.getClickCount() == 2 && getItem() != null && onActionConsumer != null
                    && getItem().getType() != DatabaseNodeType.GROUP) {
                onActionConsumer.accept(getItem());
            }
        });
    }

    // ------------------------------------------------------------------ context menus

    private ContextMenu createContextMenu(DatabaseNode item) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("explorer-context-menu");
        List<MenuItem> items = new ArrayList<>();

        switch (item.getType()) {
            case GROUP -> buildGroupMenu(item, items);
            case CONNECTION -> buildConnectionMenu(item, items);
            case DATABASE -> {
                items.add(newQuery(item));
                items.add(sep());
                items.add(action("Filter Schemas...", "fth-filter", () -> {
                    if (actions != null) actions.filterObjects(item);
                }));
                items.add(deferred("So sánh Schema...", "fth-git-pull-request"));
                items.add(deferred("Search Object Content...", "fth-search"));
                items.add(sep());
                items.add(action("Set Active Database", "fth-check-circle", () -> {
                    GlobalDesktopState.getInstance().setActiveCatalog(item.getName());
                    NotificationService.getInstance().showInfo("Database", "Database hiện tại: " + item.getName());
                }));
                items.add(refresh());
                items.add(deferred("Properties", "fth-info"));
            }
            case SCHEMA -> {
                items.add(newQuery(item));
                items.add(deferred("So sánh Schema...", "fth-git-pull-request"));
                items.add(sep());
                items.add(deferred("New Table...", "fth-plus-square"));
                items.add(sep());
                items.add(action("Set Active Schema", "fth-check-circle", () -> {
                    GlobalDesktopState.getInstance().setActiveSchema(item.getName());
                    NotificationService.getInstance().showInfo("Schema", "Schema hiện tại: " + item.getName());
                }));
                items.add(refresh());
            }
            case CATEGORY_TABLES, CATEGORY_VIEWS, CATEGORY_MATERIALIZED_VIEWS, CATEGORY_FUNCTIONS,
                 CATEGORY_PROCEDURES, CATEGORY_TRIGGERS, CATEGORY_USER_TYPES, CATEGORY_SEQUENCES, CATEGORY_SYNONYMS -> {
                items.add(newQuery(item));
                if (item.getType() == DatabaseNodeType.CATEGORY_TABLES) {
                    items.add(sep());
                    items.add(deferred("New Table...", "fth-plus-square"));
                }
                if (isFilterableCategory(item.getType())) {
                    items.add(sep());
                    items.add(action("Filter " + item.getName() + "...", "fth-filter", () -> {
                        if (actions != null) actions.filterObjects(item);
                    }));
                }
                items.add(sep());
                items.add(refresh());
            }
            case LINKED_SERVER -> buildLinkedServerMenu(item, items);
            case REMOTE_DATABASE -> {
                items.add(newQuery(item));
                items.add(sep());
                items.add(refresh());
            }
            case REMOTE_TABLE, REMOTE_VIEW -> {
                String four = "[" + item.getLinkedServer() + "].[" + item.getCatalog() + "].[" + item.getSchema() + "].["
                        + item.getName().substring(item.getName().indexOf('.') + 1) + "]";
                items.add(action("SELECT TOP 1000", "fth-file-text",
                        () -> openScript(item, "remote_select.sql", "SELECT TOP 1000 * FROM " + four + ";")));
                items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(four)));
            }
            case TABLE -> buildTableMenu(item, items);
            case VIEW, MATERIALIZED_VIEW -> buildViewMenu(item, items);
            case PROCEDURE, FUNCTION -> buildRoutineMenu(item, items);
            case COLUMN -> {
                items.add(newQuery(item));
                items.add(sep());
                items.add(deferred("Dependencies (Phụ thuộc)", "fth-share-2"));
                items.add(sep());
                items.add(action("Copy Column Name", "fth-copy", () -> copyToClipboard(item.getName())));
                items.add(action("Copy Qualified Column Name", "fth-copy", () -> {
                    String table = item.getTableMetadata() != null ? item.getTableMetadata().name() : null;
                    copyToClipboard(table != null ? getQualifiedName(item.getSchema(), table) + "." + item.getName() : item.getName());
                }));
            }
            case TRIGGER, SEQUENCE, USER_TYPE -> {
                String kind = switch (item.getType()) {
                    case TRIGGER -> "Trigger";
                    case SEQUENCE -> "Sequence";
                    default -> "Type";
                };
                items.add(newQuery(item));
                items.add(sep());
                ObjectScriptService.Kind ok = switch (item.getType()) {
                    case TRIGGER -> ObjectScriptService.Kind.TRIGGER;
                    case SEQUENCE -> ObjectScriptService.Kind.SEQUENCE;
                    default -> ObjectScriptService.Kind.USER_TYPE;
                };
                if (realScripts()) items.add(realScriptMenu("Script " + kind + " as", item, ok, false));
                items.add(action("View Definition", "fth-eye", () -> openGenerated(item, ok, ObjectScriptService.Action.CREATE,
                        item.getName() + "_definition.sql", definitionTemplate(item, kind.toUpperCase()))));
                items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
                items.add(sep());
                items.add(refresh());
                items.add(sep());
                items.add(danger("Drop " + kind, () -> openGenerated(item, ok, ObjectScriptService.Action.DROP, item.getName() + "_drop.sql",
                        "DROP " + kind.toUpperCase() + " " + getQualifiedName(item.getSchema(), item.getName()) + ";")));
            }
            case SYNONYM -> {
                items.add(newQuery(item));
                items.add(sep());
                if (realScripts()) items.add(realScriptMenu("Script Synonym as", item, ObjectScriptService.Kind.SYNONYM, false));
                items.add(action("View Definition", "fth-eye", () -> openGenerated(item, ObjectScriptService.Kind.SYNONYM,
                        ObjectScriptService.Action.CREATE, item.getName() + "_definition.sql",
                        "CREATE SYNONYM " + getQualifiedName(item.getSchema(), item.getName()) + "\nFOR " + item.getSubText() + ";")));
                items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
                items.add(sep());
                items.add(refresh());
                items.add(sep());
                items.add(danger("Drop Synonym", () -> openGenerated(item, ObjectScriptService.Kind.SYNONYM,
                        ObjectScriptService.Action.DROP, item.getName() + "_drop.sql",
                        "DROP SYNONYM " + getQualifiedName(item.getSchema(), item.getName()) + ";")));
            }
            case DB_USER, ROLE -> buildPrincipalMenu(item, items);
            case KEY, CONSTRAINT, INDEX, PARAMETER -> {
                items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
                items.add(refresh());
            }
            default -> items.add(refresh());
        }
        menu.getItems().addAll(items);
        return menu;
    }

    private void buildGroupMenu(DatabaseNode item, List<MenuItem> items) {
        String path = item.getGroupPath();
        items.add(action("New Connection...", "fth-power", () -> {
            if (actions != null) actions.newConnection(path);
        }));
        items.add(action("New Group...", "fth-folder-plus", () -> {
            if (actions != null) actions.newGroup(path);
        }));
        MenuItem rename = action("Rename Group...", "fth-edit-2", () -> {
            if (actions != null) actions.renameGroup(item);
        });
        rename.setAccelerator(KeyCombination.keyCombination("F2"));
        items.add(rename);
        items.add(sep());
        items.add(refresh());
        items.add(sep());
        MenuItem del = danger("Delete Group", () -> {
            if (actions != null) actions.deleteGroup(item);
        });
        del.setAccelerator(KeyCombination.keyCombination("Delete"));
        items.add(del);
    }

    private void buildConnectionMenu(DatabaseNode item, List<MenuItem> items) {
        Status st = ExplorerConnectionState.getInstance().get(item.getConnectionId());
        items.add(action(st == Status.CONNECTED ? "Reconnect" : "Connect / Reconnect", "fth-zap", () -> {
            TreeItem<DatabaseNode> ti = getTreeItem();
            if (ti instanceof LazyDatabaseTreeItem lazy && item.isLoaded()) {
                lazy.reload();
            } else if (ti != null) {
                ti.setExpanded(true);
            }
            GlobalDesktopState.getInstance().setActiveConnection(item.getName());
        }));
        MenuItem disconnect = action("Disconnect", "fth-log-out", () -> {
            if (actions != null) actions.disconnect(getTreeItem());
        });
        disconnect.setDisable(st != Status.CONNECTED);
        items.add(disconnect);
        items.add(sep());
        MenuItem nq = newQuery(item);
        nq.setText("New Query Console");
        items.add(nq);
        items.add(action("Filter Databases...", "fth-filter", () -> {
            if (actions != null) actions.filterDatabases(item);
        }));
        MenuItem rf = refresh();
        rf.setAccelerator(KeyCombination.keyCombination("F5"));
        items.add(rf);
        items.add(sep());
        items.add(deferred("SQL Trace Profiler...", "fth-activity"));
        items.add(deferred("Activity Monitor (Phiên & Khóa)...", "fth-heart"));
        items.add(deferred("So sánh Schema...", "fth-git-pull-request"));
        items.add(sep());
        items.add(action("New Group...", "fth-folder-plus", () -> {
            if (actions != null) actions.newGroup(item.getGroupPath() != null ? item.getGroupPath() : "");
        }));
        items.add(action("Edit Connection...", "fth-edit-2", () -> {
            if (actions != null) actions.editConnection(item);
        }));
        items.add(action("Duplicate Connection", "fth-copy", () -> {
            if (actions != null) actions.duplicateConnection(item);
        }));
        items.add(colorMenu(item));
        items.add(sep());
        MenuItem del = danger("Remove Connection", () -> {
            if (actions != null) actions.deleteConnection(item);
        });
        del.setAccelerator(KeyCombination.keyCombination("Delete"));
        items.add(del);
    }

    private Menu colorMenu(DatabaseNode item) {
        Menu colors = new Menu("Màu kết nối");
        colors.setGraphic(new FontIcon("fth-droplet"));
        ToggleGroup group = new ToggleGroup();
        String key = connectionKey(item);
        ConnectionColorService.ConnectionColor current = ConnectionColorService.getInstance().colorFor(key);
        for (ConnectionColorService.ConnectionColor c : ConnectionColorService.PALETTE) {
            RadioMenuItem mi = new RadioMenuItem(c.label());
            mi.setGraphic(new Circle(5, Color.web(c.hex())));
            mi.setToggleGroup(group);
            mi.setSelected(c.equals(current));
            mi.setOnAction(e -> ConnectionColorService.getInstance().setColor(key, c));
            colors.getItems().add(mi);
        }
        return colors;
    }

    /** Database user / role (LLTool context-menu.js): New Query, View Permissions, Script User as CREATE / DROP. */
    private void buildPrincipalMenu(DatabaseNode item, List<MenuItem> items) {
        ConnectionProfileDto p = item.getConnectionProfile();
        boolean role = item.getType() == DatabaseNodeType.ROLE;
        if (!role) {
            items.add(newQuery(item));
            items.add(sep());
        }
        items.add(action("View Permissions", "fth-shield", () -> {
            MainController mc = AppContext.getInstance().getMainController();
            if (mc != null) mc.openPrincipalPermissions(p, item.getCatalog(), item.getName());
        }));
        items.add(sep());
        Menu root = new Menu(role ? "Script Role as" : "Script User as");
        root.setGraphic(new FontIcon("fth-code"));
        root.getItems().addAll(principalScript("CREATE To", "create", true, item), principalScript("DROP To", "drop", false, item));
        items.add(root);
        items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
        items.add(sep());
        items.add(refresh());
    }

    private Menu principalScript(String verb, String base, boolean create, DatabaseNode item) {
        Menu sub = new Menu(verb);
        if (!create) sub.getStyleClass().add("danger-item");
        ConnectionProfileDto p = item.getConnectionProfile();
        java.util.function.Consumer<java.util.function.Consumer<String>> gen = use -> {
            if (p == null) return;
            app.framework.metadata.PrincipalPermissionsService.getInstance().loadAsync(p, item.getCatalog(), item.getName(), d -> {
                String ts = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " UTC";
                use.accept(create ? d.createScript(p.name(), true, ts) : d.dropScript(p.name(), ts));
            }, err -> NotificationService.getInstance().showError("Không tạo được script " + item.getName(), err));
        };
        sub.getItems().addAll(
                action("New Query Editor Window", "fth-file-text", () -> gen.accept(sql -> openScript(item, base + "_" + item.getName() + ".sql", sql))),
                action("Clipboard", "fth-copy", () -> gen.accept(DatabaseTreeCell::copyToClipboard)));
        return sub;
    }

    /** Linked server (LLTool context-menu.js): Test Connection, Script as CREATE / DROP, Refresh. Delete is left out: connections are read-only. */
    private void buildLinkedServerMenu(DatabaseNode item, List<MenuItem> items) {
        String server = item.getLinkedServer();
        ConnectionProfileDto p = item.getConnectionProfile();
        items.add(action("Test Connection", "fth-zap", () -> {
            if (p == null) return;
            NotificationService.getInstance().showInfo("Linked Server", "Đang kiểm tra kết nối tới '" + server + "'…");
            DesktopMetadataService.getInstance().testLinkedServer(p, server,
                    () -> NotificationService.getInstance().showSuccess("Linked Server", "Kết nối tới '" + server + "' thành công."),
                    err -> NotificationService.getInstance().showError("Linked Server '" + server + "'", err));
        }));
        items.add(sep());
        Menu root = new Menu("Script Linked Server as");
        root.setGraphic(new FontIcon("fth-code"));
        root.getItems().addAll(
                linkedScript("CREATE To", "create", app.framework.metadata.LinkedServerScripts.Mode.CREATE, item),
                linkedScript("DROP To", "drop", app.framework.metadata.LinkedServerScripts.Mode.DROP, item),
                linkedScript("DROP And CREATE To", "drop_and_create", app.framework.metadata.LinkedServerScripts.Mode.DROP_AND_CREATE, item));
        items.add(root);
        items.add(sep());
        items.add(refresh());
    }

    private Menu linkedScript(String verb, String base, app.framework.metadata.LinkedServerScripts.Mode mode, DatabaseNode item) {
        Menu sub = new Menu(verb);
        if (mode != app.framework.metadata.LinkedServerScripts.Mode.CREATE) sub.getStyleClass().add("danger-item");
        ConnectionProfileDto p = item.getConnectionProfile();
        String server = item.getLinkedServer();
        java.util.function.Consumer<java.util.function.Consumer<String>> gen = use -> {
            if (p == null) return;
            DesktopMetadataService.getInstance().fetchLinkedServerScript(p, server, mode, use,
                    err -> NotificationService.getInstance().showError("Không tạo được script " + server, err));
        };
        sub.getItems().addAll(
                action("New Query Editor Window", "fth-file-text", () -> gen.accept(sql -> openScript(item, base + "_" + server + ".sql", sql))),
                action("Clipboard", "fth-copy", () -> gen.accept(DatabaseTreeCell::copyToClipboard)));
        return sub;
    }

    private void buildTableMenu(DatabaseNode item, List<MenuItem> items) {
        String dialect = resolveDialect(item);
        TableMetadata tbl = resolveTableMetadata(item);
        List<ColumnMetadata> cols = resolveColumns(item);
        String q = getQualifiedName(item.getSchema(), item.getName());

        items.add(newQuery(item));
        items.add(sep());
        String drop = SqlScriptGenerator.generateDropTable(dialect, tbl);
        if (realScripts()) {
            // Phase 8 / D2: CREATE / DROP read from the server; DML templates use the real column list
            items.add(realScriptMenu("Script Table as", item, ObjectScriptService.Kind.TABLE, true));
        } else {
            Map<String, String> scripts = new java.util.LinkedHashMap<>();
            String ddl = SqlScriptGenerator.generateDdl(dialect, tbl, cols);
            scripts.put("CREATE To", ddl);
            scripts.put("DROP To", drop);
            scripts.put("DROP And CREATE To", drop + batchSeparator(dialect) + ddl);
            scripts.put("-", null);
            scripts.put("SELECT To", SqlScriptGenerator.generateSelect(dialect, tbl, cols));
            scripts.put("INSERT To", SqlScriptGenerator.generateInsert(dialect, tbl, cols));
            scripts.put("UPDATE To", SqlScriptGenerator.generateUpdate(dialect, tbl, cols));
            scripts.put("DELETE To", SqlScriptGenerator.generateDelete(dialect, tbl, cols));
            items.add(scriptAsMenu("Script Table as", item, scripts));
        }
        items.add(sep());
        items.add(action("Select Top 1000", "fth-list", () -> runOrOpen(item, item.getName() + ".sql", selectTop(dialect, q, 1000))));
        items.add(deferred("Edit Data (Top 200 Rows)", "fth-edit"));
        items.add(action("Select Count(*)", "fth-hash", () -> runOrOpen(item, item.getName() + "_count.sql",
                SqlScriptGenerator.generateCount(dialect, tbl))));
        items.add(sep());
        items.add(deferred("New Table...", "fth-plus-square"));
        items.add(deferred("Copy Table", "fth-copy"));
        items.add(deferred("Design Table", "fth-edit-3"));
        items.add(sep());
        items.add(deferred("Indexes", "fth-bookmark"));
        items.add(deferred("Dependencies (Phụ thuộc)", "fth-share-2"));
        items.add(sep());
        items.add(action("Copy Table Name", "fth-copy", () -> copyToClipboard(item.getName())));
        items.add(action("Copy Qualified Name", "fth-copy", () -> copyToClipboard(q)));
        items.add(refresh());
        items.add(sep());
        items.add(danger("Drop Table", () -> openGenerated(item, ObjectScriptService.Kind.TABLE,
                ObjectScriptService.Action.DROP, item.getName() + "_drop.sql", drop)));
    }

    private void buildViewMenu(DatabaseNode item, List<MenuItem> items) {
        boolean mv = item.getType() == DatabaseNodeType.MATERIALIZED_VIEW;
        String dialect = resolveDialect(item);
        String q = getQualifiedName(item.getSchema(), item.getName());
        String kw = mv ? "MATERIALIZED VIEW" : "VIEW";
        String create = definitionTemplate(item, kw);
        String drop = "DROP " + kw + " " + q + ";";

        items.add(newQuery(item));
        items.add(sep());
        ObjectScriptService.Kind vk = mv ? ObjectScriptService.Kind.MATERIALIZED_VIEW : ObjectScriptService.Kind.VIEW;
        if (realScripts()) {
            Menu m = realScriptMenu(mv ? "Script Materialized View as" : "Script View as", item, vk, false);
            m.getItems().add(sep());
            m.getItems().add(staticScript("SELECT To", item, selectTop(dialect, q, 1000)));
            items.add(m);
        } else {
            Map<String, String> scripts = new java.util.LinkedHashMap<>();
            scripts.put("CREATE To", create);
            scripts.put("ALTER To", create.replace("CREATE " + kw, mv ? "CREATE " + kw : "ALTER " + kw));
            scripts.put("DROP To", drop);
            scripts.put("DROP And CREATE To", drop + batchSeparator(dialect) + create);
            scripts.put("-", null);
            scripts.put("SELECT To", selectTop(dialect, q, 1000));
            items.add(scriptAsMenu(mv ? "Script Materialized View as" : "Script View as", item, scripts));
        }
        items.add(sep());
        items.add(action("Select Top 1000", "fth-list", () -> runOrOpen(item, item.getName() + ".sql", selectTop(dialect, q, 1000))));
        items.add(deferred("Design View (Trực quan hóa SQL)", "fth-git-merge"));
        items.add(sep());
        items.add(action("View Definition", "fth-eye", () -> openGenerated(item, vk,
                ObjectScriptService.Action.CREATE, item.getName() + "_definition.sql", create)));
        if (mv) {
            items.add(action("Refresh Data", "fth-refresh-ccw", () -> openScript(item, item.getName() + "_refresh.sql",
                    "REFRESH MATERIALIZED VIEW " + q + ";")));
        }
        items.add(deferred("Dependencies (Phụ thuộc)", "fth-share-2"));
        items.add(sep());
        items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
        items.add(refresh());
        items.add(sep());
        items.add(danger(mv ? "Drop Materialized View" : "Drop View", () -> openGenerated(item, vk,
                ObjectScriptService.Action.DROP, item.getName() + "_drop.sql", drop)));
    }

    private void buildRoutineMenu(DatabaseNode item, List<MenuItem> items) {
        boolean proc = item.getType() == DatabaseNodeType.PROCEDURE;
        String dialect = resolveDialect(item);
        String q = getQualifiedName(item.getSchema(), item.getName());
        String kw = proc ? "PROCEDURE" : "FUNCTION";
        String create = definitionTemplate(item, kw);
        String drop = "DROP " + kw + " " + q + ";";
        String exec = SqlScriptGenerator.generateRoutineCall(dialect, item.getSchema(), item.getName(), proc);

        items.add(newQuery(item));
        items.add(sep());
        ObjectScriptService.Kind rk = proc ? ObjectScriptService.Kind.PROCEDURE : ObjectScriptService.Kind.FUNCTION;
        if (realScripts()) {
            Menu m = realScriptMenu(proc ? "Script Procedure as" : "Script Function as", item, rk, false);
            m.getItems().add(sep());
            m.getItems().add(staticScript("EXECUTE To", item, exec));
            items.add(m);
        } else {
            Map<String, String> scripts = new java.util.LinkedHashMap<>();
            scripts.put("CREATE To", create);
            scripts.put("ALTER To", create.replace("CREATE " + kw, "ALTER " + kw));
            scripts.put("DROP To", drop);
            scripts.put("DROP And CREATE To", drop + batchSeparator(dialect) + create);
            scripts.put("-", null);
            scripts.put("EXECUTE To", exec);
            items.add(scriptAsMenu(proc ? "Script Procedure as" : "Script Function as", item, scripts));
        }
        items.add(sep());
        items.add(action("Execute", "fth-play", () -> openScript(item, item.getName() + "_exec.sql", exec)));
        items.add(action("View Definition", "fth-eye", () -> openGenerated(item, rk,
                ObjectScriptService.Action.CREATE, item.getName() + "_definition.sql", create)));
        items.add(deferred("Dependencies (Phụ thuộc)", "fth-share-2"));
        items.add(sep());
        items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
        items.add(refresh());
        items.add(sep());
        items.add(danger(proc ? "Drop Procedure" : "Drop Function", () -> openGenerated(item, rk,
                ObjectScriptService.Action.DROP, item.getName() + "_drop.sql", drop)));
    }

    // ------------------------------------------------------------------ Phase 8 / D2: real scripts

    private static boolean realScripts() {
        return app.framework.backend.BackendFlags.real(app.framework.backend.BackendFlags.Area.METADATA);
    }

    private static String parentTable(DatabaseNode item) {
        return item.getType() == DatabaseNodeType.TRIGGER && item.getTableMetadata() != null ? item.getTableMetadata().name() : null;
    }

    /**
     * Opens (or copies) a script read from the server; in mock mode opens {@code fallback}.
     * A failed read opens nothing and shows the server message.
     */
    private void openGenerated(DatabaseNode item, ObjectScriptService.Kind kind, ObjectScriptService.Action action,
                               String title, String fallback) {
        generate(item, kind, action, fallback, sql -> openScript(item, title, sql));
    }

    private void generate(DatabaseNode item, ObjectScriptService.Kind kind, ObjectScriptService.Action action,
                          String fallback, java.util.function.Consumer<String> use) {
        ConnectionProfileDto p = item.getConnectionProfile();
        if (!realScripts() || p == null) {
            use.accept(fallback);
            return;
        }
        ObjectScriptService.getInstance().scriptAsync(p, item.getCatalog(), item.getSchema(), item.getName(),
                parentTable(item), kind, action, use,
                err -> NotificationService.getInstance().showError("Không tạo được script " + item.getName(), err));
    }

    /** "Script X as" with entries generated from the server on click; table menus add DML templates. */
    private Menu realScriptMenu(String label, DatabaseNode item, ObjectScriptService.Kind kind, boolean tableDml) {
        Menu root = new Menu(label);
        root.setGraphic(new FontIcon("fth-code"));
        for (var e : ObjectScriptService.scriptActions(kind).entrySet()) {
            String verb = e.getKey();
            ObjectScriptService.Action action = e.getValue();
            String base = verb.replace(" To", "").replace(" ", "_").toLowerCase();
            Menu sub = new Menu(verb);
            sub.getItems().addAll(
                    action("New Query Editor Window", "fth-file-text",
                            () -> openGenerated(item, kind, action, item.getName() + "_" + base + ".sql", "")),
                    action("Clipboard", "fth-copy", () -> generate(item, kind, action, "", DatabaseTreeCell::copyToClipboard)));
            if (verb.startsWith("DROP")) sub.getStyleClass().add("danger-item");
            root.getItems().add(sub);
        }
        if (tableDml) {
            root.getItems().add(sep());
            for (String verb : List.of("SELECT To", "INSERT To", "UPDATE To", "DELETE To")) {
                String base = verb.replace(" To", "").toLowerCase();
                Menu sub = new Menu(verb);
                sub.getItems().addAll(
                        action("New Query Editor Window", "fth-file-text",
                                () -> withColumns(item, cols -> openScript(item, item.getName() + "_" + base + ".sql", dml(item, verb, cols)))),
                        action("Clipboard", "fth-copy", () -> withColumns(item, cols -> copyToClipboard(dml(item, verb, cols)))));
                root.getItems().add(sub);
            }
        }
        return root;
    }

    private MenuItem staticScript(String verb, DatabaseNode item, String sql) {
        String base = verb.replace(" To", "").toLowerCase();
        Menu sub = new Menu(verb);
        sub.getItems().addAll(
                action("New Query Editor Window", "fth-file-text", () -> openScript(item, item.getName() + "_" + base + ".sql", sql)),
                action("Clipboard", "fth-copy", () -> copyToClipboard(sql)));
        return sub;
    }

    /** Real column list (fetched when the table was never expanded), so DML templates name every column. */
    private void withColumns(DatabaseNode item, java.util.function.Consumer<List<ColumnMetadata>> use) {
        List<ColumnMetadata> known = resolveColumns(item);
        if (!known.isEmpty() || item.getConnectionProfile() == null) {
            use.accept(known);
            return;
        }
        app.framework.metadata.DesktopMetadataService.getInstance().fetchColumns(item.getConnectionProfile(),
                item.getCatalog(), item.getSchema(), item.getName(), use);
    }

    private String dml(DatabaseNode item, String verb, List<ColumnMetadata> cols) {
        String dialect = resolveDialect(item);
        TableMetadata tbl = resolveTableMetadata(item);
        return switch (verb) {
            case "INSERT To" -> SqlScriptGenerator.generateInsert(dialect, tbl, cols);
            case "UPDATE To" -> SqlScriptGenerator.generateUpdate(dialect, tbl, cols);
            case "DELETE To" -> SqlScriptGenerator.generateDelete(dialect, tbl, cols);
            default -> SqlScriptGenerator.generateSelect(dialect, tbl, cols);
        };
    }

    /** "Script X as ▸ CREATE To ▸ New Query Editor Window | Clipboard" (LLTool context-menu.js). */
    private Menu scriptAsMenu(String label, DatabaseNode item, Map<String, String> scripts) {
        Menu root = new Menu(label);
        root.setGraphic(new FontIcon("fth-code"));
        for (Map.Entry<String, String> e : scripts.entrySet()) {
            if (e.getValue() == null) {
                root.getItems().add(sep());
                continue;
            }
            String verb = e.getKey();
            String sql = e.getValue();
            Menu sub = new Menu(verb);
            String base = verb.replace(" To", "").replace(" ", "_").toLowerCase();
            sub.getItems().addAll(
                    action("New Query Editor Window", "fth-file-text", () -> openScript(item, item.getName() + "_" + base + ".sql", sql)),
                    action("Clipboard", "fth-copy", () -> copyToClipboard(sql)));
            if (verb.startsWith("DROP")) sub.getStyleClass().add("danger-item");
            root.getItems().add(sub);
        }
        return root;
    }

    // ------------------------------------------------------------------ menu helpers

    private static MenuItem action(String text, String iconLiteral, Runnable run) {
        MenuItem mi = new MenuItem(text);
        if (iconLiteral != null) mi.setGraphic(new FontIcon(iconLiteral));
        mi.setOnAction(e -> run.run());
        return mi;
    }

    private static MenuItem danger(String text, Runnable run) {
        MenuItem mi = action(text, "fth-trash-2", run);
        mi.getStyleClass().add("danger-item");
        return mi;
    }

    /** Feature outside the migration scope: visible but disabled (Gate B Q3 = b). */
    private static MenuItem deferred(String text, String iconLiteral) {
        MenuItem mi = new MenuItem(text + DEFERRED_SUFFIX);
        if (iconLiteral != null) mi.setGraphic(new FontIcon(iconLiteral));
        mi.setDisable(true);
        mi.getStyleClass().add("deferred-item");
        return mi;
    }

    private static SeparatorMenuItem sep() {
        return new SeparatorMenuItem();
    }

    private MenuItem refresh() {
        return action("Refresh", "fth-refresh-cw", this::reloadCurrentNode);
    }

    private MenuItem newQuery(DatabaseNode item) {
        MenuItem mi = action("New Query", "fth-file-plus", () -> {
            MainController mc = AppContext.getInstance().getMainController();
            if (mc == null) return;
            String conn = item.getConnectionProfile() != null ? item.getConnectionProfile().name()
                    : (item.getType() == DatabaseNodeType.CONNECTION ? item.getName() : null);
            String db = item.getType() == DatabaseNodeType.DATABASE ? item.getName() : item.getCatalog();
            String schema = item.getType() == DatabaseNodeType.SCHEMA ? item.getName() : item.getSchema();
            String header = "-- " + (conn != null ? conn : "") + (db != null ? " / " + db : "") + (schema != null ? " / " + schema : "") + "\n\n";
            mc.openQueryConsole("Query.sql", header, conn, db, schema);
        });
        mi.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));
        return mi;
    }

    private void openScript(DatabaseNode item, String title, String sql) {
        MainController mc = AppContext.getInstance().getMainController();
        if (mc == null) return;
        String conn = item.getConnectionProfile() != null ? item.getConnectionProfile().name() : null;
        mc.openQueryConsole(title, sql, conn, item.getCatalog(), item.getSchema());
    }

    /** Prototype rule: never run against a real database while mock metadata is on (Q1 = a). */
    private void runOrOpen(DatabaseNode item, String title, String sql) {
        MainController mc = AppContext.getInstance().getMainController();
        if (mc == null) return;
        String conn = item.getConnectionProfile() != null ? item.getConnectionProfile().name() : null;
        if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.QUERY)) {
            mc.openQueryConsole(title, sql, conn, item.getCatalog(), item.getSchema());
            NotificationService.getInstance().showInfo("Prototype",
                    "Đã mở câu lệnh trong tab mới. Chạy truy vấn thật sẽ bật khi tích hợp backend (Gate D).");
        } else {
            mc.executeQueryDirectly(title, sql, conn, item.getCatalog(), item.getSchema());
        }
    }

    private void reloadCurrentNode() {
        TreeItem<DatabaseNode> ti = getTreeItem();
        if (ti instanceof LazyDatabaseTreeItem lazyItem && ti.getValue() != null
                && ti.getValue().getType() != DatabaseNodeType.GROUP) {
            lazyItem.reload();
        } else if (actions != null) {
            actions.reloadAll();
        } else if (onActionConsumer != null && getItem() != null) {
            onActionConsumer.accept(getItem());
        }
    }

    // ------------------------------------------------------------------ SQL helpers

    private static String selectTop(String dialect, String qualified, int n) {
        if (dialect != null && dialect.toUpperCase().contains("SQLSERVER")) {
            return "SELECT TOP (" + n + ") *\nFROM " + qualified + ";";
        }
        return "SELECT *\nFROM " + qualified + "\nLIMIT " + n + ";";
    }

    private static String batchSeparator(String dialect) {
        return dialect != null && dialect.toUpperCase().contains("SQLSERVER") ? "\nGO\n\n" : "\n\n";
    }

    private static String definitionTemplate(DatabaseNode item, String kind) {
        String q = getQualifiedName(item.getSchema(), item.getName());
        String note = "-- Prototype: định nghĩa thật sẽ được đọc từ database khi tích hợp backend (Gate D).\n";
        return switch (kind) {
            case "VIEW", "MATERIALIZED VIEW" -> note + "CREATE " + kind + " " + q + " AS\nSELECT *\nFROM ...;";
            case "PROCEDURE" -> note + "CREATE PROCEDURE " + q + "\nAS\nBEGIN\n    -- ...\nEND;";
            case "FUNCTION" -> note + "CREATE FUNCTION " + q + "()\nRETURNS ...\nAS\nBEGIN\n    -- ...\nEND;";
            case "TRIGGER" -> note + "CREATE TRIGGER " + q + "\nAFTER INSERT OR UPDATE ON ...\nFOR EACH ROW\n-- ...;";
            case "SEQUENCE" -> note + "CREATE SEQUENCE " + q + " START WITH 1 INCREMENT BY 1;";
            default -> note + "CREATE TYPE " + q + " ...;";
        };
    }

    /** Search: shows the name with the matched part highlighted; otherwise the plain label text. */
    private void applyNameHighlight(DatabaseNode item) {
        String q = searchQuery;
        String name = item.getName();
        DatabaseNodeType t = item.getType();
        boolean eligible = !q.isEmpty() && name != null && !listMode && t != DatabaseNodeType.LOADING
                && !t.name().startsWith("CATEGORY_");
        int idx = eligible ? name.toLowerCase(java.util.Locale.ROOT).indexOf(q) : -1;
        if (idx < 0) {
            nameLabel.setGraphic(null);
            nameLabel.setContentDisplay(javafx.scene.control.ContentDisplay.TEXT_ONLY);
            return;
        }
        highlightBox.getChildren().clear();
        String before = name.substring(0, idx);
        String match = name.substring(idx, idx + q.length());
        String after = name.substring(idx + q.length());
        for (String part : new String[]{before, match, after}) {
            if (part.isEmpty()) continue;
            Label l = new Label(part);
            l.getStyleClass().addAll(nameLabel.getStyleClass());
            l.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            l.setPadding(javafx.geometry.Insets.EMPTY);
            if (part == match) {
                l.setStyle("-fx-background-color: -color-warning-muted; -fx-background-radius: 2; -fx-font-weight: bold;");
            }
            highlightBox.getChildren().add(l);
        }
        nameLabel.setText("");
        nameLabel.setGraphic(highlightBox);
        nameLabel.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
    }

    private static String getQualifiedName(String schema, String name) {
        if (schema != null && !schema.isBlank() && !"main".equalsIgnoreCase(schema)) {
            return schema + "." + name;
        }
        return name;
    }

    private String resolveDialect(DatabaseNode item) {
        if (item != null && item.getConnectionProfile() != null && item.getConnectionProfile().databaseType() != null) {
            return item.getConnectionProfile().databaseType();
        }
        return "POSTGRESQL";
    }

    private TableMetadata resolveTableMetadata(DatabaseNode item) {
        if (item.getTableMetadata() != null) {
            return item.getTableMetadata();
        }
        return new TableMetadata(item.getName(), item.getSchema(), item.getCatalog(), "TABLE", 0L, null);
    }

    private List<ColumnMetadata> resolveColumns(DatabaseNode item) {
        if (item == null || item.getChildren() == null) return List.of();
        List<ColumnMetadata> direct = item.getChildren().stream()
                .filter(c -> c.getType() == DatabaseNodeType.COLUMN && c.getColumnMetadata() != null)
                .map(DatabaseNode::getColumnMetadata)
                .toList();
        if (!direct.isEmpty()) return direct;
        // columns live under the "Columns" sub-folder once the table is expanded
        return item.getChildren().stream()
                .filter(c -> c.getType() == DatabaseNodeType.CATEGORY_COLUMNS)
                .flatMap(c -> c.getChildren().stream())
                .filter(c -> c.getColumnMetadata() != null)
                .map(DatabaseNode::getColumnMetadata)
                .toList();
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        NotificationService.getInstance().showInfo("Clipboard", "Đã sao chép: " + (text.length() > 60 ? text.substring(0, 60) + "…" : text));
    }

    // ------------------------------------------------------------------ misc

    private static boolean isCategory(DatabaseNodeType t) {
        return t.name().startsWith("CATEGORY_");
    }

    private static boolean isFilterableCategory(DatabaseNodeType t) {
        return switch (t) {
            case CATEGORY_TABLES, CATEGORY_VIEWS, CATEGORY_MATERIALIZED_VIEWS, CATEGORY_FUNCTIONS,
                 CATEGORY_PROCEDURES, CATEGORY_TRIGGERS, CATEGORY_USER_TYPES, CATEGORY_SYNONYMS -> true;
            default -> false;
        };
    }

    private static void show(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }
}
