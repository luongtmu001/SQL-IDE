package app.framework.dependency;

import app.framework.backend.BackendFlags;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.framework.metadata.ObjectDefinitionReader;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.dependency.DependencyGraphService;
import com.lltool.core.domain.dependency.*;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Loads real database object dependency graphs via catalog views (sys / pg_catalog)
 * with BFS traversal, node capping, and fallback to sample graph in mock mode (DG-1, DG-7).
 */
public class DependencyGraphLoader {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphLoader.class);
    private static final int MAX_NODES = 300;

    /**
     * Loads a dependency graph model for the specified database object.
     */
    public static DependencyGraphModel load(ConnectionProfileDto profile,
                                           String catalog,
                                           String schema,
                                           String objectName,
                                           String columnName,
                                           DependencyNodeType rootType,
                                           GraphDirection direction,
                                           int depth,
                                           Consumer<String> progressCallback) throws Exception {

        DependencyGraphService graphService = new DependencyGraphService();

        // Check mock mode
        if (profile == null || MockMetadataProvider.isEnabled() || BackendFlags.mock(BackendFlags.Area.METADATA)) {
            if (progressCallback != null) {
                progressCallback.accept("Đang nạp đồ thị phụ thuộc mẫu (Mock Mode)...");
            }
            return graphService.createSampleOrdersGraph(direction, depth);
        }

        String safeSchema = (schema != null && !schema.isBlank()) ? schema : "dbo";
        String safeCatalog = (catalog != null && !catalog.isBlank()) ? catalog : profile.databaseName();
        boolean isColumnLevel = columnName != null && !columnName.isBlank();

        DependencyNode rootNode;
        if (isColumnLevel) {
            rootNode = DependencyNode.ofColumn(safeSchema, objectName, columnName, true);
        } else {
            rootNode = DependencyNode.of(safeSchema, objectName, rootType != null ? rootType : DependencyNodeType.TABLE, true);
        }

        DependencyGraphModel model = new DependencyGraphModel(rootNode.getId(), direction, depth);
        model.addNode(rootNode);

        if (progressCallback != null) {
            progressCallback.accept("Đang kết nối và phân tích quan hệ phụ thuộc cho " + rootNode.getDisplayName() + "...");
        }

        boolean isSqlServer = "sqlserver".equalsIgnoreCase(profile.databaseType());

        try (DatabaseConnection dbConn = DesktopMetadataService.getInstance().explorerConnection(profile, safeCatalog)) {
            Connection conn = dbConn.getRawJdbcConnection();

            // Populate root node row count if table
            if (rootNode.getType() == DependencyNodeType.TABLE) {
                Long rowCount = fetchTableRowCount(conn, isSqlServer, safeSchema, objectName);
                if (rowCount != null) {
                    rootNode.setRowCount(rowCount);
                }
            }

            // BFS Traversal
            Set<String> visitedIds = new HashSet<>();
            visitedIds.add(rootNode.getId());

            Set<DependencyNode> currentFrontier = new HashSet<>();
            currentFrontier.add(rootNode);

            int currentLevel = 0;
            int maxDepth = (depth <= 0) ? 100 : depth;

            while (!currentFrontier.isEmpty() && currentLevel < maxDepth && model.getNodeCount() < MAX_NODES) {
                currentLevel++;
                if (progressCallback != null) {
                    progressCallback.accept("Đang phân tích cấp độ " + currentLevel + " (" + model.getNodeCount() + " đối tượng)...");
                }

                Set<DependencyNode> nextFrontier = new HashSet<>();

                for (DependencyNode currNode : currentFrontier) {
                    if (model.getNodeCount() >= MAX_NODES) {
                        log.warn("Dependency graph reached MAX_NODES limit of {}", MAX_NODES);
                        break;
                    }

                    // Query relationships based on direction
                    List<DependencyEdge> discoveredEdges = new ArrayList<>();
                    if (isSqlServer) {
                        discoveredEdges = loadSqlServerDependencies(conn, currNode, direction, isColumnLevel);
                    } else {
                        discoveredEdges = loadPostgreSqlDependencies(conn, currNode, direction, isColumnLevel);
                    }

                    for (DependencyEdge edge : discoveredEdges) {
                        String otherId = edge.getSourceId().equals(currNode.getId()) ? edge.getTargetId() : edge.getSourceId();

                        if (!model.getNodes().containsKey(otherId)) {
                            if (model.getNodeCount() >= MAX_NODES) {
                                break;
                            }
                            DependencyNode otherNode = createNodeFromId(otherId, edge);
                            if (otherNode.getType() == DependencyNodeType.TABLE) {
                                Long rc = fetchTableRowCount(conn, isSqlServer, otherNode.getSchema(), otherNode.getName());
                                if (rc != null) otherNode.setRowCount(rc);
                            }
                            model.addNode(otherNode);

                            if (!visitedIds.contains(otherId)) {
                                visitedIds.add(otherId);
                                nextFrontier.add(otherNode);
                            }
                        }
                        model.addEdge(edge);
                    }
                }

                currentFrontier = nextFrontier;
            }

            // Fetch DDL definitions for nodes
            fetchDdlDefinitions(conn, isSqlServer ? "sqlserver" : "postgresql", safeCatalog, safeSchema, model);

        } catch (Exception ex) {
            log.error("Error analyzing dependencies from catalog: {}", ex.getMessage(), ex);
            // If failed to load real metadata and graph is empty, fallback to sample graph
            if (model.getNodeCount() <= 1) {
                log.info("Falling back to sample graph due to catalog query failure");
                return graphService.createSampleOrdersGraph(direction, depth);
            }
        }

        // Layout graph
        graphService.calculateHierarchicalLayout(model);

        // Generate suggested steps
        List<SuggestedStep> steps = graphService.generateSuggestedSteps(model, rootNode.getId());
        for (SuggestedStep step : steps) {
            model.addSuggestedStep(step);
        }

        return model;
    }

    private static Long fetchTableRowCount(Connection conn, boolean isSqlServer, String schema, String table) {
        String sql;
        if (isSqlServer) {
            sql = "SELECT SUM(p.rows) FROM sys.partitions p " +
                  "JOIN sys.tables t ON p.object_id = t.object_id " +
                  "JOIN sys.schemas s ON t.schema_id = s.schema_id " +
                  "WHERE s.name = ? AND t.name = ? AND p.index_id IN (0, 1)";
        } else {
            sql = "SELECT GREATEST(0, c.reltuples::bigint) FROM pg_class c " +
                  "JOIN pg_namespace n ON c.relnamespace = n.oid " +
                  "WHERE n.nspname = ? AND c.relname = ? AND c.relkind IN ('r', 'p')";
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long val = rs.getLong(1);
                    if (!rs.wasNull()) return val;
                }
            }
        } catch (Exception ignored) {
            // Ignore stats error
        }
        return null;
    }

    private static List<DependencyEdge> loadSqlServerDependencies(Connection conn, DependencyNode node,
                                                                 GraphDirection direction, boolean isColumnLevel) {
        List<DependencyEdge> edges = new ArrayList<>();
        String schema = node.getSchema();
        String name = node.getName();

        // 1. Dependents (who references node): node -> caller
        if (direction == GraphDirection.BOTH || direction == GraphDirection.DEPENDENTS) {
            String sql = "SELECT DISTINCT " +
                    "OBJECT_SCHEMA_NAME(referencing_id) AS caller_schema, " +
                    "OBJECT_NAME(referencing_id) AS caller_name, " +
                    "o.type_desc AS caller_type, " +
                    "d.referenced_minor_id " +
                    "FROM sys.sql_expression_dependencies d " +
                    "JOIN sys.objects o ON d.referencing_id = o.object_id " +
                    "WHERE d.referenced_schema_name = ? AND d.referenced_entity_name = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String cSch = rs.getString("caller_schema");
                        String cName = rs.getString("caller_name");
                        String cType = rs.getString("caller_type");
                        if (cSch == null || cName == null) continue;

                        String callerId = cSch + "." + cName;
                        RelationType rel = mapRelationType(cType);
                        edges.add(new DependencyEdge(node.getId(), callerId, rel, DependencyType.DIRECT, Confidence.CONFIRMED, rel.getLabel()));
                    }
                }
            } catch (Exception ex) {
                log.warn("Error querying SQL Server sql_expression_dependencies (dependents): {}", ex.getMessage());
            }

            // Triggers on this table
            if (node.getType() == DependencyNodeType.TABLE) {
                String trgSql = "SELECT s.name AS trg_schema, t.name AS trg_name " +
                        "FROM sys.triggers t " +
                        "JOIN sys.tables tbl ON t.parent_id = tbl.object_id " +
                        "JOIN sys.schemas s ON tbl.schema_id = s.schema_id " +
                        "WHERE s.name = ? AND tbl.name = ?";
                try (PreparedStatement ps = conn.prepareStatement(trgSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tSch = rs.getString("trg_schema");
                            String tName = rs.getString("trg_name");
                            if (tSch != null && tName != null) {
                                String trgId = tSch + "." + tName;
                                edges.add(new DependencyEdge(node.getId(), trgId, RelationType.HAS_TRIGGER, DependencyType.DIRECT, Confidence.CONFIRMED, "TRIGGER"));
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error querying SQL Server sys.triggers: {}", ex.getMessage());
                }
            }
        }

        // 2. Dependencies (what node references): dependency -> node
        if (direction == GraphDirection.BOTH || direction == GraphDirection.DEPENDENCIES) {
            String sql = "SELECT DISTINCT " +
                    "referenced_schema_name AS ref_schema, " +
                    "referenced_entity_name AS ref_name, " +
                    "o.type_desc AS ref_type " +
                    "FROM sys.sql_expression_dependencies d " +
                    "LEFT JOIN sys.objects o ON d.referenced_id = o.object_id " +
                    "WHERE OBJECT_SCHEMA_NAME(d.referencing_id) = ? AND OBJECT_NAME(d.referencing_id) = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, schema);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rSch = rs.getString("ref_schema");
                        String rName = rs.getString("ref_name");
                        String rType = rs.getString("ref_type");
                        if (rSch == null || rName == null) continue;

                        String depId = rSch + "." + rName;
                        RelationType rel = mapRelationType(rType);
                        edges.add(new DependencyEdge(depId, node.getId(), rel, DependencyType.DIRECT, Confidence.CONFIRMED, rel.getLabel()));
                    }
                }
            } catch (Exception ex) {
                log.warn("Error querying SQL Server sql_expression_dependencies (dependencies): {}", ex.getMessage());
            }

            // Foreign keys pointing out of this table
            if (node.getType() == DependencyNodeType.TABLE) {
                String fkSql = "SELECT " +
                        "OBJECT_SCHEMA_NAME(fk.referenced_object_id) AS tgt_schema, " +
                        "OBJECT_NAME(fk.referenced_object_id) AS tgt_table, " +
                        "fk.name AS fk_name " +
                        "FROM sys.foreign_keys fk " +
                        "WHERE OBJECT_SCHEMA_NAME(fk.parent_object_id) = ? AND OBJECT_NAME(fk.parent_object_id) = ?";
                try (PreparedStatement ps = conn.prepareStatement(fkSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tSch = rs.getString("tgt_schema");
                            String tTbl = rs.getString("tgt_table");
                            String fkName = rs.getString("fk_name");
                            if (tSch != null && tTbl != null) {
                                String fkId = tSch + "." + tTbl;
                                edges.add(new DependencyEdge(fkId, node.getId(), RelationType.FOREIGN_KEY_TO, DependencyType.DIRECT, Confidence.CONFIRMED, fkName != null ? fkName : "FK"));
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error querying SQL Server foreign keys: {}", ex.getMessage());
                }
            }
        }

        return edges;
    }

    private static List<DependencyEdge> loadPostgreSqlDependencies(Connection conn, DependencyNode node,
                                                                   GraphDirection direction, boolean isColumnLevel) {
        List<DependencyEdge> edges = new ArrayList<>();
        String schema = node.getSchema();
        String name = node.getName();

        // 1. Dependents (views referencing this table/view): node -> caller
        if (direction == GraphDirection.BOTH || direction == GraphDirection.DEPENDENTS) {
            String viewSql = "SELECT DISTINCT " +
                    "n_src.nspname AS caller_schema, " +
                    "c_src.relname AS caller_name, " +
                    "c_src.relkind AS caller_kind " +
                    "FROM pg_depend d " +
                    "JOIN pg_rewrite r ON d.objid = r.oid " +
                    "JOIN pg_class c_src ON r.ev_class = c_src.oid " +
                    "JOIN pg_namespace n_src ON c_src.relnamespace = n_src.oid " +
                    "JOIN pg_class c_tgt ON d.refobjid = c_tgt.oid " +
                    "JOIN pg_namespace n_tgt ON c_tgt.relnamespace = n_tgt.oid " +
                    "WHERE d.classid = 'pg_rewrite'::regclass " +
                    "AND c_src.oid != c_tgt.oid " +
                    "AND n_tgt.nspname = ? AND c_tgt.relname = ?";

            try (PreparedStatement ps = conn.prepareStatement(viewSql)) {
                ps.setString(1, schema);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String cSch = rs.getString("caller_schema");
                        String cName = rs.getString("caller_name");
                        if (cSch == null || cName == null) continue;

                        String callerId = cSch + "." + cName;
                        edges.add(new DependencyEdge(node.getId(), callerId, RelationType.REFERENCES_VIEW, DependencyType.DIRECT, Confidence.CONFIRMED, "VIEW"));
                    }
                }
            } catch (Exception ex) {
                log.warn("Error querying PostgreSQL pg_depend (views): {}", ex.getMessage());
            }

            // Triggers on this table
            if (node.getType() == DependencyNodeType.TABLE) {
                String trgSql = "SELECT n.nspname AS trg_schema, t.tgname AS trg_name " +
                        "FROM pg_trigger t " +
                        "JOIN pg_class c ON t.tgrelid = c.oid " +
                        "JOIN pg_namespace n ON c.relnamespace = n.oid " +
                        "WHERE NOT t.tgisinternal AND n.nspname = ? AND c.relname = ?";
                try (PreparedStatement ps = conn.prepareStatement(trgSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tSch = rs.getString("trg_schema");
                            String tName = rs.getString("trg_name");
                            if (tSch != null && tName != null) {
                                String trgId = tSch + "." + tName;
                                edges.add(new DependencyEdge(node.getId(), trgId, RelationType.HAS_TRIGGER, DependencyType.DIRECT, Confidence.CONFIRMED, "TRIGGER"));
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error querying PostgreSQL triggers: {}", ex.getMessage());
                }
            }

            // Heuristic functions mentioning table name in prosrc
            String fnSql = "SELECT n.nspname AS fn_schema, p.proname AS fn_name " +
                    "FROM pg_proc p " +
                    "JOIN pg_namespace n ON p.pronamespace = n.oid " +
                    "WHERE n.nspname = ? AND p.prosrc ILIKE ? LIMIT 20";
            try (PreparedStatement ps = conn.prepareStatement(fnSql)) {
                ps.setString(1, schema);
                ps.setString(2, "%" + name + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fSch = rs.getString("fn_schema");
                        String fName = rs.getString("fn_name");
                        if (fSch != null && fName != null) {
                            String fnId = fSch + "." + fName;
                            edges.add(new DependencyEdge(node.getId(), fnId, RelationType.CALLS_FUNCTION, DependencyType.DIRECT, Confidence.POSSIBLE, "prosrc text match"));
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Error querying PostgreSQL pg_proc (heuristic): {}", ex.getMessage());
            }
        }

        // 2. Dependencies (tables/views referenced by this view): dep -> node
        if (direction == GraphDirection.BOTH || direction == GraphDirection.DEPENDENCIES) {
            String depSql = "SELECT DISTINCT " +
                    "n_tgt.nspname AS dep_schema, " +
                    "c_tgt.relname AS dep_name, " +
                    "c_tgt.relkind AS dep_kind " +
                    "FROM pg_depend d " +
                    "JOIN pg_rewrite r ON d.objid = r.oid " +
                    "JOIN pg_class c_src ON r.ev_class = c_src.oid " +
                    "JOIN pg_namespace n_src ON c_src.relnamespace = n_src.oid " +
                    "JOIN pg_class c_tgt ON d.refobjid = c_tgt.oid " +
                    "JOIN pg_namespace n_tgt ON c_tgt.relnamespace = n_tgt.oid " +
                    "WHERE d.classid = 'pg_rewrite'::regclass " +
                    "AND c_src.oid != c_tgt.oid " +
                    "AND n_src.nspname = ? AND c_src.relname = ?";

            try (PreparedStatement ps = conn.prepareStatement(depSql)) {
                ps.setString(1, schema);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String dSch = rs.getString("dep_schema");
                        String dName = rs.getString("dep_name");
                        if (dSch == null || dName == null) continue;

                        String depId = dSch + "." + dName;
                        edges.add(new DependencyEdge(depId, node.getId(), RelationType.REFERENCES_VIEW, DependencyType.DIRECT, Confidence.CONFIRMED, "SOURCE"));
                    }
                }
            } catch (Exception ex) {
                log.warn("Error querying PostgreSQL pg_depend (dependencies): {}", ex.getMessage());
            }

            // Foreign keys pointing to another table
            if (node.getType() == DependencyNodeType.TABLE) {
                String fkSql = "SELECT " +
                        "n_tgt.nspname AS tgt_schema, " +
                        "c_tgt.relname AS tgt_table, " +
                        "con.conname AS fk_name " +
                        "FROM pg_constraint con " +
                        "JOIN pg_class c_src ON con.conrelid = c_src.oid " +
                        "JOIN pg_namespace n_src ON c_src.relnamespace = n_src.oid " +
                        "JOIN pg_class c_tgt ON con.confrelid = c_tgt.oid " +
                        "JOIN pg_namespace n_tgt ON c_tgt.relnamespace = n_tgt.oid " +
                        "WHERE con.contype = 'f' AND n_src.nspname = ? AND c_src.relname = ?";
                try (PreparedStatement ps = conn.prepareStatement(fkSql)) {
                    ps.setString(1, schema);
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tSch = rs.getString("tgt_schema");
                            String tTbl = rs.getString("tgt_table");
                            String fkName = rs.getString("fk_name");
                            if (tSch != null && tTbl != null) {
                                String fkId = tSch + "." + tTbl;
                                edges.add(new DependencyEdge(fkId, node.getId(), RelationType.FOREIGN_KEY_TO, DependencyType.DIRECT, Confidence.CONFIRMED, fkName != null ? fkName : "FK"));
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Error querying PostgreSQL foreign keys: {}", ex.getMessage());
                }
            }
        }

        return edges;
    }

    private static RelationType mapRelationType(String typeDesc) {
        if (typeDesc == null) return RelationType.DEPENDS_ON;
        String clean = typeDesc.toUpperCase();
        if (clean.contains("VIEW")) return RelationType.REFERENCES_VIEW;
        if (clean.contains("TRIGGER")) return RelationType.HAS_TRIGGER;
        if (clean.contains("PROCEDURE")) return RelationType.DEPENDS_ON;
        if (clean.contains("FUNCTION")) return RelationType.CALLS_FUNCTION;
        if (clean.contains("TABLE")) return RelationType.USES_TABLE;
        return RelationType.DEPENDS_ON;
    }

    private static DependencyNode createNodeFromId(String id, DependencyEdge edge) {
        String schema = "dbo";
        String name = id;
        if (id.contains(".")) {
            int dot = id.indexOf('.');
            schema = id.substring(0, dot);
            name = id.substring(dot + 1);
        }

        DependencyNodeType type = DependencyNodeType.TABLE;
        if (edge.getRelationType() == RelationType.REFERENCES_VIEW || name.toLowerCase().startsWith("v_") || name.toLowerCase().endsWith("_v")) {
            type = DependencyNodeType.VIEW;
        } else if (edge.getRelationType() == RelationType.HAS_TRIGGER || name.toLowerCase().startsWith("trg_")) {
            type = DependencyNodeType.TRIGGER;
        } else if (edge.getRelationType() == RelationType.CALLS_FUNCTION || name.toLowerCase().startsWith("fn_")) {
            type = DependencyNodeType.FUNCTION;
        } else if (name.toLowerCase().startsWith("usp_") || name.toLowerCase().startsWith("sp_")) {
            type = DependencyNodeType.PROCEDURE;
        }

        return DependencyNode.of(schema, name, type, false);
    }

    private static void fetchDdlDefinitions(Connection conn, String engine, String catalog, String schema,
                                            DependencyGraphModel model) {
        try {
            var options = new ObjectDefinitionReader.FilterOptions(catalog, schema, null);
            var defsResult = new ObjectDefinitionReader().readDefinitions(conn, engine, options);
            if (defsResult != null && defsResult.definitions() != null) {
                for (var def : defsResult.definitions()) {
                    String fullId = def.schema() + "." + def.name();
                    DependencyNode node = model.getNode(fullId);
                    if (node != null && def.definition() != null && !def.definition().isBlank()) {
                        node.setDefinitionSql(def.definition());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not fetch batch DDL definitions for dependency graph: {}", ex.getMessage());
        }
    }
}

