package app.ui.components;

import app.application.AppContext;
import app.controllers.MainController;
import app.framework.metadata.MockMetadataProvider;
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
    private final StackPane iconBox = new StackPane();
    private final FontIcon icon = new FontIcon();
    private final ImageView imageIcon = new ImageView();
    private final Label nameLabel = new Label();
    private final Label descLabel = new Label();
    private final Label badgeLabel = new Label();
    private final Pane spacer = new Pane();
    private final Label countLabel = new Label();
    private final Button filterButton = new Button();
    private final Button moreButton = new Button();
    private final Tooltip tooltip = new Tooltip();

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
        countLabel.setMinWidth(Region.USE_PREF_SIZE);

        filterButton.getStyleClass().addAll("explorer-row-button", "explorer-filter-button");
        filterButton.setGraphic(new FontIcon("fth-filter"));
        filterButton.setFocusTraversable(false);
        filterButton.setOnAction(e -> {
            if (actions != null && getItem() != null) actions.filterObjects(getItem());
        });

        moreButton.getStyleClass().addAll("explorer-row-button", "explorer-more-button");
        moreButton.setGraphic(new FontIcon("fth-more-horizontal"));
        moreButton.setFocusTraversable(false);
        moreButton.setTooltip(new Tooltip("Thao tác"));
        moreButton.setOnAction(e -> {
            ContextMenu menu = getContextMenu();
            if (menu != null) menu.show(moreButton, Side.BOTTOM, 0, 0);
        });
        // ⋯ only on hover / selection (LLTool tree-node-actions)
        moreButton.visibleProperty().bind(hoverProperty().or(selectedProperty()));

        cellLayout.getChildren().addAll(iconBox, nameLabel, badgeLabel, descLabel, spacer,
                countLabel, filterButton, moreButton);

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

        // ----- count badge + filter button for categories
        boolean filterable = isFilterableCategory(type);
        String sub = isCategory(type) ? item.getSubText() : null;
        boolean filtered = sub != null && sub.contains("/");
        if (sub != null && !sub.isBlank()) {
            countLabel.setText(filtered ? sub.replace("/", " / ") : sub);
            show(countLabel, true);
        } else {
            show(countLabel, false);
        }
        countLabel.getStyleClass().remove("filtered");
        if (filtered) countLabel.getStyleClass().add("filtered");

        filterButton.visibleProperty().unbind();
        if (filterable && actions != null) {
            filterButton.setManaged(true);
            filterButton.getStyleClass().remove("active");
            if (filtered) {
                filterButton.getStyleClass().add("active");
                filterButton.setVisible(true);
            } else {
                filterButton.visibleProperty().bind(hoverProperty());
            }
            filterButton.setTooltip(new Tooltip("Lọc " + item.getName() + (filtered ? " (đang lọc)" : "")));
        } else {
            filterButton.setVisible(false);
            filterButton.setManaged(false);
        }

        boolean hasMenu = !listMode && type != DatabaseNodeType.LOADING;
        moreButton.setManaged(hasMenu);

        setText(null);
        setGraphic(cellLayout);
        setContextMenu(hasMenu ? createContextMenu(item) : null);
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

    private void setupDoubleClickHandler() {
        setOnMouseClicked(event -> {
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
                 CATEGORY_PROCEDURES, CATEGORY_TRIGGERS, CATEGORY_USER_TYPES, CATEGORY_SEQUENCES -> {
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
                items.add(action("View Definition", "fth-eye", () -> openScript(item, item.getName() + "_definition.sql",
                        definitionTemplate(item, kind.toUpperCase()))));
                items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
                items.add(sep());
                items.add(refresh());
                items.add(sep());
                items.add(danger("Drop " + kind, () -> openScript(item, item.getName() + "_drop.sql",
                        "DROP " + kind.toUpperCase() + " " + getQualifiedName(item.getSchema(), item.getName()) + ";")));
            }
            case KEY, CONSTRAINT, INDEX -> {
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

    private void buildTableMenu(DatabaseNode item, List<MenuItem> items) {
        String dialect = resolveDialect(item);
        TableMetadata tbl = resolveTableMetadata(item);
        List<ColumnMetadata> cols = resolveColumns(item);
        String q = getQualifiedName(item.getSchema(), item.getName());

        items.add(newQuery(item));
        items.add(sep());
        Map<String, String> scripts = new java.util.LinkedHashMap<>();
        String ddl = SqlScriptGenerator.generateDdl(dialect, tbl, cols);
        String drop = SqlScriptGenerator.generateDropTable(dialect, tbl);
        scripts.put("CREATE To", ddl);
        scripts.put("DROP To", drop);
        scripts.put("DROP And CREATE To", drop + batchSeparator(dialect) + ddl);
        scripts.put("-", null);
        scripts.put("SELECT To", SqlScriptGenerator.generateSelect(dialect, tbl, cols));
        scripts.put("INSERT To", SqlScriptGenerator.generateInsert(dialect, tbl, cols));
        scripts.put("UPDATE To", SqlScriptGenerator.generateUpdate(dialect, tbl, cols));
        scripts.put("DELETE To", SqlScriptGenerator.generateDelete(dialect, tbl, cols));
        items.add(scriptAsMenu("Script Table as", item, scripts));
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
        items.add(danger("Drop Table", () -> openScript(item, item.getName() + "_drop.sql", drop)));
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
        Map<String, String> scripts = new java.util.LinkedHashMap<>();
        scripts.put("CREATE To", create);
        scripts.put("ALTER To", create.replace("CREATE " + kw, mv ? "CREATE " + kw : "ALTER " + kw));
        scripts.put("DROP To", drop);
        scripts.put("DROP And CREATE To", drop + batchSeparator(dialect) + create);
        scripts.put("-", null);
        scripts.put("SELECT To", selectTop(dialect, q, 1000));
        items.add(scriptAsMenu(mv ? "Script Materialized View as" : "Script View as", item, scripts));
        items.add(sep());
        items.add(action("Select Top 1000", "fth-list", () -> runOrOpen(item, item.getName() + ".sql", selectTop(dialect, q, 1000))));
        items.add(deferred("Design View (Trực quan hóa SQL)", "fth-git-merge"));
        items.add(sep());
        items.add(action("View Definition", "fth-eye", () -> openScript(item, item.getName() + "_definition.sql", create)));
        if (mv) {
            items.add(action("Refresh Data", "fth-refresh-ccw", () -> openScript(item, item.getName() + "_refresh.sql",
                    "REFRESH MATERIALIZED VIEW " + q + ";")));
        }
        items.add(deferred("Dependencies (Phụ thuộc)", "fth-share-2"));
        items.add(sep());
        items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
        items.add(refresh());
        items.add(sep());
        items.add(danger(mv ? "Drop Materialized View" : "Drop View", () -> openScript(item, item.getName() + "_drop.sql", drop)));
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
        Map<String, String> scripts = new java.util.LinkedHashMap<>();
        scripts.put("CREATE To", create);
        scripts.put("ALTER To", create.replace("CREATE " + kw, "ALTER " + kw));
        scripts.put("DROP To", drop);
        scripts.put("DROP And CREATE To", drop + batchSeparator(dialect) + create);
        scripts.put("-", null);
        scripts.put("EXECUTE To", exec);
        items.add(scriptAsMenu(proc ? "Script Procedure as" : "Script Function as", item, scripts));
        items.add(sep());
        items.add(action("Execute", "fth-play", () -> openScript(item, item.getName() + "_exec.sql", exec)));
        items.add(action("View Definition", "fth-eye", () -> openScript(item, item.getName() + "_definition.sql", create)));
        items.add(deferred("Dependencies (Phụ thuộc)", "fth-share-2"));
        items.add(sep());
        items.add(action("Copy Name", "fth-copy", () -> copyToClipboard(item.getName())));
        items.add(refresh());
        items.add(sep());
        items.add(danger(proc ? "Drop Procedure" : "Drop Function", () -> openScript(item, item.getName() + "_drop.sql", drop)));
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
        if (MockMetadataProvider.isEnabled()) {
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
                 CATEGORY_PROCEDURES, CATEGORY_TRIGGERS, CATEGORY_USER_TYPES -> true;
            default -> false;
        };
    }

    private static void show(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }
}
