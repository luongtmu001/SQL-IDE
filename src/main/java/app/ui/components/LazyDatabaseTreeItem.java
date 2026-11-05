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

import java.util.List;

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
                for (DatabaseNode child : node.getChildren()) {
                    super.getChildren().add(new LazyDatabaseTreeItem(child));
                }
            } else if (!isLeaf()) {
                // Seed a dummy child so the disclosure triangle/chevron appears
                super.getChildren().add(new TreeItem<>(new DatabaseNode("Loading...", DatabaseNodeType.LOADING)));
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
                    case COLUMN, KEY, CONSTRAINT, INDEX, TRIGGER, SEQUENCE, FUNCTION, PROCEDURE, USER_TYPE, ROLES_USERS, LOADING -> true;
                    default -> false;
                };
            }
        }
        return isLeaf;
    }

    public void reload() {
        DatabaseNode node = getValue();
        if (node == null) return;
        node.setLoaded(false);
        node.clearChildren();
        super.getChildren().clear();
        isFirstTimeChildren = true;

        String connId = node.getConnectionId() != null ? node.getConnectionId()
                : (node.getConnectionProfile() != null ? node.getConnectionProfile().id() : null);
        if (connId != null) {
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

    private void loadChildrenAsync() {
        DatabaseNode node = getValue();
        if (node == null) return;

        // If node has pre-defined mock children and no real connectionProfile, use them
        if (node.getConnectionProfile() == null && node.hasChildren()) {
            super.getChildren().clear();
            for (DatabaseNode child : node.getChildren()) {
                super.getChildren().add(new LazyDatabaseTreeItem(child));
            }
            node.setLoaded(true);
            return;
        }

        ConnectionProfileDto profile = node.getConnectionProfile();
        if (profile == null) {
            super.getChildren().clear();
            node.setLoaded(true);
            return;
        }

        // Show loading indicator node
        super.getChildren().setAll(new TreeItem<>(new DatabaseNode("Loading...", DatabaseNodeType.LOADING)));

        switch (node.getType()) {
            case CONNECTION -> loadConnectionChildren(node, profile);
            case DATABASE -> loadDatabaseChildren(node, profile);
            case SCHEMA -> loadSchemaChildren(node, profile);
            case CATEGORY_TABLES -> loadCategoryTables(node, profile);
            case CATEGORY_VIEWS -> loadCategoryViews(node, profile);
            case CATEGORY_MATERIALIZED_VIEWS -> loadCategoryMaterializedViews(node, profile);
            case CATEGORY_FUNCTIONS, CATEGORY_PROCEDURES -> loadCategoryRoutines(node, profile);
            case CATEGORY_USER_TYPES -> loadCategoryUserTypes(node, profile);
            case TABLE -> loadTableSubfolders(node, profile);
            case VIEW, MATERIALIZED_VIEW -> loadViewSubfolders(node, profile);
            case CATEGORY_COLUMNS -> loadTableColumns(node, profile);
            case CATEGORY_KEYS -> loadTableKeys(node, profile);
            case CATEGORY_CONSTRAINTS -> loadTableConstraints(node, profile);
            case CATEGORY_INDEXES -> loadCategoryIndexes(node, profile);
            case CATEGORY_TRIGGERS -> loadCategoryTriggers(node, profile);
            default -> {
                super.getChildren().clear();
                node.setLoaded(true);
            }
        }
    }

    private void loadConnectionChildren(DatabaseNode node, ConnectionProfileDto profile) {
        String dialect = profile.databaseType().toUpperCase();

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
        DatabaseFilter dbFilter = DesktopFilterManager.getInstance().getDatabaseFilter(profile.id());

        DesktopMetadataService.getInstance().fetchCatalogs(profile, catalogs -> {
            super.getChildren().clear();
            node.clearChildren();

            if (catalogs.isEmpty()) {
                String defaultDb = profile.databaseName() != null && !profile.databaseName().isBlank() ? profile.databaseName() : "postgres";
                catalogs = List.of(defaultDb);
            }

            for (String catName : catalogs) {
                if (dbFilter.isActive() && !dbFilter.matches(catName)) {
                    continue; // Skip filtered out databases
                }
                DatabaseNode dbNode = new DatabaseNode(catName, DatabaseNodeType.DATABASE, profile.databaseType());
                dbNode.setConnectionProfile(profile).setCatalog(catName);
                node.addChild(dbNode);
                super.getChildren().add(new LazyDatabaseTreeItem(dbNode));
            }

            if (dialect.contains("SQLSERVER") || dialect.contains("MSSQL")) {
                DatabaseNode serverObjNode = new DatabaseNode("Server Objects", DatabaseNodeType.SERVER_OBJECTS);
                serverObjNode.setConnectionProfile(profile);
                node.addChild(serverObjNode);
                super.getChildren().add(new LazyDatabaseTreeItem(serverObjNode));
            }

            // Roles / Users node
            DatabaseNode rolesUsers = new DatabaseNode("Roles / Users", DatabaseNodeType.ROLES_USERS);
            rolesUsers.setConnectionProfile(profile);
            node.addChild(rolesUsers);
            super.getChildren().add(new LazyDatabaseTreeItem(rolesUsers));

            node.setLoaded(true);
        });
    }

    private void loadDatabaseChildren(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog() != null ? node.getCatalog() : node.getName();

        DesktopMetadataService.getInstance().fetchSchemas(profile, catalog, schemas -> {
            super.getChildren().clear();
            node.clearChildren();

            if (schemas.isEmpty()) {
                schemas = List.of(profile.databaseType().toUpperCase().contains("SQLSERVER") ? "dbo" : "public");
            }

            for (String schemaName : schemas) {
                DatabaseNode schemaNode = new DatabaseNode(schemaName, DatabaseNodeType.SCHEMA, "schema");
                schemaNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schemaName);
                node.addChild(schemaNode);
                super.getChildren().add(new LazyDatabaseTreeItem(schemaNode));
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

        // 7. Sequences (PostgreSQL)
        if (dialect.contains("POSTGRE")) {
            DatabaseNode seqCat = new DatabaseNode("Sequences", DatabaseNodeType.CATEGORY_SEQUENCES);
            seqCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
            node.addChild(seqCat);
            super.getChildren().add(new LazyDatabaseTreeItem(seqCat));
        }

        // 8. User Defined Types (SQL Server & PostgreSQL)
        if (dialect.contains("SQLSERVER") || dialect.contains("MSSQL") || dialect.contains("POSTGRE")) {
            DatabaseNode udtCat = new DatabaseNode("User Defined Types", DatabaseNodeType.CATEGORY_USER_TYPES);
            udtCat.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
            node.addChild(udtCat);
            super.getChildren().add(new LazyDatabaseTreeItem(udtCat));
        }

        node.setLoaded(true);
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

            for (TableMetadata table : matched) {
                DatabaseNode tblNode = new DatabaseNode(table.name(), DatabaseNodeType.TABLE, null);
                tblNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(table);
                node.addChild(tblNode);
                super.getChildren().add(new LazyDatabaseTreeItem(tblNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadCategoryViews(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchViews(profile, catalog, schema, views -> {
            super.getChildren().clear();
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

            for (TableMetadata view : matched) {
                DatabaseNode viewNode = new DatabaseNode(view.name(), DatabaseNodeType.VIEW, "view");
                viewNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(view);
                node.addChild(viewNode);
                super.getChildren().add(new LazyDatabaseTreeItem(viewNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadCategoryMaterializedViews(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchMaterializedViews(profile, catalog, schema, mviews -> {
            super.getChildren().clear();
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

            for (TableMetadata mv : matched) {
                DatabaseNode mvNode = new DatabaseNode(mv.name(), DatabaseNodeType.MATERIALIZED_VIEW, "materialized view");
                mvNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema).setTableMetadata(mv);
                node.addChild(mvNode);
                super.getChildren().add(new LazyDatabaseTreeItem(mvNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadCategoryRoutines(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchRoutines(profile, catalog, schema, routines -> {
            super.getChildren().clear();
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

            for (RoutineMetadata routine : matched) {
                DatabaseNodeType rt = routine.isProcedure() ? DatabaseNodeType.PROCEDURE : DatabaseNodeType.FUNCTION;
                DatabaseNode rNode = new DatabaseNode(routine.name(), rt, routine.returnType());
                rNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
                node.addChild(rNode);
                super.getChildren().add(new LazyDatabaseTreeItem(rNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadCategoryUserTypes(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();

        DesktopMetadataService.getInstance().fetchUserDefinedTypes(profile, catalog, schema, userTypes -> {
            super.getChildren().clear();
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

            for (UserTypeMetadata udt : matched) {
                DatabaseNode udtNode = new DatabaseNode(udt.name(), DatabaseNodeType.USER_TYPE, udt.baseType());
                udtNode.setConnectionProfile(profile).setCatalog(catalog).setSchema(schema);
                node.addChild(udtNode);
                super.getChildren().add(new LazyDatabaseTreeItem(udtNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadTableColumns(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : node.getName();

        DesktopMetadataService.getInstance().fetchColumns(profile, catalog, schema, table, columns -> {
            super.getChildren().clear();
            node.clearChildren();

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
                super.getChildren().add(new LazyDatabaseTreeItem(colNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadTableKeys(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : node.getName();

        DesktopMetadataService.getInstance().fetchKeys(profile, catalog, schema, table, keys -> {
            super.getChildren().clear();
            node.clearChildren();

            for (KeyMetadata key : keys) {
                boolean isPk = "PRIMARY KEY".equalsIgnoreCase(key.type()) || "PK".equalsIgnoreCase(key.type());
                boolean isFk = "FOREIGN KEY".equalsIgnoreCase(key.type()) || "FK".equalsIgnoreCase(key.type());
                DatabaseNode keyNode = new DatabaseNode(key.name(), DatabaseNodeType.KEY, key.type(), null, isPk, isFk);
                node.addChild(keyNode);
                super.getChildren().add(new LazyDatabaseTreeItem(keyNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadTableConstraints(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : node.getName();

        DesktopMetadataService.getInstance().fetchConstraints(profile, catalog, schema, table, constraints -> {
            super.getChildren().clear();
            node.clearChildren();

            for (ConstraintMetadata c : constraints) {
                DatabaseNode cNode = new DatabaseNode(c.name(), DatabaseNodeType.CONSTRAINT, c.type());
                node.addChild(cNode);
                super.getChildren().add(new LazyDatabaseTreeItem(cNode));
            }
            node.setLoaded(true);
        });
    }

    private void loadCategoryIndexes(DatabaseNode node, ConnectionProfileDto profile) {
        String catalog = node.getCatalog();
        String schema = node.getSchema();
        String table = node.getTableMetadata() != null ? node.getTableMetadata().name() : null;

        DesktopMetadataService.getInstance().fetchIndexes(profile, catalog, schema, table, indexes -> {
            super.getChildren().clear();
            node.clearChildren();

            for (var idx : indexes) {
                DatabaseNode idxNode = new DatabaseNode(idx.name(), DatabaseNodeType.INDEX, idx.unique() ? "unique" : "index");
                node.addChild(idxNode);
                super.getChildren().add(new LazyDatabaseTreeItem(idxNode));
            }
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

                for (var trg : matched) {
                    DatabaseNode trgNode = new DatabaseNode(trg.name(), DatabaseNodeType.TRIGGER, trg.table());
                    node.addChild(trgNode);
                    super.getChildren().add(new LazyDatabaseTreeItem(trgNode));
                }
            } else {
                for (var trg : triggers) {
                    DatabaseNode trgNode = new DatabaseNode(trg.name(), DatabaseNodeType.TRIGGER, trg.event());
                    node.addChild(trgNode);
                    super.getChildren().add(new LazyDatabaseTreeItem(trgNode));
                }
            }
            node.setLoaded(true);
        });
    }
}
