package app.framework.bootstrap;

import app.framework.connection.DesktopConnectionTesterAdapter;
import app.framework.connection.DesktopCredentialVaultAdapter;
import com.lltool.core.api.application.CorePlatform;
import com.lltool.core.api.connection.ConnectionApi;
import com.lltool.core.api.project.ProjectApi;
import com.lltool.core.api.workspace.WorkspaceApi;
import com.lltool.infrastructure.configuration.service.ConfigurationService;
import com.lltool.infrastructure.database.api.DatabaseProviderRegistry;
import com.lltool.infrastructure.database.postgresql.PostgreSqlDatabaseProvider;
import com.lltool.infrastructure.database.sqlite.SqliteDatabaseProvider;
import com.lltool.infrastructure.database.sqlserver.SqlServerDatabaseProvider;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import com.lltool.infrastructure.security.SecureCredentialVault;
import com.lltool.infrastructure.storage.local.LocalConnectionRepository;
import com.lltool.infrastructure.storage.local.LocalProjectRepository;
import com.lltool.infrastructure.storage.local.LocalWorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Desktop Framework Application Bootstrap (Phase 4, Phase 6.1 & Phase 6.2).
 * Coordinates initialization of Infrastructure, Core Platform, and Desktop Services.
 */
public class DesktopBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DesktopBootstrap.class);
    private static DesktopBootstrap instance;
    private static volatile java.util.function.IntConsumer progressListener = step -> { };

    /** Told the number of the step about to start (1 directories, 2 configuration, 3 vault, 4 providers). */
    public static void setProgressListener(java.util.function.IntConsumer listener) {
        progressListener = listener != null ? listener : step -> { };
    }

    private final AppDirectoryManager directoryManager;
    private final ConfigurationService configurationService;
    private final SecureCredentialVault credentialVault;
    private final DatabaseProviderRegistry providerRegistry;
    private final LocalProjectRepository projectRepository;
    private final LocalWorkspaceRepository workspaceRepository;
    private final LocalConnectionRepository connectionRepository;
    private final CorePlatform corePlatform;

    private DesktopBootstrap() {
        log.info("Starting Desktop Platform Bootstrap...");

        // 1. Filesystem Directory Manager
        progressListener.accept(1);
        this.directoryManager = AppDirectoryManager.createDefault();

        // 2. Configuration Service
        progressListener.accept(2);
        this.configurationService = new ConfigurationService(directoryManager);

        // 3. Security Credential Vault
        progressListener.accept(3);
        this.credentialVault = new SecureCredentialVault(directoryManager);

        // 4. Database Provider Registry
        progressListener.accept(4);
        this.providerRegistry = new DatabaseProviderRegistry();
        providerRegistry.register(new PostgreSqlDatabaseProvider());
        providerRegistry.register(new SqliteDatabaseProvider());
        providerRegistry.register(new SqlServerDatabaseProvider());

        // 5. Local Storage & Repositories
        this.projectRepository = new LocalProjectRepository(directoryManager);
        this.workspaceRepository = new LocalWorkspaceRepository(directoryManager);
        this.connectionRepository = new LocalConnectionRepository(directoryManager);

        // 6. Core Platform Composition Root (Clean Architecture Ports & Adapters)
        DesktopCredentialVaultAdapter vaultAdapter = new DesktopCredentialVaultAdapter(credentialVault);
        DesktopConnectionTesterAdapter testerAdapter = new DesktopConnectionTesterAdapter(providerRegistry);

        this.corePlatform = CorePlatform.builder()
                .projectRepository(projectRepository)
                .workspaceRepository(workspaceRepository)
                .connectionRepository(connectionRepository)
                .credentialVaultPort(vaultAdapter)
                .connectionTesterPort(testerAdapter)
                .build();

        log.info("Desktop Platform Bootstrap completed successfully.");
    }

    public static synchronized DesktopBootstrap getInstance() {
        if (instance == null) {
            instance = new DesktopBootstrap();
        }
        return instance;
    }

    public static synchronized void resetForTesting() {
        instance = null;
    }

    public AppDirectoryManager getDirectoryManager() {
        return directoryManager;
    }

    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    public SecureCredentialVault getCredentialVault() {
        return credentialVault;
    }

    public DatabaseProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public LocalProjectRepository getProjectRepository() {
        return projectRepository;
    }

    public LocalWorkspaceRepository getWorkspaceRepository() {
        return workspaceRepository;
    }

    public LocalConnectionRepository getConnectionRepository() {
        return connectionRepository;
    }

    public CorePlatform getCorePlatform() {
        return corePlatform;
    }

    public ProjectApi getProjectApi() {
        return corePlatform.projectApi();
    }

    public WorkspaceApi getWorkspaceApi() {
        return corePlatform.workspaceApi();
    }

    public ConnectionApi getConnectionApi() {
        return corePlatform.connectionApi();
    }
}

