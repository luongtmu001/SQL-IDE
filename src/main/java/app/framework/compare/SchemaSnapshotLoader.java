package app.framework.compare;

import app.framework.backend.BackendFlags;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import app.framework.designer.TableDesignerService;
import app.framework.metadata.DesktopMetadataService;
import app.framework.metadata.MockMetadataProvider;
import app.framework.metadata.ObjectDefinitionReader;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.schemacompare.SchemaCompareService;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.schemacompare.model.SchemaSnapshot;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.DatabaseProvider;
import com.lltool.infrastructure.database.api.DatabaseType;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Loads real database SchemaSnapshot for Schema Compare (SC-1, SC-10).
 * Queries tables and invokes getTableDesign concurrently, views/procedures/functions/triggers via ObjectDefinitionReader.
 */
public class SchemaSnapshotLoader {

    private static final Logger log = LoggerFactory.getLogger(SchemaSnapshotLoader.class);
    private static final ObjectDefinitionReader defReader = new ObjectDefinitionReader();
    private static final SchemaCompareService compareService = new SchemaCompareService();

    public record SnapshotLoadResult(
            SchemaSnapshot snapshot,
            List<String> warnings
    ) {}

    public static SnapshotLoadResult load(ConnectionProfileDto profile, String catalog, String schema,
                                          boolean isSource, Consumer<String> progressCallback) throws Exception {
        String role = isSource ? "nguồn" : "đích";
        String engine = (profile != null && profile.databaseType() != null && profile.databaseType().toLowerCase().contains("postgre"))
                ? "postgresql" : "sqlserver";
        String targetCat = catalog != null && !catalog.isBlank() ? catalog : (profile != null ? profile.databaseName() : "default");
        String targetSch = schema != null && !schema.isBlank() && !schema.equals("*") ? schema : (engine.equals("postgresql") ? "public" : "dbo");

        SchemaSnapshot snapshot = new SchemaSnapshot(engine, targetCat, targetSch);
        List<String> warnings = new ArrayList<>();

        if (profile == null || MockMetadataProvider.isEnabled() || BackendFlags.mock(BackendFlags.Area.METADATA)) {
            if (progressCallback != null) {
                progressCallback.accept("Đang nạp dữ liệu mẫu cho phía " + role + "...");
            }
            // Mock snapshot fallback when mock mode is enabled
            return new SnapshotLoadResult(createMockSnapshot(engine, targetCat, targetSch, isSource), warnings);
        }

        if (progressCallback != null) {
            progressCallback.accept("Đang kết nối và quét danh sách bảng phía " + role + "...");
        }

        DatabaseType type = ConnectionSessionManager.mapDatabaseType(profile.databaseType());
        Optional<DatabaseProvider> providerOpt = DesktopBootstrap.getInstance().getProviderRegistry().getProvider(type);
        if (providerOpt.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy DatabaseProvider cho kiểu CSDL " + type);
        }
        DatabaseProvider provider = providerOpt.get();

        // 1. Fetch tables in schema
        List<TableMetadata> tableList = new ArrayList<>();
        try (DatabaseConnection metaConn = DesktopMetadataService.getInstance().explorerConnection(profile, targetCat)) {
            Result<List<TableMetadata>> tRes = provider.getTables(metaConn, targetCat, targetSch);
            if (tRes != null && tRes.isSuccess() && tRes.getData().isPresent()) {
                for (TableMetadata tm : tRes.getData().get()) {
                    if (!tm.isView()) {
                        tableList.add(tm);
                    }
                }
            } else if (tRes != null && !tRes.isSuccess()) {
                warnings.add("Không thể lấy danh sách bảng: " + tRes.getError().map(e -> e.message()).orElse("Unknown"));
            }
        } catch (Exception ex) {
            String msg = "Lỗi khi lấy danh sách bảng phía " + role + ": " + ex.getMessage();
            log.warn(msg, ex);
            warnings.add(msg);
        }

        // 2. Concurrently load TableDesign for tables (max 4 parallel connections)
        int totalTables = tableList.size();
        AtomicInteger completedTables = new AtomicInteger(0);

        if (totalTables > 0) {
            int threadCount = Math.min(4, totalTables);
            ExecutorService pool = Executors.newFixedThreadPool(threadCount, r -> {
                Thread t = new Thread(r, "SchemaCompare-TableLoader-" + role);
                t.setDaemon(true);
                return t;
            });

            List<Future<Result<TableDesign>>> futures = new ArrayList<>();
            for (TableMetadata tm : tableList) {
                futures.add(pool.submit(() -> {
                    try (DatabaseConnection conn = DesktopMetadataService.getInstance().explorerConnection(profile, targetCat)) {
                        Result<TableDesign> tdRes = provider.getTableDesign(conn, targetCat, targetSch, tm.name());
                        int done = completedTables.incrementAndGet();
                        if (progressCallback != null) {
                            progressCallback.accept(String.format("Phía %s: Đang nạp thiết kế bảng (%d/%d)...", role, done, totalTables));
                        }
                        return tdRes;
                    }
                }));
            }
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.MINUTES);

            for (Future<Result<TableDesign>> f : futures) {
                try {
                    Result<TableDesign> r = f.get();
                    if (r.isSuccess()) {
                        snapshot.addTable(r.getDataOrThrow());
                    } else {
                        warnings.add("Lỗi nạp bảng: " + r.getError().map(e -> e.message()).orElse("Unknown"));
                    }
                } catch (Exception e) {
                    warnings.add("Lỗi luồng nạp bảng: " + e.getMessage());
                }
            }
        }

        // 3. Views, Procedures, Functions, Triggers via ObjectDefinitionReader
        if (progressCallback != null) {
            progressCallback.accept("Đang nạp Views, Procedures, Functions, Triggers phía " + role + "...");
        }

        try (DatabaseConnection conn = DesktopMetadataService.getInstance().explorerConnection(profile, targetCat)) {
            Connection rawConn = conn.getRawJdbcConnection();
            ObjectDefinitionReader.FilterOptions opts = new ObjectDefinitionReader.FilterOptions(
                    targetCat, targetSch,
                    Set.of("VIEW", "PROCEDURE", "FUNCTION", "TRIGGER")
            );
            ObjectDefinitionReader.ObjectDefinitionResult defRes = defReader.readDefinitions(rawConn, engine, opts);

            for (ObjectDefinitionReader.ObjectDefinition def : defRes.definitions()) {
                switch (def.type().toUpperCase()) {
                    case "VIEW" -> snapshot.addView(def.name(), def.definition());
                    case "PROCEDURE" -> snapshot.addProcedure(def.name(), def.definition());
                    case "FUNCTION" -> snapshot.addFunction(def.name(), def.definition(), def.identityArgs());
                    case "TRIGGER" -> snapshot.addTrigger(def.name(), def.definition(), def.parentTable());
                }
            }
            warnings.addAll(defRes.warnings());
        } catch (Exception ex) {
            String msg = "Lỗi khi lấy định nghĩa đối tượng phía " + role + ": " + ex.getMessage();
            log.warn(msg, ex);
            warnings.add(msg);
        }

        return new SnapshotLoadResult(snapshot, warnings);
    }

    public static SchemaSnapshot createMockSnapshot(String engine, String database, String schema, boolean isSource) {
        return compareService.createMockSnapshot(engine, database, schema, isSource);
    }
}
