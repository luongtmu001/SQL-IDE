package app.controllers;

import app.application.AppContext;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.framework.metadata.SqlScriptGenerator;
import app.framework.metadata.filter.DesktopFilterManager;
import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.services.ConnectionColorService;
import app.services.DatabaseMockService;
import app.services.ExplorerConnectionState;
import app.services.ExplorerGroupService;
import app.services.NotificationService;
import app.ui.components.DatabaseTreeCell;
import app.ui.components.LazyDatabaseTreeItem;
import app.ui.dialogs.ConnectionManagerDialog;
import app.ui.dialogs.DatabaseFilterDialog;
import app.ui.dialogs.ObjectFilterDialog;
import app.ui.dialogs.SchemaFilterDialog;
import com.lltool.core.api.request.CreateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.*;

/**
 * Object Explorer (SCR-MAIN-002, Gate B 2026-11-05).
 *
 * <ul>
 *   <li>Connections are grouped in folders built from {@code profile.group} ("A/B").</li>
 *   <li>Expanding a connection uses {@link MockMetadataProvider} data (Q1 = a): no real database.</li>
 *   <li>Group create / rename / delete / drag-drop rewrite the local connection config (Q2 = a).</li>
 * </ul>
 */
public class SidebarController implements Initializable, DatabaseTreeCell.ExplorerActions {

    @FXML private VBox fullSidebarView;
    @FXML private VBox miniSidebarView;
    @FXML private VBox miniConnectionIconsBox;

    @FXML private TreeView<DatabaseNode> explorerTree;
    @FXML private TextField filterTextField;
    @FXML private Button clearFilterButton;
    @FXML private Label matchCountLabel;
    @FXML private VBox emptyStateBox;
    @FXML private VBox noConnectionsBox;

    private DatabaseNode rootData;
    private List<ConnectionProfileDto> profiles = new ArrayList<>();
    /** Group paths the user collapsed (groups are expanded by default, like LLTool). */
    private final Set<String> collapsedGroups = new HashSet<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTreeView();
        installConnectGuard();
        loadData();
        setupFilter();
        ExplorerConnectionState.getInstance().revisionProperty().addListener((o, a, b) -> explorerTree.refresh());
        ConnectionColorService.getInstance().revisionProperty().addListener((o, a, b) -> {
            explorerTree.refresh();
            if (miniSidebarView != null && miniSidebarView.isVisible()) populateMiniConnectionIcons();
        });
    }

    private void setupTreeView() {
        explorerTree.setShowRoot(false);
        // S3: appearance.iconSize (now and after every Settings save)
        app.ui.components.DatabaseTreeCell.setIconSize(iconSizeOf(app.services.SettingsService.getInstance().getCurrentSettings()));
        app.services.SettingsService.getInstance().addSettingsListener(s -> {
            int before = lastIconSize;
            app.ui.components.DatabaseTreeCell.setIconSize(iconSizeOf(s));
            if (lastIconSize != before) explorerTree.refresh();
        });
        explorerTree.setCellFactory(tv -> new DatabaseTreeCell(this::handleNodeAction, this));
        LazyDatabaseTreeItem.setTreeRefresher(explorerTree::refresh);

        // Keyboard: Enter = connect/expand, F2 = rename group, Delete = delete, F5 = refresh node.
        explorerTree.setOnKeyPressed(e -> {
            TreeItem<DatabaseNode> sel = explorerTree.getSelectionModel().getSelectedItem();
            DatabaseNode node = sel != null ? sel.getValue() : null;
            if (node == null) return;
            if (e.getCode() == KeyCode.F2 && node.getType() == DatabaseNodeType.GROUP) {
                renameGroup(node);
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE) {
                if (node.getType() == DatabaseNodeType.GROUP) deleteGroup(node);
                else if (node.getType() == DatabaseNodeType.CONNECTION) deleteConnection(node);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                if (node.getType() == DatabaseNodeType.GROUP || node.getType() == DatabaseNodeType.CONNECTION) {
                    sel.setExpanded(!sel.isExpanded());
                } else {
                    handleNodeAction(node);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.F5) {
                if (sel instanceof LazyDatabaseTreeItem lazy && node.getType() != DatabaseNodeType.GROUP) lazy.reload();
                else loadData();
                e.consume();
            }
        });

        // Drop on the empty area below the rows = move to "no group".
        explorerTree.setOnDragOver(e -> {
            if (e.getDragboard().hasContent(DatabaseTreeCell.EXPLORER_ITEM)) {
                e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            }
        });
        explorerTree.setOnDragDropped(e -> {
            Object payload = e.getDragboard().getContent(DatabaseTreeCell.EXPLORER_ITEM);
            boolean done = false;
            if (payload instanceof String s) {
                if (s.startsWith("conn:")) moveConnection(s.substring(5), "");
                else if (s.startsWith("group:")) moveGroup(s.substring(6), "");
                done = true;
            }
            e.setDropCompleted(done);
            e.consume();
        });

        // Context menu on the empty area (LLTool "sidebar" registry entry).
        ContextMenu areaMenu = new ContextMenu();
        MenuItem newConn = new MenuItem("New Connection...", new FontIcon("fth-power"));
        newConn.setOnAction(e -> newConnection(""));
        MenuItem newGroup = new MenuItem("New Group...", new FontIcon("fth-folder-plus"));
        newGroup.setOnAction(e -> newGroup(""));
        MenuItem refresh = new MenuItem("Refresh", new FontIcon("fth-refresh-cw"));
        refresh.setOnAction(e -> handleRefresh());
        areaMenu.getItems().addAll(newConn, newGroup, new SeparatorMenuItem(), refresh);
        explorerTree.setContextMenu(areaMenu);
    }

    /** SCR-DLG-002: ask for the password before opening a connection when the policy requires it. */
    private void installConnectGuard() {
        LazyDatabaseTreeItem.setConnectGuard((node, proceed) -> {
            ConnectionProfileDto p = node.getConnectionProfile();
            var policy = app.services.PasswordPolicyService.getInstance();
            if (p == null || !policy.needsPrompt(p)) {
                proceed.accept(true);
                return;
            }
            Platform.runLater(() -> {
                String target = (p.username() != null ? p.username() + " @ " : "")
                        + (p.host() != null ? p.host() : "") + (p.port() > 0 ? ":" + p.port() : "");
                var answer = app.ui.dialogs.PasswordPromptDialog.show(ownerStage(), p.name(), target, policy.isExpired(p.id()));
                if (answer.isEmpty()) {
                    proceed.accept(false);
                    return;
                }
                String err = policy.applyPrompt(p, answer.get().password(), answer.get().save());
                if (err != null) NotificationService.getInstance().showWarning("Mật khẩu", err);
                proceed.accept(true);
            });
        });
    }

    public void loadMockData() {
        loadData();
    }

    public void loadData() {
        app.framework.connection.ProfileLookup.invalidate();
        app.services.DefaultSchemaService.importFromLltoolOnce();
        ensureDefaultConnections();
        rootData = new DatabaseNode("Database Explorer", DatabaseNodeType.ROOT, null, "mdi2d-database");
        profiles = new ArrayList<>();

        try {
            var res = DesktopBootstrap.getInstance().getConnectionApi().getConnections();
            if (res.isSuccess() && !res.getDataOrThrow().isEmpty()) {
                profiles = new ArrayList<>(res.getDataOrThrow());
                buildGroupedTree(rootData, profiles);
            } else {
                rootData = DatabaseMockService.getInstance().createMockRoot();
            }
        } catch (Exception e) {
            rootData = DatabaseMockService.getInstance().createMockRoot();
        }

        normalRoot = null; // data reloaded: the normal tree is rebuilt
        rebuildTree(filterTextField != null ? filterTextField.getText() : null);
        if (miniSidebarView != null && miniSidebarView.isVisible()) {
            populateMiniConnectionIcons();
        }
    }

    /** Builds GROUP folders (nested by '/') and puts each connection in its group. */
    private void buildGroupedTree(DatabaseNode root, List<ConnectionProfileDto> list) {
        app.ui.components.ConnectionTreeBuilder.buildGroupedTree(root, list);
    }

    private void ensureDefaultConnections() {
        // sample profiles are prototype data only (Phase 8: never create profiles for real servers)
        if (app.framework.backend.BackendFlags.real(app.framework.backend.BackendFlags.Area.CONNECTIONS)) return;
        try {
            var api = DesktopBootstrap.getInstance().getConnectionApi();
            var res = api.getConnections();
            if (res.isSuccess()) {
                var existing = res.getDataOrThrow();
                boolean hasPg = existing.stream().anyMatch(c -> c.name().contains("PostgreSQL"));
                boolean hasMs = existing.stream().anyMatch(c -> c.name().contains("SQL Server"));
                boolean hasLite = existing.stream().anyMatch(c -> c.name().contains("SQLite"));

                if (!hasPg) {
                    api.createConnection(new CreateConnectionRequest(
                            "PostgreSQL - dev-local", "LOCAL-SERVER", "DEV", false, "POSTGRESQL",
                            "localhost", 5432, "postgres", "", "postgres", "PASSWORD", "", true, 15, true, Map.of()));
                }
                if (!hasMs) {
                    api.createConnection(new CreateConnectionRequest(
                            "SQL Server - production-replica", "BRAVO-SERVER", "PROD", true, "SQLSERVER",
                            "192.168.1.100", 1433, "bravo_db", "", "sa", "PASSWORD", "", true, 15, true, Map.of()));
                }
                if (!hasLite) {
                    api.createConnection(new CreateConnectionRequest(
                            "SQLite - embedded", "Khách hàng", "DEV", false, "SQLITE",
                            "", 0, "", "memory:test.db", "", "NONE", "", false, 15, true, Map.of()));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private DatabaseNode createConnectionNode(ConnectionProfileDto profile) {
        return app.ui.components.ConnectionTreeBuilder.createConnectionNode(profile);
    }

    private void setupFilter() {
        if (filterTextField != null) {
            filterTextField.textProperty().addListener((obs, oldVal, newVal) -> {
                boolean hasText = newVal != null && !newVal.isBlank();
                if (clearFilterButton != null) {
                    clearFilterButton.setVisible(hasText);
                    clearFilterButton.setManaged(hasText);
                }
                // empty text restores the normal tree at once; typing waits a moment so every key does not rebuild
                if (!hasText) {
                    searchDebounce.stop();
                    rebuildTree(newVal);
                } else {
                    searchDebounce.setOnFinished(e -> rebuildTree(filterTextField.getText()));
                    searchDebounce.playFromStart();
                }
            });
        }
    }

    /** The normal (lazy) tree. Kept while searching so clearing the search brings back the very same nodes. */
    private TreeItem<DatabaseNode> normalRoot;
    private final javafx.animation.PauseTransition searchDebounce = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));

    private static final Comparator<DatabaseNode> NODE_ORDER =
            Comparator.comparing((DatabaseNode n) -> n.getType() == DatabaseNodeType.GROUP ? 0 : 1)
                    .thenComparing(n -> n.getType() == DatabaseNodeType.GROUP ? n.getName().toLowerCase(Locale.ROOT) : "");

    private void rebuildTree(String filterText) {
        if (rootData == null) return;
        String query = filterText != null ? filterText.trim().toLowerCase(Locale.ROOT) : "";
        app.ui.components.DatabaseTreeCell.setSearchQuery(query);

        int matchCount = 0;
        if (query.isEmpty()) {
            if (normalRoot == null || normalRoot.getValue() != rootData) {
                normalRoot = new TreeItem<>(rootData);
                populateTreeItem(rootData, normalRoot, "");
            }
            if (explorerTree.getRoot() != normalRoot) explorerTree.setRoot(normalRoot);
        } else {
            // Search: a separate tree built from what is already loaded (groups, connections, databases, schemas,
            // objects), keeping only the matches and their ancestors, all opened. The normal tree is untouched.
            TreeItem<DatabaseNode> searchRoot = new TreeItem<>(rootData);
            int[] selfMatches = {0};
            buildSearchItems(rootData, searchRoot, query, selfMatches);
            searchRoot.setExpanded(true);
            explorerTree.setRoot(searchRoot);
            matchCount = selfMatches[0];
        }

        boolean noConnections = rootData.getChildren().isEmpty();
        if (noConnectionsBox != null) {
            noConnectionsBox.setVisible(noConnections && query.isEmpty());
            noConnectionsBox.setManaged(noConnections && query.isEmpty());
        }

        if (!query.isEmpty()) {
            if (matchCountLabel != null) {
                matchCountLabel.setText(matchCount + " kết quả");
                matchCountLabel.setVisible(true);
                matchCountLabel.setManaged(true);
            }
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(matchCount == 0);
                emptyStateBox.setManaged(matchCount == 0);
            }
            explorerTree.setVisible(matchCount > 0);
            explorerTree.setManaged(matchCount > 0);
        } else {
            if (matchCountLabel != null) {
                matchCountLabel.setVisible(false);
                matchCountLabel.setManaged(false);
            }
            if (emptyStateBox != null) {
                emptyStateBox.setVisible(false);
                emptyStateBox.setManaged(false);
            }
            explorerTree.setVisible(true);
            explorerTree.setManaged(true);
        }
    }

    /** Returns how many nodes of the subtree were added (matches + their ancestors count only as structure). */
    private int buildSearchItems(DatabaseNode data, TreeItem<DatabaseNode> parent, String q, int[] selfMatches) {
        int added = 0;
        List<DatabaseNode> kids = new ArrayList<>(data.getChildren());
        kids.sort(NODE_ORDER);
        for (DatabaseNode child : kids) {
            DatabaseNodeType t = child.getType();
            boolean folder = t.name().startsWith("CATEGORY_") || t == DatabaseNodeType.LOADING; // "Tables", … are not results
            boolean self = !folder && child.getName() != null && child.getName().toLowerCase(Locale.ROOT).contains(q);
            TreeItem<DatabaseNode> item = new TreeItem<>(child);
            int below = buildSearchItems(child, item, q, selfMatches);
            if (self || below > 0) {
                if (below > 0) item.setExpanded(true);
                parent.getChildren().add(item);
                if (self) selfMatches[0]++;
                added += 1;
            }
        }
        return added;
    }

    /**
     * Recursively populates matching TreeItems; returns the number of matching nodes.
     * Groups are plain TreeItems (children known); connections are lazy (mock metadata on expand).
     */
    private int populateTreeItem(DatabaseNode dataNode, TreeItem<DatabaseNode> treeItem, String query) {
        int matches = 0;
        List<DatabaseNode> children = new ArrayList<>(dataNode.getChildren());
        // groups first, then the rest, each A→Z (connections are already sorted)
        children.sort(Comparator.comparing((DatabaseNode n) -> n.getType() == DatabaseNodeType.GROUP ? 0 : 1)
                .thenComparing(n -> n.getType() == DatabaseNodeType.GROUP ? n.getName().toLowerCase(Locale.ROOT) : ""));

        for (DatabaseNode childData : children) {
            boolean selfMatches = query.isEmpty() || childData.getName().toLowerCase(Locale.ROOT).contains(query);
            TreeItem<DatabaseNode> childItem;
            int childMatches;
            if (childData.getType() == DatabaseNodeType.GROUP) {
                childItem = new TreeItem<>(childData);
                childMatches = populateTreeItem(childData, childItem, selfMatches && !query.isEmpty() ? "" : query);
                String path = childData.getGroupPath();
                boolean expand = !query.isEmpty() || !collapsedGroups.contains(path);
                childItem.setExpanded(expand);
                childItem.expandedProperty().addListener((o, was, now) -> {
                    if (now) collapsedGroups.remove(path); else collapsedGroups.add(path);
                    explorerTree.refresh(); // folder open / closed icon
                });
                if (!query.isEmpty() && selfMatches) childMatches = Math.max(childMatches, 0);
            } else {
                LazyDatabaseTreeItem lazy = new LazyDatabaseTreeItem(childData);
                childItem = lazy;
                childMatches = childData.hasChildren() ? populateTreeItem(childData, lazy, query) : 0;
                if (!query.isEmpty() && childMatches > 0 && !opensLiveConnection(lazy)) {
                    lazy.setExpanded(true);
                }
            }

            if (selfMatches || childMatches > 0) {
                treeItem.getChildren().add(childItem);
                matches += (selfMatches ? 1 : 0) + childMatches;
            }
        }
        return matches;
    }

    @FXML
    public void handleClearFilter() {
        if (filterTextField != null) {
            filterTextField.clear();
        }
    }

    @FXML
    public void handleCollapseAll() {
        if (explorerTree.getRoot() != null) {
            setExpandedRecursive(explorerTree.getRoot(), false);
            explorerTree.getRoot().setExpanded(true);
        }
    }

    /** Kept for compatibility; never expands live connections. Not shown in the header any more. */
    @FXML
    public void handleExpandAll() {
        if (explorerTree.getRoot() != null) {
            setExpandedRecursive(explorerTree.getRoot(), true);
        }
    }

    @FXML
    public void handleRefresh() {
        loadData();
        NotificationService.getInstance().showInfo("Object Explorer", "Đã làm mới danh sách kết nối.");
    }

    @FXML
    public void handleNewGroup() {
        TreeItem<DatabaseNode> sel = explorerTree.getSelectionModel().getSelectedItem();
        String parent = "";
        if (sel != null && sel.getValue() != null && sel.getValue().getType() == DatabaseNodeType.GROUP) {
            parent = sel.getValue().getGroupPath();
        }
        newGroup(parent);
    }

    @FXML
    public void handleCollapseToMini() {
        app.services.LayoutService.getInstance().setSidebarCollapsed(true);
    }

    @FXML
    public void handleExpandFromMini() {
        app.services.LayoutService.getInstance().setSidebarCollapsed(false);
    }

    public void setCollapsed(boolean collapsed) {
        if (fullSidebarView != null) {
            fullSidebarView.setVisible(!collapsed);
            fullSidebarView.setManaged(!collapsed);
        }
        if (miniSidebarView != null) {
            miniSidebarView.setVisible(collapsed);
            miniSidebarView.setManaged(collapsed);
        }
        if (collapsed) {
            populateMiniConnectionIcons();
        }
    }

    private static void collectConnections(DatabaseNode node, List<DatabaseNode> out) {
        for (DatabaseNode c : node.getChildren()) {
            if (c.getType() == DatabaseNodeType.CONNECTION) out.add(c);
            else if (c.getType() == DatabaseNodeType.GROUP) collectConnections(c, out);
        }
    }

    private void populateMiniConnectionIcons() {
        if (miniConnectionIconsBox == null || rootData == null) return;
        miniConnectionIconsBox.getChildren().clear();
        List<DatabaseNode> conns = new ArrayList<>();
        collectConnections(rootData, conns);
        for (DatabaseNode connNode : conns) {
            miniConnectionIconsBox.getChildren().add(createMiniConnectionButton(connNode));
        }
    }

    private Button createMiniConnectionButton(DatabaseNode connNode) {
        Button btn = new Button();
        btn.getStyleClass().addAll("icon-button", "mini-connection-btn");
        btn.setFocusTraversable(false);

        FontIcon icon = new FontIcon("mdi2d-database-outline");
        icon.setIconSize(14);
        String color = ConnectionColorService.getInstance().colorFor(
                connNode.getConnectionId() != null ? connNode.getConnectionId() : connNode.getName()).hex();
        icon.setStyle("-fx-icon-color: " + color + ";");
        btn.setGraphic(icon);

        ConnectionProfileDto p = connNode.getConnectionProfile();
        var status = ExplorerConnectionState.getInstance().get(connNode.getConnectionId());
        String tooltipText = String.format(
                "Connection: %s\nDetails: %s\nStatus: %s",
                connNode.getName(),
                connNode.getSubText() != null ? connNode.getSubText() : "",
                switch (status) {
                    case CONNECTED -> "đã kết nối";
                    case CONNECTING -> "đang kết nối…";
                    case ERROR -> "lỗi";
                    default -> "chưa kết nối";
                });
        if (p != null && p.group() != null && !p.group().isBlank()) tooltipText += "\nNhóm: " + p.group();
        Tooltip tooltip = new Tooltip(tooltipText + "\n\nBấm để mở trong Explorer");
        tooltip.setShowDelay(javafx.util.Duration.millis(150));
        btn.setTooltip(tooltip);

        btn.setOnAction(e -> {
            app.services.LayoutService.getInstance().setSidebarCollapsed(false);
            selectConnectionInTree(connNode);
        });
        return btn;
    }

    private void selectConnectionInTree(DatabaseNode targetNode) {
        if (explorerTree.getRoot() == null) return;
        TreeItem<DatabaseNode> found = findItem(explorerTree.getRoot(), targetNode);
        if (found != null) {
            for (TreeItem<DatabaseNode> p = found.getParent(); p != null; p = p.getParent()) p.setExpanded(true);
            explorerTree.getSelectionModel().select(found);
            explorerTree.scrollTo(Math.max(0, explorerTree.getRow(found) - 2));
        }
    }

    private TreeItem<DatabaseNode> findItem(TreeItem<DatabaseNode> parent, DatabaseNode target) {
        for (TreeItem<DatabaseNode> item : parent.getChildren()) {
            DatabaseNode v = item.getValue();
            if (v == null) continue;
            if (v.getType() == DatabaseNodeType.CONNECTION
                    && Objects.equals(v.getConnectionId(), target.getConnectionId())
                    && v.getName().equals(target.getName())) {
                return item;
            }
            if (v.getType() == DatabaseNodeType.GROUP) {
                TreeItem<DatabaseNode> r = findItem(item, target);
                if (r != null) return r;
            }
        }
        return null;
    }

    @FXML
    public void handleCollapseSidebar() {
        handleCollapseToMini();
    }

    @FXML
    public void handleAddConnection() {
        newConnection("");
    }

    private Stage ownerStage() {
        return (fullSidebarView != null && fullSidebarView.getScene() != null && fullSidebarView.getScene().getWindow() instanceof Stage s)
                ? s
                : AppContext.getInstance().getPrimaryStage();
    }

    @FXML
    public void handleToolbarFilter() {
        TreeItem<DatabaseNode> selected = explorerTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue() != null) {
            DatabaseNode node = selected.getValue();
            if (node.getType() == DatabaseNodeType.CONNECTION && node.getConnectionProfile() != null) {
                filterDatabases(node);
                return;
            } else if (isCategoryNode(node.getType())) {
                filterObjects(node);
                return;
            }
        }
        // Fallback: first connection of the tree
        List<DatabaseNode> conns = new ArrayList<>();
        if (rootData != null) collectConnections(rootData, conns);
        conns.stream().filter(n -> n.getConnectionProfile() != null).findFirst().ifPresentOrElse(
                this::filterDatabases,
                () -> NotificationService.getInstance().showInfo("Lọc database", "Chọn một kết nối trước."));
    }

    private boolean isCategoryNode(DatabaseNodeType type) {
        return switch (type) {
            case CATEGORY_TABLES, CATEGORY_VIEWS, CATEGORY_MATERIALIZED_VIEWS,
                 CATEGORY_FUNCTIONS, CATEGORY_PROCEDURES, CATEGORY_TRIGGERS,
                 CATEGORY_USER_TYPES, CATEGORY_SYNONYMS -> true;
            default -> false;
        };
    }

    private String getObjectFilterType(DatabaseNodeType type, String defaultName) {
        return switch (type) {
            case CATEGORY_TABLES -> "TABLES";
            case CATEGORY_VIEWS -> "VIEWS";
            case CATEGORY_MATERIALIZED_VIEWS -> "MATERIALIZED_VIEWS";
            case CATEGORY_FUNCTIONS -> "FUNCTIONS";
            case CATEGORY_PROCEDURES -> "PROCEDURES";
            case CATEGORY_TRIGGERS -> "TRIGGERS";
            case CATEGORY_USER_TYPES -> "USER_TYPES";
            case CATEGORY_SYNONYMS -> "SYNONYMS";
            default -> defaultName != null ? defaultName.toUpperCase() : "OBJECTS";
        };
    }

    /** True when expanding this item would trigger a metadata load (real DB when mock is off). */
    private static boolean opensLiveConnection(TreeItem<DatabaseNode> item) {
        DatabaseNode node = item != null ? item.getValue() : null;
        return node != null && node.getConnectionProfile() != null && !node.isLoaded();
    }

    @SuppressWarnings("unchecked")
    private void setExpandedRecursive(TreeItem<?> item, boolean expanded) {
        if (item != null) {
            if (expanded && item.getValue() instanceof DatabaseNode
                    && opensLiveConnection((TreeItem<DatabaseNode>) item)) {
                return;
            }
            item.setExpanded(expanded);
            for (TreeItem<?> child : item.getChildren()) {
                setExpandedRecursive(child, expanded);
            }
        }
    }

    private void handleNodeAction(DatabaseNode node) {
        if (node == null) return;
        if (node.getType() == DatabaseNodeType.CONNECTION) {
            TreeItem<DatabaseNode> sel = explorerTree.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue() == node) sel.setExpanded(true);
            return;
        }
        if (node.getType() == DatabaseNodeType.DB_USER || node.getType() == DatabaseNodeType.ROLE) {
            MainController mc = AppContext.getInstance().getMainController();
            if (mc != null) mc.openPrincipalPermissions(node.getConnectionProfile(), node.getCatalog(), node.getName());
            return;
        }
        if (node.getType() == DatabaseNodeType.TABLE || node.getType() == DatabaseNodeType.VIEW) {
            MainController mc = AppContext.getInstance().getMainController();
            if (mc != null) {
                String dialect = node.getConnectionProfile() != null ? node.getConnectionProfile().databaseType() : "POSTGRESQL";
                TableMetadata tblMeta = node.getTableMetadata() != null ? node.getTableMetadata()
                        : new TableMetadata(node.getName(), node.getSchema(), node.getCatalog(), "TABLE", 0L, null);
                String sql = SqlScriptGenerator.generateSelect(dialect, tblMeta, null);
                String conn = node.getConnectionProfile() != null ? node.getConnectionProfile().name() : null;
                if (app.framework.backend.BackendFlags.mock(app.framework.backend.BackendFlags.Area.QUERY)) {
                    mc.openQueryConsole(node.getName() + ".sql", sql, conn, node.getCatalog(), node.getSchema());
                } else {
                    mc.executeQueryDirectly(node.getName() + ".sql", sql, conn, node.getCatalog(), node.getSchema());
                }
            }
        }
    }

    private int lastIconSize = -1;

    private int iconSizeOf(app.models.IdeSettings s) {
        int px = s != null && s.getAppearance() != null ? s.getAppearance().getIconSize() : 13;
        lastIconSize = px;
        return px;
    }

        public TreeView<DatabaseNode> getExplorerTree() {
        return explorerTree;
    }

    // ================================================================== ExplorerActions

    @Override
    public void newConnection(String groupPath) {
        String connectId = ConnectionManagerDialog.showNew(ownerStage(), groupPath);
        loadData();
        openConnectionById(connectId);
    }

    @Override
    public void newGroup(String parentPath) {
        app.ui.dialogs.GroupNameDialog.show(ownerStage(), false, parentPath, "",
                        name -> ExplorerGroupService.getInstance().createGroup(parentPath, name, profiles))
                .ifPresent(name -> {
                    collapsedGroups.remove(ExplorerGroupService.join(parentPath, name));
                    loadData();
                    NotificationService.getInstance().showSuccess("Nhóm", "Đã tạo nhóm \"" + name + "\".");
                });
    }

    @Override
    public void renameGroup(DatabaseNode group) {
        if (group == null) return;
        String oldPath = group.getGroupPath();
        app.ui.dialogs.GroupNameDialog.show(ownerStage(), true, ExplorerGroupService.parentOf(oldPath), group.getName(),
                        name -> ExplorerGroupService.getInstance().renameGroup(oldPath, name, profiles))
                .ifPresent(name -> {
                    loadData();
                    NotificationService.getInstance().showSuccess("Nhóm", "Đã đổi tên nhóm thành \"" + name + "\".");
                });
    }

    @Override
    public void deleteGroup(DatabaseNode group) {
        if (group == null) return;
        String path = group.getGroupPath();
        int count = ExplorerGroupService.getInstance().countConnections(path, profiles);
        boolean ok = app.ui.dialogs.ConfirmDialog.show(ownerStage(), "Xóa nhóm",
                "Xóa nhóm \"" + group.getName() + "\"?",
                count > 0
                        ? count + " kết nối và các nhóm con bên trong sẽ chuyển về \"Chưa phân nhóm\". Kết nối không bị xóa."
                        : "Nhóm đang trống.",
                "Xóa nhóm", true);
        if (ok) {
            String err = ExplorerGroupService.getInstance().deleteGroup(path, profiles);
            loadData();
            if (err != null) NotificationService.getInstance().showWarning("Nhóm", err);
            else NotificationService.getInstance().showInfo("Nhóm", "Đã xóa nhóm \"" + group.getName() + "\".");
        }
    }

    @Override
    public void editConnection(DatabaseNode connection) {
        String target = connection.getConnectionId() != null && !connection.getConnectionId().isBlank()
                ? connection.getConnectionId() : connection.getName();
        String connectId = ConnectionManagerDialog.show(ownerStage(), target);
        loadData();
        openConnectionById(connectId);
    }

    @Override
    public void duplicateConnection(DatabaseNode connection) {
        if (connection.getConnectionId() == null) return;
        String newName = connection.getName() + "_Copy";
        try {
            var res = DesktopBootstrap.getInstance().getConnectionApi().duplicateConnection(connection.getConnectionId(), newName);
            if (res.isSuccess()) {
                loadData();
                NotificationService.getInstance().showSuccess("Kết nối", "Đã nhân bản thành \"" + newName + "\".");
            } else {
                NotificationService.getInstance().showWarning("Kết nối", res.getError().map(e -> e.message()).orElse("Không nhân bản được."));
            }
        } catch (Exception e) {
            NotificationService.getInstance().showWarning("Kết nối", "Không nhân bản được: " + e.getMessage());
        }
    }

    @Override
    public void deleteConnection(DatabaseNode connection) {
        boolean ok = app.ui.dialogs.ConfirmDialog.show(ownerStage(), "Xóa kết nối",
                "Xóa kết nối \"" + connection.getName() + "\"?",
                "Cấu hình kết nối sẽ bị xóa khỏi máy. Database không bị ảnh hưởng.",
                "Xóa kết nối", true);
        if (ok) {
            if (connection.getConnectionId() != null) {
                app.framework.session.TabSessionManager.getInstance().closeAllFor(connection.getConnectionId());
                DesktopBootstrap.getInstance().getConnectionApi().deleteConnection(connection.getConnectionId());
                ExplorerConnectionState.getInstance().set(connection.getConnectionId(), ExplorerConnectionState.Status.DISCONNECTED);
                app.services.PasswordPolicyService.getInstance().clear(connection.getConnectionId());
            }
            loadData();
        }
    }

    @Override
    public void disconnect(TreeItem<DatabaseNode> item) {
        if (item == null || item.getValue() == null) return;
        DatabaseNode node = item.getValue();
        item.setExpanded(false);
        node.setLoaded(false);
        node.clearChildren();
        item.getChildren().clear();
        if (item instanceof LazyDatabaseTreeItem) {
            item.getChildren().add(new TreeItem<>(new DatabaseNode("Đang tải…", DatabaseNodeType.LOADING)));
        }
        if (node.getConnectionId() != null) {
            // connection hierarchy (2026-11-06): the Explorer connection is the top level, so disconnecting it closes
            // the session of every tab on it too (running statements are cancelled); the tabs show "Chưa kết nối"
            app.framework.session.TabSessionManager.getInstance().closeAllFor(node.getConnectionId());
            DesktopMetadataService.getInstance().invalidate(node.getConnectionId());
            ExplorerConnectionState.getInstance().set(node.getConnectionId(), ExplorerConnectionState.Status.DISCONNECTED);
        }
        NotificationService.getInstance().showInfo("Kết nối", "Đã ngắt kết nối " + node.getName() + " (cả các tab đang dùng).");
    }

    @Override
    public void filterDatabases(DatabaseNode node) {
        ConnectionProfileDto profile = node.getConnectionProfile();
        if (profile == null) return;
        String connId = profile.id();
        Stage stage = ownerStage();
        DesktopMetadataService.getInstance().fetchCatalogs(profile, dbs ->
                Platform.runLater(() -> DatabaseFilterDialog.show(stage, connId, node.getName(), dbs, () -> refreshNodeFromCache(node))));
    }

    /**
     * A filter changed: rebuild only that node's children from the cached metadata and keep the rest of the
     * tree (and the open nodes below it) as it is — no whole-tree reload, no server round trip.
     */
    private void refreshNodeFromCache(DatabaseNode node) {
        TreeItem<DatabaseNode> ti = findTreeItem(explorerTree.getRoot(), node);
        if (ti instanceof LazyDatabaseTreeItem lazy) {
            lazy.refreshFromCache();
        }
        explorerTree.refresh(); // filter button state / count badge
    }

    private static TreeItem<DatabaseNode> findTreeItem(TreeItem<DatabaseNode> from, DatabaseNode node) {
        if (from == null) return null;
        if (from.getValue() == node) return from;
        for (TreeItem<DatabaseNode> c : from.getChildren()) {
            TreeItem<DatabaseNode> r = findTreeItem(c, node);
            if (r != null) return r;
        }
        return null;
    }

    private void filterSchemas(DatabaseNode node) {
        ConnectionProfileDto profile = node.getConnectionProfile();
        if (profile == null) return;
        String catalog = node.getCatalog() != null ? node.getCatalog() : node.getName();
        Stage stage = ownerStage();
        DesktopMetadataService.getInstance().fetchSchemas(profile, catalog, schemas ->
                Platform.runLater(() -> SchemaFilterDialog.show(stage, profile.id(), catalog, schemas, () -> refreshNodeFromCache(node))));
    }

    @Override
    public void filterObjects(DatabaseNode node) {
        if (node.getType() == DatabaseNodeType.DATABASE) {
            filterSchemas(node);
            return;
        }
        String connId = node.getConnectionProfile() != null ? node.getConnectionProfile().id() : node.getConnectionId();
        String objType = getObjectFilterType(node.getType(), node.getName());
        ObjectFilterDialog.show(ownerStage(), connId, node.getCatalog(), node.getSchema(), objType, () -> refreshNodeFromCache(node));
    }

    @Override
    public void moveConnection(String connectionId, String targetGroupPath) {
        ConnectionProfileDto p = profiles.stream().filter(x -> Objects.equals(x.id(), connectionId)).findFirst().orElse(null);
        if (p == null) return;
        String err = ExplorerGroupService.getInstance().moveConnection(p, targetGroupPath);
        if (err != null) {
            NotificationService.getInstance().showWarning("Di chuyển kết nối", err);
        }
        collapsedGroups.remove(ExplorerGroupService.normalize(targetGroupPath));
        loadData();
    }

    @Override
    public void moveGroup(String groupPath, String targetParentPath) {
        String err = ExplorerGroupService.getInstance().moveGroup(groupPath, targetParentPath, profiles);
        if (err != null) {
            NotificationService.getInstance().showWarning("Không thể di chuyển nhóm", err);
        }
        loadData();
    }

    /** "Lưu & kết nối" in the Connection Manager: select and expand that connection in the tree. */
    public void openConnectionById(String connectionId) {
        if (connectionId == null || explorerTree.getRoot() == null) return;
        List<DatabaseNode> conns = new ArrayList<>();
        collectConnections(rootData, conns);
        conns.stream().filter(n -> connectionId.equals(n.getConnectionId())).findFirst().ifPresent(n -> {
            selectConnectionInTree(n);
            TreeItem<DatabaseNode> sel = explorerTree.getSelectionModel().getSelectedItem();
            if (sel != null) sel.setExpanded(true);
        });
    }

    @Override
    public void reloadAll() {
        loadData();
    }

}
