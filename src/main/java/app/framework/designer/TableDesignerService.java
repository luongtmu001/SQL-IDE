package app.framework.designer;

import app.framework.async.AsyncTaskManager;
import app.framework.backend.BackendFlags;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.framework.query.SqlStatementSplitter;
import app.framework.session.CredentialResolver;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.tabledesigner.TableDesignDiffer;
import com.lltool.core.application.tabledesigner.TableDesignValidator;
import com.lltool.core.application.tabledesigner.dialect.DdlDialect;
import com.lltool.core.application.tabledesigner.dialect.PostgresDdlDialect;
import com.lltool.core.application.tabledesigner.dialect.SqlServerDdlDialect;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.core.common.validation.ValidationResult;
import com.lltool.core.domain.tabledesigner.model.*;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseProvider;
import com.lltool.infrastructure.database.api.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service orchestrating Table Designer operations for the desktop IDE (Feature 10 / TD2).
 * Supports mock fallback via {@link BackendFlags} or {@link MockMetadataProvider},
 * live metadata retrieval, migration script generation, and transactional DDL execution.
 */
public class TableDesignerService {

    private static final Logger log = LoggerFactory.getLogger(TableDesignerService.class);
    private static TableDesignerService instance;

    private final TableDesignValidator validator = new TableDesignValidator();
    private final TableDesignDiffer differ = new TableDesignDiffer();
    private final SqlServerDdlDialect sqlServerDialect = new SqlServerDdlDialect();
    private final PostgresDdlDialect postgresDialect = new PostgresDdlDialect();

    private TableDesignerService() {}

    public static synchronized TableDesignerService getInstance() {
        if (instance == null) {
            instance = new TableDesignerService();
        }
        return instance;
    }

    /**
     * Resolves the DDL dialect for the given engine or profile.
     */
    public DdlDialect getDialect(String engine) {
        if ("postgresql".equalsIgnoreCase(engine) || "postgres".equalsIgnoreCase(engine)) {
            return postgresDialect;
        }
        return sqlServerDialect;
    }

    /**
     * Returns the list of standard data types supported by the specified engine.
     */
    public List<String> getSupportedTypes(String engine) {
        if ("postgresql".equalsIgnoreCase(engine) || "postgres".equalsIgnoreCase(engine)) {
            return List.of(
                    "integer", "bigint", "smallint", "varchar", "char", "text", "boolean",
                    "numeric", "double precision", "real", "timestamp", "timestamptz",
                    "date", "time", "interval", "uuid", "jsonb", "json", "bytea", "citext",
                    "inet", "macaddr", "serial", "bigserial"
            );
        }
        return List.of(
                "int", "bigint", "smallint", "tinyint", "varchar", "nvarchar", "char", "nchar",
                "text", "ntext", "bit", "decimal", "numeric", "float", "real", "money", "smallmoney",
                "datetime2", "datetime", "datetimeoffset", "smalldatetime", "date", "time",
                "uniqueidentifier", "varbinary", "binary", "image", "xml", "geography", "geometry",
                "hierarchyid", "sysname"
        );
    }

    /**
     * Loads the full table design metadata from the database (or mock data if enabled).
     */
    public Result<TableDesign> loadTableDesign(ConnectionProfileDto profile, String catalog, String schema, String table) {
        if (profile == null) {
            return Result.failure(AppError.validation("NULL_PROFILE", "Connection profile cannot be null"));
        }
        if (table == null || table.isBlank()) {
            return Result.failure(AppError.validation("EMPTY_TABLE", "Table name cannot be empty"));
        }

        String dbTypeStr = profile.databaseType();
        String engine = (dbTypeStr != null && dbTypeStr.toLowerCase().contains("postgre")) ? "postgresql" : "sqlserver";

        // Mock fallback
        if (MockMetadataProvider.isEnabled() || BackendFlags.mock(BackendFlags.Area.METADATA)) {
            log.info("Loading mock TableDesign for {}.{}.{}", catalog, schema, table);
            return Result.success(createMockTableDesign(engine, catalog, schema, table));
        }

        DatabaseType type = ConnectionSessionManager.mapDatabaseType(dbTypeStr);
        if (type == null) {
            return Result.failure(AppError.validation("UNSUPPORTED_DATABASE_TYPE",
                    "Database type " + dbTypeStr + " is not supported in Table Designer"));
        }
        if (type == DatabaseType.SQLITE) {
            return Result.failure(AppError.validation("SQLITE_UNSUPPORTED",
                    "Table Designer chưa hỗ trợ SQLite (do SQLite không hỗ trợ ALTER TABLE đầy đủ)"));
        }

        Optional<DatabaseProvider> providerOpt = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
        if (providerOpt.isEmpty()) {
            return Result.failure(AppError.validation("PROVIDER_NOT_FOUND", "No provider registered for " + type));
        }

        try (DatabaseConnection conn = DesktopMetadataService.getInstance().explorerConnection(profile, catalog)) {
            return providerOpt.get().getTableDesign(conn, catalog, schema, table);
        } catch (Exception e) {
            log.error("Failed to load table design for {}.{}.{}: {}", catalog, schema, table, e.getMessage(), e);
            return Result.failure(AppError.system("Failed to load table design: " + e.getMessage(), e));
        }
    }

    /**
     * Asynchronously loads the table design on a worker thread.
     */
    public CompletableFuture<Result<TableDesign>> loadTableDesignAsync(
            ConnectionProfileDto profile, String catalog, String schema, String table) {
        return AsyncTaskManager.getInstance().executeAsync(
                () -> loadTableDesign(profile, catalog, schema, table),
                null,
                null
        );
    }

    /**
     * Clones an existing TableDesign for a Copy Table workflow (SCR-TD-001 / TD5).
     */
    public Result<TableDesign> cloneForCopy(TableDesign source, String newSchema, String newTableName, CopyTableOptions options) {
        if (source == null) {
            return Result.failure(AppError.validation("NULL_SOURCE_DESIGN", "Source table design cannot be null"));
        }
        if (newTableName == null || newTableName.isBlank()) {
            return Result.failure(AppError.validation("EMPTY_TABLE_NAME", "Target table name cannot be empty"));
        }
        TableDesign clone = source.cloneForCopy(newSchema, newTableName, options);
        return Result.success(clone);
    }

    /**
     * Generates a migration plan comparing original and modified designs.
     */
    public Result<MigrationPlan> generateMigrationPlan(TableDesign original, TableDesign modified) {
        if (modified == null) {
            return Result.failure(AppError.validation("NULL_MODIFIED", "Modified table design cannot be null"));
        }

        // Validate modified design first
        ValidationResult valResult = validator.validate(modified);
        if (!valResult.isValid()) {
            StringBuilder sb = new StringBuilder();
            for (AppError.ValidationError err : valResult.errors()) {
                if (!sb.isEmpty()) sb.append("; ");
                sb.append(err.message());
            }
            return Result.failure(AppError.validation("VALIDATION_ERROR", sb.toString()));
        }

        DdlDialect dialect = getDialect(modified.getEngine());
        MigrationPlan plan = differ.computeDiff(original, modified, dialect);
        return Result.success(plan);
    }

    /**
     * Applies the migration plan within a single transaction.
     * Enforces strict read-only safety guardrails (-Dsqlide.readOnly=true).
     */
    public Result<Void> applyMigration(ConnectionProfileDto profile, String catalog, MigrationPlan plan, String customSql) {
        if (profile == null) {
            return Result.failure(AppError.validation("NULL_PROFILE", "Connection profile cannot be null"));
        }

        // 1. Guardrail checks
        if (BackendFlags.readOnlyAll()) {
            return Result.failure(AppError.business("READ_ONLY_GUARDRAIL",
                    "Chế độ chỉ đọc đang bật (-Dsqlide.readOnly=true). Mọi thao tác DDL bị chặn."));
        }
        if (profile.readOnly()) {
            return Result.failure(AppError.business("READ_ONLY_PROFILE",
                    "Kết nối \"" + profile.name() + "\" đang ở chế độ chỉ đọc. Thao tác DDL bị chặn."));
        }

        // 2. Mock mode
        if (MockMetadataProvider.isEnabled() || BackendFlags.mock(BackendFlags.Area.METADATA)) {
            log.info("Simulating migration apply in mock mode for profile {}", profile.name());
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            DesktopMetadataService.getInstance().invalidate(profile.id());
            return Result.success(null);
        }

        // 3. Extract SQL statements
        List<String> statements = new ArrayList<>();
        if (customSql != null && !customSql.isBlank()) {
            for (SqlStatementSplitter.Part part : SqlStatementSplitter.split(customSql)) {
                String stmt = part.text().trim();
                if (!stmt.isBlank()) {
                    statements.add(stmt);
                }
            }
        } else if (plan != null && !plan.statements().isEmpty()) {
            for (MigrationStatement ms : plan.statements()) {
                String sql = ms.sql().trim();
                if (!sql.isBlank()) {
                    statements.add(sql);
                }
            }
        }

        if (statements.isEmpty()) {
            return Result.success(null); // Nothing to execute
        }

        // 4. Open exclusive execution connection and execute in a single transaction
        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
        String targetDb = catalog != null && !catalog.isBlank() ? catalog : profile.databaseName();
        String pwd = CredentialResolver.resolve(profile, null);

        Result<DatabaseConnection> connRes = ConnectionSessionManager.getInstance().openSession(profile, targetDb, pwd);
        if (connRes.isFailure()) {
            return Result.failure(connRes.getError().get());
        }

        try (DatabaseConnection conn = connRes.getDataOrThrow()) {
            Connection raw = conn.getRawJdbcConnection();
            boolean prevAutoCommit = raw.getAutoCommit();
            raw.setAutoCommit(false);

            try (Statement st = raw.createStatement()) {
                for (int i = 0; i < statements.size(); i++) {
                    String sql = statements.get(i);
                    log.info("Executing DDL migration statement [{}/{}]: {}", i + 1, statements.size(), sql);
                    try {
                        st.execute(sql);
                    } catch (Exception stmtEx) {
                        log.error("Statement [{}/{}] failed, rolling back: {}", i + 1, statements.size(), stmtEx.getMessage());
                        try {
                            raw.rollback();
                        } catch (Exception rbEx) {
                            log.error("Rollback failed: {}", rbEx.getMessage());
                        }
                        return Result.failure(AppError.system(
                                String.format("Lỗi tại lệnh %d/%d: %s\nCâu lệnh: %s\n(Giao dịch đã được rollback an toàn)",
                                        i + 1, statements.size(), stmtEx.getMessage(), sql),
                                stmtEx
                        ));
                    }
                }
                raw.commit();
            } finally {
                try {
                    raw.setAutoCommit(prevAutoCommit);
                } catch (Exception ignored) {}
            }

            // Invalidate Explorer cache for this database
            DesktopMetadataService.getInstance().invalidate(profile.id());
            DesktopMetadataService.getInstance().invalidateNode(profile.id(), targetDb != null ? targetDb : "");

            return Result.success(null);
        } catch (Exception e) {
            log.error("Error connecting to execute migration: {}", e.getMessage(), e);
            return Result.failure(AppError.system("Error executing migration: " + e.getMessage(), e));
        }
    }

    /**
     * Creates a realistic mock TableDesign for prototype and test environments.
     */
    public TableDesign createMockTableDesign(String engine, String catalog, String schema, String tableName) {
        boolean isPg = "postgresql".equalsIgnoreCase(engine);
        String s = schema != null && !schema.isBlank() ? schema : (isPg ? "public" : "dbo");
        String t = tableName != null && !tableName.isBlank() ? tableName : "Customers";

        TableDesign design = new TableDesign(isPg ? "postgresql" : "sqlserver", s, t);
        design.setOriginalTable(t);
        design.setDatabase(catalog != null ? catalog : (isPg ? "postgres" : "master"));
        design.setComment("Mock table for Table Designer testing");

        if (isPg) {
            ColumnDesign c1 = ColumnDesign.loadedFromDb("id", "serial");
            c1.setPrimaryKey(true);
            c1.setIdentity(true);

            ColumnDesign c2 = ColumnDesign.loadedFromDb("customer_code", "varchar");
            c2.setLength(20);
            c2.setNotNull(true);

            ColumnDesign c3 = ColumnDesign.loadedFromDb("full_name", "varchar");
            c3.setLength(150);
            c3.setNotNull(true);

            ColumnDesign c4 = ColumnDesign.loadedFromDb("email", "varchar");
            c4.setLength(255);
            c4.setNotNull(true);

            ColumnDesign c5 = ColumnDesign.loadedFromDb("phone_number", "varchar");
            c5.setLength(30);

            ColumnDesign c6 = ColumnDesign.loadedFromDb("balance", "numeric");
            c6.setLength(18);
            c6.setScale(2);
            c6.setNotNull(true);
            c6.setDefaultValue("0.00");

            ColumnDesign c7 = ColumnDesign.loadedFromDb("status", "varchar");
            c7.setLength(20);
            c7.setNotNull(true);
            c7.setDefaultValue("'ACTIVE'");

            ColumnDesign c8 = ColumnDesign.loadedFromDb("created_at", "timestamptz");
            c8.setNotNull(true);
            c8.setDefaultValue("CURRENT_TIMESTAMP");

            design.addColumn(c1);
            design.addColumn(c2);
            design.addColumn(c3);
            design.addColumn(c4);
            design.addColumn(c5);
            design.addColumn(c6);
            design.addColumn(c7);
            design.addColumn(c8);

            IndexDesign idx = new IndexDesign("ix_customers_code");
            idx.setIndexType(IndexType.BTREE);
            idx.getColumns().add(new IndexColumn("customer_code", false));
            design.getIndexes().add(idx);

            UniqueDesign uq = new UniqueDesign("uq_customers_email");
            uq.getColumns().add("email");
            design.getUniques().add(uq);

            CheckDesign ck = new CheckDesign("ck_customers_balance", "balance >= 0");
            design.getChecks().add(ck);

            TriggerDesign tr = new TriggerDesign("tr_customers_audit", "-- audit log trigger");
            tr.setTiming("AFTER");
            tr.getEvents().addAll(List.of("INSERT", "UPDATE"));
            design.getTriggers().add(tr);
        } else {
            ColumnDesign c1 = ColumnDesign.loadedFromDb("Id", "int");
            c1.setPrimaryKey(true);
            c1.setIdentity(true);
            c1.setIdentitySeed(1L);
            c1.setIdentityIncrement(1L);

            ColumnDesign c2 = ColumnDesign.loadedFromDb("CustomerCode", "varchar");
            c2.setLength(20);
            c2.setNotNull(true);

            ColumnDesign c3 = ColumnDesign.loadedFromDb("FullName", "nvarchar");
            c3.setLength(150);
            c3.setNotNull(true);

            ColumnDesign c4 = ColumnDesign.loadedFromDb("Email", "varchar");
            c4.setLength(255);
            c4.setNotNull(true);

            ColumnDesign c5 = ColumnDesign.loadedFromDb("PhoneNumber", "varchar");
            c5.setLength(30);

            ColumnDesign c6 = ColumnDesign.loadedFromDb("Balance", "decimal");
            c6.setLength(18);
            c6.setScale(2);
            c6.setNotNull(true);
            c6.setDefaultValue("0.00");

            ColumnDesign c7 = ColumnDesign.loadedFromDb("Status", "varchar");
            c7.setLength(20);
            c7.setNotNull(true);
            c7.setDefaultValue("'ACTIVE'");

            ColumnDesign c8 = ColumnDesign.loadedFromDb("CreatedAt", "datetime2");
            c8.setNotNull(true);
            c8.setDefaultValue("GETDATE()");

            design.addColumn(c1);
            design.addColumn(c2);
            design.addColumn(c3);
            design.addColumn(c4);
            design.addColumn(c5);
            design.addColumn(c6);
            design.addColumn(c7);
            design.addColumn(c8);

            IndexDesign idx = new IndexDesign("IX_Customers_Code");
            idx.setIndexType(IndexType.NONCLUSTERED);
            idx.getColumns().add(new IndexColumn("CustomerCode", false));
            design.getIndexes().add(idx);

            UniqueDesign uq = new UniqueDesign("UQ_Customers_Email");
            uq.getColumns().add("Email");
            design.getUniques().add(uq);

            CheckDesign ck = new CheckDesign("CK_Customers_Balance", "[Balance] >= 0");
            design.getChecks().add(ck);

            TriggerDesign tr = new TriggerDesign("TR_Customers_Audit", "-- audit log trigger");
            tr.setTiming("AFTER");
            tr.getEvents().addAll(List.of("INSERT", "UPDATE"));
            design.getTriggers().add(tr);
        }

        return design;
    }
}

