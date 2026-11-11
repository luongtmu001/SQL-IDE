package app.ui.components;

import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.filter.DatabaseFilter;
import app.framework.metadata.filter.DesktopFilterManager;
import app.framework.metadata.filter.ObjectFilter;
import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.metadata.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * High-performance Lazy-Loading TreeItem for Database Explorer (Phase 6.3).
 * Fetches schema metadata on-demand as nodes expand, preventing UI freezes.
 * Fully supports Table sub-categories (Columns, Keys, Constraints, Triggers, Indexes)
 * and Schema-level categories (Tables, Views, Materialized Views, Procedures, Functions,
 * Triggers, Sequences, User Defined Types) with object count badges and filtering.
 */
public class LazyDatabaseTreeItem extends TreeItem<DatabaseNode> {

    private static final Logger log = LoggerFactory.getLogger(LazyDatabaseTreeItem.class);

    private boolean isFirstTimeChildren = true;
    private boolean isFirstTimeLeaf = true;
    private boolean isLeaf = false;
    private boolean guardPassed = false;

    /** Called before a CONNECTION is opened; answers true to continue (Phase 2B password policy). */
    private static java.util.function.BiConsumer<DatabaseNode, java.util.function.Consumer<Boolean>> connectGuard;

    /** Repaints the Explorer rows (set by the SidebarController): a count badge changed after the row was drawn. */
    private static Runnable treeRefresher;

    public static void setTreeRefresher(Runnable refresher) {
        treeRefresher = refresher;
    }

    private static void refreshTreeView() {
        if (treeRefresher != null) treeRefresher.run();
    }

    public static void setConnectGuard(java.util.function.BiConsumer<DatabaseNode, java.util.function.Consumer<Boolean>> guard) {
        connectGuard = guard;
    }

    public LazyDatabaseTreeItem(DatabaseNode value) {
        super(value);

        expandedProperty().addListener((obs, oldVal, isExpanded) -> {
            if (isExpanded && value != null && !value.isLoaded()) {
                if (value.getType() == DatabaseNodeType.CONNECTION && connectGuard != null && !guardPassed) {
                    // SCR-DLG-002: ask for the password first when needed; cancel collapses the node
                    connectGuard.accept(value, ok -> {
                        if (ok) {
                            guardPassed = true;
                            loadChildrenAsync();
                        } else {
                            setExpanded(false);
                        }
                    });
                } else {
                    loadChildrenAsync();
                }
            }
            if (!isExpanded) guardPassed = false;
        });
    }

    @Override
    public ObservableList<TreeItem<DatabaseNode>> getChildren() {
        if (isFirstTimeChildren) {
            isFirstTimeChildren = false;
            DatabaseNode node = getValue();
            if (node != null && node.isLoaded() && node.hasChildren()) {
                super.getChildren().clear();
                java.util.List<TreeItem<DatabaseNode>> batch1 = new java.util.ArrayList<>(); // one tree event, not one per row
                for (DatabaseNode child : node.getChildren()) {
                    batch1.add(new LazyDatabaseTreeItem(child));
                }
                super.getChildren().addAll(batch1);
            } else if (!isLeaf() && !(node != null && node.isLoaded())) {
                // Seed a dummy child so the disclosure triangle/chevron appears
                super.getChildren().add(new TreeItem<>(new DatabaseNode("Đang tải…", DatabaseNodeType.LOADING)));
            }
        }
        return super.getChildren();
    }

    @Override
    public boolean isLeaf() {
        if (isFirstTimeLeaf) {
            isFirstTimeLeaf = false;
            DatabaseNode node = getValue();
            if (node == null) {
                isLeaf = true;
            } else {
                isLeaf = switch (node.getType()) {
                    case COLUMN, KEY, CONSTRAINT, INDEX, TRIGGER, SEQUENCE, USER_TYPE, PARAMETER, ROLE, SYNONYM, DB_USER, REMOTE_TABLE, REMOTE_VIEW, LOADING -> true;
                    default -> false;
                };
            }
        }
        return isLeaf;
    }

    /** Refresh: re-reads the server (cache invalidated) and re-loads this node. */
    public void reload() {
        reloadInternal(true);
    }

    /**
     * Filter applied: rebuilds only this node's children from the cached metadata (no server round trip) and
     * re-opens the descendants that were open before, so the tree does not collapse.
     */
    public void refreshFromCache() {
        Set<String> open = new HashSet<>();
        collectExpanded(this, "", open);
        if (!open.isEmpty()) {
            whenLoaded(() -> restoreExpandedChildren(open, ""));
        }
        reloadInternal(false);
    }

    private static void collectExpanded(TreeItem<DatabaseNode> item, String prefix, Set<String> out) {
        for (TreeItem<DatabaseNode> c : item.getChildren()) {
            if (c.getValue() == null || c.isLeaf() || !c.isExpanded()) continue;
            String key = prefix + c.getValue().getType() + ":" + c.getValue().getName() + "/";
            out.add(key);
            collectExpanded(c, key, out);
        }
    }

    private void restoreExpandedChildren(Set<String> keys, String prefix) {
        for (TreeItem<DatabaseNode> c : new java.util.ArrayList<>(super.getChildren())) {
            if (!(c instanceof LazyDatabaseTreeItem li) || li.getValue() == null) continue;
            String key = prefix + li.getValue().getType() + ":" + li.getValue().getName() + "/";
            if (keys.contains(key)) {
                li.whenLoaded(() -> li.restoreExpandedChildren(keys, key));
                li.setExpanded(true);
            }
        }
    }

    /** Runs {@code action} once, when this node finishes loading its children. */
    private void whenLoaded(Runnable action) {
        DatabaseNode n = getValue();
        if (n == null) return;
        n.loadingProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Boolean> o, Boolean was, Boolean now) {
                if (Boolean.FALSE.equals(now)) {
                    n.loadingProperty().removeListener(this);
                    if (n.isLoaded()) action.run();
                }
            }
        });
    }

    private void reloadInternal(boolean invalidateCache) {
        DatabaseNode node = getValue();
        if (node == null) return;
        node.setLoaded(false);
        node.setLoading(false);
        node.clearChildren();
        super.getChildren().clear();
        isFirstTimeChildren = false; // the loader below fills the children; re-seeding would leave a stray "Đang tải…" row

        String connId = node.getConnectionId() != null ? node.getConnectionId()
                : (node.getConnectionProfile() != null ? node.getConnectionProfile().id() : null);
        if (invalidateCache && connId != null) {
            if (node.getType() == DatabaseNodeType.CONNECTION) {
                DesktopMetadataService.getInstance().invalidate(connId);
            } else {
                String path = (node.getCatalog() != null ? node.getCatalog() : "") + "/" + (node.getSchema() != null ? node.getSchema() : "");
                DesktopMetadataService.getInstance().invalidateNode(connId, path);
            }
        }
        if (isExpanded()) {
            loadChildrenAsync();
        } else {
            setExpanded(true);
        }
    }

    /**
     * Phase 8 / D2: a failed metadata read shows "⚠ Không tải được: …" under the node (instead of an
     * empty folder) and leaves the node unloaded, so expanding again or Refresh retries.
     */
    private boolean showLoadError(ConnectionProfileDto profile) {
        String err = DesktopMetadataService.getInstance().takeError(profile.id());
        if (err == null) return false;
        DatabaseNode msg = new DatabaseNode("⚠ Không tải được: " + err, DatabaseNodeType.LOADING);
        super.getChildren().setAll(new TreeItem<>(msg));
        if (getValue() != null) {
            getValue().setLoaded(false);
            getValue().setLoading(false);
        }
        return true;
    }

    private void loadChildrenAsync() {
        DatabaseNode node = getValue();
        if (node == null) return;

        // If node has pre-defined mock children and no real connectionProfile, use them
        if (node.getConnectionProfile() == null && node.hasChildren()) {
            super.getChildren().clear();
            java.util.List<TreeItem<DatabaseNode>> batch2 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (DatabaseNode child : node.getChildren()) {
                batch2.add(new LazyDatabaseTreeItem(child));
            }
            super.getChildren().addAll(batch2);
            node.setLoaded(true);
            return;
        }

        ConnectionProfileDto profile = node.getConnectionProfile();
        if (profile == null) {
            super.getChildren().clear();
            node.setLoaded(true);
            return;
        }

        if (node.isLoading()) {
            return; // already fetching (expanded, collapsed and expanded again)
        }
        // Loading: a "Đang tải…" row right under the node (small spinner, see DatabaseTreeCell)
        node.setLoading(true);
        super.getChildren().setAll(new TreeItem<>(new DatabaseNode("Đang tải…", DatabaseNodeType.LOADING)));

        switch (node.getType()) {
            case CONNECTION -> loadConnectionChildren(node, profile);
            case DATABASE -> loadDatabaseChildren(node, profile);
            case SCHEMA -> loadSchemaChildren(node, profile);
            case CATEGORY_TABLES -> loadCategoryTables(node, profile);
            case CATEGORY_VIEWS -> loadCategoryViews(node, profile);
            case CATEGORY_MATERIALIZED_VIEWS -> loadCategoryMaterializedViews(node, profile);
            case CATEGORY_FUNCTIONS, CATEGORY_PROCEDURES -> loadCategoryRoutines(node, profile);
            case CATEGORY_USER_TYPES -> loadCategoryUserTypes(node, profile);
            case CATEGORY_SEQUENCES -> loadCategorySequences(node, profile);
            case CATEGORY_SYNONYMS -> loadCategorySynonyms(node, profile);
            case CATEGORY_USERS -> loadDatabaseUsers(node, profile);
            case TABLE -> loadTableSubfolders(node, profile);
            case VIEW, MATERIALIZED_VIEW -> loadViewSubfolders(node, profile);
            case CATEGORY_COLUMNS -> loadTableColumns(node, profile);
            case CATEGORY_KEYS -> loadTableKeys(node, profile);
            case CATEGORY_CONSTRAINTS -> loadTableConstraints(node, profile);
            case CATEGORY_INDEXES -> loadCategoryIndexes(node, profile);
            case CATEGORY_TRIGGERS -> loadCategoryTriggers(node, profile);
            case FUNCTION, PROCEDURE -> loadRoutineParameters(node, profile);
            case ROLES_USERS -> loadRoles(node, profile);
            case SERVER_OBJECTS -> loadLinkedServers(node, profile);
            case LINKED_SERVER -> loadRemoteCatalogs(node, profile);
            case REMOTE_DATABASE -> loadRemoteObjects(node, profile);
            default -> {
                super.getChildren().clear();
                node.setLoaded(true);
            }
        }
    }

    private void loadConnectionChildren(DatabaseNode node, ConnectionProfileDto profile) {
        String dialect = profile.databaseType().toUpperCase();
        if (!DesktopFilterManager.getInstance().hasDatabaseFilter(profile.id())) {
            DesktopFilterManager.getInstance().syncFromProfile(profile);
        }
        String defaultSchema = app.services.DefaultSchemaService.get(profile.id());
        if (defaultSchema == null && profile.properties() != null) {
            defaultSchema = profile.properties().get("defaultSchema");
            if (defaultSchema == null) defaultSchema = profile.properties().get("schema");
        }
        final String resolvedSchema = defaultSchema;
        Platform.runLater(() -> {
            if (profile.name() != null && !profile.name().isBlank()) {
                app.framework.state.GlobalDesktopState.getInstance().setActiveConnection(profile.name());
            }
            if (profile.databaseName() != null && !profile.databaseName().isBlank()) {
                app.framework.state.GlobalDesktopState.getInstance().setActiveCatalog(profile.databaseName());
            }
            if (resolvedSchema != null && !resolvedSchema.isBlank()) {
                app.framework.state.GlobalDesktopState.getInstance().setActiveSchema(resolvedSchema);
            }
        });

        if (dialect.contains("SQLITE")) {
            Platform.runLater(() -> {
                super.getChildren().clear();
                node.clearChildren();

                DatabaseNode tablesCat = new DatabaseNode("Tables", DatabaseNodeType.CATEGORY_TABLES);
                tablesCat.setConnectionProfile(profile).setCatalog("main").setSchema("main");
                node.addChild(tablesCat);

                DatabaseNode viewsCat = new DatabaseNode("Views", DatabaseNodeType.CATEGORY_VIEWS);
                viewsCat.setConnectionProfile(profile).setCatalog("main").setSchema("main");
                node.addChild(viewsCat);

                DatabaseNode indexesCat = new DatabaseNode("Indexes", DatabaseNodeType.CATEGORY_INDEXES);
                indexesCat.setConnectionProfile(profile).setCatalog("main").setSchema("main");
                node.addChild(indexesCat);

                DatabaseNode triggersCat = new DatabaseNode("Triggers", DatabaseNodeType.CATEGORY_TRIGGERS);
                triggersCat.setConnectionProfile(profile).setCatalog("main").setSchema("main");
                node.addChild(triggersCat);

                super.getChildren().addAll(
                        new LazyDatabaseTreeItem(tablesCat),
                        new LazyDatabaseTreeItem(viewsCat),
                        new LazyDatabaseTreeItem(indexesCat),
                        new LazyDatabaseTreeItem(triggersCat)
                );
                node.setLoaded(true);
            });
            return;
        }

        // PostgreSQL & SQL Server: Fetch catalogs (databases) with DatabaseFilter
        DesktopMetadataService.getInstance().fetchCatalogs(profile, catalogs -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            DatabaseFilter dbFilter = DesktopFilterManager.getInstance().getDatabaseFilter(profile.id());

            if (catalogs.isEmpty()) {
                if (profile.databaseName() != null && !profile.databaseName().isBlank()) {
                    catalogs = List.of(profile.databaseName());
                } else if (dialect.contains("SQLSERVER") || dialect.contains("MSSQL")) {
                    catalogs = List.of("master");
                } else if (dialect.contains("POSTGRE")) {
                    catalogs = List.of("postgres");
                }
            }

            java.util.List<TreeItem<DatabaseNode>> batch3 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (String catName : catalogs) {
                if (dbFilter.isActive() && !dbFilter.matches(catName)) {
                    continue; // Skip filtered out databases
                }
                DatabaseNode dbNode = new DatabaseNode(catName, DatabaseNodeType.DATABASE, profile.databaseType());
                // LLTool: the database node carries the connection's default schema (New Query context)
                dbNode.setConnectionProfile(profile).setCatalog(catName).setSchema(resolvedSchema);
                node.addChild(dbNode);
                batch3.add(new LazyDatabaseTreeItem(dbNode));
            }
            if (batch3.isEmpty() && dbFilter.isActive() && !dbFilter.getSelectedDatabases().isEmpty()) {
                for (String catName : dbFilter.getSelectedDatabases()) {
                    DatabaseNode dbNode = new DatabaseNode(catName, DatabaseNodeType.DATABASE, profile.databaseType());
                    dbNode.setConnectionProfile(profile).setCatalog(catName).setSchema(resolvedSchema);
                    node.addChild(dbNode);
                    batch3.add(new LazyDatabaseTreeItem(dbNode));
                }
            }
            super.getChildren().addAll(batch3);

            if (dialect.contains("SQLSERVER") || dialect.contains("MSSQL")) {
                DatabaseNode serverObjNode = new DatabaseNode("Linked Servers", DatabaseNodeType.SERVER_OBJECTS);
                serverObjNode.setConnectionProfile(profile);
                node.addChild(serverObjNode);
                super.getChildren().add(new LazyDatabaseTreeItem(serverObjNode));
            }

            // Roles / Users node: PostgreSQL only (LLTool parity); SQL Server users sit under each database (batch 2)
            if (dialect.contains("POSTGRE")) {
                DatabaseNode rolesUsers = new DatabaseNode("Roles / Users", DatabaseNodeType.ROLES_USERS);
                rolesUsers.setConnectionProfile(profile);
                node.addChild(rolesUsers);
                super.getChildren().add(new LazyDatabaseTreeItem(rolesUsers));
            }

            node.setLoaded(true);
            refreshTreeView();
        });
    }

    private void loadDatabaseChildren(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog() != null ? node.getCatalog() : node.getName();

        // LLTool: a connection with a default schema shows only that schema when its database is first opened
        // (the schema filter starts as [default schema]; the user can change or clear it afterwards)
        String defaultSchema = app.services.DefaultSchemaService.get(profile.id());
        DesktopFilterManager filters = DesktopFilterManager.getInstance();
        if (defaultSchema != null && !filters.hasSchemaFilter(profile.id(), catalog)
                && app.services.DefaultSchemaService.firstApply(profile.id(), catalog)) {
            filters.setSchemaFilter(profile.id(), catalog, new DatabaseFilter(java.util.Set.of(defaultSchema)));
            refreshTreeView();
        }

        DesktopMetadataService.getInstance().fetchSchemas(profile, catalog, schemas -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            if (schemas.isEmpty()) {
                schemas = List.of(profile.databaseType().toUpperCase().contains("SQLSERVER") ? "dbo" : "public");
            }

            DatabaseFilter schemaFilter = filters.getSchemaFilter(profile.id(), catalog);
            if (schemaFilter.isActive() && defaultSchema != null && schemaFilter.getSelectedDatabases().size() == 1
                    && schemaFilter.getSelectedDatabases().iterator().next().equalsIgnoreCase(defaultSchema)
                    && schemas.stream().noneMatch(schemaFilter::matches)) {
                // the default schema does not exist in this database: show all schemas (LLTool fallback)
                filters.setSchemaFilter(profile.id(), catalog, null);
                schemaFilter = filters.getSchemaFilter(profile.id(), catalog);
                refreshTreeView();
            }
            java.util.List<TreeItem<DatabaseNode>> batch4 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (String schemaName : schemas) {
                if (!schemaFilter.matches(schemaName)) {
                    continue; // Explorer parity: "Filter Schemas"
                }
                DatabaseNode schemaNode = new DatabaseNode(schemaName, DatabaseNodeType.SCHEMA, "schema");
                schemaNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schemaName);
                node.addChild(schemaNode);
                batch4.add(new LazyDatabaseTreeItem(schemaNode));
            }
            super.getChildren().addAll(batch4);
            // SQL Server: the database's users (not subject to the schema filter)
            if (profile.databaseType().toUpperCase().contains("SQLSERVER") || profile.databaseType().toUpperCase().contains("MSSQL")) {
                DatabaseNode usersCat = new DatabaseNode("Users", DatabaseNodeType.CATEGORY_USERS);
                usersCat.setConnectionProfile(profile).setCatalog(catalog);
                node.addChild(usersCat);
                super.getChildren().add(new LazyDatabaseTreeItem(usersCat));
            }
            node.setLoaded(true);
        });
    }

    private void loadSchemaChildren(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema() != null ? node.getSchema() : node.getName();
        String dialect = profile.databaseType().toUpperCase();

        super.getChildren().clear();
        node.clearChildren();

        // 1. Tables
        DatabaseNode tablesCat = new DatabaseNode("Tables", DatabaseNodeType.CATEGORY_TABLES);
        tablesCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
        node.addChild(tablesCat);
        super.getChildren().add(new LazyDatabaseTreeItem(tablesCat));

        // 2. Views
        DatabaseNode viewsCat = new DatabaseNode("Views", DatabaseNodeType.CATEGORY_VIEWS);
        viewsCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
        node.addChild(viewsCat);
        super.getChildren().add(new LazyDatabaseTreeItem(viewsCat));

        // 3. Materialized Views (PostgreSQL)
        if (dialect.contains("POSTGRE")) {
            DatabaseNode mviewsCat = new DatabaseNode("Materialized Views", DatabaseNodeType.CATEGORY_MATERIALIZED_VIEWS);
            mviewsCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
            node.addChild(mviewsCat);
            super.getChildren().add(new LazyDatabaseTreeItem(mviewsCat));
        }

        // 4. Procedures
        DatabaseNode procsCat = new DatabaseNode("Procedures", DatabaseNodeType.CATEGORY_PROCEDURES);
        procsCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
        node.addChild(procsCat);
        super.getChildren().add(new LazyDatabaseTreeItem(procsCat));

        // 5. Functions
        DatabaseNode funcsCat = new DatabaseNode("Functions", DatabaseNodeType.CATEGORY_FUNCTIONS);
        funcsCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
        node.addChild(funcsCat);
        super.getChildren().add(new LazyDatabaseTreeItem(funcsCat));

        // 6. Triggers (schema level)
        DatabaseNode triggersCat = new DatabaseNode("Triggers", DatabaseNodeType.CATEGORY_TRIGGERS);
        triggersCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
        node.addChild(triggersCat);
        super.getChildren().add(new LazyDatabaseTreeItem(triggersCat));

        // 7. Sequences (PostgreSQL, SQL Server — Phase 8 / D2)
        if (dialect.contains("POSTGRE") || dialect.contains("SQLSERVER") || dialect.contains("MSSQL")) {
            DatabaseNode seqCat = new DatabaseNode("Sequences", DatabaseNodeType.CATEGORY_SEQUENCES);
            seqCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
            node.addChild(seqCat);
            super.getChildren().add(new LazyDatabaseTreeItem(seqCat));
        }

        // 8. Synonyms (SQL Server)
        if (dialect.contains("SQLSERVER") || dialect.contains("MSSQL")) {
            DatabaseNode synCat = new DatabaseNode("Synonyms", DatabaseNodeType.CATEGORY_SYNONYMS);
            synCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
            node.addChild(synCat);
            super.getChildren().add(new LazyDatabaseTreeItem(synCat));
        }

        // 9. User Defined Types (SQL Server & PostgreSQL)
        if (dialect.contains("SQLSERVER") || dialect.contains("MSSQL") || dialect.contains("POSTGRE")) {
            DatabaseNode udtCat = new DatabaseNode("User Defined Types", DatabaseNodeType.CATEGORY_USER_TYPES);
            udtCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
            node.addChild(udtCat);
            super.getChildren().add(new LazyDatabaseTreeItem(udtCat));
        }

        node.setLoaded(true);
        primeCounts(node, profile, catalog, schema);
    }

    private static String countText(int total, int matched, boolean filterActive) {
        if (filterActive) return matched + "/" + total;
        return total > 0 ? String.valueOf(total) : "";
    }

    private <T> void applyCount(DatabaseNode cat, ConnectionProfileDto profile, List<T> items, Function<T, String> nameOf,
                                String filterType, String catalog, String schema) {
        if (items.isEmpty()) {
            DesktopMetadataService.getInstance().takeError(profile.id()); // a failed count must not hide behind the next load
        }
        ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, filterType);
        int matched = (int) items.stream().filter(i -> filter.matches(nameOf.apply(i), schema)).count();
        cat.setSubText(countText(items.size(), matched, filter.isActive()));
        refreshTreeView();
    }

    /**
     * Explorer parity: when a schema opens, every category shows its object count straight away
     * (instead of only after the category is expanded). Results go into the same cache the categories use.
     */
    private void primeCounts(DatabaseNode schemaNode, ConnectionProfileDto profile, String catalog, String schema) {
        DesktopMetadataService.getInstance().fetchSchemaCounts(profile, catalog, schema, counts -> {
            List<DatabaseNode> rest = new java.util.ArrayList<>();
            for (DatabaseNode cat : new java.util.ArrayList<>(schemaNode.getChildren())) {
                String key = countKey(cat.getType());
                if (key == null) continue;
                Integer n = counts == null ? null : counts.get(key);
                boolean filtered = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, key).isActive();
                if (n != null && !filtered) {
                    cat.setSubText(n > 0 ? String.valueOf(n) : "");
                } else {
                    rest.add(cat); // active filter needs the names (matched/total); no aggregate query -> per-category list
                }
            }
            refreshTreeView();
            primeListCounts(rest, profile, catalog, schema);
        });
    }

    private static String countKey(DatabaseNodeType t) {
        return switch (t) {
            case CATEGORY_TABLES -> "TABLES";
            case CATEGORY_VIEWS -> "VIEWS";
            case CATEGORY_MATERIALIZED_VIEWS -> "MATERIALIZED_VIEWS";
            case CATEGORY_PROCEDURES -> "PROCEDURES";
            case CATEGORY_FUNCTIONS -> "FUNCTIONS";
            case CATEGORY_TRIGGERS -> "TRIGGERS";
            case CATEGORY_SEQUENCES -> "SEQUENCES";
            case CATEGORY_SYNONYMS -> "SYNONYMS";
            case CATEGORY_USER_TYPES -> "USER_TYPES";
            default -> null;
        };
    }

    private void primeListCounts(List<DatabaseNode> categories, ConnectionProfileDto profile, String catalog, String schema) {
        DesktopMetadataService svc = DesktopMetadataService.getInstance();
        DatabaseNode procs = null, funcs = null;
        for (DatabaseNode cat : categories) {
            switch (cat.getType()) {
                case CATEGORY_TABLES -> svc.fetchTables(profile, catalog, schema,
                        l -> applyCount(cat, profile, l, TableMetadata::name, "TABLES", catalog, schema));
                case CATEGORY_VIEWS -> svc.fetchViews(profile, catalog, schema,
                        l -> applyCount(cat, profile, l, TableMetadata::name, "VIEWS", catalog, schema));
                case CATEGORY_MATERIALIZED_VIEWS -> svc.fetchMaterializedViews(profile, catalog, schema,
                        l -> applyCount(cat, profile, l, v -> v.name(), "MATERIALIZED_VIEWS", catalog, schema));
                case CATEGORY_SYNONYMS -> svc.fetchSynonyms(profile, catalog, schema,
                        l -> applyCount(cat, profile, l, DesktopMetadataService.SynonymInfo::name, "SYNONYMS", catalog, schema));
                case CATEGORY_TRIGGERS -> svc.fetchTriggers(profile, catalog, schema, null,
                        l -> applyCount(cat, profile, l, TriggerMetadata::name, "TRIGGERS", catalog, schema));
                case CATEGORY_SEQUENCES -> svc.fetchSequences(profile, catalog, schema,
                        l -> applyCount(cat, profile, l, n -> n, "SEQUENCES", catalog, schema));
                case CATEGORY_USER_TYPES -> svc.fetchUserDefinedTypes(profile, catalog, schema,
                        l -> applyCount(cat, profile, l, UserTypeMetadata::name, "USER_TYPES", catalog, schema));
                case CATEGORY_PROCEDURES -> procs = cat;
                case CATEGORY_FUNCTIONS -> funcs = cat;
                default -> { }
            }
        }
        if (procs != null || funcs != null) {
            final DatabaseNode pc = procs, fc = funcs;
            svc.fetchRoutines(profile, catalog, schema, all -> {
                if (pc != null) applyCount(pc, profile, all.stream().filter(RoutineMetadata::isProcedure).toList(),
                        RoutineMetadata::name, "PROCEDURES", catalog, schema);
                if (fc != null) applyCount(fc, profile, all.stream().filter(r -> !r.isProcedure()).toList(),
                        RoutineMetadata::name, "FUNCTIONS", catalog, schema);
            });
        }
    }

    private void loadTableSubfolders(DatabaseNode node, ConnectionProfileDto profile) {
        Platform.runLater(() -> {
            super.getChildren().clear();
            node.clearChildren();

            String catalog = node.getCatalog();
            String schema = node.getSchema();
            TableMetadata meta = node.getTableMetadata() != null ? node.getTableMetadata()
                    : new TableMetadata(node.getName(), schema, catalog, "TABLE", 0L, null);

            DatabaseNode colsCat = new DatabaseNode("Columns", DatabaseNodeType.CATEGORY_COLUMNS);
            colsCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(meta);

            DatabaseNode keysCat = new DatabaseNode("Keys", DatabaseNodeType.CATEGORY_KEYS);
            keysCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(meta);

            DatabaseNode constraintsCat = new DatabaseNode("Constraints", DatabaseNodeType.CATEGORY_CONSTRAINTS);
            constraintsCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(meta);

            DatabaseNode triggersCat = new DatabaseNode("Triggers", DatabaseNodeType.CATEGORY_TRIGGERS);
            triggersCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(meta);

            DatabaseNode indexesCat = new DatabaseNode("Indexes", DatabaseNodeType.CATEGORY_INDEXES);
            indexesCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(meta);

            node.addChild(colsCat).addChild(keysCat).addChild(constraintsCat).addChild(triggersCat).addChild(indexesCat);
            super.getChildren().addAll(
                    new LazyDatabaseTreeItem(colsCat),
                    new LazyDatabaseTreeItem(keysCat),
                    new LazyDatabaseTreeItem(constraintsCat),
                    new LazyDatabaseTreeItem(triggersCat),
                    new LazyDatabaseTreeItem(indexesCat)
            );
            node.setLoaded(true);
        });
    }

    private void loadRoutineParameters(DatabaseNode node, ConnectionProfileDto profile) {
        String routine = node.getName();
        DesktopMetadataService.getInstance().fetchRoutineParameters(profile, node.getCatalog(), node.getSchema(), routine, params -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            java.util.List<TreeItem<DatabaseNode>> batch5 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (DesktopMetadataService.RoutineParam p : params) {
                String mode = p.mode() == null || p.mode().isBlank() || "IN".equalsIgnoreCase(p.mode()) ? "" : p.mode() + " ";
                DatabaseNode pNode = new DatabaseNode(p.name(), DatabaseNodeType.PARAMETER, mode + (p.type() != null ? p.type() : ""));
                pNode.setConnectionProfile(profile).setCatalog(node.getCatalog()).setSchema(node.getSchema());
                node.addChild(pNode);
                batch5.add(new LazyDatabaseTreeItem(pNode));
            }
            super.getChildren().addAll(batch5);
            node.setLoaded(true);
        });
    }

    private void loadLinkedServers(DatabaseNode node, ConnectionProfileDto profile) {
        DesktopMetadataService.getInstance().fetchLinkedServers(profile, servers -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            node.setSubText(servers.isEmpty() ? "" : String.valueOf(servers.size()));
            java.util.List<TreeItem<DatabaseNode>> batch6 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (DesktopMetadataService.LinkedServerInfo ls : servers) {
                DatabaseNode n = new DatabaseNode(ls.name(), DatabaseNodeType.LINKED_SERVER,
                        ls.product() != null ? ls.product() : "");
                n.setConnectionProfile(profile).setLinkedServer(ls.name());
                node.addChild(n);
                batch6.add(new LazyDatabaseTreeItem(n));
            }
            super.getChildren().addAll(batch6);
            node.setLoaded(true);
        });
    }

    private void loadRemoteCatalogs(DatabaseNode node, ConnectionProfileDto profile) {
        DesktopMetadataService.getInstance().fetchRemoteCatalogs(profile, node.getLinkedServer(), catalogs -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            java.util.List<TreeItem<DatabaseNode>> batch7 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (String c : catalogs) {
                DatabaseNode n = new DatabaseNode(c, DatabaseNodeType.REMOTE_DATABASE);
                n.setConnectionProfile(profile).setLinkedServer(node.getLinkedServer()).setCatalog(c);
                node.addChild(n);
                batch7.add(new LazyDatabaseTreeItem(n));
            }
            super.getChildren().addAll(batch7);
            node.setLoaded(true);
        });
    }

    private void loadRemoteObjects(DatabaseNode node, ConnectionProfileDto profile) {
        DesktopMetadataService.getInstance().fetchRemoteObjects(profile, node.getLinkedServer(), node.getName(), objs -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            node.setSubText(objs.isEmpty() ? "" : String.valueOf(objs.size()));
            java.util.List<TreeItem<DatabaseNode>> batch8 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (DesktopMetadataService.RemoteObjectInfo o : objs) {
                DatabaseNode n = new DatabaseNode(o.schema() + "." + o.name(),
                        o.view() ? DatabaseNodeType.REMOTE_VIEW : DatabaseNodeType.REMOTE_TABLE);
                n.setConnectionProfile(profile).setLinkedServer(node.getLinkedServer()).setCatalog(node.getName()).setSchema(o.schema());
                node.addChild(n);
                batch8.add(new LazyDatabaseTreeItem(n));
            }
            super.getChildren().addAll(batch8);
            node.setLoaded(true);
        });
    }

    private void loadRoles(DatabaseNode node, ConnectionProfileDto profile) {
        DesktopMetadataService.getInstance().fetchRoles(profile, roles -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            node.setSubText(roles.isEmpty() ? "" : String.valueOf(roles.size()));
            java.util.List<TreeItem<DatabaseNode>> batch9 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (DesktopMetadataService.RoleInfo r : roles) {
                String kind = r.superuser() ? "Superuser" : r.canLogin() ? "Can Login" : "Group / NoLogin";
                DatabaseNode rNode = new DatabaseNode(r.name(), DatabaseNodeType.ROLE, kind);
                rNode.setConnectionProfile(profile);
                node.addChild(rNode);
                batch9.add(new LazyDatabaseTreeItem(rNode));
            }
            super.getChildren().addAll(batch9);
            node.setLoaded(true);
        });
    }

    private void loadViewSubfolders(DatabaseNode node, ConnectionProfileDto profile) {
        Platform.runLater(() -> {
            super.getChildren().clear();
            node.clearChildren();

            String catalog = node.getCatalog();
            String schema = node.getSchema();
            TableMetadata meta = node.getTableMetadata() != null ? node.getTableMetadata()
                    : new TableMetadata(node.getName(), schema, catalog, "VIEW", 0L, null);

            DatabaseNode colsCat = new DatabaseNode("Columns", DatabaseNodeType.CATEGORY_COLUMNS);
            colsCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(meta);

            DatabaseNode triggersCat = new DatabaseNode("Triggers", DatabaseNodeType.CATEGORY_TRIGGERS);
            triggersCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(meta);

            node.addChild(colsCat).addChild(triggersCat);
            super.getChildren().addAll(
                    new LazyDatabaseTreeItem(colsCat),
                    new LazyDatabaseTreeItem(triggersCat)
            );
            node.setLoaded(true);
        });
    }

    private void loadCategoryTables(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchTables(profile, catalog, schema, tables -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, "TABLES");
            List<TableMetadata> matched = tables.stream()
                    .filter(t -> filter.matches(t.name(), schema))
                    .toList();

            if (filter.isActive()) {
                node.setSubText(matched.size() + "/" + tables.size());
            } else if (!tables.isEmpty()) {
                node.setSubText(String.valueOf(tables.size()));
            } else {
                node.setSubText("");
            }

            java.util.List<TreeItem<DatabaseNode>> batch10 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (TableMetadata table : matched) {
                DatabaseNode tblNode = new DatabaseNode(table.name(), DatabaseNodeType.TABLE, null);
                tblNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(table);
                node.addChild(tblNode);
                batch10.add(new LazyDatabaseTreeItem(tblNode));
            }
            super.getChildren().addAll(batch10);
            node.setLoaded(true);
        });
    }

    private void loadCategoryViews(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchViews(profile, catalog, schema, views -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, "VIEWS");
            List<TableMetadata> matched = views.stream()
                    .filter(v -> filter.matches(v.name(), schema))
                    .toList();

            if (filter.isActive()) {
                node.setSubText(matched.size() + "/" + views.size());
            } else if (!views.isEmpty()) {
                node.setSubText(String.valueOf(views.size()));
            } else {
                node.setSubText("");
            }

            java.util.List<TreeItem<DatabaseNode>> batch11 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (TableMetadata view : matched) {
                DatabaseNode viewNode = new DatabaseNode(view.name(), DatabaseNodeType.VIEW, "view");
                viewNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(view);
                node.addChild(viewNode);
                batch11.add(new LazyDatabaseTreeItem(viewNode));
            }
            super.getChildren().addAll(batch11);
            node.setLoaded(true);
        });
    }

    private void loadCategoryMaterializedViews(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchMaterializedViews(profile, catalog, schema, mviews -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, "MATERIALIZED_VIEWS");
            List<TableMetadata> matched = mviews.stream()
                    .filter(v -> filter.matches(v.name(), schema))
                    .toList();

            if (filter.isActive()) {
                node.setSubText(matched.size() + "/" + mviews.size());
            } else if (!mviews.isEmpty()) {
                node.setSubText(String.valueOf(mviews.size()));
            } else {
                node.setSubText("");
            }

            java.util.List<TreeItem<DatabaseNode>> batch12 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (TableMetadata mv : matched) {
                DatabaseNode mvNode = new DatabaseNode(mv.name(), DatabaseNodeType.MATERIALIZED_VIEW, "materialized view");
                mvNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(mv);
                node.addChild(mvNode);
                batch12.add(new LazyDatabaseTreeItem(mvNode));
            }
            super.getChildren().addAll(batch12);
            node.setLoaded(true);
        });
    }

    private void loadCategoryRoutines(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchRoutines(profile, catalog, schema, routines -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            boolean isProc = node.getType() == DatabaseNodeType.CATEGORY_PROCEDURES;
            String filterType = isProc ? "PROCEDURES" : "FUNCTIONS";
            ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, filterType);

            List<RoutineMetadata> relevant = routines.stream().filter(r -> r.isProcedure() == isProc).toList();
            List<RoutineMetadata> matched = relevant.stream().filter(r -> filter.matches(r.name(), schema)).toList();

            if (filter.isActive()) {
                node.setSubText(matched.size() + "/" + relevant.size());
            } else if (!relevant.isEmpty()) {
                node.setSubText(String.valueOf(relevant.size()));
            } else {
                node.setSubText("");
            }

            java.util.List<TreeItem<DatabaseNode>> batch13 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (RoutineMetadata routine : matched) {
                String rName = routine.name();
                if (rName != null && rName.contains(";")) {
                    rName = rName.substring(0, rName.indexOf(';')).trim();
                }
                DatabaseNodeType rt = routine.isProcedure() ? DatabaseNodeType.PROCEDURE : DatabaseNodeType.FUNCTION;
                DatabaseNode rNode = new DatabaseNode(rName, rt, routine.returnType());
                rNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
                node.addChild(rNode);
                batch13.add(new LazyDatabaseTreeItem(rNode));
            }
            super.getChildren().addAll(batch13);
            node.setLoaded(true);
        });
    }

    private void loadCategoryUserTypes(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchUserDefinedTypes(profile, catalog, schema, userTypes -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, "USER_TYPES");
            List<UserTypeMetadata> matched = userTypes.stream().filter(u -> filter.matches(u.name(), schema)).toList();

            if (filter.isActive()) {
                node.setSubText(matched.size() + "/" + userTypes.size());
            } else if (!userTypes.isEmpty()) {
                node.setSubText(String.valueOf(userTypes.size()));
            } else {
                node.setSubText("");
            }

            java.util.List<TreeItem<DatabaseNode>> batch14 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (UserTypeMetadata udt : matched) {
                DatabaseNode udtNode = new DatabaseNode(udt.name(), DatabaseNodeType.USER_TYPE, udt.baseType());
                udtNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
                node.addChild(udtNode);
                batch14.add(new LazyDatabaseTreeItem(udtNode));
            }
            super.getChildren().addAll(batch14);
            node.setLoaded(true);
        });
    }

    private void loadCategorySequences(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        DesktopMetadataService.getInstance().fetchSequences(profile, catalog, schema, sequences -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, "SEQUENCES");
            List<String> matched = sequences.stream().filter(n -> filter.matches(n, schema)).toList();
            node.setSubText(filter.isActive() ? matched.size() + "/" + sequences.size()
                    : sequences.isEmpty() ? "" : String.valueOf(sequences.size()));
            java.util.List<TreeItem<DatabaseNode>> batch15 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (String name : matched) {
                DatabaseNode seq = new DatabaseNode(name, DatabaseNodeType.SEQUENCE, "sequence");
                seq.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
                node.addChild(seq);
                batch15.add(new LazyDatabaseTreeItem(seq));
            }
            super.getChildren().addAll(batch15);
            node.setLoaded(true);
        });
    }

    private void loadCategorySynonyms(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        DesktopMetadataService.getInstance().fetchSynonyms(profile, catalog, schema, synonyms -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, "SYNONYMS");
            List<DesktopMetadataService.SynonymInfo> matched = synonyms.stream().filter(sy -> filter.matches(sy.name(), schema)).toList();
            node.setSubText(filter.isActive() ? matched.size() + "/" + synonyms.size()
                    : synonyms.isEmpty() ? "" : String.valueOf(synonyms.size()));
            java.util.List<TreeItem<DatabaseNode>> batch16 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (DesktopMetadataService.SynonymInfo sy : matched) {
                DatabaseNode n = new DatabaseNode(sy.name(), DatabaseNodeType.SYNONYM, sy.target());
                n.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
                node.addChild(n);
                batch16.add(new LazyDatabaseTreeItem(n));
            }
            super.getChildren().addAll(batch16);
            node.setLoaded(true);
        });
    }

    private void loadDatabaseUsers(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        DesktopMetadataService.getInstance().fetchDatabaseUsers(profile, catalog, users -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();
            node.setSubText(users.isEmpty() ? "" : String.valueOf(users.size()));
            java.util.List<TreeItem<DatabaseNode>> batch17 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (DesktopMetadataService.DbUserInfo u : users) {
                String sub = (u.typeDesc() != null ? u.typeDesc() : "")
                        + (u.defaultSchema() != null && !u.defaultSchema().isBlank() ? " · " + u.defaultSchema() : "")
                        + (u.disabled() ? " · Disabled" : "");
                DatabaseNode n = new DatabaseNode(u.name(), DatabaseNodeType.DB_USER, sub);
                n.setConnectionProfile(profile).setCatalog(catalog);
                node.addChild(n);
                batch17.add(new LazyDatabaseTreeItem(n));
            }
            super.getChildren().addAll(batch17);
            node.setLoaded(true);
        });
    }

    private void loadTableColumns(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : node.getName();

        DesktopMetadataService.getInstance().fetchColumns(profile, catalog, schema, table, columns -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            java.util.List<TreeItem<DatabaseNode>> batch18 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (ColumnMetadata col : columns) {
                String sub = col.dataType();
                if (col.columnSize() > 0 && !sub.equalsIgnoreCase("integer") && !sub.equalsIgnoreCase("int")) {
                    sub += "(" + col.columnSize() + ")";
                }
                DatabaseNode colNode = new DatabaseNode(
                        col.name(),
                        DatabaseNodeType.COLUMN,
                        sub,
                        null,
                        col.primaryKey(),
                        col.foreignKey()
                );
                colNode.setColumnMetadata(col).setCatalog(catalog).setSchema(schema);
                node.addChild(colNode);
                batch18.add(new LazyDatabaseTreeItem(colNode));
            }
            super.getChildren().addAll(batch18);
            node.setLoaded(true);
        });
    }

    private void loadTableKeys(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : node.getName();

        DesktopMetadataService.getInstance().fetchKeys(profile, catalog, schema, table, keys -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            java.util.List<TreeItem<DatabaseNode>> batch19 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (KeyMetadata key : keys) {
                boolean isPk = "PRIMARY KEY".equalsIgnoreCase(key.type()) || "PK".equalsIgnoreCase(key.type());
                boolean isFk = "FOREIGN KEY".equalsIgnoreCase(key.type()) || "FK".equalsIgnoreCase(key.type());
                DatabaseNode keyNode = new DatabaseNode(key.name(), DatabaseNodeType.KEY, key.type(), null, isPk, isFk);
                node.addChild(keyNode);
                batch19.add(new LazyDatabaseTreeItem(keyNode));
            }
            super.getChildren().addAll(batch19);
            node.setLoaded(true);
        });
    }

    private void loadTableConstraints(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : node.getName();

        DesktopMetadataService.getInstance().fetchConstraints(profile, catalog, schema, table, constraints -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            java.util.List<TreeItem<DatabaseNode>> batch20 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (ConstraintMetadata c : constraints) {
                DatabaseNode cNode = new DatabaseNode(c.name(), DatabaseNodeType.CONSTRAINT, c.type());
                node.addChild(cNode);
                batch20.add(new LazyDatabaseTreeItem(cNode));
            }
            super.getChildren().addAll(batch20);
            node.setLoaded(true);
        });
    }

    private void loadCategoryIndexes(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : null;

        DesktopMetadataService.getInstance().fetchIndexes(profile, catalog, schema, table, indexes -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            java.util.List<TreeItem<DatabaseNode>> batch21 = new java.util.ArrayList<>(); // one tree event, not one per row
            for (var idx : indexes) {
                DatabaseNode idxNode = new DatabaseNode(idx.name(), DatabaseNodeType.INDEX, idx.unique() ? "unique" : "index");
                node.addChild(idxNode);
                batch21.add(new LazyDatabaseTreeItem(idxNode));
            }
            super.getChildren().addAll(batch21);
            node.setLoaded(true);
        });
    }

    private void loadCategoryTriggers(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        boolean isTableTrigger = node.getTableMetadata() != null;
        String table = isTableTrigger ? node.getTableMetadata().name() : null;

        DesktopMetadataService.getInstance().fetchTriggers(profile, catalog, schema, table, triggers -> {
            super.getChildren().clear();
            if (showLoadError(profile)) return;
            node.clearChildren();

            if (!isTableTrigger) {
                ObjectFilter filter = DesktopFilterManager.getInstance().getObjectFilter(profile.id(), catalog, schema, "TRIGGERS");
                List<TriggerMetadata> matched = triggers.stream().filter(t -> filter.matches(t.name(), schema)).toList();

                if (filter.isActive()) {
                    node.setSubText(matched.size() + "/" + triggers.size());
                } else if (!triggers.isEmpty()) {
                    node.setSubText(String.valueOf(triggers.size()));
                } else {
                    node.setSubText("");
                }

                java.util.List<TreeItem<DatabaseNode>> batch22 = new java.util.ArrayList<>(); // one tree event, not one per row
                for (var trg : matched) {
                    DatabaseNode trgNode = new DatabaseNode(trg.name(), DatabaseNodeType.TRIGGER, trg.table());
                    trgNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
                    if (trg.table() != null) trgNode.setTableMetadata(new TableMetadata(trg.table(), schema, catalog, "TABLE", 0L, null));
                    node.addChild(trgNode);
                    batch22.add(new LazyDatabaseTreeItem(trgNode));
                }
                super.getChildren().addAll(batch22);
            } else {
                java.util.List<TreeItem<DatabaseNode>> batch23 = new java.util.ArrayList<>(); // one tree event, not one per row
                for (var trg : triggers) {
                    DatabaseNode trgNode = new DatabaseNode(trg.name(), DatabaseNodeType.TRIGGER, trg.event());
                    trgNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(node.getTableMetadata());
                    node.addChild(trgNode);
                    batch23.add(new LazyDatabaseTreeItem(trgNode));
                }
                super.getChildren().addAll(batch23);
            }
            node.setLoaded(true);
        });
    }
}
